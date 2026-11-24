package com.antigravity.mobile.core.chroot

import com.antigravity.mobile.contracts.RootExecutor
import com.antigravity.mobile.contracts.ShellResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BindMountManagerTest {

    @Test
    fun `mountAll is idempotent - checks mountpoint -q before every mount and skips already mounted targets`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val mountedTargets = mutableSetOf<String>()
        val mountCommands = mutableListOf<String>()
        val rootExecutor = mockk<RootExecutor>()

        coEvery { rootExecutor.exec(any(), any(), any()) } answers {
            val cmd = firstArg<String>()
            when {
                cmd.startsWith("mountpoint -q ") -> {
                    val target = cmd.removePrefix("mountpoint -q ").trim().trim('\'')
                    if (target in mountedTargets) {
                        ShellResult(0, "", "")
                    } else {
                        ShellResult(1, "", "")
                    }
                }
                cmd.startsWith("mount ") -> {
                    mountCommands += cmd
                    val target = cmd.split(" ").last().trim('\'')
                    mountedTargets += target
                    ShellResult(0, "", "")
                }
                cmd == "getprop net.dns1" -> ShellResult(0, "192.168.1.1\n", "")
                cmd == "getprop net.dns2" -> ShellResult(0, "", "")
                cmd == "getprop net.dns3" -> ShellResult(0, "", "")
                else -> ShellResult(0, "", "")
            }
        }

        val manager = DefaultBindMountManager(
            rootExecutor = rootExecutor,
            ioDispatcher = dispatcher,
            forceKillGraceMs = 0L,
        )

        // First mountAll: mounts all 7 targets.
        val first = manager.mountAll("/data/local/ag_rootfs", "/data/local/ag_profiles")
        assertTrue(first.isSuccess)
        val status = first.getOrThrow()
        assertTrue(status.allCoreMounted)
        assertTrue(status.androidRootMounted)
        assertTrue(status.dnsConfigured)
        assertTrue(status.loopbackReachable)
        assertEquals(7, mountCommands.size)

        // Second mountAll: ZERO additional mount commands (strictly idempotent).
        mountCommands.clear()
        val second = manager.mountAll("/data/local/ag_rootfs", "/data/local/ag_profiles")
        assertTrue(second.isSuccess)
        assertEquals(0, mountCommands.size)
    }

    @Test
    fun `configureDns writes active Android DNS plus 8_8_8_8 and 1_1_1_1 fallback`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val executed = mutableListOf<String>()
        val rootExecutor = mockk<RootExecutor>()

        coEvery { rootExecutor.exec(any(), any(), any()) } answers {
            val cmd = firstArg<String>()
            executed += cmd
            when (cmd) {
                "getprop net.dns1" -> ShellResult(0, "10.0.0.53\n", "")
                "getprop net.dns2" -> ShellResult(0, "bad;rm -rf /\n", "")
                "getprop net.dns3" -> ShellResult(0, "", "")
                else -> ShellResult(0, "", "")
            }
        }

        val manager = DefaultBindMountManager(rootExecutor, dispatcher, forceKillGraceMs = 0L)
        assertTrue(manager.configureDns("/data/local/ag_rootfs"))

        val writeCmd = executed.first { it.contains("/data/local/ag_rootfs/etc/resolv.conf") }
        assertTrue(writeCmd.contains("nameserver 10.0.0.53"))
        assertTrue(writeCmd.contains("nameserver 8.8.8.8"))
        assertTrue(writeCmd.contains("nameserver 1.1.1.1"))
        assertFalse(writeCmd.contains("rm -rf /"))
    }

    @Test
    fun `unmountSafely refuses unmount when active chroot processes remain and force is false`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val umountCommands = mutableListOf<String>()
        val rootExecutor = mockk<RootExecutor>()

        coEvery { rootExecutor.exec(any(), any(), any()) } answers {
            val cmd = firstArg<String>()
            when {
                cmd.contains("readlink") -> ShellResult(0, "4242\n4300\n", "")
                cmd.startsWith("umount ") -> {
                    umountCommands += cmd
                    ShellResult(0, "", "")
                }
                else -> ShellResult(0, "", "")
            }
        }

        val manager = DefaultBindMountManager(rootExecutor, dispatcher, forceKillGraceMs = 0L)
        val res = manager.unmountSafely("/data/local/ag_rootfs", force = false)

        assertTrue(res.isFailure)
        val ex = res.exceptionOrNull() as? ChrootBusyException
        assertEquals(listOf(4242, 4300), ex?.activePids)
        assertEquals(0, umountCommands.size)
    }

    @Test
    fun `unmountSafely unmounts strictly in reverse order when no active processes remain`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val rootfs = "/data/local/ag_rootfs"
        val mountedTargets = DefaultBindMountManager.unmountOrderTargets(rootfs).toMutableSet()
        val umountOrder = mutableListOf<String>()
        val rootExecutor = mockk<RootExecutor>()

        coEvery { rootExecutor.exec(any(), any(), any()) } answers {
            val cmd = firstArg<String>()
            when {
                cmd.contains("readlink") -> ShellResult(0, "", "")
                cmd.startsWith("mountpoint -q ") -> {
                    val target = cmd.removePrefix("mountpoint -q ").trim().trim('\'')
                    if (target in mountedTargets) ShellResult(0, "", "") else ShellResult(1, "", "")
                }
                cmd.startsWith("umount ") -> {
                    val target = cmd.removePrefix("umount ").trim().trim('\'')
                    umountOrder += target
                    mountedTargets.remove(target)
                    ShellResult(0, "", "")
                }
                else -> ShellResult(0, "", "")
            }
        }

        val manager = DefaultBindMountManager(rootExecutor, dispatcher, forceKillGraceMs = 0L)
        val res = manager.unmountSafely(rootfs, force = false)

        assertTrue(res.isSuccess)
        assertEquals(
            listOf(
                "$rootfs/dev/pts",
                "$rootfs/dev",
                "$rootfs/sys",
                "$rootfs/proc",
                "$rootfs/profiles",
                "$rootfs/mnt/android_root",
                "$rootfs/mnt/sdcard",
            ),
            umountOrder,
        )
        assertFalse(res.getOrThrow().allCoreMounted)
    }

    @Test
    fun `unmountSafely with force terminates active pids with SIGTERM then SIGKILL before unmounting`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val rootfs = "/data/local/ag_rootfs"
        val mountedTargets = DefaultBindMountManager.unmountOrderTargets(rootfs).toMutableSet()
        val signalsSent = mutableListOf<String>()
        var scanCount = 0
        val rootExecutor = mockk<RootExecutor>()

        coEvery { rootExecutor.exec(any(), any(), any()) } answers {
            val cmd = firstArg<String>()
            when {
                cmd.contains("readlink") -> {
                    scanCount++
                    when (scanCount) {
                        1 -> ShellResult(0, "1001\n1002\n", "")
                        2 -> ShellResult(0, "1002\n", "") // 1002 survived SIGTERM
                        else -> ShellResult(0, "", "")   // gone after SIGKILL
                    }
                }
                cmd.startsWith("kill -") -> {
                    signalsSent += cmd
                    ShellResult(0, "", "")
                }
                cmd.startsWith("mountpoint -q ") -> {
                    val target = cmd.removePrefix("mountpoint -q ").trim().trim('\'')
                    if (target in mountedTargets) ShellResult(0, "", "") else ShellResult(1, "", "")
                }
                cmd.startsWith("umount ") -> {
                    val target = cmd.removePrefix("umount ").trim().trim('\'')
                    mountedTargets.remove(target)
                    ShellResult(0, "", "")
                }
                else -> ShellResult(0, "", "")
            }
        }

        val manager = DefaultBindMountManager(rootExecutor, dispatcher, forceKillGraceMs = 0L)
        val res = manager.unmountSafely(rootfs, force = true)

        assertTrue(res.isSuccess)
        assertEquals(2, signalsSent.size)
        assertTrue(signalsSent[0].startsWith("kill -TERM 1001 1002"))
        assertTrue(signalsSent[1].startsWith("kill -KILL 1002"))
    }
}
