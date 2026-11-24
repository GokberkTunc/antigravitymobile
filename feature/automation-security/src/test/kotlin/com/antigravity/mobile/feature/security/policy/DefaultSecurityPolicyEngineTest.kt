package com.antigravity.mobile.feature.security.policy

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.antigravity.mobile.contracts.McpToolNames
import com.antigravity.mobile.contracts.McpToolRisk
import com.antigravity.mobile.contracts.PermissionMode
import com.antigravity.mobile.contracts.PolicyDecision
import com.antigravity.mobile.contracts.PolicyRuleOverride
import com.antigravity.mobile.feature.security.killswitch.KillSwitchCoordinator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultSecurityPolicyEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createEngine(
        scope: TestScope,
        fileName: String = "policy_test.preferences_pb",
        killSwitch: KillSwitchCoordinator = mockk(relaxed = true),
    ): DefaultSecurityPolicyEngine {
        val dispatcher = UnconfinedTestDispatcher(scope.testScheduler)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope.backgroundScope,
            produceFile = { File(tempFolder.root, fileName) },
        )
        val store = DataStoreSecurityPolicyStore(dataStore, dispatcher)
        return DefaultSecurityPolicyEngine(
            store = store,
            killSwitchCoordinator = killSwitch,
        )
    }

    @Test
    fun `default denylist includes major Turkish banks, e-Devlet, 2FA, password managers, and Google Wallet`() = runTest {
        val engine = createEngine(this)
        engine.initialize()
        val denylist = engine.denylistPackages.value

        val requiredPackages = listOf(
            // Turkish banks & fintech
            "com.garanti.cepsubesi",
            "com.pozitron.iscep",
            "com.ziraat.ziraatmobil",
            "com.akbank.android.apps.akbank_direkt",
            "com.ykb.android",
            "com.finansbank.mobile.cepsube",
            "com.finansbank.mobile.enpara",
            "com.mobillium.papara",
            "com.denizbank.mobildeniz",
            "com.vakifbank.mobile",
            // e-Devlet
            "tr.gov.turkiye.edevlet.kapisi",
            // 2FA / Authenticators
            "com.google.android.apps.authenticator2",
            "com.azure.authenticator",
            "com.authy.authy",
            "com.beemdevelopment.aegis",
            // Password managers
            "com.x8bit.bitwarden",
            "com.onepassword.android",
            "com.kunzisoft.keepass.free",
            // Google Wallet
            "com.google.android.apps.walletnfcrel",
        )

        for (pkg in requiredPackages) {
            assertTrue("Expected protected package $pkg in default denylist", pkg in denylist)
        }
    }

    @Test
    fun `CRITICAL tools ALWAYS require approval even in AUTO mode and with ALLOW override`() = runTest {
        val engine = createEngine(this)
        engine.setPermissionMode(PermissionMode.AUTO)

        // Attempt to bypass with per-project ALLOW overrides (must NOT bypass CRITICAL gate!)
        engine.setRuleOverride(
            PolicyRuleOverride(
                id = "ovr-call",
                projectId = "proj-1",
                toolName = McpToolNames.PHONE_CALL_CONTROL,
                decision = PolicyDecision.ALLOW,
            ),
        )
        engine.setRuleOverride(
            PolicyRuleOverride(
                id = "ovr-sms",
                projectId = "proj-1",
                toolName = McpToolNames.SMS_MANAGE,
                decision = PolicyDecision.ALLOW,
            ),
        )
        engine.setRuleOverride(
            PolicyRuleOverride(
                id = "ovr-root",
                projectId = "proj-1",
                toolName = McpToolNames.ROOT_EXEC,
                decision = PolicyDecision.ALLOW,
            ),
        )

        val phoneEval = engine.evaluate(
            toolName = McpToolNames.PHONE_CALL_CONTROL,
            riskLevel = McpToolRisk.CRITICAL,
            args = mapOf("action" to "call", "phone_number" to "+905551112233"),
            projectId = "proj-1",
        )
        assertEquals(PolicyDecision.REQUIRE_APPROVAL, phoneEval.decision)
        assertFalse(phoneEval.canBeOverridden)

        // Even if caller mislabels riskLevel as LOW, phone_call_control is still forced CRITICAL
        val mislabeledPhone = engine.evaluate(
            toolName = McpToolNames.PHONE_CALL_CONTROL,
            riskLevel = McpToolRisk.LOW,
            args = mapOf("action" to "dial"),
            projectId = "proj-1",
        )
        assertEquals(PolicyDecision.REQUIRE_APPROVAL, mislabeledPhone.decision)
        assertEquals(McpToolRisk.CRITICAL, mislabeledPhone.riskLevel)

        val smsSendEval = engine.evaluate(
            toolName = McpToolNames.SMS_MANAGE,
            riskLevel = McpToolRisk.CRITICAL,
            args = mapOf("action" to "send", "address" to "+905551112233", "body" to "Merhaba"),
            projectId = "proj-1",
        )
        assertEquals(PolicyDecision.REQUIRE_APPROVAL, smsSendEval.decision)
        assertFalse(smsSendEval.canBeOverridden)

        // Even if caller mislabels sms_manage send as MEDIUM, still forced CRITICAL
        val mislabeledSmsSend = engine.evaluate(
            toolName = McpToolNames.SMS_MANAGE,
            riskLevel = McpToolRisk.MEDIUM,
            args = mapOf("action" to "send", "address" to "1234", "body" to "hi"),
            projectId = "proj-1",
        )
        assertEquals(PolicyDecision.REQUIRE_APPROVAL, mislabeledSmsSend.decision)
        assertEquals(McpToolRisk.CRITICAL, mislabeledSmsSend.riskLevel)

        val rootExecEval = engine.evaluate(
            toolName = McpToolNames.ROOT_EXEC,
            riskLevel = McpToolRisk.CRITICAL,
            args = mapOf("command" to "ls -la /sdcard"),
            projectId = "proj-1",
        )
        assertEquals(PolicyDecision.REQUIRE_APPROVAL, rootExecEval.decision)
        assertFalse(rootExecEval.canBeOverridden)
    }

    @Test
    fun `destructive root_exec patterns ALWAYS return REQUIRE_APPROVAL or DENY in AUTO mode`() = runTest {
        val engine = createEngine(this)
        engine.setPermissionMode(PermissionMode.AUTO)

        val destructiveCommands = mapOf(
            "rm -rf /tmp/cache" to PolicyDecision.REQUIRE_APPROVAL,
            "rm -fr /sdcard/temp" to PolicyDecision.REQUIRE_APPROVAL,
            "dd if=/dev/zero of=/sdcard/test.img bs=1M count=2" to PolicyDecision.REQUIRE_APPROVAL,
            "mkfs.ext4 /sdcard/loop.img" to PolicyDecision.REQUIRE_APPROVAL,
            "echo 127.0.0.1 test >> /system/etc/hosts" to PolicyDecision.REQUIRE_APPROVAL,
            "mount -o rw,remount /system" to PolicyDecision.REQUIRE_APPROVAL,
            "cp /sdcard/foo /system/bin/foo" to PolicyDecision.REQUIRE_APPROVAL,
            "reboot bootloader" to PolicyDecision.REQUIRE_APPROVAL,
            "reboot fastboot" to PolicyDecision.REQUIRE_APPROVAL,
            "reboot edl" to PolicyDecision.REQUIRE_APPROVAL,
            "fastboot flash recovery twrp.img" to PolicyDecision.REQUIRE_APPROVAL,
            "fastboot erase userdata" to PolicyDecision.REQUIRE_APPROVAL,
        )

        for ((command, expectedDecision) in destructiveCommands) {
            val eval = engine.evaluate(
                toolName = McpToolNames.ROOT_EXEC,
                riskLevel = McpToolRisk.CRITICAL,
                args = mapOf("command" to command),
            )
            assertEquals("Command '$command' failed decision check", expectedDecision, eval.decision)
            assertNotNull("Command '$command' should report matchedDestructivePattern", eval.matchedDestructivePattern)
            assertFalse("Command '$command' must not be overridable", eval.canBeOverridden)
        }

        val forbiddenCommands = listOf(
            "rm -rf /",
            "rm -rf /*",
            "rm -rf /system",
            "dd if=/dev/zero of=/dev/block/sda1",
            "mkfs.ext4 /dev/block/by-name/userdata",
            "fastboot erase bootloader",
            "fastboot flash efs efs.img",
            ":(){ :|:& };:",
        )

        for (command in forbiddenCommands) {
            val eval = engine.evaluate(
                toolName = McpToolNames.ROOT_EXEC,
                riskLevel = McpToolRisk.CRITICAL,
                args = mapOf("command" to command),
            )
            assertEquals("Forbidden command '$command' must be DENIED", PolicyDecision.DENY, eval.decision)
            assertNotNull(eval.matchedDestructivePattern)
            assertFalse(eval.canBeOverridden)
        }
    }

    @Test
    fun `protected packages block ui_action, ui_input_raw, and screen_capture even in AUTO mode`() = runTest {
        val engine = createEngine(this)
        engine.setPermissionMode(PermissionMode.AUTO)

        // Even with an explicit ALLOW override, protected packages cannot be bypassed
        engine.setRuleOverride(
            PolicyRuleOverride(
                id = "ovr-ui",
                projectId = "proj-1",
                toolName = McpToolNames.UI_ACTION,
                decision = PolicyDecision.ALLOW,
            ),
        )

        val protectedSamples = listOf(
            "com.garanti.cepsubesi",
            "com.pozitron.iscep",
            "com.ziraat.ziraatmobil",
            "tr.gov.turkiye.edevlet.kapisi",
            "com.google.android.apps.authenticator2",
            "com.x8bit.bitwarden",
            "com.google.android.apps.walletnfcrel",
        )
        val gatedTools = listOf(
            McpToolNames.UI_ACTION to McpToolRisk.MEDIUM,
            McpToolNames.UI_INPUT_RAW to McpToolRisk.MEDIUM,
            McpToolNames.SCREEN_CAPTURE to McpToolRisk.LOW,
        )

        for (pkg in protectedSamples) {
            for ((tool, risk) in gatedTools) {
                val eval = engine.evaluate(
                    toolName = tool,
                    riskLevel = risk,
                    foregroundPackage = pkg,
                    projectId = "proj-1",
                )
                assertEquals(
                    "Tool $tool on protected package $pkg must REQUIRE_APPROVAL",
                    PolicyDecision.REQUIRE_APPROVAL,
                    eval.decision,
                )
                assertEquals(pkg, eval.matchedProtectedPackage)
                assertFalse(eval.canBeOverridden)
            }
        }
    }

    @Test
    fun `LOW and MEDIUM tools on non-protected packages follow AUTO, ASK, and PLAN modes`() = runTest {
        val engine = createEngine(this)
        val normalPkg = "com.example.notes"

        // AUTO mode: LOW & MEDIUM allowed on non-protected packages
        engine.setPermissionMode(PermissionMode.AUTO)
        assertEquals(
            PolicyDecision.ALLOW,
            engine.evaluate(McpToolNames.UI_GET_TREE, McpToolRisk.LOW, foregroundPackage = normalPkg).decision,
        )
        assertEquals(
            PolicyDecision.ALLOW,
            engine.evaluate(McpToolNames.UI_ACTION, McpToolRisk.MEDIUM, foregroundPackage = normalPkg).decision,
        )
        assertEquals(
            PolicyDecision.ALLOW,
            engine.evaluate(McpToolNames.UI_INPUT_RAW, McpToolRisk.MEDIUM, foregroundPackage = normalPkg).decision,
        )
        assertEquals(
            PolicyDecision.ALLOW,
            engine.evaluate(McpToolNames.SCREEN_CAPTURE, McpToolRisk.LOW, foregroundPackage = normalPkg).decision,
        )

        // ASK mode: LOW & MEDIUM require approval
        engine.setPermissionMode(PermissionMode.ASK)
        assertEquals(
            PolicyDecision.REQUIRE_APPROVAL,
            engine.evaluate(McpToolNames.UI_GET_TREE, McpToolRisk.LOW, foregroundPackage = normalPkg).decision,
        )
        assertEquals(
            PolicyDecision.REQUIRE_APPROVAL,
            engine.evaluate(McpToolNames.UI_ACTION, McpToolRisk.MEDIUM, foregroundPackage = normalPkg).decision,
        )

        // PLAN mode: mutating tools DENIED, read-only LOW tools ALLOWED
        engine.setPermissionMode(PermissionMode.PLAN)
        assertEquals(
            PolicyDecision.DENY,
            engine.evaluate(McpToolNames.UI_ACTION, McpToolRisk.MEDIUM, foregroundPackage = normalPkg).decision,
        )
        assertEquals(
            PolicyDecision.DENY,
            engine.evaluate(McpToolNames.UI_INPUT_RAW, McpToolRisk.MEDIUM, foregroundPackage = normalPkg).decision,
        )
        assertEquals(
            PolicyDecision.DENY,
            engine.evaluate(
                McpToolNames.CONTACTS_CALENDAR_QUERY,
                McpToolRisk.MEDIUM,
                args = mapOf("action" to "insert"),
                foregroundPackage = normalPkg,
            ).decision,
        )
        assertEquals(
            PolicyDecision.ALLOW,
            engine.evaluate(McpToolNames.UI_GET_TREE, McpToolRisk.LOW, foregroundPackage = normalPkg).decision,
        )
    }

    @Test
    fun `per-project rule overrides apply to non-critical tools without bypassing policy gate`() = runTest {
        val engine = createEngine(this)
        engine.setPermissionMode(PermissionMode.ASK)

        // Allow ui_get_tree specifically for project A
        engine.setRuleOverride(
            PolicyRuleOverride(
                id = "projA-tree",
                projectId = "project-A",
                toolName = McpToolNames.UI_GET_TREE,
                decision = PolicyDecision.ALLOW,
            ),
        )

        val projAEval = engine.evaluate(
            toolName = McpToolNames.UI_GET_TREE,
            riskLevel = McpToolRisk.LOW,
            foregroundPackage = "com.example.notes",
            projectId = "project-A",
        )
        assertEquals(PolicyDecision.ALLOW, projAEval.decision)

        // Project B does NOT inherit Project A's override
        val projBEval = engine.evaluate(
            toolName = McpToolNames.UI_GET_TREE,
            riskLevel = McpToolRisk.LOW,
            foregroundPackage = "com.example.notes",
            projectId = "project-B",
        )
        assertEquals(PolicyDecision.REQUIRE_APPROVAL, projBEval.decision)

        // Clearing override restores ASK default for Project A
        engine.clearRuleOverride("projA-tree")
        val afterClear = engine.evaluate(
            toolName = McpToolNames.UI_GET_TREE,
            riskLevel = McpToolRisk.LOW,
            foregroundPackage = "com.example.notes",
            projectId = "project-A",
        )
        assertEquals(PolicyDecision.REQUIRE_APPROVAL, afterClear.decision)
    }

    @Test
    fun `protected package denylist modifications persist in DataStore`() = runTest {
        val customPkg = "com.custom.crypto.wallet"
        val fileName = "denylist_persist.preferences_pb"

        val engine1 = createEngine(this, fileName = fileName)
        engine1.setPermissionMode(PermissionMode.AUTO)
        engine1.addProtectedPackage(customPkg)
        assertTrue(customPkg in engine1.denylistPackages.value)

        val blocked = engine1.evaluate(
            toolName = McpToolNames.SCREEN_CAPTURE,
            riskLevel = McpToolRisk.LOW,
            foregroundPackage = customPkg,
        )
        assertEquals(PolicyDecision.REQUIRE_APPROVAL, blocked.decision)

        // Remove custom package and verify non-protected behavior resumes
        engine1.removeProtectedPackage(customPkg)
        assertFalse(customPkg in engine1.denylistPackages.value)

        val allowed = engine1.evaluate(
            toolName = McpToolNames.SCREEN_CAPTURE,
            riskLevel = McpToolRisk.LOW,
            foregroundPackage = customPkg,
        )
        assertEquals(PolicyDecision.ALLOW, allowed.decision)
    }

    @Test
    fun `triggerEmergencyKillSwitch delegates to KillSwitchCoordinator`() = runTest {
        val killSwitch = mockk<KillSwitchCoordinator>()
        coEvery { killSwitch.trigger("Unit test reason") } returns Result.success(Unit)

        val engine = createEngine(this, killSwitch = killSwitch)
        val result = engine.triggerEmergencyKillSwitch("Unit test reason")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { killSwitch.trigger("Unit test reason") }
    }
}
