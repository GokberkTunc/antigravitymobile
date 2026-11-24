package com.antigravity.mobile.core.chroot

import com.antigravity.mobile.contracts.ChrootConstants
import com.antigravity.mobile.contracts.MountStatus
import com.antigravity.mobile.contracts.RootExecutor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Thrown when safe unmount is requested while chroot processes are still running
 * and `force == false`.
 */
class ChrootBusyException(
    val activePids: List<Int>,
) : IllegalStateException(
    "Cannot unmount chroot safely while ${activePids.size} active process(es) remain: ${activePids.joinToString()}",
)

/**
 * Describes a single chroot mount entry.
 */
data class ChrootMountEntry(
    val id: String,
    val source: String,
    val targetRelative: String,
    val fsType: String? = null,
    val isBind: Boolean = true,
    val required: Boolean = true,
)

/**
 * Manages idempotent bind-mounts, `/etc/resolv.conf` DNS setup, `127.0.0.1` loopback check,
 * active chroot process inspection, and safe reverse-order unmount via [RootExecutor]
 * (which operates inside the global/master mount namespace via `Shell.FLAG_MOUNT_MASTER`).
 */
interface BindMountManager {
    suspend fun mountAll(
        rootfsPath: String = ChrootConstants.DEFAULT_ROOTFS_PATH,
        profilesHostPath: String = ChrootConstants.DEFAULT_PROFILES_HOST_PATH,
    ): Result<MountStatus>

    suspend fun unmountSafely(
        rootfsPath: String = ChrootConstants.DEFAULT_ROOTFS_PATH,
        force: Boolean = false,
    ): Result<MountStatus>

    suspend fun getMountStatus(
        rootfsPath: String = ChrootConstants.DEFAULT_ROOTFS_PATH,
    ): MountStatus

    suspend fun findActiveChrootPids(
        rootfsPath: String = ChrootConstants.DEFAULT_ROOTFS_PATH,
    ): List<Int>

    suspend fun configureDns(
        rootfsPath: String = ChrootConstants.DEFAULT_ROOTFS_PATH,
    ): Boolean

    suspend fun verifyLoopback(
        rootfsPath: String = ChrootConstants.DEFAULT_ROOTFS_PATH,
    ): Boolean
}

