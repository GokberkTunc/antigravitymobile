package com.antigravity.mobile.core.accessibility

import com.antigravity.mobile.contracts.ContractsJson
import com.antigravity.mobile.contracts.UiActionRequest
import com.antigravity.mobile.contracts.UiActionType
import com.antigravity.mobile.contracts.UiBounds
import com.antigravity.mobile.contracts.UiNode
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UiTreeSnapshotAndProviderTest {

    private class FakeNodeHandle(
        override val className: String = "android.view.View",
        override val packageName: String = "com.example.app",
        override val text: String? = null,
        override val contentDesc: String? = null,
        override val resourceId: String? = null,
        override val bounds: UiBounds = UiBounds(0, 0, 100, 50),
        override val clickable: Boolean = false,
        override val longClickable: Boolean = false,
        override val editable: Boolean = false,
        override val scrollable: Boolean = false,
        override val focused: Boolean = false,
        override val checked: Boolean? = null,
        override val enabled: Boolean = true,
        override val visibleToUser: Boolean = true,
    ) : AccessibilityNodeHandle {
        val mutableChildren = mutableListOf<AccessibilityNodeHandle>()
        val performedActions = mutableListOf<Pair<UiActionType, String?>>()

        override val identityKey: Any get() = this
        override val childCount: Int get() = mutableChildren.size
        override fun childAt(index: Int): AccessibilityNodeHandle? = mutableChildren.getOrNull(index)
        override fun performAction(action: UiActionType, value: String?): Boolean {
            performedActions += action to value
            return true
        }
    }

    @Test
    fun `snapshot builder enforces maxDepth and maxNodes limits cleanly`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val builder = UiTreeSnapshotBuilder(dispatcher)

        // Build a 10-level deep chain
        val root = FakeNodeHandle(text = "level-0")
        var current = root
        for (i in 1..10) {
            val child = FakeNodeHandle(text = "level-$i")
            current.mutableChildren += child
            current = child
        }

        val depthLimited = builder.build(root, includeInvisible = false, maxDepth = 3, maxNodes = 500)
        assertTrue(depthLimited.truncatedByDepth)
        assertFalse(depthLimited.truncatedByNodeLimit)
        assertEquals(4, depthLimited.totalNodes) // depths 0, 1, 2, 3

        // Build wide tree with 20 children and cap at maxNodes = 6
        val wideRoot = FakeNodeHandle(text = "root")
        repeat(20) { idx ->
            wideRoot.mutableChildren += FakeNodeHandle(text = "item-$idx")
        }
        val nodeLimited = builder.build(wideRoot, includeInvisible = false, maxDepth = 30, maxNodes = 6)
        assertTrue(nodeLimited.truncatedByNodeLimit)
        assertEquals(6, nodeLimited.totalNodes)
    }

    @Test
    fun `snapshot builder protects against cyclic node graphs via IdentityHashMap`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val builder = UiTreeSnapshotBuilder(dispatcher)

        val root = FakeNodeHandle(text = "root")
        val child = FakeNodeHandle(text = "child")
        val grandChild = FakeNodeHandle(text = "grandchild")
        root.mutableChildren += child
        child.mutableChildren += grandChild
        // Create direct and ancestor cycles!
        grandChild.mutableChildren += root
        grandChild.mutableChildren += child
        child.mutableChildren += child

        val snapshot = builder.build(root, includeInvisible = true, maxDepth = 30, maxNodes = 500)
        assertEquals(3, snapshot.totalNodes)
        assertNotNull(snapshot.root)
        assertEquals("n0", snapshot.root?.nodeId)
        assertEquals(1, snapshot.root?.children?.size)
        assertEquals(1, snapshot.root?.children?.first()?.children?.size)
        assertTrue(snapshot.root?.children?.first()?.children?.first()?.children.orEmpty().isEmpty())
    }

    @Test
    fun `snapshot builder filters invisible nodes and produces compact JSON`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val builder = UiTreeSnapshotBuilder(dispatcher)

        val root = FakeNodeHandle(text = "visible-root", visibleToUser = true)
        val hidden = FakeNodeHandle(text = "hidden-node", visibleToUser = false)
        val visibleChild = FakeNodeHandle(text = "Send", clickable = true, visibleToUser = true)
        root.mutableChildren += hidden
        root.mutableChildren += visibleChild

        val visibleOnly = builder.build(root, includeInvisible = false)
        assertEquals(2, visibleOnly.totalNodes)
        val json = builder.toCompactJson(visibleOnly.root)
        assertFalse(json.contains("hidden-node"))
        assertTrue(json.contains("\"Send\""))
        assertEquals(visibleOnly.root, ContractsJson.decodeFromString(UiNode.serializer(), json))

        val withHidden = builder.build(root, includeInvisible = true)
        assertEquals(3, withHidden.totalNodes)
    }

    @Test
    fun `AccessibilityUiTreeProvider resolves actions by nodeId, resourceId, text, and global actions`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val bridge = AccessibilityServiceBridge()
            val builder = UiTreeSnapshotBuilder(dispatcher)
            val provider = AccessibilityUiTreeProvider(bridge, builder, dispatcher)

            val inputField = FakeNodeHandle(
                className = "android.widget.EditText",
                resourceId = "com.example.chat:id/message_input",
                editable = true,
            )
            val sendBtn = FakeNodeHandle(
                className = "android.widget.Button",
                text = "Send Message",
                resourceId = "com.example.chat:id/send_btn",
                clickable = true,
            )
            val root = FakeNodeHandle(packageName = "com.example.chat").apply {
                mutableChildren += inputField
                mutableChildren += sendBtn
            }

            val globalActionsExecuted = mutableListOf<UiActionType>()
            val fakeHost = object : AccessibilityHost {
                override fun activeRootNode(): AccessibilityNodeHandle = root
                override fun activePackageName(): String = "com.example.chat"
                override fun performGlobalAction(action: UiActionType): Boolean {
                    globalActionsExecuted += action
                    return true
                }
            }

            assertFalse(provider.isServiceConnected.value)
            assertNull(provider.captureUiTree())

            bridge.attachHost(fakeHost)
            assertTrue(provider.isServiceConnected.value)
            assertEquals("com.example.chat", provider.getActivePackageName())

            val captured = provider.captureUiTree()
            assertNotNull(captured)
            val sendNodeId = captured!!.children[1].nodeId

            // Action by cached nodeId
            val clickRes = provider.performAction(UiActionRequest(action = UiActionType.CLICK, nodeId = sendNodeId))
            assertTrue(clickRes.success)
            assertEquals(sendNodeId, clickRes.matchedNodeId)
            assertEquals(listOf(UiActionType.CLICK to null), sendBtn.performedActions)

            // Action by suffix resourceId with SET_TEXT value
            val setTextRes = provider.performAction(
                UiActionRequest(
                    action = UiActionType.SET_TEXT,
                    targetResourceId = "message_input",
                    value = "Merhaba",
                ),
            )
            assertTrue(setTextRes.success)
            assertEquals(listOf(UiActionType.SET_TEXT to "Merhaba"), inputField.performedActions)

            // Action by text lookup
            val longClickRes = provider.performAction(
                UiActionRequest(action = UiActionType.LONG_CLICK, targetText = "send message"),
            )
            assertTrue(longClickRes.success)
            assertEquals(2, sendBtn.performedActions.size)
            assertEquals(UiActionType.LONG_CLICK to null, sendBtn.performedActions.last())

            // Global action BACK
            val backRes = provider.performAction(UiActionRequest(action = UiActionType.BACK))
            assertTrue(backRes.success)
            assertEquals(listOf(UiActionType.BACK), globalActionsExecuted)

            bridge.detachHost(fakeHost)
            assertFalse(provider.isServiceConnected.value)
        }
}
