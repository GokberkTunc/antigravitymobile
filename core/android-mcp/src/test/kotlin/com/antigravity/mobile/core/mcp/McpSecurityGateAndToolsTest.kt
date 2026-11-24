package com.antigravity.mobile.core.mcp

import com.antigravity.mobile.contracts.McpToolCall
import com.antigravity.mobile.contracts.McpToolNames
import com.antigravity.mobile.contracts.McpToolRisk
import com.antigravity.mobile.contracts.OverlayController
import com.antigravity.mobile.contracts.PolicyDecision
import com.antigravity.mobile.contracts.PolicyEvaluation
import com.antigravity.mobile.contracts.RootExecutor
import com.antigravity.mobile.contracts.SecurityPolicyEngine
import com.antigravity.mobile.contracts.ShellResult
import com.antigravity.mobile.contracts.UiActionResult
import com.antigravity.mobile.contracts.UiBounds
import com.antigravity.mobile.contracts.UiNode
import com.antigravity.mobile.contracts.UiTreeProvider
import com.antigravity.mobile.core.accessibility.NotificationBridge
import com.antigravity.mobile.core.accessibility.NotificationRecord
import com.antigravity.mobile.core.root.RootFileEntry
import com.antigravity.mobile.core.root.RootFileListResult
import com.antigravity.mobile.core.root.RootFileReadResult
import com.antigravity.mobile.core.root.RootFileWriteResult
import com.antigravity.mobile.core.root.SuFileRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class McpSecurityGateAndToolsTest {

    @Test
    fun `CRITICAL tools cannot execute when SecurityPolicyEngine returns DENY`() = runTest {
        val dispatcherIo = StandardTestDispatcher(testScheduler)
        val rootExecutor = mockk<RootExecutor>(relaxed = true)
        val suFileRepo = mockk<SuFileRepository>(relaxed = true)
        val uiTreeProvider = mockk<UiTreeProvider>(relaxed = true)
        val notificationBridge = mockk<NotificationBridge>(relaxed = true)
        val gateway = mockk<AndroidSystemToolGateway>(relaxed = true)
        val policyEngine = mockk<SecurityPolicyEngine>()
        val overlayController = mockk<OverlayController>(relaxed = true)

        coEvery { uiTreeProvider.getActivePackageName() } returns "com.ziraat.ziraatmobil"
        coEvery { policyEngine.evaluate(any(), any(), any(), any(), any()) } answers {
            PolicyEvaluation(
                toolName = firstArg(),
                riskLevel = secondArg(),
                decision = PolicyDecision.DENY,
                reason = "Blocked by security policy",
            )
        }

        val dispatcher = McpToolDispatcher(
            rootExecutor, suFileRepo, uiTreeProvider, notificationBridge,
            gateway, policyEngine, overlayController, dispatcherIo,
        )

        // 1. root_exec DENIED
        val rootRes = dispatcher.dispatch(
            McpToolCall("c1", McpToolNames.ROOT_EXEC, """{"command":"rm -rf /data"}"""),
        )
        assertTrue(rootRes.isError)
        assertTrue(rootRes.textContent.contains("Policy denied"))
        coVerify(exactly = 0) { rootExecutor.exec(any(), any(), any()) }

        // 2. phone_call_control DENIED
        val callRes = dispatcher.dispatch(
            McpToolCall("c2", McpToolNames.PHONE_CALL_CONTROL, """{"action":"call","phone_number":"+905550000000"}"""),
        )
        assertTrue(callRes.isError)
        coVerify(exactly = 0) { gateway.phoneCallControl(any(), any(), any()) }

        // 3. sms_manage DENIED
        val smsRes = dispatcher.dispatch(
            McpToolCall("c3", McpToolNames.SMS_MANAGE, """{"action":"send","address":"+905550000000","body":"hi"}"""),
        )
        assertTrue(smsRes.isError)
        coVerify(exactly = 0) { gateway.smsManage(any(), any(), any(), any()) }

        // Overlay approval prompt must not even be requested on hard DENY
        coVerify(exactly = 0) { overlayController.requestUserApproval(any()) }

        // Foreground protected package was passed to policy engine for every call
        coVerify(exactly = 1) {
            policyEngine.evaluate(
                toolName = McpToolNames.ROOT_EXEC,
                riskLevel = McpToolRisk.CRITICAL,
                args = any(),
                foregroundPackage = "com.ziraat.ziraatmobil",
                projectId = null,
            )
        }
    }

    @Test
    fun `CRITICAL tools cannot execute when REQUIRE_APPROVAL is rejected, and execute only when approved`() =
        runTest {
            val dispatcherIo = StandardTestDispatcher(testScheduler)
            val rootExecutor = mockk<RootExecutor>()
            val suFileRepo = mockk<SuFileRepository>(relaxed = true)
            val uiTreeProvider = mockk<UiTreeProvider>(relaxed = true)
            val notificationBridge = mockk<NotificationBridge>(relaxed = true)
            val gateway = mockk<AndroidSystemToolGateway>()
            val policyEngine = mockk<SecurityPolicyEngine>()
            val overlayController = mockk<OverlayController>(relaxed = true)

            coEvery { uiTreeProvider.getActivePackageName() } returns "com.android.dialer"
            coEvery { policyEngine.evaluate(any(), any(), any(), any(), any()) } answers {
                PolicyEvaluation(
                    toolName = firstArg(),
                    riskLevel = secondArg(),
                    decision = PolicyDecision.REQUIRE_APPROVAL,
                    reason = "CRITICAL action requires explicit user confirmation",
                )
            }

            val dispatcher = McpToolDispatcher(
                rootExecutor, suFileRepo, uiTreeProvider, notificationBridge,
                gateway, policyEngine, overlayController, dispatcherIo,
            )

            // User rejects approval -> NONE of the 3 CRITICAL tools may execute!
            coEvery { overlayController.requestUserApproval(any()) } returns false

            assertTrue(
                dispatcher.dispatch(McpToolCall("r1", McpToolNames.ROOT_EXEC, """{"command":"id"}""")).isError,
            )
            assertTrue(
                dispatcher.dispatch(
                    McpToolCall("r2", McpToolNames.PHONE_CALL_CONTROL, """{"action":"call","phone_number":"112"}"""),
                ).isError,
            )
            assertTrue(
                dispatcher.dispatch(
                    McpToolCall("r3", McpToolNames.SMS_MANAGE, """{"action":"send","address":"1234","body":"code"}"""),
                ).isError,
            )

            coVerify(exactly = 0) { rootExecutor.exec(any(), any(), any()) }
            coVerify(exactly = 0) { gateway.phoneCallControl(any(), any(), any()) }
            coVerify(exactly = 0) { gateway.smsManage(any(), any(), any(), any()) }

            // User approves -> CRITICAL tools execute cleanly
            coEvery { overlayController.requestUserApproval(any()) } returns true
            coEvery { rootExecutor.exec("id", any(), any()) } returns ShellResult(0, "uid=0(root)", "")
            coEvery { gateway.phoneCallControl("dial", "+905551112233", null) } returns
                buildJsonObject { put("success", true) }
            coEvery { gateway.smsManage("send", "+905551112233", "ok", 20) } returns
                buildJsonObject { put("sent", true) }

            val okRoot = dispatcher.dispatch(McpToolCall("a1", McpToolNames.ROOT_EXEC, """{"command":"id"}"""))
            assertFalse(okRoot.isError)
            assertTrue(okRoot.textContent.contains("uid=0(root)"))

            val okCall = dispatcher.dispatch(
                McpToolCall("a2", McpToolNames.PHONE_CALL_CONTROL, """{"action":"dial","phone_number":"+905551112233"}"""),
            )
            assertFalse(okCall.isError)

            val okSms = dispatcher.dispatch(
                McpToolCall("a3", McpToolNames.SMS_MANAGE, """{"action":"send","address":"+905551112233","body":"ok"}"""),
            )
            assertFalse(okSms.isError)
        }

    @Test
    fun `all 15 MCP tools dispatch cleanly when policy allows`() = runTest {
        val dispatcherIo = StandardTestDispatcher(testScheduler)
        val rootExecutor = mockk<RootExecutor>()
        val suFileRepo = mockk<SuFileRepository>()
        val uiTreeProvider = mockk<UiTreeProvider>()
        val notificationBridge = mockk<NotificationBridge>()
        val gateway = mockk<AndroidSystemToolGateway>()
        val policyEngine = mockk<SecurityPolicyEngine>()
        val overlayController = mockk<OverlayController>(relaxed = true)

        every { uiTreeProvider.isServiceConnected } returns MutableStateFlow(true)
        coEvery { uiTreeProvider.getActivePackageName() } returns "com.example.launcher"
        coEvery { uiTreeProvider.captureUiTree(any(), any(), any()) } returns
            UiNode("n0", bounds = UiBounds(0, 0, 100, 100))
        coEvery { uiTreeProvider.performAction(any()) } returns UiActionResult(true, "ok", "n0")

        every { notificationBridge.isConnected } returns MutableStateFlow(true)
        coEvery { notificationBridge.listActiveNotifications() } returns
            listOf(NotificationRecord("k1", "com.chat", "Hi", "Msg"))
        coEvery { notificationBridge.dismissNotification("k1") } returns true
        coEvery { notificationBridge.replyToNotification("k1", "ok") } returns true

        coEvery { rootExecutor.exec(any(), any(), any()) } returns ShellResult(0, "ok", "")
        coEvery { rootExecutor.execArgv(any(), any(), any()) } returns ShellResult(0, "ok", "")

        coEvery { suFileRepo.readFile("/sdcard/a.txt", 0L, 65_536) } returns
            Result.success(RootFileReadResult("/sdcard/a.txt", "hello", 0L, 5, false))
        coEvery { suFileRepo.writeFile("/sdcard/a.txt", "world", false) } returns
            Result.success(RootFileWriteResult("/sdcard/a.txt", 5, false))
        coEvery { suFileRepo.listDirectory("/sdcard", 0, 200) } returns
            Result.success(RootFileListResult("/sdcard", listOf(RootFileEntry("a.txt", "/sdcard/a.txt", false)), 1, false))

        coEvery { gateway.screenCapture(any(), any()) } returns buildJsonObject { put("format", "webp") }
        coEvery { gateway.appLaunchOrManage(any(), any(), any()) } returns buildJsonObject { put("ok", true) }
        coEvery { gateway.phoneCallControl(any(), any(), any()) } returns buildJsonObject { put("ok", true) }
        coEvery { gateway.smsManage(any(), any(), any(), any()) } returns buildJsonObject { put("ok", true) }
        coEvery { gateway.contactsCalendarQuery(any(), any(), any(), any(), any()) } returns buildJsonObject { put("ok", true) }
        coEvery { gateway.deviceControl(any(), any(), any()) } returns buildJsonObject { put("ok", true) }
        coEvery { gateway.mediaLocationCapture(any(), any(), any()) } returns buildJsonObject { put("ok", true) }

        coEvery { policyEngine.evaluate(any(), any(), any(), any(), any()) } answers {
            PolicyEvaluation(firstArg(), secondArg(), PolicyDecision.ALLOW, "ok")
        }

        val dispatcher = McpToolDispatcher(
            rootExecutor, suFileRepo, uiTreeProvider, notificationBridge,
            gateway, policyEngine, overlayController, dispatcherIo,
        )

        val calls = listOf(
            McpToolCall("1", McpToolNames.ROOT_EXEC, """{"command":"whoami"}"""),
            McpToolCall("2a", McpToolNames.FS_READ, """{"path":"/sdcard/a.txt"}"""),
            McpToolCall("2b", McpToolNames.FS_WRITE, """{"path":"/sdcard/a.txt","content":"world"}"""),
            McpToolCall("2c", McpToolNames.FS_LIST, """{"path":"/sdcard"}"""),
            McpToolCall("3", McpToolNames.UI_GET_TREE, """{}"""),
            McpToolCall("4", McpToolNames.UI_ACTION, """{"action":"CLICK","node_id":"n0"}"""),
            McpToolCall("5", McpToolNames.UI_INPUT_RAW, """{"type":"tap","x1":100,"y1":200}"""),
            McpToolCall("6", McpToolNames.SCREEN_CAPTURE, """{"scale":0.5,"format":"webp"}"""),
            McpToolCall("7", McpToolNames.APP_LAUNCH_OR_MANAGE, """{"action":"list"}"""),
            McpToolCall("8", McpToolNames.PHONE_CALL_CONTROL, """{"action":"dial","phone_number":"123"}"""),
            McpToolCall("9", McpToolNames.SMS_MANAGE, """{"action":"list"}"""),
            McpToolCall("10", McpToolNames.CONTACTS_CALENDAR_QUERY, """{"target":"contacts","action":"query"}"""),
            McpToolCall("11", McpToolNames.NOTIFICATIONS_MANAGE, """{"action":"list"}"""),
            McpToolCall("12", McpToolNames.DEVICE_CONTROL, """{"action":"wake"}"""),
            McpToolCall("13", McpToolNames.MEDIA_LOCATION_CAPTURE, """{"action":"get_location"}"""),
        )

        assertEquals(15, dispatcher.registeredTools().size)
        for (call in calls) {
            val result = dispatcher.dispatch(call)
            assertFalse("Tool ${call.toolName} failed: ${result.textContent}", result.isError)
        }
    }
}
