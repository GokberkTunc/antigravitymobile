package com.antigravity.mobile.core.accessibility

import com.antigravity.mobile.contracts.ContractsJson
import com.antigravity.mobile.contracts.UiNode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.IdentityHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class UiTreeSnapshot(
    val root: UiNode?,
    val nodesById: Map<String, AccessibilityNodeHandle>,
    val uiNodesById: Map<String, UiNode>,
    val totalNodes: Int,
    val truncatedByDepth: Boolean,
    val truncatedByNodeLimit: Boolean,
)

/**
 * Traverses an [AccessibilityNodeHandle] hierarchy strictly off the Android Main Thread
 * (`Dispatchers.Default`), enforcing `maxDepth` (default 30), `maxNodes` (default 500),
 * visibility filtering, and cycle protection via [IdentityHashMap].
 */
@Singleton
class UiTreeSnapshotBuilder(
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    @Inject
    constructor() : this(defaultDispatcher = Dispatchers.Default)

    suspend fun build(
        rootHandle: AccessibilityNodeHandle?,
        includeInvisible: Boolean = false,
        maxDepth: Int = DEFAULT_MAX_DEPTH,
        maxNodes: Int = DEFAULT_MAX_NODES,
    ): UiTreeSnapshot = withContext(defaultDispatcher) {
        if (rootHandle == null) {
            return@withContext UiTreeSnapshot(
                root = null,
                nodesById = emptyMap(),
                uiNodesById = emptyMap(),
                totalNodes = 0,
                truncatedByDepth = false,
                truncatedByNodeLimit = false,
            )
        }

        val boundedMaxDepth = maxDepth.coerceAtLeast(0)
        val boundedMaxNodes = maxNodes.coerceAtLeast(1)
        val visited = IdentityHashMap<Any, Boolean>()
        val handlesById = LinkedHashMap<String, AccessibilityNodeHandle>()
        val uiNodesById = LinkedHashMap<String, UiNode>()
        var nextId = 0
        var truncatedByDepth = false
        var truncatedByNodeLimit = false

        fun traverse(handle: AccessibilityNodeHandle, depth: Int): UiNode? {
            if (handlesById.size >= boundedMaxNodes) {
                truncatedByNodeLimit = true
                return null
            }

            val key = handle.identityKey
            if (visited.containsKey(key)) {
                // Cycle protection: never re-enter an already visited node reference.
                return null
            }

            if (!includeInvisible && !handle.visibleToUser) {
                return null
            }

            visited[key] = true
            val nodeId = "n${nextId++}"
            handlesById[nodeId] = handle

            val children = mutableListOf<UiNode>()
            val count = handle.childCount
            if (count > 0) {
                if (depth >= boundedMaxDepth) {
                    truncatedByDepth = true
                } else {
                    for (index in 0 until count) {
                        if (handlesById.size >= boundedMaxNodes) {
                            truncatedByNodeLimit = true
                            break
                        }
                        val childHandle = handle.childAt(index) ?: continue
                        val childNode = traverse(childHandle, depth + 1)
                        if (childNode != null) {
                            children += childNode
                        }
                    }
                }
            }

            val uiNode = UiNode(
                nodeId = nodeId,
                className = handle.className,
                packageName = handle.packageName,
                text = handle.text,
                contentDesc = handle.contentDesc,
                resourceId = handle.resourceId,
                bounds = handle.bounds,
                clickable = handle.clickable,
                longClickable = handle.longClickable,
                editable = handle.editable,
                scrollable = handle.scrollable,
                focused = handle.focused,
                checked = handle.checked,
                enabled = handle.enabled,
                visible = handle.visibleToUser,
                children = children,
            )
            uiNodesById[nodeId] = uiNode
            return uiNode
        }

        val rootNode = traverse(rootHandle, depth = 0)
        UiTreeSnapshot(
            root = rootNode,
            nodesById = handlesById,
            uiNodesById = uiNodesById,
            totalNodes = handlesById.size,
            truncatedByDepth = truncatedByDepth,
            truncatedByNodeLimit = truncatedByNodeLimit,
        )
    }

    fun toCompactJson(root: UiNode?): String {
        if (root == null) return "null"
        return ContractsJson.encodeToString(UiNode.serializer(), root)
    }

    companion object {
        const val DEFAULT_MAX_DEPTH = 30
        const val DEFAULT_MAX_NODES = 500
    }
}
