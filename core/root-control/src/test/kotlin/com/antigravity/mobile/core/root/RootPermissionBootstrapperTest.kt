package com.antigravity.mobile.core.root

import com.antigravity.mobile.contracts.ShellResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootPermissionBootstrapperTest {

    @Test
    fun `mergeColonSeparatedSetting appends non-destructively and prevents duplicates`() {
        val target = "com.antigravity.mobile/com.antigravity.mobile.core.accessibility.AntigravityAccessibilityService"

        // Empty / null / literal "null" from Android settings
        assertEquals(target to true, RootPermissionBootstrapper.mergeColonSeparatedSetting(null, target))
        assertEquals(target to true, RootPermissionBootstrapper.mergeColonSeparatedSetting("null\n", target))
        assertEquals(target to true, RootPermissionBootstrapper.mergeColonSeparatedSetting("   ", target))

        // Preserves existing services and appends at end
        val existing = "com.google.android.marvin.talkback/.TalkBackService:com.bitwarden/.AutofillService"
        val (merged, changed) = RootPermissionBootstrapper.mergeColonSeparatedSetting(existing, target)
        assertTrue(changed)
        assertEquals("$existing:$target", merged)

        // Idempotent when already present (exact match)
        val (sameExact, changedExact) = RootPermissionBootstrapper.mergeColonSeparatedSetting(merged, target)
        assertFalse(changedExact)
        assertEquals(merged, sameExact)

        // Idempotent when present in shortened pkg/.Class form
        val shortened = "com.antigravity.mobile/.core.accessibility.AntigravityAccessibilityService"
        val (sameShort, changedShort) = RootPermissionBootstrapper.mergeColonSeparatedSetting(
            "com.other/.Svc:$shortened",
            target,
        )
        assertFalse(changedShort)
        assertEquals("com.other/.Svc:$shortened", sameShort)
    }

    @Test
    fun `bootstrap grants runtime perms, ACCESS_RESTRICTED_SETTINGS, Doze whitelist, and appends services`() =
        runTest {
            val executedCommands = mutableListOf<String>()
            val backend = mockk<RootShellBackend>()
            coEvery { backend.isRootGranted() } returns true
            coEvery { backend.executeCommand(any(), any()) } answers {
                val cmd = firstArg<String>()
                executedCommands += cmd
                when {
                    cmd.contains("__VER__") -> ShellResult(
                        exitCode = 0,
                        stdout = "__VER__:KernelSU 1.0.5\n__KSU__:1\n__ID__:0",
                        stderr = "",
                    )

                    cmd.contains("enabled_accessibility_services") && cmd.contains("'get'") ->
                        ShellResult(0, "com.other.app/.OtherAccessibilityService", "")

                    cmd.contains("enabled_notification_listeners") && cmd.contains("'get'") ->
                        ShellResult(0, "null", "")

                    // Force fallback --uid path for ACCESS_RESTRICTED_SETTINGS to prove it works too!
                    cmd == "'appops' 'set' 'com.antigravity.mobile' 'ACCESS_RESTRICTED_SETTINGS' 'allow'" ->
                        ShellResult(1, "", "Requires --uid on this build")

                    else -> ShellResult(0, "OK", "")
                }
            }

            val detector = RootEnvironmentDetector(backend)
            val bootstrapper = RootPermissionBootstrapper(backend, detector)

            val accessibilityComp =
                "com.antigravity.mobile/com.antigravity.mobile.core.accessibility.AntigravityAccessibilityService"
            val notifComp =
                "com.antigravity.mobile/com.antigravity.mobile.core.accessibility.AntigravityNotificationListenerService"

            val report = bootstrapper.bootstrap(
                packageName = "com.antigravity.mobile",
                accessibilityServiceComponent = accessibilityComp,
                notificationListenerComponent = notifComp,
            )

            assertTrue(report.allCriticalReady)
            assertTrue(report.restrictedSettingsGranted)
            assertTrue(report.accessibilityEnabled)
            assertTrue(report.notificationListenerEnabled)
            assertTrue(report.batteryOptimizationExempted)
            assertTrue(report.appOpsGranted.contains("ACCESS_RESTRICTED_SETTINGS"))
            assertTrue(report.appOpsGranted.contains("SYSTEM_ALERT_WINDOW"))
            assertTrue(report.runtimePermissionsGranted.contains("android.permission.CALL_PHONE"))
            assertTrue(report.runtimePermissionsGranted.contains("android.permission.POST_NOTIFICATIONS"))

            // Verify ACCESS_RESTRICTED_SETTINGS fallback --uid command ran
            assertTrue(
                executedCommands.contains(
                    "'appops' 'set' '--uid' 'com.antigravity.mobile' 'ACCESS_RESTRICTED_SETTINGS' 'allow'",
                ),
            )

            // Verify non-destructive append kept com.other.app/.OtherAccessibilityService intact
            val expectedPutAccessibility =
                "'settings' 'put' 'secure' 'enabled_accessibility_services' " +
                    "'com.other.app/.OtherAccessibilityService:$accessibilityComp'"
            assertTrue(
                "Expected non-destructive append command, got: $executedCommands",
                executedCommands.contains(expectedPutAccessibility),
            )
            assertTrue(
                executedCommands.contains("'settings' 'put' 'secure' 'accessibility_enabled' '1'"),
            )

            // Verify notification listener replaced Android's literal "null" cleanly
            assertTrue(
                executedCommands.contains(
                    "'settings' 'put' 'secure' 'enabled_notification_listeners' '$notifComp'",
                ),
            )

            // Verify Doze whitelist
            assertTrue(
                executedCommands.contains("'dumpsys' 'deviceidle' 'whitelist' '+com.antigravity.mobile'"),
            )
        }
}
