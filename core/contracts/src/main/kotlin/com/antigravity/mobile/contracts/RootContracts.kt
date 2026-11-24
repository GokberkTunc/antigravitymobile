package com.antigravity.mobile.contracts

import kotlinx.serialization.Serializable

@Serializable
enum class RootProviderType {
    KERNEL_SU,
    MAGISK,
    APATCH,
    GENERIC_SU,
    NONE,
}

@Serializable
data class RootEnvironmentInfo(
    val isRootGranted: Boolean,
    val provider: RootProviderType,
    val suVersion: String? = null,
    val mountMasterSupported: Boolean = false,
    val recommendedSuArgs: List<String> = emptyList(),
    val managerPackageName: String? = null,
    val guidanceMessage: String = "",
)

@Serializable
data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean = false,
    val durationMs: Long = 0L,
) {
    val isSuccess: Boolean
        get() = exitCode == 0 && !timedOut
}

@Serializable
data class BootstrapStepResult(
    val name: String,
    val succeeded: Boolean,
    val detail: String = "",
)

@Serializable
data class BootstrapReport(
    val packageName: String,
    val environment: RootEnvironmentInfo,
    val runtimePermissionsGranted: List<String> = emptyList(),
    val appOpsGranted: List<String> = emptyList(),
    val restrictedSettingsGranted: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val notificationListenerEnabled: Boolean = false,
    val batteryOptimizationExempted: Boolean = false,
    val steps: List<BootstrapStepResult> = emptyList(),
    val warnings: List<String> = emptyList(),
) {
    val allCriticalReady: Boolean
        get() = environment.isRootGranted && restrictedSettingsGranted && accessibilityEnabled
}

/**
 * Executes privileged commands via `libsu` (`Shell.FLAG_MOUNT_MASTER`, KernelSU/Magisk/APatch compatible)
 * strictly off the Android Main Thread (`Dispatchers.IO`).
 */
interface RootExecutor {
    suspend fun isRootAvailable(): Boolean

    suspend fun detectEnvironment(): RootEnvironmentInfo

    suspend fun exec(
        command: String,
        timeoutMs: Long = 30_000L,
        cwd: String? = null,
    ): ShellResult

    suspend fun execArgv(
        argv: List<String>,
        timeoutMs: Long = 30_000L,
        cwd: String? = null,
    ): ShellResult

    suspend fun bootstrapPermissions(
        packageName: String,
        accessibilityServiceComponent: String? = null,
        notificationListenerComponent: String? = null,
    ): BootstrapReport
}
