package com.antigravity.mobile.core.root

import com.antigravity.mobile.contracts.RootProviderType
import com.antigravity.mobile.contracts.ShellResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class RootEnvironmentAndExecutorTest {

    @Test
    fun `detectEnvironment identifies KernelSU with --mount-master and KernelSU Manager`() = runTest {
        val backend = mockk<RootShellBackend>()
        coEvery { backend.isRootGranted() } returns true
        coEvery { backend.executeCommand(any(), any()) } returns ShellResult(
            exitCode = 0,
            stdout = """
                __VER__:1.0.5:KernelSU
                __HELP__:Usage: su [options] --mount-master -M
                __KSU__:1
                __ID__:0
            """.trimIndent(),
            stderr = "",
        )

        val detector = RootEnvironmentDetector(backend)
        val info = detector.detect()

        assertTrue(info.isRootGranted)
        assertEquals(RootProviderType.KERNEL_SU, info.provider)
        assertTrue(info.mountMasterSupported)
        assertEquals(listOf("--mount-master"), info.recommendedSuArgs)
        assertEquals(RootEnvironmentDetector.KERNEL_SU_MANAGER_PACKAGE, info.managerPackageName)
        assertTrue(info.guidanceMessage.contains("KernelSU", ignoreCase = true))
    }

    @Test
    fun `detectEnvironment identifies Magisk with -mm flag`() = runTest {
        val backend = mockk<RootShellBackend>()
        coEvery { backend.isRootGranted() } returns true
        coEvery { backend.executeCommand(any(), any()) } returns ShellResult(
            exitCode = 0,
            stdout = """
                __VER__:28.0:MAGISKSU
                __HELP__:Options: -mm, --mount-master
                __MAGISK__:1
                __ID__:0
            """.trimIndent(),
            stderr = "",
        )

        val info = RootEnvironmentDetector(backend).detect()

        assertTrue(info.isRootGranted)
        assertEquals(RootProviderType.MAGISK, info.provider)
        assertEquals(listOf("-mm"), info.recommendedSuArgs)
        assertEquals(RootEnvironmentDetector.MAGISK_MANAGER_PACKAGE, info.managerPackageName)
    }

    @Test
    fun `detectEnvironment identifies APatch and NONE`() = runTest {
        val backend = mockk<RootShellBackend>()
        coEvery { backend.isRootGranted() } returns true
        coEvery { backend.executeCommand(any(), any()) } returns ShellResult(
            exitCode = 0,
            stdout = "__VER__:APatch 10900\n__APATCH__:1\n__ID__:0",
            stderr = "",
        )
        val apatch = RootEnvironmentDetector(backend).detect()
        assertEquals(RootProviderType.APATCH, apatch.provider)
        assertEquals(listOf("--mount-master"), apatch.recommendedSuArgs)

        coEvery { backend.isRootGranted() } returns false
        val none = RootEnvironmentDetector(backend).detect()
        assertFalse(none.isRootGranted)
        assertEquals(RootProviderType.NONE, none.provider)
        assertTrue(none.guidanceMessage.contains("KernelSU Manager"))
    }

    @Test
    fun `LibsuRootExecutor quotes cwd and argv and never swallows CancellationException`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val backend = mockk<RootShellBackend>()
        val detector = RootEnvironmentDetector(backend)
        val bootstrapper = RootPermissionBootstrapper(backend, detector)
        val executor = LibsuRootExecutor(backend, detector, bootstrapper, dispatcher)

        coEvery { backend.executeCommand(any(), any()) } returns ShellResult(0, "ok", "")

        val res = executor.execArgv(
            argv = listOf("ls", "-la", "/sdcard/My Docs"),
            timeoutMs = 5_000L,
            cwd = "/data/local/tmp/dir'1",
        )
        assertTrue(res.isSuccess)
        coVerify {
            backend.executeCommand(
                "cd '/data/local/tmp/dir'\\''1' && 'ls' '-la' '/sdcard/My Docs'",
                5_000L,
            )
        }

        coEvery { backend.executeCommand(any(), any()) } throws CancellationException("cancelled")
        var caughtCancellation = false
        try {
            executor.exec("sleep 10")
        } catch (_: CancellationException) {
            caughtCancellation = true
        }
        assertTrue("CancellationException must always be rethrown", caughtCancellation)
    }

    @Test
    fun `SuFileRepository supports read, write, and list via shell fallback when SuFile unavailable`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val rootExecutor = mockk<com.antigravity.mobile.contracts.RootExecutor>()
        val unavailableGateway = object : SuFileGateway {
            override fun isAvailable(): Boolean = false
            override fun readBytes(path: String, offset: Long, limit: Int): Pair<ByteArray, Boolean> = error("unused")
            override fun writeBytes(path: String, bytes: ByteArray, append: Boolean) = error("unused")
            override fun listEntries(path: String): List<RootFileEntry> = error("unused")
        }
        val repo = LibsuFileRepository(rootExecutor, unavailableGateway, dispatcher)

        val encoded = Base64.getEncoder().encodeToString("hello world!".toByteArray())
        coEvery { rootExecutor.exec(match { it.contains("dd if='/sdcard/test.txt'") }, any(), any()) } returns
            ShellResult(0, encoded, "")
        val read = repo.readFile("/sdcard/test.txt", offset = 0, limit = 5).getOrThrow()
        assertEquals("hello", read.content)
        assertEquals(5, read.bytesRead)
        assertTrue(read.truncated)

        coEvery { rootExecutor.exec(match { it.contains("base64 -d > '/sdcard/out.txt'") }, any(), any()) } returns
            ShellResult(0, "", "")
        val written = repo.writeFile("/sdcard/out.txt", "abc", append = false).getOrThrow()
        assertEquals(3, written.bytesWritten)
        assertFalse(written.append)

        coEvery { rootExecutor.exec("ls -1Ap '/sdcard'", any(), any()) } returns
            ShellResult(0, "Download/\nnotes.txt\nDCIM/\n", "")
        val listed = repo.listDirectory("/sdcard", offset = 0, limit = 2).getOrThrow()
        assertEquals(3, listed.totalCount)
        assertEquals(2, listed.entries.size)
        assertTrue(listed.truncated)
        assertEquals("DCIM", listed.entries[0].name)
        assertTrue(listed.entries[0].isDirectory)
        assertEquals("Download", listed.entries[1].name)
    }
}
