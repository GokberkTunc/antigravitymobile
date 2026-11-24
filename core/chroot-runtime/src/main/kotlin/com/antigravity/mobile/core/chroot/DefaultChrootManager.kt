package com.antigravity.mobile.core.chroot

import com.antigravity.mobile.contracts.ChrootCommandSpec
import com.antigravity.mobile.contracts.ChrootConstants
import com.antigravity.mobile.contracts.ChrootManager
import com.antigravity.mobile.contracts.ChrootState
import com.antigravity.mobile.contracts.MountStatus
import com.antigravity.mobile.contracts.RootEnvironmentInfo
import com.antigravity.mobile.contracts.RootExecutor
import com.antigravity.mobile.contracts.RootfsSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Default implementation of [ChrootManager] managing ARM64 glibc rootfs installation,
 * KernelSU/Magisk-compatible idempotent bind-mounts, `agy` installation/versioning,
 * POSIX-safe chroot command building, and clean SIGTERM -> SIGKILL termination.
 */
class DefaultChrootManager(
    private val rootExecutor: RootExecutor,
    private val rootfsInstaller: RootfsInstaller,
    private val bindMountManager: BindMountManager,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val rootfsPath: String = ChrootConstants.DEFAULT_ROOTFS_PATH,
    private val profilesHostPath: String = ChrootConstants.DEFAULT_PROFILES_HOST_PATH,
    private val agyBinaryPath: String = ChrootConstants.DEFAULT_AGY_BINARY,
    private val killGracePeriodMs: Long = 500L,
) : ChrootManager {

    private val lifecycleMutex = Mutex()
    private val _state = MutableStateFlow<ChrootState>(ChrootState.NotInstalled)
    override val state: StateFlow<ChrootState> = _state.asStateFlow()

    override suspend fun ensureRootfsReady(source: RootfsSource): Result<Unit> =
        lifecycleMutex.withLock {
            withContext(ioDispatcher) {
                try {
                    if (rootfsInstaller.isInstalled(rootfsPath)) {
                        refreshInstalledState()
                        return@withContext Result.success(Unit)
                    }

                    _state.value = ChrootState.Installing(stage = "Preparing rootfs", progressPercent = 0)
                    val installResult = rootfsInstaller.install(source, rootfsPath) { stage, percent ->
                        _state.value = ChrootState.Installing(stage = stage, progressPercent = percent)
                    }
                    installResult.fold(
                        onSuccess = {
                            val version = readAgyVersionInternal().getOrNull()
                            _state.value = ChrootState.Unmounted(
                                rootfsPath = rootfsPath,
                                agyVersion = version,
                            )
                            Result.success(Unit)
                        },
                        onFailure = { error ->
                            if (error is CancellationException) throw error
                            _state.value = ChrootState.Error(
                                message = error.message ?: "Rootfs installation failed",
                                recoverable = true,
                            )
                            Result.failure(error)
                        },
                    )
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    _state.value = ChrootState.Error(
                        message = t.message ?: "Unexpected rootfs error",
                        recoverable = true,
                    )
                    Result.failure(t)
                }
            }
        }

    override suspend fun mountAll(): Result<MountStatus> =
        lifecycleMutex.withLock {
            withContext(ioDispatcher) {
                try {
                    if (!rootfsInstaller.isInstalled(rootfsPath)) {
                        val err = IllegalStateException("Rootfs is not installed at $rootfsPath")
                        _state.value = ChrootState.NotInstalled
                        return@withContext Result.failure(err)
                    }

                    val mountResult = bindMountManager.mountAll(rootfsPath, profilesHostPath)
                    mountResult.fold(
                        onSuccess = { status ->
                            val version = readAgyVersionInternal().getOrNull()
                            _state.value = ChrootState.Mounted(
                                rootfsPath = rootfsPath,
                                agyVersion = version,
                                mountStatus = status,
                            )
                            Result.success(status)
                        },
                        onFailure = { error ->
                            if (error is CancellationException) throw error
                            _state.value = ChrootState.Error(
                                message = error.message ?: "Failed to mount chroot",
                                recoverable = true,
                            )
                            Result.failure(error)
                        },
                    )
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    _state.value = ChrootState.Error(
                        message = t.message ?: "Unexpected mount error",
                        recoverable = true,
                    )
                    Result.failure(t)
                }
            }
        }

    override suspend fun unmountSafely(force: Boolean): Result<MountStatus> =
        lifecycleMutex.withLock {
            withContext(ioDispatcher) {
                try {
                    val result = bindMountManager.unmountSafely(rootfsPath, force)
                    result.fold(
                        onSuccess = { status ->
                            val prevVersion = currentKnownVersion()
                            _state.value = ChrootState.Unmounted(
                                rootfsPath = rootfsPath,
                                agyVersion = prevVersion,
                            )
                            Result.success(status)
                        },
                        onFailure = { error ->
                            if (error is CancellationException) throw error
                            Result.failure(error)
                        },
                    )
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    Result.failure(t)
                }
            }
        }

    override suspend fun getMountStatus(): MountStatus = withContext(ioDispatcher) {
        bindMountManager.getMountStatus(rootfsPath)
    }

    override suspend fun installOrUpdateAgy(): Result<String> =
        lifecycleMutex.withLock {
            withContext(ioDispatcher) {
                try {
                    if (!rootfsInstaller.isInstalled(rootfsPath)) {
                        return@withContext Result.failure(
                            IllegalStateException("Rootfs must be installed before installing agy"),
                        )
                    }

                    var mountStatus = bindMountManager.getMountStatus(rootfsPath)
                    if (!mountStatus.allCoreMounted) {
                        mountStatus = bindMountManager.mountAll(rootfsPath, profilesHostPath).getOrThrow()
                    }

                    _state.value = ChrootState.Installing(
                        stage = "Installing Antigravity CLI (agy)",
                        progressPercent = null,
                    )

                    val qRootfs = PosixShellQuoting.quote(rootfsPath)
                    val qBinary = PosixShellQuoting.quote(agyBinaryPath)
                    val installScript = buildString {
                        append("set -e; ")
                        append("curl -fsSL https://antigravity.google/cli/install.sh | bash; ")
                        append("FOUND=''; ")
                        append("for candidate in ")
                        append("\"/root/.local/bin/agy\" ")
                        append("\"/root/.agy/bin/agy\" ")
                        append("\"/usr/local/bin/agy\" ")
                        append("\"\$(command -v agy 2>/dev/null || true)\"; do ")
                        append("if [ -n \"\$candidate\" ] && [ -x \"\$candidate\" ]; then FOUND=\"\$candidate\"; break; fi; ")
                        append("done; ")
                        append("if [ -z \"\$FOUND\" ]; then echo 'agy binary not found after install' >&2; exit 1; fi; ")
                        append("mkdir -p \"\$(dirname $qBinary)\"; ")
                        append("if [ \"\$FOUND\" != $qBinary ]; then ln -sf \"\$FOUND\" $qBinary; fi; ")
                        append("chmod 755 \"\$FOUND\"")
                    }

                    val cmd = "chroot $qRootfs /usr/bin/env -i " +
                        "HOME=/root PATH=/usr/local/bin:/usr/bin:/bin:/root/.local/bin:/root/.agy/bin " +
                        "/bin/bash -c ${PosixShellQuoting.quote(installScript)}"

                    val execRes = rootExecutor.exec(cmd, timeoutMs = 300_000L)
                    if (!execRes.isSuccess) {
                        val err = IOException(
                            "Failed to install/update agy (exit=${execRes.exitCode}): " +
                                execRes.stderr.ifBlank { execRes.stdout },
                        )
                        _state.value = ChrootState.Error(
                            message = err.message ?: "agy install failed",
                            recoverable = true,
                        )
                        return@withContext Result.failure(err)
                    }

                    val version = readAgyVersionInternal().getOrThrow()
                        ?: throw IOException("agy installed but `agy --version` returned empty output")

                    _state.value = ChrootState.Mounted(
                        rootfsPath = rootfsPath,
                        agyVersion = version,
                        mountStatus = mountStatus,
                    )
                    Result.success(version)
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    _state.value = ChrootState.Error(
                        message = t.message ?: "agy install error",
                        recoverable = true,
                    )
                    Result.failure(t)
                }
            }
        }

    override suspend fun getAgyVersion(): Result<String?> = withContext(ioDispatcher) {
        readAgyVersionInternal()
    }

    override suspend fun buildChrootCommand(spec: ChrootCommandSpec): List<String> = withContext(ioDispatcher) {
        val envInfo = rootExecutor.detectEnvironment()
        formatChrootCommand(spec = spec, envInfo = envInfo)
    }

    override suspend fun killAllAgyProcesses(): Result<Int> = withContext(ioDispatcher) {
        try {
            val initialPids = findChrootAgyPids()
            if (initialPids.isEmpty()) {
                return@withContext Result.success(0)
            }

            // Step 1: SIGTERM to process groups and individual PIDs.
            sendSignalToAgyPids(initialPids, "TERM")

            if (killGracePeriodMs > 0L) {
                delay(killGracePeriodMs)
            }

            // Step 2: SIGKILL to any survivors.
            val survivors = findChrootAgyPids()
            if (survivors.isNotEmpty()) {
                sendSignalToAgyPids(survivors, "KILL")
            }

            Result.success(initialPids.size)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    private suspend fun refreshInstalledState() {
        val status = bindMountManager.getMountStatus(rootfsPath)
        val version = readAgyVersionInternal().getOrNull()
        _state.value = if (status.allCoreMounted) {
            ChrootState.Mounted(
                rootfsPath = rootfsPath,
                agyVersion = version,
                mountStatus = status,
            )
        } else {
            ChrootState.Unmounted(
                rootfsPath = rootfsPath,
                agyVersion = version,
            )
        }
    }

    private suspend fun readAgyVersionInternal(): Result<String?> {
        return try {
            val qRootfs = PosixShellQuoting.quote(rootfsPath)
            val qHostBinary = PosixShellQuoting.quote("$rootfsPath$agyBinaryPath")
            val exists = rootExecutor.exec("[ -x $qHostBinary ]", timeoutMs = 5_000L)
            if (!exists.isSuccess) {
                return Result.success(null)
            }

            val qBinary = PosixShellQuoting.quote(agyBinaryPath)
            val res = rootExecutor.exec(
                command = "chroot $qRootfs /usr/bin/env -i PATH=/usr/local/bin:/usr/bin:/bin $qBinary --version",
                timeoutMs = 15_000L,
            )
            if (!res.isSuccess) {
                return Result.failure(
                    IOException("Failed to execute agy --version (exit=${res.exitCode}): ${res.stderr}"),
                )
            }
            Result.success(parseVersionOutput(res.stdout))
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    private suspend fun findChrootAgyPids(): List<Int> {
        val qRootfs = PosixShellQuoting.quote(rootfsPath)
        val scanCmd = "for p in /proc/[0-9]*; do " +
            "pid=\${p#/proc/}; " +
            "[ \"\$pid\" = \"\$\$\" ] && continue; " +
            "r=\$(readlink \"\$p/root\" 2>/dev/null); " +
            "[ \"\$r\" = $qRootfs ] || continue; " +
            "cmd=\$(tr '\\000' ' ' < \"\$p/cmdline\" 2>/dev/null); " +
            "case \"\$cmd\" in *agy*) echo \"\$pid\" ;; esac; " +
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

    private suspend fun sendSignalToAgyPids(pids: List<Int>, signal: String) {
        if (pids.isEmpty()) return
        val pidList = pids.joinToString(" ")
        // Send signal to each PID's process group (via /proc/<pid>/stat field 5 PGID) and direct PID.
        val cmd = "for pid in $pidList; do " +
            "pgid=\$(awk '{print \$5}' \"/proc/\$pid/stat\" 2>/dev/null); " +
            "if [ -n \"\$pgid\" ] && [ \"\$pgid\" -gt 1 ] 2>/dev/null; then kill -$signal \"-\$pgid\" 2>/dev/null || true; fi; " +
            "kill -$signal \"\$pid\" 2>/dev/null || true; " +
            "done"
        rootExecutor.exec(cmd, timeoutMs = 10_000L)
    }

    private fun currentKnownVersion(): String? = when (val current = _state.value) {
        is ChrootState.Mounted -> current.agyVersion
        is ChrootState.Unmounted -> current.agyVersion
        else -> null
    }

    companion object {
        private val SEMVER_REGEX = Regex("\\b\\d+\\.\\d+(?:\\.\\d+)?(?:[-+][A-Za-z0-9._-]+)?\\b")

        /**
         * Extracts the version token (e.g. `1.4.11`) from `agy --version` output.
         */
        fun parseVersionOutput(stdout: String): String? {
            val firstLine = stdout.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.isNotEmpty() }
                ?: return null
            return SEMVER_REGEX.find(firstLine)?.value ?: firstLine
        }

        /**
         * Builds a KernelSU/Magisk/APatch-compatible `su ... -c <chroot command>` argv list
         * with strict POSIX quoting and zero shell injection vectors.
         *
         * NEVER emits Magisk-only `-mm`. Uses [RootEnvironmentInfo.recommendedSuArgs] or
         * standard `--mount-master` when [RootEnvironmentInfo.mountMasterSupported] is true.
         */
        fun formatChrootCommand(
            spec: ChrootCommandSpec,
            envInfo: RootEnvironmentInfo,
        ): List<String> {
            val cleanRootfs = PosixShellQuoting.validateAbsolutePath(spec.rootfsPath, "rootfsPath")
            val cleanBinary = PosixShellQuoting.validateAbsolutePath(spec.binaryPath, "binaryPath")
            val cleanWorkDir = spec.workingDirectory?.let {
                PosixShellQuoting.validateAbsolutePath(it, "workingDirectory")
            }

            val effectiveEnv = LinkedHashMap<String, String>()
            effectiveEnv["PATH"] = DEFAULT_CHROOT_PATH
            spec.environment.entries
                .sortedBy { it.key }
                .forEach { (key, value) ->
                    val validKey = PosixShellQuoting.validateEnvKey(key)
                    require('\u0000' !in value) { "Environment value for '$validKey' must not contain NUL bytes" }
                    effectiveEnv[validKey] = value
                }

            val envAssignments = effectiveEnv.entries.joinToString(" ") { (k, v) ->
                "$k=${PosixShellQuoting.quote(v)}"
            }

            val innerCommand = buildString {
                append("chroot ")
                append(PosixShellQuoting.quote(cleanRootfs))
                append(" /usr/bin/env -i ")
                append(envAssignments)
                append(" ")
                if (cleanWorkDir != null) {
                    // Positional parameters prevent any nested shell quoting/injection.
                    append("/bin/sh -c 'cd -- \"\$1\" && shift && exec \"\$@\"' _ ")
                    append(PosixShellQuoting.quote(cleanWorkDir))
                    append(" ")
                    append(PosixShellQuoting.quote(cleanBinary))
                } else {
                    append(PosixShellQuoting.quote(cleanBinary))
                }
                for (arg in spec.args) {
                    append(" ")
                    append(PosixShellQuoting.quote(arg))
                }
            }

            val suPrefix = buildSuPrefix(envInfo)
            return suPrefix + listOf("-c", innerCommand)
        }

        private fun buildSuPrefix(envInfo: RootEnvironmentInfo): List<String> {
            val sanitizedArgs = envInfo.recommendedSuArgs
                .map { it.trim() }
                .filter { it.isNotEmpty() && it != "-c" }
                .map { arg ->
                    // Never emit Magisk-only `-mm`; normalize to cross-provider `--mount-master`.
                    if (arg == "-mm") "--mount-master" else arg
                }

            return when {
                sanitizedArgs.isNotEmpty() -> {
                    if (sanitizedArgs.first() == "su" || sanitizedArgs.first().endsWith("/su")) {
                        sanitizedArgs
                    } else {
                        listOf("su") + sanitizedArgs
                    }
                }
                envInfo.mountMasterSupported -> listOf("su", "--mount-master")
                else -> listOf("su")
            }
        }

        private const val DEFAULT_CHROOT_PATH = "/usr/local/bin:/usr/bin:/bin"
    }
}
