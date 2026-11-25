package com.antigravity.mobile.core.acp

import com.antigravity.mobile.contracts.AcpSessionConfig
import com.antigravity.mobile.contracts.ChrootCommandSpec
import com.antigravity.mobile.contracts.ChrootConstants
import com.antigravity.mobile.contracts.ChrootManager
import com.antigravity.mobile.contracts.ProfilePaths
import com.antigravity.mobile.contracts.RootEnvironmentInfo
import com.antigravity.mobile.contracts.RootExecutor
import com.antigravity.mobile.contracts.RootProviderType

/**
 * Constructs verified `agy 1.4.11` `agy acp` launch commands inside the ARM64 glibc chroot
 * with strict per-profile environment isolation and KernelSU/Magisk/APatch-compatible root
 * wrapping (never hardcoding Magisk-only `su -mm`).
 */
class AcpLaunchCommandBuilder(
    private val chrootManager: ChrootManager? = null,
    private val rootExecutor: RootExecutor? = null,
    private val rootfsPath: String = ChrootConstants.DEFAULT_ROOTFS_PATH,
    private val agyBinaryPath: String = ChrootConstants.DEFAULT_AGY_BINARY,
) {
    internal fun chrootManagerOrNull(): ChrootManager? = chrootManager
    /**
     * Builds the [ChrootCommandSpec] containing verified `agy acp` 1.4.11 flags and isolated
     * profile environment variables.
     */
    fun buildCommandSpec(
        config: AcpSessionConfig,
        profilePaths: ProfilePaths = ProfilePaths.forProfileId(config.profileId),
    ): ChrootCommandSpec {
        val args = buildAgyAcpArgs(config, profilePaths)
        val env = profilePaths.toChrootEnvironment()
        return ChrootCommandSpec(
            rootfsPath = rootfsPath,
            binaryPath = agyBinaryPath,
            args = args,
            environment = env,
            workingDirectory = config.workspaceDir.takeIf { it.isNotBlank() },
        )
    }

    /**
     * Builds the full [AcpProcessLaunchRequest] using [ChrootManager.buildChrootCommand] when
     * available and wrapping with a KernelSU/Magisk/APatch-compatible `su` invocation when needed.
     */
    suspend fun buildLaunchRequest(
        config: AcpSessionConfig,
        profilePaths: ProfilePaths = ProfilePaths.forProfileId(config.profileId),
    ): AcpProcessLaunchRequest {
        val spec = buildCommandSpec(config, profilePaths)
        val rootEnv = rootExecutor?.detectEnvironment()
        val chrootCommand = chrootManager?.buildChrootCommand(spec)?.takeIf { it.isNotEmpty() }
            ?: buildInnerChrootArgv(spec)

        val finalCommand = if (isSuInvocation(chrootCommand)) {
            chrootCommand
        } else {
            wrapWithRoot(chrootCommand, rootEnv)
        }

        return AcpProcessLaunchRequest(
            command = finalCommand,
            commandSpec = spec,
            profilePaths = profilePaths,
            sessionConfig = config,
        )
    }

    /**
     * Builds verified `agy 1.4.11` `agy acp` arguments per `docs/agy_acp_reference.md`.
     *
     * Note: `--model`, `--effort`, and `--mode` are NOT `agy acp` CLI flags in `agy 1.4.11`;
     * those are passed in JSON-RPC session parameters (`session/new` / `session/load`).
     */
    internal fun buildAgyAcpArgs(
        config: AcpSessionConfig,
        profilePaths: ProfilePaths,
    ): List<String> = buildList {
        add("acp")
        add("--stdio")
        if (config.processPerSession) {
            add("--process-per-session")
        }
        if (config.ideMcpPort > 0) {
            require(config.ideMcpPort in 1..65535) {
                "ideMcpPort must be between 1 and 65535, got: ${config.ideMcpPort}"
            }
            add("--ide-mcp-port")
            add(config.ideMcpPort.toString())
        }

        val addDirs = buildList {
            if (config.workspaceDir.isNotBlank()) add(config.workspaceDir)
            config.additionalDirs.filter { it.isNotBlank() }.forEach { add(it) }
        }.distinct()
        for (dir in addDirs) {
            add("--add-dir")
            add(dir)
        }

        // `agy acp` requires `--app-data-dir` and `--gemini-dir` to be RELATIVE subdirectory names
        // under `$HOME` (passing absolute paths causes `language server failed to start: must not be absolute`).
        // Per-profile isolation is guaranteed by `HOME=/profiles/<profileId>/home` in the chroot env.
        add("--app-data-dir")
        add(DEFAULT_APP_DATA_DIR_NAME)
        add("--gemini-dir")
        add(DEFAULT_GEMINI_DIR_NAME)
        add("--log-file")
        add("${profilePaths.chrootData}/acp.log")

        val agentName = config.agentName?.trim()?.takeIf { it.isNotEmpty() }
        if (agentName != null) {
            add("--agent")
            add(agentName)
        }
        if (config.sandboxed) {
            add("--sandbox")
        }
    }

    /**
     * Builds the unprivileged-shell-escaped `chroot <rootfs> /usr/bin/env -i ...` argv list.
     */
    internal fun buildInnerChrootArgv(spec: ChrootCommandSpec): List<String> = buildList {
        add("chroot")
        add(spec.rootfsPath)
        add("/usr/bin/env")
        add("-i")
        spec.environment.entries
            .sortedBy { it.key }
            .forEach { (key, value) ->
                require(ENV_KEY_REGEX.matches(key)) { "Invalid environment variable name: $key" }
                add("$key=$value")
            }
        add(spec.binaryPath)
        addAll(spec.args)
    }

    /**
     * Wraps [innerArgv] in a KernelSU/Magisk/APatch-compatible `su` invocation.
     * Uses `recommendedSuArgs` from [RootEnvironmentInfo] when available, or `--mount-master`
     * (matching `libsu` `Shell.FLAG_MOUNT_MASTER`), and NEVER hardcodes Magisk-only `-mm`.
     */
    internal fun wrapWithRoot(
        innerArgv: List<String>,
        rootEnvironment: RootEnvironmentInfo?,
    ): List<String> {
        val shellCommand = innerArgv.joinToString(" ") { shellQuote(it) }
        val customArgs = rootEnvironment?.recommendedSuArgs
            ?.filter { it.isNotBlank() }
            .orEmpty()

        if (customArgs.isNotEmpty()) {
            return buildList {
                if (customArgs.first() != "su" && !customArgs.first().endsWith("/su")) {
                    add("su")
                }
                addAll(customArgs)
                if (customArgs.last() != "-c") {
                    add("-c")
                }
                add(shellCommand)
            }
        }

        val useMountMaster = rootEnvironment == null ||
            rootEnvironment.mountMasterSupported ||
            rootEnvironment.provider in MOUNT_MASTER_PROVIDERS

        return if (useMountMaster) {
            listOf("su", "--mount-master", "-c", shellCommand)
        } else {
            listOf("su", "-c", shellCommand)
        }
    }

    private fun isSuInvocation(command: List<String>): Boolean {
        val first = command.firstOrNull() ?: return false
        return first == "su" || first.endsWith("/su")
    }

    companion object {
        const val DEFAULT_APP_DATA_DIR_NAME = "antigravity-cli"
        const val DEFAULT_GEMINI_DIR_NAME = ".gemini"

        private val ENV_KEY_REGEX = Regex("^[A-Za-z_][A-Za-z0-9_]*$")
        private val SAFE_UNQUOTED_REGEX = Regex("^[A-Za-z0-9_./:=+-]+$")
        private val MOUNT_MASTER_PROVIDERS = setOf(
            RootProviderType.KERNEL_SU,
            RootProviderType.MAGISK,
            RootProviderType.APATCH,
        )

        /**
         * POSIX-safe single-quote escaping to prevent shell injection in `su -c` commands.
         */
        fun shellQuote(value: String): String {
            if (value.isEmpty()) return "''"
            if (SAFE_UNQUOTED_REGEX.matches(value)) return value
            return "'" + value.replace("'", "'\"'\"'") + "'"
        }
    }
}
