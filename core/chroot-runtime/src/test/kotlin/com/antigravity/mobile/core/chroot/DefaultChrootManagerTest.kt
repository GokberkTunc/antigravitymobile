package com.antigravity.mobile.core.chroot

import com.antigravity.mobile.contracts.ChrootCommandSpec
import com.antigravity.mobile.contracts.ChrootState
import com.antigravity.mobile.contracts.MountStatus
import com.antigravity.mobile.contracts.RootEnvironmentInfo
import com.antigravity.mobile.contracts.RootExecutor
import com.antigravity.mobile.contracts.RootProviderType
import com.antigravity.mobile.contracts.RootfsSource
import com.antigravity.mobile.contracts.ShellResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultChrootManagerTest {

    @Test
    fun `buildChrootCommand uses KernelSU compatible mount-master flag and NEVER emits Magisk-only -mm`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val rootExecutor = mockk<RootExecutor>()
        val ksuEnv = RootEnvironmentInfo(
            isRootGranted = true,
            provider = RootProviderType.KERNEL_SU,
            mountMasterSupported = true,
            recommendedSuArgs = listOf("--mount-master"),
        )
        coEvery { rootExecutor.detectEnvironment() } returns ksuEnv

        val manager = DefaultChrootManager(
            rootExecutor = rootExecutor,
            rootfsInstaller = mockk(relaxed = true),
            bindMountManager = mockk(relaxed = true),
            ioDispatcher = dispatcher,
            killGracePeriodMs = 0L,
        )

        val spec = ChrootCommandSpec(
            args = listOf("acp", "--process-per-session", "--add-dir", "/mnt/sdcard/My Project"),
            environment = mapOf(
                "HOME" to "/profiles/uuid-1/home",
                "XDG_CONFIG_HOME" to "/profiles/uuid-1/config",
            ),
            workingDirectory = "/mnt/sdcard/My Project",
        )

        val argv = manager.buildChrootCommand(spec)
        assertEquals("su", argv[0])
        assertEquals("--mount-master", argv[1])
        assertEquals("-c", argv[2])
        assertFalse("Must NEVER contain Magisk-only -mm", argv.contains("-mm"))

        val inner = argv[3]
        assertTrue(inner.contains("chroot /data/local/ag_rootfs /usr/bin/env -i"))
        assertTrue(inner.contains("HOME=/profiles/uuid-1/home"))
        assertTrue(inner.contains("'/mnt/sdcard/My Project'"))
        assertTrue(inner.contains("/bin/sh -c 'cd -- \"\$1\" && shift && exec \"\$@\"' _ '/mnt/sdcard/My Project'"))
    }

    @Test
    fun `buildChrootCommand normalizes legacy -mm flag to --mount-master and neutralizes injection`() {
        val legacyEnv = RootEnvironmentInfo(
            isRootGranted = true,
            provider = RootProviderType.MAGISK,
            mountMasterSupported = true,
            recommendedSuArgs = listOf("-mm"),
        )

        val injectionArg = "\$(touch /tmp/pwned); rm -rf / #"
        val injectionEnvVal = "foo'bar\$(id)"
        val spec = ChrootCommandSpec(
            args = listOf("--prompt", injectionArg),
            environment = mapOf("CUSTOM_VAR" to injectionEnvVal),
        )

        val argv = DefaultChrootManager.formatChrootCommand(spec, legacyEnv)
        assertFalse(argv.contains("-mm"))
        assertEquals("--mount-master", argv[1])
        val inner = argv[3]
        assertTrue(inner.contains("'${injectionArg}'"))
        assertTrue(inner.contains("CUSTOM_VAR='foo'\"'\"'bar\$(id)'"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `buildChrootCommand rejects shell injection in environment key`() {
        val env = RootEnvironmentInfo(isRootGranted = true, provider = RootProviderType.KERNEL_SU)
        val spec = ChrootCommandSpec(
            environment = mapOf("BAD;KEY" to "value"),
        )
        DefaultChrootManager.formatChrootCommand(spec, env)
    }

    @Test
    fun `installOrUpdateAgy mounts if needed, runs official install script, and verifies version`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val rootExecutor = mockk<RootExecutor>()
        val installer = mockk<RootfsInstaller>()
        val mountManager = mockk<BindMountManager>()
        val fullMounts = MountStatus(
            procMounted = true,
            sysMounted = true,
            devMounted = true,
            devPtsMounted = true,
            sdcardMounted = true,
            androidRootMounted = true,
            profilesMounted = true,
            dnsConfigured = true,
            loopbackReachable = true,
        )

        coEvery { installer.isInstalled(any()) } returns true
        coEvery { mountManager.getMountStatus(any()) } returns MountStatus() // initially unmounted
        coEvery { mountManager.mountAll(any(), any()) } returns Result.success(fullMounts)
        coEvery { rootExecutor.exec(match { it.contains("https://antigravity.google/cli/install.sh") }, any(), any()) } returns
            ShellResult(0, "Installed agy", "")
        coEvery { rootExecutor.exec(match { it.startsWith("[ -x ") }, any(), any()) } returns
            ShellResult(0, "", "")
        coEvery { rootExecutor.exec(match { it.contains("--version") }, any(), any()) } returns
            ShellResult(0, "1.4.11\n", "")

        val manager = DefaultChrootManager(
            rootExecutor = rootExecutor,
            rootfsInstaller = installer,
            bindMountManager = mountManager,
            ioDispatcher = dispatcher,
            killGracePeriodMs = 0L,
        )

        val result = manager.installOrUpdateAgy()
        assertTrue(result.isSuccess)
        assertEquals("1.4.11", result.getOrThrow())

        val state = manager.state.value
        assertTrue(state is ChrootState.Mounted)
        assertEquals("1.4.11", (state as ChrootState.Mounted).agyVersion)
    }

    @Test
    fun `getAgyVersion returns null when binary is not installed`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val rootExecutor = mockk<RootExecutor>()
        coEvery { rootExecutor.exec(match { it.startsWith("[ -x ") }, any(), any()) } returns
            ShellResult(1, "", "")

        val manager = DefaultChrootManager(
            rootExecutor = rootExecutor,
            rootfsInstaller = mockk(relaxed = true),
            bindMountManager = mockk(relaxed = true),
            ioDispatcher = dispatcher,
        )

        val versionRes = manager.getAgyVersion()
        assertTrue(versionRes.isSuccess)
        assertNull(versionRes.getOrNull())
    }

    @Test
    fun `killAllAgyProcesses sends SIGTERM then SIGKILL to surviving chroot agy process groups`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val rootExecutor = mockk<RootExecutor>()
        val commands = mutableListOf<String>()
        var scanCalls = 0

        coEvery { rootExecutor.exec(any(), any(), any()) } answers {
            val cmd = firstArg<String>()
            commands += cmd
            if (cmd.contains("cmdline")) {
                scanCalls++
                if (scanCalls == 1) ShellResult(0, "5010\n5011\n", "")
                else ShellResult(0, "5011\n", "")
            } else {
                ShellResult(0, "", "")
            }
        }

        val manager = DefaultChrootManager(
            rootExecutor = rootExecutor,
            rootfsInstaller = mockk(relaxed = true),
            bindMountManager = mockk(relaxed = true),
            ioDispatcher = dispatcher,
            killGracePeriodMs = 0L,
        )

        val res = manager.killAllAgyProcesses()
        assertTrue(res.isSuccess)
        assertEquals(2, res.getOrThrow())

        val termCmd = commands.first { it.contains("kill -TERM") }
        val killCmd = commands.first { it.contains("kill -KILL") }
        assertTrue(termCmd.contains("5010 5011"))
        assertTrue(killCmd.contains("5011"))
    }

    @Test
    fun `ensureRootfsReady never swallows CancellationException`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val installer = mockk<RootfsInstaller>()
        coEvery { installer.isInstalled(any()) } throws CancellationException("cancelled")

        val manager = DefaultChrootManager(
            rootExecutor = mockk(relaxed = true),
            rootfsInstaller = installer,
            bindMountManager = mockk(relaxed = true),
            ioDispatcher = dispatcher,
        )

        val caughtCancellation = try {
            manager.ensureRootfsReady(RootfsSource.BundledAsset())
            false
        } catch (_: CancellationException) {
            true
        }
        assertTrue(caughtCancellation)
    }
}
