package com.antigravity.mobile.core.root

import com.antigravity.mobile.contracts.ShellResult
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Low-level root shell execution backend abstracted for deterministic unit testing.
 * Production builds bind [LibsuShellBackend] backed by `libsu` (`Shell.FLAG_MOUNT_MASTER`).
 */
interface RootShellBackend {
    suspend fun isRootGranted(): Boolean

    suspend fun executeCommand(
        command: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): ShellResult

    companion object {
        const val DEFAULT_TIMEOUT_MS: Long = 30_000L
    }
}

/**
 * `libsu`-backed [RootShellBackend] configured with [Shell.FLAG_MOUNT_MASTER] so bind-mounts
 * and privileged commands work natively on KernelSU (Android 17), APatch, and Magisk without
 * hardcoding Magisk-specific `su -mm` flags.
 *
 * All shell invocations run strictly off the Android Main Thread on [ioDispatcher].
 */
@Singleton
class LibsuShellBackend(
    private val verboseLogging: Boolean = BuildConfig.DEBUG,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val defaultShellTimeoutSec: Long = 20L,
) : RootShellBackend {

    @Inject
    constructor() : this(
        verboseLogging = BuildConfig.DEBUG,
        ioDispatcher = Dispatchers.IO,
        defaultShellTimeoutSec = 20L,
    )

    private fun ensureShellConfigured() {
        if (shellInitialized.compareAndSet(false, true)) {
            Shell.enableVerboseLogging = verboseLogging
            try {
                Shell.setDefaultBuilder(
                    Shell.Builder.create()
                        .setFlags(Shell.FLAG_MOUNT_MASTER)
                        .setTimeout(defaultShellTimeoutSec),
                )
            } catch (_: IllegalStateException) {
                // Default builder may already have been initialized by Application.onCreate.
            }
        }
    }

    override suspend fun isRootGranted(): Boolean = withContext(ioDispatcher) {
        ensureShellConfigured()
        try {
            val cached = Shell.isAppGrantedRoot()
            if (cached != null) return@withContext cached
            val shell = Shell.getShell()
            shell.isRoot
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            false
        }
    }

    override suspend fun executeCommand(
        command: String,
        timeoutMs: Long,
    ): ShellResult = withContext(ioDispatcher) {
        ensureShellConfigured()
        require(timeoutMs > 0L) { "timeoutMs must be positive, got $timeoutMs" }
        val startedAt = System.nanoTime()
        try {
            val result = withTimeoutOrNull(timeoutMs) {
                submitLibsuCommand(command)
            }
            val elapsedMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
            result?.copy(durationMs = elapsedMs) ?: ShellResult(
                exitCode = 124,
                stdout = "",
                stderr = "Command timed out after ${timeoutMs}ms",
                timedOut = true,
                durationMs = elapsedMs,
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            val elapsedMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
            ShellResult(
                exitCode = 1,
                stdout = "",
                stderr = t.message ?: t::class.java.simpleName,
                timedOut = false,
                durationMs = elapsedMs,
            )
        }
    }

    private suspend fun submitLibsuCommand(command: String): ShellResult =
        suspendCancellableCoroutine { continuation ->
            val stdoutLines = BoundedOutputList(MAX_OUTPUT_LINES)
            val stderrLines = BoundedOutputList(MAX_OUTPUT_LINES)
            Shell.cmd(command)
                .to(stdoutLines, stderrLines)
                .submit { res ->
                    if (continuation.isActive) {
                        continuation.resume(
                            ShellResult(
                                exitCode = res.code,
                                stdout = stdoutLines.joinToString("\n"),
                                stderr = stderrLines.joinToString("\n"),
                                timedOut = false,
                            ),
                        )
                    }
                }
        }

    private class BoundedOutputList(private val maxLines: Int) : ArrayList<String>() {
        override fun add(element: String): Boolean {
            if (size >= maxLines) return false
            return super.add(element)
        }
    }

    companion object {
        private const val MAX_OUTPUT_LINES = 10_000
        private val shellInitialized = AtomicBoolean(false)
    }
}
