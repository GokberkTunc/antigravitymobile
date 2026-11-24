package com.antigravity.mobile.core.acp

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Deterministic piped stdio [AcpProcess] for integration tests, exercising real UTF-8
 * line-delimited framing over [PipedInputStream]/[PipedOutputStream].
 */
class FakeAcpProcess : AcpProcess {
    private val clientToServerIn = PipedInputStream(PIPE_BUFFER_SIZE)
    private val clientToServerOut = PipedOutputStream(clientToServerIn)

    private val serverToClientIn = PipedInputStream(PIPE_BUFFER_SIZE)
    private val serverToClientOut = PipedOutputStream(serverToClientIn)

    private val stderrIn = PipedInputStream(PIPE_BUFFER_SIZE)
    private val stderrOut = PipedOutputStream(stderrIn)

    private val serverReader = BufferedReader(InputStreamReader(clientToServerIn, StandardCharsets.UTF_8))
    private val serverStdoutWriter = BufferedWriter(OutputStreamWriter(serverToClientOut, StandardCharsets.UTF_8))
    private val serverStderrWriter = BufferedWriter(OutputStreamWriter(stderrOut, StandardCharsets.UTF_8))

    private val alive = AtomicBoolean(true)
    private val exitLatch = CountDownLatch(1)
    private val exitCodeRef = AtomicReference<Int?>(null)
    val destroyCount = AtomicInteger(0)
    val forceDestroyCount = AtomicInteger(0)

    override val outputStream: OutputStream
        get() = clientToServerOut

    override val inputStream: InputStream
        get() = serverToClientIn

    override val errorStream: InputStream
        get() = stderrIn

    override val isAlive: Boolean
        get() = alive.get()

    /** Reads the next line written by [JsonRpcTransport] to the process stdin. */
    fun readClientLine(): String? = serverReader.readLine()

    /** Reads and parses the next JSON object sent by the client. */
    fun readClientJson(json: Json = Json): JsonObject? {
        val line = readClientLine() ?: return null
        return json.parseToJsonElement(line).jsonObject
    }

    /** Sends a raw line to the client's stdout reader. */
    fun writeServerStdoutLine(line: String) {
        synchronized(serverStdoutWriter) {
            serverStdoutWriter.write(line)
            serverStdoutWriter.newLine()
            serverStdoutWriter.flush()
        }
    }

    /** Sends a raw line to the client's stderr reader. */
    fun writeServerStderrLine(line: String) {
        synchronized(serverStderrWriter) {
            serverStderrWriter.write(line)
            serverStderrWriter.newLine()
            serverStderrWriter.flush()
        }
    }

    /** Simulates the process terminating with [code] and closing stdout/stderr pipes. */
    fun terminateWithExitCode(code: Int) {
        if (alive.compareAndSet(true, false)) {
            exitCodeRef.set(code)
            runCatching { serverStdoutWriter.close() }
            runCatching { serverStderrWriter.close() }
            runCatching { clientToServerIn.close() }
            exitLatch.countDown()
        }
    }

    override fun destroy() {
        destroyCount.incrementAndGet()
        terminateWithExitCode(0)
    }

    override fun destroyForcibly() {
        forceDestroyCount.incrementAndGet()
        terminateWithExitCode(137)
    }

    override fun waitForExitCode(): Int {
        exitLatch.await()
        return exitCodeRef.get() ?: -1
    }

    override fun waitForExit(timeoutMs: Long): Boolean =
        exitLatch.await(timeoutMs, TimeUnit.MILLISECONDS)

    override fun exitCodeOrNull(): Int? = exitCodeRef.get()

    companion object {
        private const val PIPE_BUFFER_SIZE = 65_536
    }
}

/**
 * [AcpProcessLauncher] returning scripted [FakeAcpProcess] instances and recording launch requests.
 */
class FakeAcpProcessLauncher(
    private val processFactory: (AcpProcessLaunchRequest) -> FakeAcpProcess,
) : AcpProcessLauncher {
    val recordedRequests = mutableListOf<AcpProcessLaunchRequest>()
    val launchedProcesses = mutableListOf<FakeAcpProcess>()

    override suspend fun launch(request: AcpProcessLaunchRequest): AcpProcess {
        recordedRequests.add(request)
        val process = processFactory(request)
        launchedProcesses.add(process)
        return process
    }
}
