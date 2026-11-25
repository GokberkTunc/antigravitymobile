package com.antigravity.mobile.core.acp

import com.antigravity.mobile.contracts.AcpModel
import com.antigravity.mobile.contracts.AcpSessionConfig
import com.antigravity.mobile.contracts.ChrootConstants
import com.antigravity.mobile.contracts.ChrootManager
import com.antigravity.mobile.contracts.PermissionMode
import com.antigravity.mobile.contracts.RootEnvironmentInfo
import com.antigravity.mobile.contracts.RootExecutor
import com.antigravity.mobile.contracts.RootProviderType
import com.antigravity.mobile.contracts.ThinkingEffort
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AcpLaunchCommandBuilderTest {

    @Test
    fun `buildCommandSpec produces exact agy 1_4_11 flags and isolated profile env`() {
        val builder = AcpLaunchCommandBuilder()
        val config = AcpSessionConfig(
            profileId = "profile-123",
            workspaceDir = "/mnt/sdcard",
            additionalDirs = listOf("/mnt/sdcard"),
            model = AcpModel.GEMINI_4_CARBON,
            thinkingEffort = ThinkingEffort.HIGH,
            permissionMode = PermissionMode.AUTO,
            ideMcpPort = 41234,
            processPerSession = true,
            agentName = "android-agent",
            sandboxed = true,
        )

        val spec = builder.buildCommandSpec(config)

        assertEquals(ChrootConstants.DEFAULT_ROOTFS_PATH, spec.rootfsPath)
        assertEquals("/usr/local/bin/agy", spec.binaryPath)
        assertEquals(
            listOf(
                "acp",
                "--stdio",
                "--process-per-session",
                "--ide-mcp-port",
                "41234",
                "--add-dir",
                "/mnt/sdcard",
                "--app-data-dir",
                "antigravity-cli",
                "--gemini-dir",
                ".gemini",
                "--log-file",
                "/profiles/profile-123/data/acp.log",
                "--agent",
                "android-agent",
                "--sandbox",
            ),
            spec.args,
        )

        val appDataDirValue = spec.args[spec.args.indexOf("--app-data-dir") + 1]
        val geminiDirValue = spec.args[spec.args.indexOf("--gemini-dir") + 1]
        assertFalse("--app-data-dir must be relative", appDataDirValue.startsWith("/"))
        assertFalse("--gemini-dir must be relative", geminiDirValue.startsWith("/"))

        assertEquals("/profiles/profile-123/home", spec.environment["HOME"])
        assertEquals("/profiles/profile-123/config", spec.environment["XDG_CONFIG_HOME"])
        assertEquals("/profiles/profile-123/data", spec.environment["XDG_DATA_HOME"])
        assertEquals("/profiles/profile-123/cache", spec.environment["XDG_CACHE_HOME"])
        assertEquals("/profiles/profile-123/data/conversations", spec.environment["AGY_CONVERSATIONS_DIR"])
        assertEquals("/usr/local/bin:/usr/bin:/bin", spec.environment["PATH"])

        // Must never emit nonexistent agy acp flags or dangerously-skip-permissions
        assertFalse(spec.args.contains("--model"))
        assertFalse(spec.args.contains("--effort"))
        assertFalse(spec.args.contains("--mode"))
        assertFalse(spec.args.contains("--login"))
        assertFalse(spec.args.contains("--dangerously-skip-permissions"))
    }

    @Test
    fun `buildCommandSpec omits optional flags when disabled or zero`() {
        val builder = AcpLaunchCommandBuilder()
        val config = AcpSessionConfig(
            profileId = "p-minimal",
            workspaceDir = "/mnt/sdcard/Work",
            additionalDirs = listOf("/mnt/sdcard", "/mnt/sdcard/Work"),
            ideMcpPort = 0,
            processPerSession = false,
            agentName = null,
            sandboxed = false,
        )

        val spec = builder.buildCommandSpec(config)

        assertFalse(spec.args.contains("--process-per-session"))
        assertFalse(spec.args.contains("--ide-mcp-port"))
        assertFalse(spec.args.contains("--agent"))
        assertFalse(spec.args.contains("--sandbox"))

        // Deduplicated --add-dir preserves workspaceDir first then additionalDirs
        val addDirValues = spec.args.windowed(2).filter { it[0] == "--add-dir" }.map { it[1] }
        assertEquals(listOf("/mnt/sdcard/Work", "/mnt/sdcard"), addDirValues)
    }

    @Test
    fun `buildLaunchRequest wraps KernelSU with mount-master and never hardcodes su -mm`() = runTest {
        val rootExecutor = mockk<RootExecutor>()
        coEvery { rootExecutor.detectEnvironment() } returns RootEnvironmentInfo(
            isRootGranted = true,
            provider = RootProviderType.KERNEL_SU,
            suVersion = "KernelSU 1.0",
            mountMasterSupported = true,
            recommendedSuArgs = emptyList(),
        )

        val builder = AcpLaunchCommandBuilder(
            chrootManager = null,
            rootExecutor = rootExecutor,
        )
        val request = builder.buildLaunchRequest(
            AcpSessionConfig(profileId = "ksu-profile", ideMcpPort = 9001),
        )

        assertEquals("su", request.command[0])
        assertEquals("--mount-master", request.command[1])
        assertEquals("-c", request.command[2])
        assertFalse(request.command.contains("-mm"))
        assertFalse(request.command[3].contains("-mm"))
        assertTrue(request.command[3].contains("chroot /data/local/ag_rootfs /usr/bin/env -i"))
        assertTrue(request.command[3].contains("HOME=/profiles/ksu-profile/home"))
        assertTrue(request.command[3].contains("/usr/local/bin/agy acp --stdio --process-per-session --ide-mcp-port 9001"))
    }

    @Test
    fun `buildLaunchRequest honors recommendedSuArgs without double-wrapping`() = runTest {
        val rootExecutor = mockk<RootExecutor>()
        coEvery { rootExecutor.detectEnvironment() } returns RootEnvironmentInfo(
            isRootGranted = true,
            provider = RootProviderType.APATCH,
            mountMasterSupported = true,
            recommendedSuArgs = listOf("su", "-M"),
        )

        val builder = AcpLaunchCommandBuilder(rootExecutor = rootExecutor)
        val request = builder.buildLaunchRequest(AcpSessionConfig(profileId = "apatch-user"))

        assertEquals(listOf("su", "-M", "-c"), request.command.take(3))
        assertEquals(4, request.command.size)
    }

    @Test
    fun `buildLaunchRequest uses bare su -c when mountMasterSupported is false on generic su`() = runTest {
        val rootExecutor = mockk<RootExecutor>()
        coEvery { rootExecutor.detectEnvironment() } returns RootEnvironmentInfo(
            isRootGranted = true,
            provider = RootProviderType.GENERIC_SU,
            mountMasterSupported = false,
        )

        val builder = AcpLaunchCommandBuilder(rootExecutor = rootExecutor)
        val request = builder.buildLaunchRequest(AcpSessionConfig(profileId = "generic-user"))

        assertEquals(listOf("su", "-c"), request.command.take(2))
        assertEquals(3, request.command.size)
    }

    @Test
    fun `buildLaunchRequest does not double-wrap when ChrootManager already returns su argv`() = runTest {
        val chrootManager = mockk<ChrootManager>()
        val expectedCommand = listOf("su", "--mount-master", "-c", "chroot /data/local/ag_rootfs /usr/local/bin/agy acp --stdio")
        coEvery { chrootManager.buildChrootCommand(any()) } returns expectedCommand

        val builder = AcpLaunchCommandBuilder(chrootManager = chrootManager)
        val request = builder.buildLaunchRequest(AcpSessionConfig(profileId = "managed-user"))

        assertEquals(expectedCommand, request.command)
    }

    @Test
    fun `buildLaunchRequest wraps bare chroot command returned by ChrootManager`() = runTest {
        val chrootManager = mockk<ChrootManager>()
        coEvery { chrootManager.buildChrootCommand(any()) } returns listOf(
            "chroot",
            "/data/local/ag_rootfs",
            "/usr/bin/env",
            "-i",
            "/usr/local/bin/agy",
            "acp",
            "--stdio",
        )
        val rootExecutor = mockk<RootExecutor>()
        coEvery { rootExecutor.detectEnvironment() } returns RootEnvironmentInfo(
            isRootGranted = true,
            provider = RootProviderType.MAGISK,
            mountMasterSupported = true,
        )

        val builder = AcpLaunchCommandBuilder(
            chrootManager = chrootManager,
            rootExecutor = rootExecutor,
        )
        val request = builder.buildLaunchRequest(AcpSessionConfig(profileId = "magisk-user"))

        assertEquals("su", request.command[0])
        assertEquals("--mount-master", request.command[1])
        assertEquals("-c", request.command[2])
        assertTrue(request.command[3].startsWith("chroot /data/local/ag_rootfs"))
    }

    @Test
    fun `shellQuote escapes spaces quotes and shell metacharacters safely`() {
        assertEquals("/mnt/sdcard", AcpLaunchCommandBuilder.shellQuote("/mnt/sdcard"))
        assertEquals("'/mnt/sdcard/My Project'", AcpLaunchCommandBuilder.shellQuote("/mnt/sdcard/My Project"))
        assertEquals("'/tmp/a'\"'\"'b;rm -rf /'", AcpLaunchCommandBuilder.shellQuote("/tmp/a'b;rm -rf /"))
        assertEquals("''", AcpLaunchCommandBuilder.shellQuote(""))
    }

    @Test
    fun `app-data-dir and gemini-dir flags are always relative while env isolates profile`() = runTest {
        val builder = AcpLaunchCommandBuilder()
        val profileId = "48ed61eb-d59a-47cb-a068-29103797bb90"
        val request = builder.buildLaunchRequest(AcpSessionConfig(profileId = profileId))
        val args = request.commandSpec.args

        val appDataDir = args[args.indexOf("--app-data-dir") + 1]
        val geminiDir = args[args.indexOf("--gemini-dir") + 1]
        val logFile = args[args.indexOf("--log-file") + 1]

        assertEquals(AcpLaunchCommandBuilder.DEFAULT_APP_DATA_DIR_NAME, appDataDir)
        assertEquals(AcpLaunchCommandBuilder.DEFAULT_GEMINI_DIR_NAME, geminiDir)
        assertFalse(appDataDir.startsWith("/"))
        assertFalse(geminiDir.startsWith("/"))
        assertEquals("/profiles/$profileId/data/acp.log", logFile)
        assertEquals("/profiles/$profileId/home", request.commandSpec.environment["HOME"])
        assertEquals("/profiles/$profileId/data", request.commandSpec.environment["XDG_DATA_HOME"])

        val shellCmd = request.command.last()
        assertTrue(shellCmd.contains("--app-data-dir antigravity-cli"))
        assertTrue(shellCmd.contains("--gemini-dir .gemini"))
        assertFalse(shellCmd.contains("--app-data-dir /"))
        assertFalse(shellCmd.contains("--gemini-dir /"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `buildCommandSpec rejects path traversal profileId`() {
        val builder = AcpLaunchCommandBuilder()
        builder.buildCommandSpec(AcpSessionConfig(profileId = "../escape"))
    }
}
