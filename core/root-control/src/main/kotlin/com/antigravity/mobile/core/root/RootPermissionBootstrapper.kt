package com.antigravity.mobile.core.root

import com.antigravity.mobile.contracts.BootstrapReport
import com.antigravity.mobile.contracts.BootstrapStepResult
import com.antigravity.mobile.contracts.RootEnvironmentInfo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Automates all privileged Android runtime grants, AppOps (including Android 13–17
 * `ACCESS_RESTRICTED_SETTINGS`), non-destructive Accessibility/NotificationListener registration,
 * and Doze battery-optimization whitelisting via root shell.
 */
@Singleton
class RootPermissionBootstrapper @Inject constructor(
    private val shellBackend: RootShellBackend,
    private val environmentDetector: RootEnvironmentDetector,
) {

    suspend fun bootstrap(
        packageName: String,
        accessibilityServiceComponent: String? = null,
        notificationListenerComponent: String? = null,
        runtimePermissions: List<String> = DEFAULT_RUNTIME_PERMISSIONS,
        appOps: List<String> = DEFAULT_APP_OPS,
    ): BootstrapReport {
        val safePkg = ShellEscaping.validatePackageName(packageName)
        val safeAccessibility = accessibilityServiceComponent?.let {
            ShellEscaping.validateComponentName(it)
        }
        val safeNotification = notificationListenerComponent?.let {
            ShellEscaping.validateComponentName(it)
        }

        val env: RootEnvironmentInfo = environmentDetector.detect()
        if (!env.isRootGranted) {
            return BootstrapReport(
                packageName = safePkg,
                environment = env,
                steps = listOf(
                    BootstrapStepResult(
                        name = "root_check",
                        succeeded = false,
                        detail = env.guidanceMessage,
                    ),
                ),
                warnings = listOf(env.guidanceMessage),
            )
        }

        val steps = mutableListOf<BootstrapStepResult>()
        val warnings = mutableListOf<String>()
        val grantedRuntime = mutableListOf<String>()
        val grantedAppOps = mutableListOf<String>()
        var restrictedSettingsGranted = false
        var accessibilityEnabled = false
        var notificationListenerEnabled = false
        var batteryExempted = false

        // 1. Grant runtime permissions via pm grant
        for (permission in runtimePermissions) {
            val safePerm = ShellEscaping.validateIdentifier(permission)
            val cmd = ShellEscaping.joinArgv(listOf("pm", "grant", safePkg, safePerm))
            val res = shellBackend.executeCommand(cmd, timeoutMs = STEP_TIMEOUT_MS)
            if (res.isSuccess) {
                grantedRuntime += safePerm
                steps += BootstrapStepResult(
                    name = "pm_grant:$safePerm",
                    succeeded = true,
                    detail = "Granted",
                )
            } else {
                val detail = res.stderr.ifBlank { res.stdout }.ifBlank { "exit=${res.exitCode}" }
                steps += BootstrapStepResult(
                    name = "pm_grant:$safePerm",
                    succeeded = false,
                    detail = detail,
                )
                warnings += "Failed to grant $safePerm: $detail"
            }
        }

        // 2. Grant special AppOps including Android 13–17 ACCESS_RESTRICTED_SETTINGS
        for (op in appOps) {
            val safeOp = ShellEscaping.validateIdentifier(op)
            val primaryCmd = ShellEscaping.joinArgv(listOf("appops", "set", safePkg, safeOp, "allow"))
            var res = shellBackend.executeCommand(primaryCmd, timeoutMs = STEP_TIMEOUT_MS)
            if (!res.isSuccess && safeOp == APP_OP_ACCESS_RESTRICTED_SETTINGS) {
                val fallbackCmd = ShellEscaping.joinArgv(
                    listOf("appops", "set", "--uid", safePkg, safeOp, "allow"),
                )
                res = shellBackend.executeCommand(fallbackCmd, timeoutMs = STEP_TIMEOUT_MS)
            }
            if (res.isSuccess) {
                grantedAppOps += safeOp
                if (safeOp == APP_OP_ACCESS_RESTRICTED_SETTINGS) {
                    restrictedSettingsGranted = true
                }
                steps += BootstrapStepResult(
                    name = "appops:$safeOp",
                    succeeded = true,
                    detail = "Allowed",
                )
            } else {
                val detail = res.stderr.ifBlank { res.stdout }.ifBlank { "exit=${res.exitCode}" }
                steps += BootstrapStepResult(
                    name = "appops:$safeOp",
                    succeeded = false,
                    detail = detail,
                )
                warnings += "Failed to set AppOp $safeOp: $detail"
            }
        }

        // 3. Non-destructive append to enabled_accessibility_services + accessibility_enabled 1
        if (safeAccessibility != null) {
            val appendOutcome = appendSecureSettingList(
                settingKey = SETTING_ENABLED_ACCESSIBILITY_SERVICES,
                entryToAdd = safeAccessibility,
            )
            steps += BootstrapStepResult(
                name = "accessibility_service_append",
                succeeded = appendOutcome.succeeded,
                detail = appendOutcome.detail,
            )
            if (!appendOutcome.succeeded) {
                warnings += "Failed to append accessibility service: ${appendOutcome.detail}"
            }

            val enableCmd = ShellEscaping.joinArgv(
                listOf("settings", "put", "secure", SETTING_ACCESSIBILITY_ENABLED, "1"),
            )
            val enableRes = shellBackend.executeCommand(enableCmd, timeoutMs = STEP_TIMEOUT_MS)
            steps += BootstrapStepResult(
                name = "accessibility_enabled_flag",
                succeeded = enableRes.isSuccess,
                detail = if (enableRes.isSuccess) "Enabled" else enableRes.stderr.ifBlank { "exit=${enableRes.exitCode}" },
            )
            accessibilityEnabled = appendOutcome.succeeded && enableRes.isSuccess
            if (!enableRes.isSuccess) {
                warnings += "Failed to enable accessibility_enabled flag: ${enableRes.stderr}"
            }
        }

        // 4. Non-destructive append to enabled_notification_listeners
        if (safeNotification != null) {
            val notifOutcome = appendSecureSettingList(
                settingKey = SETTING_ENABLED_NOTIFICATION_LISTENERS,
                entryToAdd = safeNotification,
            )
            steps += BootstrapStepResult(
                name = "notification_listener_append",
                succeeded = notifOutcome.succeeded,
                detail = notifOutcome.detail,
            )
            notificationListenerEnabled = notifOutcome.succeeded
            if (!notifOutcome.succeeded) {
                warnings += "Failed to append notification listener: ${notifOutcome.detail}"
            }
        }

        // 5. Battery / Doze whitelist
        val dozeCmd = ShellEscaping.joinArgv(listOf("dumpsys", "deviceidle", "whitelist", "+$safePkg"))
        val dozeRes = shellBackend.executeCommand(dozeCmd, timeoutMs = STEP_TIMEOUT_MS)
        batteryExempted = dozeRes.isSuccess
        steps += BootstrapStepResult(
            name = "deviceidle_whitelist",
            succeeded = dozeRes.isSuccess,
            detail = if (dozeRes.isSuccess) {
                dozeRes.stdout.ifBlank { "Whitelisted" }
            } else {
                dozeRes.stderr.ifBlank { "exit=${dozeRes.exitCode}" }
            },
        )
        if (!dozeRes.isSuccess) {
            warnings += "Failed to whitelist $safePkg from Doze: ${dozeRes.stderr}"
        }

        return BootstrapReport(
            packageName = safePkg,
            environment = env,
            runtimePermissionsGranted = grantedRuntime,
            appOpsGranted = grantedAppOps,
            restrictedSettingsGranted = restrictedSettingsGranted,
            accessibilityEnabled = accessibilityEnabled,
            notificationListenerEnabled = notificationListenerEnabled,
            batteryOptimizationExempted = batteryExempted,
            steps = steps,
            warnings = warnings,
        )
    }

    private data class AppendOutcome(
        val succeeded: Boolean,
        val detail: String,
    )

    private suspend fun appendSecureSettingList(
        settingKey: String,
        entryToAdd: String,
    ): AppendOutcome {
        val getCmd = ShellEscaping.joinArgv(listOf("settings", "get", "secure", settingKey))
        val getRes = shellBackend.executeCommand(getCmd, timeoutMs = STEP_TIMEOUT_MS)
        if (!getRes.isSuccess) {
            return AppendOutcome(
                succeeded = false,
                detail = "Could not read $settingKey: ${getRes.stderr.ifBlank { "exit=${getRes.exitCode}" }}",
            )
        }

        val (merged, changed) = mergeColonSeparatedSetting(getRes.stdout, entryToAdd)
        if (!changed) {
            return AppendOutcome(succeeded = true, detail = "Already present in $settingKey")
        }

        val putCmd = ShellEscaping.joinArgv(listOf("settings", "put", "secure", settingKey, merged))
        val putRes = shellBackend.executeCommand(putCmd, timeoutMs = STEP_TIMEOUT_MS)
        return if (putRes.isSuccess) {
            AppendOutcome(succeeded = true, detail = "Appended to $settingKey")
        } else {
            AppendOutcome(
                succeeded = false,
                detail = putRes.stderr.ifBlank { "exit=${putRes.exitCode}" },
            )
        }
    }

    companion object {
        private const val STEP_TIMEOUT_MS = 10_000L

        const val APP_OP_ACCESS_RESTRICTED_SETTINGS = "ACCESS_RESTRICTED_SETTINGS"
        const val SETTING_ENABLED_ACCESSIBILITY_SERVICES = "enabled_accessibility_services"
        const val SETTING_ACCESSIBILITY_ENABLED = "accessibility_enabled"
        const val SETTING_ENABLED_NOTIFICATION_LISTENERS = "enabled_notification_listeners"

        val DEFAULT_RUNTIME_PERMISSIONS: List<String> = listOf(
            "android.permission.CALL_PHONE",
            "android.permission.READ_PHONE_STATE",
            "android.permission.ANSWER_PHONE_CALLS",
            "android.permission.READ_SMS",
            "android.permission.SEND_SMS",
            "android.permission.RECEIVE_SMS",
            "android.permission.READ_CONTACTS",
            "android.permission.WRITE_CONTACTS",
            "android.permission.READ_CALENDAR",
            "android.permission.WRITE_CALENDAR",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.CAMERA",
            "android.permission.RECORD_AUDIO",
            "android.permission.POST_NOTIFICATIONS",
        )

        val DEFAULT_APP_OPS: List<String> = listOf(
            "SYSTEM_ALERT_WINDOW",
            "MANAGE_EXTERNAL_STORAGE",
            "WRITE_SETTINGS",
            "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
            APP_OP_ACCESS_RESTRICTED_SETTINGS,
        )

        /**
         * Non-destructively appends [entryToAdd] to a colon-separated Android secure setting list.
         * Returns `(mergedValue, changed)`.
         *
         * Handles Android's literal `"null"` return value for unset settings and prevents duplicates
         * (including shortened `pkg/.Service` vs expanded `pkg/pkg.Service` forms).
         */
        fun mergeColonSeparatedSetting(
            existingRaw: String?,
            entryToAdd: String,
        ): Pair<String, Boolean> {
            val cleanedTarget = entryToAdd.trim()
            require(cleanedTarget.isNotEmpty()) { "entryToAdd must not be blank" }

            val existingEntries = existingRaw
                .orEmpty()
                .trim()
                .takeUnless { it.isEmpty() || it.equals("null", ignoreCase = true) }
                ?.split(":")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
                ?: emptyList()

            val canonicalTarget = canonicalComponent(cleanedTarget)
            val alreadyPresent = existingEntries.any { existing ->
                existing == cleanedTarget || canonicalComponent(existing) == canonicalTarget
            }
            if (alreadyPresent) {
                return existingEntries.joinToString(":") to false
            }

            val updated = existingEntries + cleanedTarget
            return updated.joinToString(":") to true
        }

        private fun canonicalComponent(component: String): String {
            val slashIndex = component.indexOf('/')
            if (slashIndex <= 0) return component
            val pkg = component.substring(0, slashIndex)
            val cls = component.substring(slashIndex + 1)
            return if (cls.startsWith(".")) {
                "$pkg/$pkg$cls"
            } else {
                component
            }
        }
    }
}