class DefaultBindMountManager(
    private val rootExecutor: RootExecutor,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val forceKillGraceMs: Long = 500L,
) : BindMountManager {

    override suspend fun mountAll(
        rootfsPath: String,
        profilesHostPath: String,
    ): Result<MountStatus> = withContext(ioDispatcher) {
        try {
            val cleanRootfs = PosixShellQuoting.validateAbsolutePath(rootfsPath, "rootfsPath")
            val cleanProfiles = PosixShellQuoting.validateAbsolutePath(profilesHostPath, "profilesHostPath")

            // Ensure host profiles directory exists before binding.
            val qProfilesHost = PosixShellQuoting.quote(cleanProfiles)
            val mkProfiles = rootExecutor.exec("mkdir -p $qProfilesHost && chmod 700 $qProfilesHost", timeoutMs = 10_000L)
            if (!mkProfiles.isSuccess) {
                return@withContext Result.failure(
                    IOException("Failed to prepare host profiles directory '$cleanProfiles': ${mkProfiles.stderr}"),
                )
            }

            val entries = mountOrderEntries(cleanProfiles)
            for (entry in entries) {
                val target = "$cleanRootfs/${entry.targetRelative}"
                ensureMountedIdempotent(entry, target)
            }

            val dnsOk = configureDns(cleanRootfs)
            val loopbackOk = verifyLoopback(cleanRootfs)
            val status = queryMountStatusInternal(cleanRootfs, dnsConfigured = dnsOk, loopbackReachable = loopbackOk)

            if (!status.allCoreMounted) {
                return@withContext Result.failure(
                    IOException("Core chroot mounts incomplete after mountAll: $status"),
                )
            }
            Result.success(status)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    override suspend fun unmountSafely(
        rootfsPath: String,
        force: Boolean,
    ): Result<MountStatus> = withContext(ioDispatcher) {
        try {
            val cleanRootfs = PosixShellQuoting.validateAbsolutePath(rootfsPath, "rootfsPath")

            var activePids = findActiveChrootPidsInternal(cleanRootfs)
            if (activePids.isNotEmpty()) {
                if (!force) {
                    return@withContext Result.failure(ChrootBusyException(activePids))
                }
                terminatePids(activePids, signal = "TERM")
                if (forceKillGraceMs > 0L) {
                    delay(forceKillGraceMs)
                }
                activePids = findActiveChrootPidsInternal(cleanRootfs)
                if (activePids.isNotEmpty()) {
                    terminatePids(activePids, signal = "KILL")
                    if (forceKillGraceMs > 0L) {
                        delay(150L)
                    }
                    activePids = findActiveChrootPidsInternal(cleanRootfs)
                    if (activePids.isNotEmpty()) {
                        return@withContext Result.failure(ChrootBusyException(activePids))
                    }
                }
            }

            // Strict safe reverse-order unmount:
            // /dev/pts -> /dev -> /sys -> /proc -> bind dirs (/profiles, /mnt/android_root, /mnt/sdcard)
            val unmountOrder = unmountOrderTargets(cleanRootfs)
            val failures = mutableListOf<String>()
            for (target in unmountOrder) {
                if (isMountPoint(target)) {
                    val qTarget = PosixShellQuoting.quote(target)
                    val res = rootExecutor.exec("umount $qTarget", timeoutMs = 15_000L)
                    if (!res.isSuccess) {
                        // Retry check: if already unmounted race, accept; otherwise record failure.
                        if (isMountPoint(target)) {
                            failures += "$target (${res.stderr.ifBlank { "exit=${res.exitCode}" }})"
                        }
                    }
                }
            }

            val finalStatus = queryMountStatusInternal(cleanRootfs, dnsConfigured = false, loopbackReachable = false)
            if (failures.isNotEmpty()) {
                return@withContext Result.failure(
                    IOException("Failed to unmount chroot target(s): ${failures.joinToString("; ")}"),
                )
            }
            Result.success(finalStatus)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    override suspend fun getMountStatus(rootfsPath: String): MountStatus = withContext(ioDispatcher) {
        val cleanRootfs = PosixShellQuoting.validateAbsolutePath(rootfsPath, "rootfsPath")
        val qResolv = PosixShellQuoting.quote("$cleanRootfs/etc/resolv.conf")
        val dnsOk = rootExecutor.exec("[ -s $qResolv ] && grep -q '^nameserver ' $qResolv", timeoutMs = 5_000L).isSuccess
        val procMounted = isMountPoint("$cleanRootfs/proc")
        val loopbackOk = if (procMounted) verifyLoopback(cleanRootfs) else false
        queryMountStatusInternal(cleanRootfs, dnsConfigured = dnsOk, loopbackReachable = loopbackOk)
    }

    override suspend fun findActiveChrootPids(rootfsPath: String): List<Int> = withContext(ioDispatcher) {
        val cleanRootfs = PosixShellQuoting.validateAbsolutePath(rootfsPath, "rootfsPath")
        findActiveChrootPidsInternal(cleanRootfs)
    }

    override suspend fun configureDns(rootfsPath: String): Boolean = withContext(ioDispatcher) {
        val cleanRootfs = PosixShellQuoting.validateAbsolutePath(rootfsPath, "rootfsPath")
        val dnsServers = LinkedHashSet<String>()

        // Read active Android DNS servers via getprop (safe on all Android 10-17 devices).
        for (prop in listOf("net.dns1", "net.dns2", "net.dns3")) {
            val res = rootExecutor.exec("getprop $prop", timeoutMs = 5_000L)
            if (res.isSuccess) {
                val candidate = res.stdout.trim()
                if (PosixShellQuoting.isValidDnsAddress(candidate)) {
                    dnsServers.add(candidate)
                }
            }
        }

        // Always append reliable fallback DNS resolvers.
        dnsServers.add(FALLBACK_DNS_PRIMARY)
        dnsServers.add(FALLBACK_DNS_SECONDARY)

        val resolvContent = dnsServers.joinToString(separator = "\\n", postfix = "\\n") { "nameserver $it" }
        val qEtc = PosixShellQuoting.quote("$cleanRootfs/etc")
        val qResolv = PosixShellQuoting.quote("$cleanRootfs/etc/resolv.conf")
        val qHosts = PosixShellQuoting.quote("$cleanRootfs/etc/hosts")
        val qResolvPayload = PosixShellQuoting.quote(resolvContent)

        val cmd = buildString {
            append("mkdir -p $qEtc && ")
            append("rm -f $qResolv && ")
            append("printf $qResolvPayload > $qResolv && ")
            append("chmod 644 $qResolv && ")
            append("(grep -q '127\\.0\\.0\\.1' $qHosts 2>/dev/null || printf '127.0.0.1 localhost\\n::1 localhost\\n' >> $qHosts)")
        }
        rootExecutor.exec(cmd, timeoutMs = 10_000L).isSuccess
    }

    override suspend fun verifyLoopback(rootfsPath: String): Boolean = withContext(ioDispatcher) {
        val cleanRootfs = PosixShellQuoting.validateAbsolutePath(rootfsPath, "rootfsPath")
        val qRootfs = PosixShellQuoting.quote(cleanRootfs)
        val probe = "chroot $qRootfs /usr/bin/env -i PATH=/usr/local/bin:/usr/bin:/bin /bin/sh -c " +
            PosixShellQuoting.quote(
                "if command -v ping >/dev/null 2>&1; then ping -c 1 -W 1 127.0.0.1 >/dev/null 2>&1; " +
                    "elif [ -r /proc/net/dev ]; then grep -q 'lo:' /proc/net/dev; " +
                    "else exit 1; fi",
            )
        rootExecutor.exec(probe, timeoutMs = 10_000L).isSuccess
    }

    private suspend fun ensureMountedIdempotent(entry: ChrootMountEntry, target: String) {
        val qTarget = PosixShellQuoting.quote(target)

        // Always check `mountpoint -q "$TARGET"` before mounting to guarantee zero double-mounts.
        if (isMountPoint(target)) {
            return
        }

        val mkRes = rootExecutor.exec("mkdir -p $qTarget", timeoutMs = 10_000L)
        if (!mkRes.isSuccess) {
            if (entry.required) {
                throw IOException("Failed to create mount target '$target': ${mkRes.stderr}")
            }
            return
        }

        val mountCmd = if (entry.isBind) {
            val qSource = PosixShellQuoting.quote(entry.source)
            "mount -o bind $qSource $qTarget"
        } else {
            val fs = PosixShellQuoting.quote(requireNotNull(entry.fsType))
            val qSource = PosixShellQuoting.quote(entry.source)
            "mount -t $fs $qSource $qTarget"
        }

        val mountRes = rootExecutor.exec(mountCmd, timeoutMs = 15_000L)
        if (!mountRes.isSuccess && entry.required) {
            throw IOException(
                "Failed to mount ${entry.source} -> $target (exit=${mountRes.exitCode}): " +
                    mountRes.stderr.ifBlank { mountRes.stdout },
            )
        }
    }

    private suspend fun isMountPoint(target: String): Boolean {
        val qTarget = PosixShellQuoting.quote(target)
        if (rootExecutor.exec("mountpoint -q $qTarget", timeoutMs = 5_000L).isSuccess) {
            return true
        }
        // Toybox `mountpoint -q` only compares st_dev against parent, so same-filesystem bind-mounts
        // (e.g. `/data/local/ag_profiles` -> `/data/local/ag_rootfs/profiles`, both on `/data`)
        // exit 1 even when mounted. Fall back to checking `/proc/self/mountinfo`.
        val qSpacedTarget = PosixShellQuoting.quote(" $target ")
        return rootExecutor.exec(
            "grep -Fq $qSpacedTarget /proc/self/mountinfo 2>/dev/null",
            timeoutMs = 5_000L,
        ).isSuccess
    }

    private suspend fun findActiveChrootPidsInternal(rootfsPath: String): List<Int> {
        val qRootfs = PosixShellQuoting.quote(rootfsPath)
        val scanCmd = "for p in /proc/[0-9]*; do " +
            "pid=\${p#/proc/}; " +
            "[ \"\$pid\" = \"\$\$\" ] && continue; " +
            "r=\$(readlink \"\$p/root\" 2>/dev/null); " +
            "[ \"\$r\" = $qRootfs ] && echo \"\$pid\"; " +
            "done"
        val res = rootExecutor.exec(scanCmd, timeoutMs = 10_000L)
        if (!res.isSuccess && res.stdout.isBlank()) {
            return emptyList()
        }
        return res.stdout
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { it.toIntOrNull() }
            .filter { it > 1 }
            .distinct()
            .toList()
    }

    private suspend fun terminatePids(pids: List<Int>, signal: String) {
        if (pids.isEmpty()) return
        val pidArgs = pids.joinToString(" ")
        rootExecutor.exec("kill -$signal $pidArgs 2>/dev/null || true", timeoutMs = 10_000L)
    }

    private suspend fun queryMountStatusInternal(
        rootfsPath: String,
        dnsConfigured: Boolean,
        loopbackReachable: Boolean,
    ): MountStatus = MountStatus(
        procMounted = isMountPoint("$rootfsPath/proc"),
        sysMounted = isMountPoint("$rootfsPath/sys"),
        devMounted = isMountPoint("$rootfsPath/dev"),
        devPtsMounted = isMountPoint("$rootfsPath/dev/pts"),
        sdcardMounted = isMountPoint("$rootfsPath/mnt/sdcard"),
        androidRootMounted = isMountPoint("$rootfsPath/mnt/android_root"),
        profilesMounted = isMountPoint("$rootfsPath/profiles"),
        dnsConfigured = dnsConfigured,
        loopbackReachable = loopbackReachable,
    )

    companion object {
        const val FALLBACK_DNS_PRIMARY = "8.8.8.8"
        const val FALLBACK_DNS_SECONDARY = "1.1.1.1"

        fun mountOrderEntries(profilesHostPath: String): List<ChrootMountEntry> = listOf(
            ChrootMountEntry(
                id = "proc",
                source = "proc",
                targetRelative = "proc",
                fsType = "proc",
                isBind = false,
                required = true,
            ),
            ChrootMountEntry(
                id = "sys",
                source = "/sys",
                targetRelative = "sys",
                isBind = true,
                required = true,
            ),
            ChrootMountEntry(
                id = "dev",
                source = "/dev",
                targetRelative = "dev",
                isBind = true,
                required = true,
            ),
            ChrootMountEntry(
                id = "dev_pts",
                source = "/dev/pts",
                targetRelative = "dev/pts",
                isBind = true,
                required = true,
            ),
            ChrootMountEntry(
                id = "sdcard",
                source = "/sdcard",
                targetRelative = "mnt/sdcard",
                isBind = true,
                required = true,
            ),
            ChrootMountEntry(
                id = "android_root",
                source = "/",
                targetRelative = "mnt/android_root",
                isBind = true,
                required = false,
            ),
            ChrootMountEntry(
                id = "profiles",
                source = profilesHostPath,
                targetRelative = "profiles",
                isBind = true,
                required = true,
            ),
        )

        /**
         * Safe reverse-order unmount sequence:
         * `/dev/pts` -> `/dev` -> `/sys` -> `/proc` -> bind dirs.
         */
        fun unmountOrderTargets(rootfsPath: String): List<String> = listOf(
            "$rootfsPath/dev/pts",
            "$rootfsPath/dev",
            "$rootfsPath/sys",
            "$rootfsPath/proc",
            "$rootfsPath/profiles",
            "$rootfsPath/mnt/android_root",
            "$rootfsPath/mnt/sdcard",
        )
    }
}
