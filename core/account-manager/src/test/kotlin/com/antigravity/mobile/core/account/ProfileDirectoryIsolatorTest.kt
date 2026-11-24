package com.antigravity.mobile.core.account

import com.antigravity.mobile.contracts.ProfilePaths
import com.antigravity.mobile.contracts.RootExecutor
import com.antigravity.mobile.contracts.ShellResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileDirectoryIsolatorTest {

    @Test
    fun `ensureDirectories creates isolated profile subdirectories with chmod 700`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val rootExecutor = mockk<RootExecutor>()
        val capturedCmds = mutableListOf<String>()

        coEvery { rootExecutor.exec(capture(capturedCmds), any(), any()) } returns ShellResult(0, "", "")

        val isolator = DefaultProfileDirectoryIsolator(rootExecutor, ioDispatcher = dispatcher)
        val paths = ProfilePaths.forProfileId("prof-uuid-1")

        val res = isolator.ensureDirectories(paths)
        assertTrue(res.isSuccess)
        assertEquals(1, capturedCmds.size)
        val cmd = capturedCmds.first()
        assertTrue(cmd.contains("/data/local/ag_profiles/prof-uuid-1/home/.gemini"))
        assertTrue(cmd.contains("/data/local/ag_profiles/prof-uuid-1/config"))
        assertTrue(cmd.contains("/data/local/ag_profiles/prof-uuid-1/data/conversations"))
        assertTrue(cmd.contains("/data/local/ag_profiles/prof-uuid-1/cache"))
        assertTrue(cmd.contains("chmod 700 /data/local/ag_profiles/prof-uuid-1"))
    }

    @Test
    fun `importDesktopProfile extracts tar_gz archive and detects email from google_accounts_json`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val rootExecutor = mockk<RootExecutor>()

        coEvery { rootExecutor.exec(any(), any(), any()) } answers {
            val cmd = firstArg<String>()
            when {
                cmd.contains("wc -l") -> ShellResult(0, "4\n", "")
                cmd.contains("google_accounts.json") -> ShellResult(0, """{"active":"alice@gmail.com"}""", "")
                else -> ShellResult(0, "", "")
            }
        }

        val isolator = DefaultProfileDirectoryIsolator(rootExecutor, ioDispatcher = dispatcher)
        val paths = ProfilePaths.forProfileId("prof-uuid-2")
        val res = isolator.importDesktopProfile(paths, "/sdcard/Download/desktop-gemini.tar.gz")

        assertTrue(res.isSuccess)
        assertEquals("alice@gmail.com", res.getOrThrow().detectedEmail)
        assertEquals(4, res.getOrThrow().importedFilesCount)
        coVerify { rootExecutor.exec(match { it.contains("tar -xzf /sdcard/Download/desktop-gemini.tar.gz") }, any(), any()) }
    }

    @Test
    fun `importDesktopProfile rejects path traversal`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val rootExecutor = mockk<RootExecutor>(relaxed = true)
        val isolator = DefaultProfileDirectoryIsolator(rootExecutor, ioDispatcher = dispatcher)
        val paths = ProfilePaths.forProfileId("prof-uuid-3")

        val res = isolator.importDesktopProfile(paths, "/sdcard/../etc/shadow")
        assertTrue(res.isFailure)
        assertTrue(res.exceptionOrNull() is IllegalArgumentException)
    }
}
