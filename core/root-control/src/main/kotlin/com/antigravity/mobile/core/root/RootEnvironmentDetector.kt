package com.antigravity.mobile.core.root

import com.antigravity.mobile.contracts.RootEnvironmentInfo
import com.antigravity.mobile.contracts.RootProviderType
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Probes the device root runtime to distinguish KernelSU, Magisk, APatch, generic `su`, or no root,
 * and derives the appropriate mount-master arguments and manager guidance.
 */
@Singleton
class RootEnvironmentDetector @Inject constructor(
    private val shellBackend: RootShellBackend,
) {

    suspend fun detect(): RootEnvironmentInfo {
        val granted = try {
            shellBackend.isRootGranted()
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            false
        }

        if (!granted) {
            return RootEnvironmentInfo(
                isRootGranted = false,
                provider = RootProviderType.NONE,
                suVersion = null,
                mountMasterSupported = false,
                recommendedSuArgs = emptyList(),
                managerPackageName = null,
                guidanceMessage =
                    "Root access is not granted. Open KernelSU Manager (or Magisk / APatch Manager) " +
                        "and grant superuser permission to Antigravity Mobile.",
            )
        }

        val probeCommand = buildString {
            append("echo \"__VER__:\$(su -v 2>/dev/null || su -V 2>/dev/null || true)\"; ")
            append("echo \"__HELP__:\$(su --help 2>&1 | tr '\\n' ' ' || true)\"; ")
            append("if [ -e /data/adb/ksud ] || [ -d /data/adb/ksu ]; then echo \"__KSU__:1\"; fi; ")
            append("if [ -e /data/adb/apd ] || [ -d /data/adb/ap ]; then echo \"__APATCH__:1\"; fi; ")
            append("if [ -e /data/adb/magisk ] || command -v magisk >/dev/null 2>&1; then echo \"__MAGISK__:1\"; fi; ")
            append("echo \"__ID__:\$(id -u 2>/dev/null || true)\"")
        }

        val probeResult = shellBackend.executeCommand(probeCommand, timeoutMs = 10_000L)
        return parseProbeOutput(
            isRootGranted = true,
            probeStdout = probeResult.stdout,
        )
    }

    internal fun parseProbeOutput(
        isRootGranted: Boolean,
        probeStdout: String,
    ): RootEnvironmentInfo {
        if (!isRootGranted) {
            return RootEnvironmentInfo(
                isRootGranted = false,
                provider = RootProviderType.NONE,
                guidanceMessage =
                    "Root access is not granted. Grant superuser access in KernelSU Manager, " +
                        "Magisk Manager, or APatch Manager.",
            )
        }

        val lines = probeStdout.lineSequence().map { it.trim() }.toList()
        val rawVersion = lines.firstOrNull { it.startsWith("__VER__:") }
            ?.removePrefix("__VER__:")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        val rawHelp = lines.firstOrNull { it.startsWith("__HELP__:") }
            ?.removePrefix("__HELP__:")
            ?.trim()
            .orEmpty()
        val hasKsuMarker = lines.any { it == "__KSU__:1" }
        val hasApatchMarker = lines.any { it == "__APATCH__:1" }
        val hasMagiskMarker = lines.any { it == "__MAGISK__:1" }

        val versionLower = rawVersion.orEmpty().lowercase()
        val provider = when {
            hasKsuMarker ||
                versionLower.contains("kernelsu") ||
                KSU_TOKEN_REGEX.containsMatchIn(versionLower) ->
                RootProviderType.KERNEL_SU

            hasApatchMarker || versionLower.contains("apatch") ->
                RootProviderType.APATCH

            hasMagiskMarker || versionLower.contains("magisk") ->
                RootProviderType.MAGISK

            else -> RootProviderType.GENERIC_SU
        }

        val supportsMountMasterLong = rawHelp.contains("--mount-master")
        val supportsMountMasterShortKsu = rawHelp.contains("-M")
        val supportsMagiskMm = rawHelp.contains("-mm")

        return when (provider) {
            RootProviderType.KERNEL_SU -> RootEnvironmentInfo(
                isRootGranted = true,
                provider = RootProviderType.KERNEL_SU,
                suVersion = rawVersion ?: "KernelSU",
                mountMasterSupported = true,
                recommendedSuArgs = when {
                    supportsMountMasterLong -> listOf("--mount-master")
                    supportsMountMasterShortKsu -> listOf("-M")
                    else -> listOf("--mount-master")
                },
                managerPackageName = KERNEL_SU_MANAGER_PACKAGE,
                guidanceMessage =
                    "KernelSU detected. Ensure Antigravity Mobile has superuser access and global " +
                        "mount namespace enabled in KernelSU Manager.",
            )

            RootProviderType.APATCH -> RootEnvironmentInfo(
                isRootGranted = true,
                provider = RootProviderType.APATCH,
                suVersion = rawVersion ?: "APatch",
                mountMasterSupported = true,
                recommendedSuArgs = listOf("--mount-master"),
                managerPackageName = APATCH_MANAGER_PACKAGE,
                guidanceMessage =
                    "APatch detected. Ensure superuser privileges remain enabled in APatch Manager.",
            )

            RootProviderType.MAGISK -> RootEnvironmentInfo(
                isRootGranted = true,
                provider = RootProviderType.MAGISK,
                suVersion = rawVersion ?: "Magisk",
                mountMasterSupported = true,
                recommendedSuArgs = if (supportsMagiskMm || !supportsMountMasterLong) {
                    listOf("-mm")
                } else {
                    listOf("--mount-master")
                },
                managerPackageName = MAGISK_MANAGER_PACKAGE,
                guidanceMessage =
                    "Magisk detected. Global mount namespace (Shell.FLAG_MOUNT_MASTER / -mm) is ready.",
            )

            RootProviderType.GENERIC_SU -> RootEnvironmentInfo(
                isRootGranted = true,
                provider = RootProviderType.GENERIC_SU,
                suVersion = rawVersion,
                mountMasterSupported = supportsMountMasterLong || supportsMountMasterShortKsu || supportsMagiskMm,
                recommendedSuArgs = when {
                    supportsMountMasterLong -> listOf("--mount-master")
                    supportsMountMasterShortKsu -> listOf("-M")
                    supportsMagiskMm -> listOf("-mm")
                    else -> emptyList()
                },
                managerPackageName = null,
                guidanceMessage = "Generic su binary detected.",
            )

            RootProviderType.NONE -> RootEnvironmentInfo(
                isRootGranted = false,
                provider = RootProviderType.NONE,
                guidanceMessage =
                    "Root access is not granted. Open KernelSU Manager, Magisk Manager, or APatch Manager.",
            )
        }
    }

    companion object {
        private val KSU_TOKEN_REGEX = Regex("(^|[^a-z])ksu($|[^a-z])")
        const val KERNEL_SU_MANAGER_PACKAGE = "me.weishu.kernelsu"
        const val MAGISK_MANAGER_PACKAGE = "com.topjohnwu.magisk"
        const val APATCH_MANAGER_PACKAGE = "me.bmax.apatch"
    }
}
