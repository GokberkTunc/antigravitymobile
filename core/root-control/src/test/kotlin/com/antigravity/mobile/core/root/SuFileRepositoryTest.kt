package com.antigravity.mobile.core.root

import com.antigravity.mobile.contracts.RootExecutor
import com.antigravity.mobile.contracts.ShellResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SuFileRepositoryTest {

    private val unavailableGateway = object : SuFileGateway {
        override fun isAvailable(): Boolean = false
        override fun readBytes(path: String, offset: Long, limit: Int): Pair<ByteArray, Boolean> =
            error("Not used")
        override fun writeBytes(path: String, bytes: ByteArray, append: Boolean) =
            error("Not used")
        override fun listEntries(path: String): List<RootFileEntry> =
            error("Not used")
    }

    @Test
    fun `readFile shell fallback fails when file is missing or unreadable`() = runTest {
        val executor = mockk<RootExecutor>()
        coEvery { executor.exec(any()) } returns ShellResult(
            exitCode = 1,
            stdout = "",
            stderr = "unreadable",
        )
        val repo = LibsuFileRepository(
            rootExecutor = executor,
            suFileGateway = unavailableGateway,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        val result = repo.readFile("/data/local/tmp/missing.txt", offset = 0, limit = 1024)
        assertTrue(result.isFailure)
    }

    @Test
    fun `writeFile shell fallback chunks large payloads to stay below ARG_MAX`() = runTest {
        val executor = mockk<RootExecutor>()
        coEvery { executor.exec(any()) } returns ShellResult(exitCode = 0, stdout = "", stderr = "")
        val repo = LibsuFileRepository(
            rootExecutor = executor,
            suFileGateway = unavailableGateway,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        // 60 KiB payload (> 24 KiB chunk size) must issue init + 3 chunked writes
        val largeContent = "A".repeat(60 * 1024)
        val result = repo.writeFile("/data/local/tmp/large.txt", largeContent, append = false)

        assertTrue(result.isSuccess)
        assertEquals(60 * 1024, result.getOrThrow().bytesWritten)
        coVerify(exactly = 4) { executor.exec(any()) }
    }
}
