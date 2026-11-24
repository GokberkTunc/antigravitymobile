package com.antigravity.mobile.core.acp

import com.antigravity.mobile.contracts.AcpSessionConfig
import com.antigravity.mobile.contracts.ChrootCommandSpec
import com.antigravity.mobile.contracts.ProfilePaths
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Abstraction over a running `agy acp` stdio process so production code can use
 * [java.lang.Process] while unit/integration tests inject deterministic piped mocks.
 */
interface AcpProcess {
    /** Stream connected to the child process stdin (client writes JSON-RPC lines here). */
    val outputStream: OutputStream

    /** Stream connected to the child process stdout (client reads JSON-RPC lines from here). */
    val inputStream: InputStream

    /** Stream connected to the child process stderr (OAuth URL & crash diagnostics). */
    val errorStream: InputStream

    val isAlive: Boolean

    fun destroy()

    fun destroyForcibly()

    /** Blocks until the process exits and returns its exit code. Must be called off Main Thread. */
    fun waitForExitCode(): Int

    /** Waits up to [timeoutMs] for exit, returning true if the process exited within the window. */
    fun waitForExit(timeoutMs: Long): Boolean

    fun exitCodeOrNull(): Int?
}

/**
 * Full launch descriptor passed to [AcpProcessLauncher].
 */
data class AcpProcessLaunchRequest(
    val command: List<String>,
    val commandSpec: ChrootCommandSpec,
    val profilePaths: ProfilePaths,
    val sessionConfig: AcpSessionConfig,
)

/**
 * Pluggable process launcher for starting `agy acp` inside the rooted ARM64 chroot.
 */
interface AcpProcessLauncher {
    suspend fun launch(request: AcpProcessLaunchRequest): AcpProcess
}

/**
 * [AcpProcess] implementation wrapping a real JVM/Android [Process].
 */
class JvmAcpProcess(
    private val process: Process,
) : AcpProcess {
    override val outputStream: OutputStream
        get() = process.outputStream

    override val inputStream: InputStream
        get() = process.inputStream

    override val errorStream: InputStream
        get() = process.errorStream

    override val isAlive: Boolean
        get() = process.isAlive

    override fun destroy() {
        process.destroy()
    }

    override fun destroyForcibly() {
        process.destroyForcibly()
    }

    override fun waitForExitCode(): Int = process.waitFor()

    override fun waitForExit(timeoutMs: Long): Boolean =
        process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)

    override fun exitCodeOrNull(): Int? =
        if (process.isAlive) null else runCatching { process.exitValue() }.getOrNull()
}

/**
 * Production [AcpProcessLauncher] executing the constructed chroot/root command via
 * [ProcessBuilder] strictly on [ioDispatcher] (`Dispatchers.IO`).
 */
class ProcessBuilderAcpProcessLauncher(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AcpProcessLauncher {
    override suspend fun launch(request: AcpProcessLaunchRequest): AcpProcess =
        withContext(ioDispatcher) {
            require(request.command.isNotEmpty()) {
                "ACP launch command must not be empty"
            }
            val process = ProcessBuilder(request.command)
                .redirectErrorStream(false)
                .start()
            JvmAcpProcess(process)
        }
}
