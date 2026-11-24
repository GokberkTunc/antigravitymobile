package com.antigravity.mobile.contracts

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object ChrootConstants {
    const val DEFAULT_ROOTFS_PATH = "/data/local/ag_rootfs"
    const val DEFAULT_PROFILES_HOST_PATH = "/data/local/ag_profiles"
    const val DEFAULT_PROFILES_CHROOT_PATH = "/profiles"
    const val DEFAULT_AGY_BINARY = "/usr/local/bin/agy"
    const val DEFAULT_BUNDLED_ASSET_PATH = "rootfs/arm64-rootfs.tar.gz"
}

@Serializable
sealed interface RootfsSource {
    @Serializable
    @SerialName("bundled_asset")
    data class BundledAsset(
        val assetPath: String = ChrootConstants.DEFAULT_BUNDLED_ASSET_PATH,
    ) : RootfsSource

    @Serializable
    @SerialName("remote_download")
    data class RemoteDownload(
        val url: String,
        val sha256: String? = null,
    ) : RootfsSource

    @Serializable
    @SerialName("local_file")
    data class LocalFile(
        val absolutePath: String,
        val sha256: String? = null,
    ) : RootfsSource
}

@Serializable
data class MountStatus(
    val procMounted: Boolean = false,
    val sysMounted: Boolean = false,
    val devMounted: Boolean = false,
    val devPtsMounted: Boolean = false,
    val sdcardMounted: Boolean = false,
    val androidRootMounted: Boolean = false,
    val profilesMounted: Boolean = false,
    val dnsConfigured: Boolean = false,
    val loopbackReachable: Boolean = false,
) {
    val allCoreMounted: Boolean
        get() = procMounted && sysMounted && devMounted && devPtsMounted && sdcardMounted && profilesMounted
}

@Serializable
sealed interface ChrootState {
    @Serializable
    @SerialName("not_installed")
    data object NotInstalled : ChrootState

    @Serializable
    @SerialName("installing")
    data class Installing(
        val stage: String,
        val progressPercent: Int? = null,
    ) : ChrootState

    @Serializable
    @SerialName("mounted")
    data class Mounted(
        val rootfsPath: String = ChrootConstants.DEFAULT_ROOTFS_PATH,
        val agyVersion: String? = null,
        val mountStatus: MountStatus = MountStatus(),
    ) : ChrootState

    @Serializable
    @SerialName("unmounted")
    data class Unmounted(
        val rootfsPath: String = ChrootConstants.DEFAULT_ROOTFS_PATH,
        val agyVersion: String? = null,
    ) : ChrootState

    @Serializable
    @SerialName("error")
    data class Error(
        val message: String,
        val recoverable: Boolean = true,
    ) : ChrootState
}

@Serializable
data class ChrootCommandSpec(
    val rootfsPath: String = ChrootConstants.DEFAULT_ROOTFS_PATH,
    val binaryPath: String = ChrootConstants.DEFAULT_AGY_BINARY,
    val args: List<String> = emptyList(),
    val environment: Map<String, String> = emptyMap(),
    val workingDirectory: String? = null,
)

/**
 * Manages the ARM64 glibc chroot rootfs lifecycle, idempotent bind-mounts
 * (via KernelSU/Magisk-compatible `Shell.FLAG_MOUNT_MASTER`), and `agy` binary installation.
 */
interface ChrootManager {
    val state: StateFlow<ChrootState>

    suspend fun ensureRootfsReady(
        source: RootfsSource = RootfsSource.BundledAsset(),
    ): Result<Unit>

    suspend fun mountAll(): Result<MountStatus>

    suspend fun unmountSafely(force: Boolean = false): Result<MountStatus>

    suspend fun getMountStatus(): MountStatus

    suspend fun installOrUpdateAgy(): Result<String>

    suspend fun getAgyVersion(): Result<String?>

    suspend fun buildChrootCommand(spec: ChrootCommandSpec): List<String>

    suspend fun killAllAgyProcesses(): Result<Int>
}
