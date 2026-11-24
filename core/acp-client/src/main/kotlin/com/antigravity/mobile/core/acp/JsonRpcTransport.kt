package com.antigravity.mobile.core.acp

import com.antigravity.mobile.contracts.ContractsJson
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Callbacks invoked by [JsonRpcTransport] for asynchronous server-initiated frames, malformed
 * stdout lines, stderr diagnostics, and unexpected process termination.
 */
interface JsonRpcTransportCallbacks {
    suspend fun onNotification(notification: IncomingJsonRpcMessage.Notification)
    suspend fun onServerRequest(request: IncomingJsonRpcMessage.ServerRequest)
    suspend fun onMalformedLine(rawLine: String, cause: Throwable)
    suspend fun onStderrLine(line: String)
    suspend fun onUnexpectedExit(exitCode: Int?, stderrTail: List<String>)
}

/**
 * Bidirectional line-delimited JSON-RPC 2.0 transport over an [AcpProcess]'s stdin/stdout/stderr
 * streams. All blocking stream I/O executes strictly on [ioDispatcher] (`Dispatchers.IO`).
 */
class JsonRpcTransport(
    private val process: AcpProcess,
    private val callbacks: JsonRpcTransportCallbacks,
    private val json: Json = ContractsJson,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val gracefulShutdownTimeoutMs: Long = DEFAULT_GRACEFUL_SHUTDOWN_MS,
    private val maxStderrHistoryLines: Int = DEFAULT_MAX_STDERR_LINES,
) {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val writeMutex = Mutex()
    private val nextRequestId = AtomicLong(1L)
    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<JsonRpcResponse>>()
    private val closed = AtomicBoolean(false)
    private val exitNotified = AtomicBoolean(false)

    private val stderrLock = Any()
    private val stderrHistory = ArrayDeque<String>(maxStderrHistoryLines)

    private val writer = BufferedWriter(
        OutputStreamWriter(process.outputStream, StandardCharsets.UTF_8),
    )

    private var stdoutJob: Job? = null
    private var stderrJob: Job? = null
    private var monitorJob: Job? = null

    val isClosed: Boolean
        get() = closed.get()

    val isAlive: Boolean
        get() = !closed.get() && process.isAlive

    fun start() {
        stderrJob = scope.launch { readStderrLoop() }
        stdoutJob = scope.launch { readStdoutLoop() }
        monitorJob = scope.launch { monitorProcessExit() }
    }

    /**
     * Sends a JSON-RPC 2.0 request and awaits the correlated response up to [timeoutMs].
     * External coroutine cancellation is always preserved (never swallows [CancellationException]).
     */
    suspend fun sendRequest(
        method: String,
        params: JsonElement? = null,
        timeoutMs: Long = DEFAULT_REQUEST_TIMEOUT_MS,
    ): JsonRpcResponse {
        check(!closed.get()) { "ACP JSON-RPC transport is closed" }

        val idNum = nextRequestId.getAndIncrement()
        val idKey = idNum.toString()
        val deferred = CompletableDeferred<JsonRpcResponse>()
        pendingRequests[idKey] = deferred

        val request = JsonRpcRequest(
            id = JsonPrimitive(idNum),
            method = method,
            params = params,
        )

        try {
            writeLine(json.encodeToString(JsonRpcRequest.serializer(), request))
            val response = if (timeoutMs > 0L) {
                withTimeoutOrNull(timeoutMs) { deferred.await() }
                    ?: throw AcpRequestTimeoutException(method, timeoutMs)
            } else {
                deferred.await()
            }
            return response
        } finally {
            pendingRequests.remove(idKey)
        }
    }

    /**
     * Sends a JSON-RPC 2.0 notification (no `id`, no response awaited).
     */
    suspend fun sendNotification(
        method: String,
        params: JsonElement? = null,
    ) {
        check(!closed.get()) { "ACP JSON-RPC transport is closed" }
        val notification = JsonRpcNotification(
            method = method,
            params = params,
        )
        writeLine(json.encodeToString(JsonRpcNotification.serializer(), notification))
    }

    /**
     * Sends a JSON-RPC 2.0 response to a server-initiated request (e.g. `session/request_permission`).
     */
    suspend fun sendResponse(
        id: JsonElement,
        result: JsonElement? = null,
        error: JsonRpcError? = null,
    ) {
        check(!closed.get()) { "ACP JSON-RPC transport is closed" }
        val response = JsonRpcResponse(
            id = id,
            result = result,
            error = error,
        )
        writeLine(json.encodeToString(JsonRpcResponse.serializer(), response))
    }

    fun recentStderrLines(): List<String> = synchronized(stderrLock) {
        stderrHistory.toList()
    }

    /**
     * Gracefully closes the transport and terminates the child process on [ioDispatcher].
     */
    suspend fun close() {
        if (!closed.compareAndSet(false, true)) return

        val closeError = CancellationException("ACP JSON-RPC transport closed")
        failAllPending(closeError)

        withContext(ioDispatcher) {
            runCatching { writer.close() }
            runCatching { process.destroy() }
            val exitedCleanly = runCatching {
                process.waitForExit(gracefulShutdownTimeoutMs)
            }.getOrDefault(false)
            if (!exitedCleanly && process.isAlive) {
                runCatching { process.destroyForcibly() }
            }
        }

        stdoutJob?.cancel()
        stderrJob?.cancel()
        monitorJob?.cancel()
        scope.cancel()
    }

    private suspend fun writeLine(payload: String) {
        withContext(ioDispatcher) {
            writeMutex.withLock {
                if (closed.get()) {
                    throw IllegalStateException("ACP JSON-RPC transport is closed")
                }
                writer.write(payload)
                writer.newLine()
                writer.flush()
            }
        }
    }

    private suspend fun readStdoutLoop() {
        try {
            BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8)).use { reader ->
                while (scope.isActive && !closed.get()) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) continue
                    handleStdoutLine(line)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (!closed.get()) {
                callbacks.onMalformedLine("<stdout-io-error>", e)
            }
        } finally {
            if (!closed.get()) {
                stderrJob?.let { job ->
                    withTimeoutOrNull(STDERR_DRAIN_WAIT_MS) { job.join() }
                }
                runCatching { process.waitForExit(STDERR_DRAIN_WAIT_MS) }
                notifyUnexpectedTermination(process.exitCodeOrNull())
            }
        }
    }

    private suspend fun readStderrLoop() {
        try {
            BufferedReader(InputStreamReader(process.errorStream, StandardCharsets.UTF_8)).use { reader ->
                while (scope.isActive && !closed.get()) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) continue
                    appendStderr(line)
                    callbacks.onStderrLine(line)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // Stream closed during process shutdown; ignore if closing.
        }
    }

    private suspend fun monitorProcessExit() {
        try {
            val exitCode = process.waitForExitCode()
            if (!closed.get()) {
                // Give stderr reader a brief chance to drain final diagnostic lines.
                stderrJob?.let { job ->
                    withTimeoutOrNull(STDERR_DRAIN_WAIT_MS) { job.join() }
                }
                notifyUnexpectedTermination(exitCode)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            if (!closed.get()) {
                notifyUnexpectedTermination(process.exitCodeOrNull())
            }
        }
    }

    private suspend fun handleStdoutLine(line: String) {
        val parsed = try {
            parseIncomingMessage(line)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            callbacks.onMalformedLine(line, e)
            return
        }

        when (parsed) {
            is IncomingJsonRpcMessage.Response -> {
                pendingRequests.remove(parsed.idKey)?.complete(parsed.response)
            }
            is IncomingJsonRpcMessage.ServerRequest -> {
                callbacks.onServerRequest(parsed)
            }
            is IncomingJsonRpcMessage.Notification -> {
                callbacks.onNotification(parsed)
            }
        }
    }

    internal fun parseIncomingMessage(line: String): IncomingJsonRpcMessage {
        val element = json.parseToJsonElement(line)
        val obj = element as? JsonObject
            ?: throw IllegalArgumentException("Expected JSON object frame")

        val method = (obj["method"] as? JsonPrimitive)?.contentOrNull
        val id = obj["id"]
        val paramsObj = obj["params"] as? JsonObject

        return when {
            method != null && id != null -> {
                IncomingJsonRpcMessage.ServerRequest(
                    id = id,
                    method = method,
                    params = paramsObj,
                )
            }
            method != null -> {
                IncomingJsonRpcMessage.Notification(
                    method = method,
                    params = paramsObj,
                )
            }
            id != null -> {
                val idKey = id.rpcIdKey()
                    ?: throw IllegalArgumentException("Unsupported JSON-RPC response id: $id")
                val response = JsonRpcResponse(
                    jsonrpc = (obj["jsonrpc"] as? JsonPrimitive)?.contentOrNull ?: JSON_RPC_VERSION,
                    id = id,
                    result = obj["result"],
                    error = parseJsonRpcError(obj["error"]),
                )
                IncomingJsonRpcMessage.Response(
                    idKey = idKey,
                    response = response,
                )
            }
            else -> throw IllegalArgumentException("JSON object is neither a JSON-RPC request, notification, nor response")
        }
    }

    private suspend fun notifyUnexpectedTermination(exitCode: Int?) {
        if (!exitNotified.compareAndSet(false, true)) return
        val stderrTail = recentStderrLines()
        val exception = AcpProcessTerminatedException(
            exitCode = exitCode,
            stderrSummary = stderrTail.takeLast(5).joinToString(" | "),
        )
        failAllPending(exception)
        callbacks.onUnexpectedExit(exitCode, stderrTail)
    }

    private fun failAllPending(cause: Throwable) {
        val keys = pendingRequests.keys.toList()
        for (key in keys) {
            pendingRequests.remove(key)?.completeExceptionally(cause)
        }
    }

    private fun appendStderr(line: String) {
        synchronized(stderrLock) {
            if (stderrHistory.size >= maxStderrHistoryLines) {
                stderrHistory.removeFirst()
            }
            stderrHistory.addLast(line)
        }
    }

    companion object {
        const val DEFAULT_REQUEST_TIMEOUT_MS = 30_000L
        const val DEFAULT_GRACEFUL_SHUTDOWN_MS = 500L
        const val DEFAULT_MAX_STDERR_LINES = 100
        private const val STDERR_DRAIN_WAIT_MS = 200L
    }
}
