package com.antigravity.mobile.core.acp

import com.antigravity.mobile.contracts.AcpClient
import com.antigravity.mobile.contracts.AcpEvent
import com.antigravity.mobile.contracts.AcpSessionConfig
import com.antigravity.mobile.contracts.AcpSessionInfo
import com.antigravity.mobile.contracts.AcpSessionState
import com.antigravity.mobile.contracts.ContractsJson
import com.antigravity.mobile.contracts.PermissionResponse
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Default [AcpClient] implementation communicating with `agy acp --stdio` over bidirectional
 * line-delimited JSON-RPC 2.0 on [ioDispatcher] (`Dispatchers.IO`).
 */
class DefaultAcpClient(
    private val commandBuilder: AcpLaunchCommandBuilder,
    private val processLauncher: AcpProcessLauncher,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val json: Json = ContractsJson,
    private val requestTimeoutMs: Long = DEFAULT_REQUEST_TIMEOUT_MS,
    private val promptTimeoutMs: Long = DEFAULT_PROMPT_TIMEOUT_MS,
    private val clock: () -> Long = System::currentTimeMillis,
) : AcpClient {
    private val eventMapper = AcpEventMapper(json = json, clock = clock)
    private val lifecycleMutex = Mutex()

    private val _events = MutableSharedFlow<AcpEvent>(
        replay = 0,
        extraBufferCapacity = EVENT_BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.SUSPEND,
    )
    override val events: SharedFlow<AcpEvent> = _events.asSharedFlow()

    private val _sessionState = MutableStateFlow(AcpSessionState.DISCONNECTED)
    override val sessionState: StateFlow<AcpSessionState> = _sessionState.asStateFlow()

    private val _activeSession = MutableStateFlow<AcpSessionInfo?>(null)
    override val activeSession: StateFlow<AcpSessionInfo?> = _activeSession.asStateFlow()

    @Volatile
    private var activeTransport: JsonRpcTransport? = null

    @Volatile
    private var activeProfileId: String? = null

    private val activeTurnInFlight = AtomicBoolean(false)
    private val pendingPermissions = ConcurrentHashMap<String, ParsedPermissionRequest>()
    private val lastEmittedAuthUrl = AtomicReference<String?>(null)

    override suspend fun startSession(config: AcpSessionConfig): Result<AcpSessionInfo> =
        lifecycleMutex.withLock {
            try {
                teardownCurrentTransportLocked()
                updateState(AcpSessionState.CONNECTING, sessionId = null, emitEvent = false)

                val transport = launchAndInitializeLocked(config)
                val response = if (!config.resumeConversationId.isNullOrBlank()) {
                    transport.sendRequest(
                        method = METHOD_SESSION_LOAD,
                        params = buildSessionParams(config, sessionId = config.resumeConversationId),
                        timeoutMs = requestTimeoutMs,
                    )
                } else {
                    transport.sendRequest(
                        method = METHOD_SESSION_NEW,
                        params = buildSessionParams(config, sessionId = null),
                        timeoutMs = requestTimeoutMs,
                    )
                }

                handleRpcErrorIfPresent(response.error, sessionId = config.resumeConversationId)?.let { ex ->
                    return@withLock Result.failure(ex)
                }

                val sessionInfo = eventMapper.parseSessionInfo(
                    result = response.result,
                    config = config,
                    fallbackSessionId = config.resumeConversationId,
                ).copy(state = AcpSessionState.READY)

                _activeSession.value = sessionInfo
                updateState(AcpSessionState.READY, sessionId = sessionInfo.sessionId)
                Result.success(sessionInfo)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                handleLifecycleFailure(e, sessionId = _activeSession.value?.sessionId)
                Result.failure(e)
            }
        }

    override suspend fun listSessions(profileId: String): Result<List<AcpSessionInfo>> {
        val reusableTransport = activeTransport?.takeIf { it.isAlive && activeProfileId == profileId }
        if (reusableTransport != null) {
            return executeSessionList(reusableTransport, profileId)
        }

        return lifecycleMutex.withLock {
            val lockedReusable = activeTransport?.takeIf { it.isAlive && activeProfileId == profileId }
            if (lockedReusable != null) {
                return@withLock executeSessionList(lockedReusable, profileId)
            }

            var ephemeralTransport: JsonRpcTransport? = null
            try {
                val config = AcpSessionConfig(profileId = profileId)
                val launchRequest = commandBuilder.buildLaunchRequest(config)
                val process = processLauncher.launch(launchRequest)
                ephemeralTransport = JsonRpcTransport(
                    process = process,
                    callbacks = NoOpTransportCallbacks(
                        onStderr = { line -> inspectDiagnosticLineForAuth(line, sessionId = null) },
                    ),
                    json = json,
                    ioDispatcher = ioDispatcher,
                ).also { it.start() }

                val initResp = ephemeralTransport.sendRequest(
                    method = METHOD_INITIALIZE,
                    params = buildInitializeParams(),
                    timeoutMs = requestTimeoutMs,
                )
                handleRpcErrorIfPresent(initResp.error, sessionId = null)?.let { ex ->
                    return@withLock Result.failure(ex)
                }

                executeSessionList(ephemeralTransport, profileId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Result.failure(e)
            } finally {
                ephemeralTransport?.close()
            }
        }
    }

    override suspend fun loadSession(
        sessionId: String,
        config: AcpSessionConfig,
    ): Result<AcpSessionInfo> = lifecycleMutex.withLock {
        try {
            val existingTransport = activeTransport?.takeIf {
                it.isAlive && activeProfileId == config.profileId
            }
            val transport = existingTransport ?: run {
                teardownCurrentTransportLocked()
                updateState(AcpSessionState.CONNECTING, sessionId = sessionId)
                launchAndInitializeLocked(config)
            }

            val response = transport.sendRequest(
                method = METHOD_SESSION_LOAD,
                params = buildSessionParams(config, sessionId = sessionId),
                timeoutMs = requestTimeoutMs,
            )

            handleRpcErrorIfPresent(response.error, sessionId = sessionId)?.let { ex ->
                return@withLock Result.failure(ex)
            }

            val sessionInfo = eventMapper.parseSessionInfo(
                result = response.result,
                config = config,
                fallbackSessionId = sessionId,
            ).copy(state = AcpSessionState.READY)

            _activeSession.value = sessionInfo
            updateState(AcpSessionState.READY, sessionId = sessionInfo.sessionId)
            Result.success(sessionInfo)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            handleLifecycleFailure(e, sessionId = sessionId)
            Result.failure(e)
        }
    }

    override suspend fun sendMessage(
        sessionId: String,
        prompt: String,
        attachments: List<String>,
    ): Result<Unit> {
        val transport = activeTransport?.takeIf { it.isAlive }
            ?: return Result.failure(IllegalStateException("ACP client has no active running session"))

        activeTurnInFlight.set(true)
        updateState(AcpSessionState.STREAMING, sessionId = sessionId)

        return try {
            val response = transport.sendRequest(
                method = METHOD_SESSION_PROMPT,
                params = buildPromptParams(sessionId, prompt, attachments),
                timeoutMs = promptTimeoutMs,
            )

            val rpcError = response.error
            if (rpcError != null) {
                val exception = handleRpcErrorIfPresent(rpcError, sessionId = sessionId)
                    ?: JsonRpcException(rpcError.code, rpcError.message, rpcError.data)
                if (rpcError.code == JsonRpcErrorCodes.REQUEST_CANCELLED) {
                    updateState(AcpSessionState.READY, sessionId = sessionId, detail = "Cancelled")
                } else if (rpcError.code != JsonRpcErrorCodes.AUTH_REQUIRED) {
                    updateState(AcpSessionState.READY, sessionId = sessionId, detail = rpcError.message)
                }
                Result.failure(exception)
            } else {
                val current = _activeSession.value
                if (current != null && current.sessionId == sessionId) {
                    _activeSession.value = current.copy(
                        state = AcpSessionState.READY,
                        updatedAtEpochMs = clock(),
                    )
                }
                if (_sessionState.value !in TERMINAL_OR_AUTH_STATES) {
                    updateState(AcpSessionState.READY, sessionId = sessionId)
                }
                Result.success(Unit)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (_sessionState.value !in TERMINAL_OR_AUTH_STATES) {
                _events.emit(
                    AcpEvent.Error(
                        sessionId = sessionId,
                        message = e.message ?: "Failed to send prompt",
                        recoverable = e !is AcpProcessTerminatedException,
                    ),
                )
                val nextState = if (e is AcpProcessTerminatedException) {
                    AcpSessionState.ERROR
                } else {
                    AcpSessionState.READY
                }
                updateState(nextState, sessionId = sessionId, detail = e.message)
            }
            Result.failure(e)
        } finally {
            activeTurnInFlight.set(false)
        }
    }

    override suspend fun cancelSession(sessionId: String): Result<Unit> {
        val transport = activeTransport?.takeIf { !it.isClosed }
        if (transport == null) {
            updateState(AcpSessionState.DISCONNECTED, sessionId = sessionId, detail = "No active process")
            return Result.success(Unit)
        }

        return try {
            updateState(AcpSessionState.CANCELLING, sessionId = sessionId, detail = "Cancellation requested")

            // Clear pending permissions for this session so UI/caller does not hang during kill-switch.
            pendingPermissions.entries.removeIf { it.value.event.sessionId == sessionId }

            transport.sendNotification(
                method = METHOD_SESSION_CANCEL,
                params = buildJsonObject {
                    put("sessionId", sessionId)
                },
            )

            if (!activeTurnInFlight.get()) {
                updateState(AcpSessionState.READY, sessionId = sessionId, detail = "Session cancelled")
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            _events.emit(
                AcpEvent.Error(
                    sessionId = sessionId,
                    message = "Failed to send session/cancel: ${e.message}",
                    recoverable = true,
                ),
            )
            Result.failure(e)
        }
    }

    override suspend fun respondToPermission(response: PermissionResponse): Result<Unit> {
        val pending = pendingPermissions.remove(response.requestId)
            ?: return Result.failure(
                IllegalArgumentException("No pending permission request found for id '${response.requestId}'"),
            )

        val transport = activeTransport?.takeIf { !it.isClosed }
            ?: return Result.failure(IllegalStateException("ACP transport is closed"))

        return try {
            val selectedOptionId = when {
                response.approved && response.rememberForSession -> pending.allowAlwaysOptionId
                response.approved -> pending.allowOnceOptionId
                else -> pending.rejectOptionId
            }

            val resultPayload = buildJsonObject {
                put(
                    "outcome",
                    buildJsonObject {
                        put("outcome", "selected")
                        put("optionId", selectedOptionId)
                    },
                )
                put("requestId", response.requestId)
                put("approved", response.approved)
                put("rememberForSession", response.rememberForSession)
                response.reason?.let { put("reason", it) }
            }

            transport.sendResponse(
                id = pending.rpcId,
                result = resultPayload,
            )

            if (pendingPermissions.isEmpty() && _sessionState.value == AcpSessionState.AWAITING_PERMISSION) {
                val restoredState = if (activeTurnInFlight.get()) {
                    AcpSessionState.STREAMING
                } else {
                    AcpSessionState.READY
                }
                updateState(restoredState, sessionId = pending.event.sessionId)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    override suspend fun close() {
        lifecycleMutex.withLock {
            val previousSessionId = _activeSession.value?.sessionId
            teardownCurrentTransportLocked()
            _activeSession.value = null
            updateState(
                state = AcpSessionState.CLOSED,
                sessionId = previousSessionId,
                emitEvent = previousSessionId != null,
            )
        }
    }

    private suspend fun launchAndInitializeLocked(config: AcpSessionConfig): JsonRpcTransport {
        val launchRequest = commandBuilder.buildLaunchRequest(config)
        val process = processLauncher.launch(launchRequest)
        val transport = JsonRpcTransport(
            process = process,
            callbacks = ActiveTransportCallbacks(),
            json = json,
            ioDispatcher = ioDispatcher,
        )
        activeTransport = transport
        activeProfileId = config.profileId
        lastEmittedAuthUrl.set(null)
        transport.start()

        updateState(AcpSessionState.INITIALIZING, sessionId = null, emitEvent = false)
        val initResponse = transport.sendRequest(
            method = METHOD_INITIALIZE,
            params = buildInitializeParams(),
            timeoutMs = requestTimeoutMs,
        )

        handleRpcErrorIfPresent(initResponse.error, sessionId = null)?.let { ex ->
            throw ex
        }
        return transport
    }

    private suspend fun executeSessionList(
        transport: JsonRpcTransport,
        profileId: String,
    ): Result<List<AcpSessionInfo>> = try {
        val response = transport.sendRequest(
            method = METHOD_SESSION_LIST,
            params = buildJsonObject {
                put("profileId", profileId)
            },
            timeoutMs = requestTimeoutMs,
        )
        handleRpcErrorIfPresent(response.error, sessionId = null)?.let { ex ->
            return Result.failure(ex)
        }
        Result.success(eventMapper.parseSessionList(response.result, profileId))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }

    private suspend fun teardownCurrentTransportLocked() {
        pendingPermissions.clear()
        activeTurnInFlight.set(false)
        lastEmittedAuthUrl.set(null)
        val transport = activeTransport
        activeTransport = null
        activeProfileId = null
        transport?.close()
    }

    private suspend fun handleRpcErrorIfPresent(
        error: JsonRpcError?,
        sessionId: String?,
    ): JsonRpcException? {
        if (error == null) return null
        val exception = JsonRpcException(error.code, error.message, error.data)
        if (error.code == JsonRpcErrorCodes.AUTH_REQUIRED) {
            val authUrl = OAuthUrlExtractor.extractFromRpcError(error)
            emitAuthRequired(
                sessionId = sessionId ?: _activeSession.value?.sessionId,
                errorCode = error.code,
                message = error.message,
                authUrl = authUrl,
            )
        } else {
            _events.emit(
                AcpEvent.Error(
                    sessionId = sessionId ?: _activeSession.value?.sessionId,
                    code = error.code,
                    message = error.message,
                    recoverable = true,
                ),
            )
        }
        return exception
    }

    private suspend fun handleLifecycleFailure(
        cause: Throwable,
        sessionId: String?,
    ) {
        if (cause is JsonRpcException && cause.code == JsonRpcErrorCodes.AUTH_REQUIRED) {
            return
        }
        _events.emit(
            AcpEvent.Error(
                sessionId = sessionId,
                code = (cause as? JsonRpcException)?.code,
                message = cause.message ?: cause::class.simpleName.orEmpty(),
                recoverable = true,
            ),
        )
        updateState(AcpSessionState.ERROR, sessionId = sessionId, detail = cause.message)
    }

    private suspend fun emitAuthRequired(
        sessionId: String?,
        errorCode: Int,
        message: String,
        authUrl: String?,
    ) {
        if (authUrl != null) {
            lastEmittedAuthUrl.set(authUrl)
        }
        updateState(AcpSessionState.AWAITING_AUTH, sessionId = sessionId, detail = message)
        _events.emit(
            AcpEvent.AuthRequired(
                sessionId = sessionId,
                errorCode = errorCode,
                message = message,
                authUrl = authUrl,
            ),
        )
    }

    private suspend fun inspectDiagnosticLineForAuth(line: String, sessionId: String?) {
        val url = OAuthUrlExtractor.extractFromDiagnosticLine(line)
        if (url != null) {
            if (lastEmittedAuthUrl.getAndSet(url) != url) {
                emitAuthRequired(
                    sessionId = sessionId,
                    errorCode = JsonRpcErrorCodes.AUTH_REQUIRED,
                    message = line,
                    authUrl = url,
                )
            }
        } else if (OAuthUrlExtractor.looksLikeAuthRequiredMessage(line) &&
            _sessionState.value != AcpSessionState.AWAITING_AUTH
        ) {
            emitAuthRequired(
                sessionId = sessionId,
                errorCode = JsonRpcErrorCodes.AUTH_REQUIRED,
                message = line,
                authUrl = null,
            )
        }
    }

    private suspend fun updateState(
        state: AcpSessionState,
        sessionId: String?,
        detail: String? = null,
        emitEvent: Boolean = true,
    ) {
        _sessionState.value = state
        _activeSession.value?.let { current ->
            if (sessionId == null || current.sessionId == sessionId) {
                _activeSession.value = current.copy(state = state)
            }
        }
        if (emitEvent && !sessionId.isNullOrBlank()) {
            _events.emit(
                AcpEvent.SessionStateChanged(
                    sessionId = sessionId,
                    state = state,
                    detail = detail,
                ),
            )
        }
    }

    private fun buildInitializeParams(): JsonObject = buildJsonObject {
        put("protocolVersion", ACP_PROTOCOL_VERSION)
        put(
            "clientInfo",
            buildJsonObject {
                put("name", CLIENT_NAME)
                put("version", CLIENT_VERSION)
            },
        )
        put(
            "clientCapabilities",
            buildJsonObject {
                put(
                    "fs",
                    buildJsonObject {
                        put("readTextFile", false)
                        put("writeTextFile", false)
                    },
                )
                put("terminal", false)
                put("permissions", true)
            },
        )
    }

    private fun buildSessionParams(
        config: AcpSessionConfig,
        sessionId: String?,
    ): JsonObject = buildJsonObject {
        if (!sessionId.isNullOrBlank()) {
            put("sessionId", sessionId)
        }
        put("profileId", config.profileId)
        put("cwd", config.workspaceDir)
        put("workspaceDir", config.workspaceDir)
        put(
            "additionalDirs",
            buildJsonArray {
                config.additionalDirs.forEach { add(JsonPrimitive(it)) }
            },
        )
        put("model", config.model.cliId)
        put("thinkingEffort", config.thinkingEffort.cliFlagValue)
        put("effort", config.thinkingEffort.cliFlagValue)
        put("permissionMode", config.permissionMode.cliFlagValue)
        put("mode", config.permissionMode.cliFlagValue)
        put("sandboxed", config.sandboxed)
        config.agentName?.takeIf { it.isNotBlank() }?.let { put("agent", it) }
        put("mcpServers", JsonArray(emptyList()))
    }

    private fun buildPromptParams(
        sessionId: String,
        prompt: String,
        attachments: List<String>,
    ): JsonObject = buildJsonObject {
        put("sessionId", sessionId)
        put("text", prompt)
        put(
            "prompt",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("type", "text")
                        put("text", prompt)
                    },
                )
                attachments.filter { it.isNotBlank() }.forEach { path ->
                    add(
                        buildJsonObject {
                            put("type", "resource_link")
                            put("uri", path)
                        },
                    )
                }
            },
        )
        if (attachments.isNotEmpty()) {
            put(
                "attachments",
                buildJsonArray {
                    attachments.forEach { add(JsonPrimitive(it)) }
                },
            )
        }
    }

    private inner class ActiveTransportCallbacks : JsonRpcTransportCallbacks {
        override suspend fun onNotification(notification: IncomingJsonRpcMessage.Notification) {
            val currentSessionId = _activeSession.value?.sessionId
            val mapped = eventMapper.mapNotification(
                method = notification.method,
                params = notification.params,
                fallbackSessionId = currentSessionId,
            ) ?: return

            when (mapped) {
                is AcpEvent.AuthRequired -> {
                    if (mapped.authUrl != null) {
                        lastEmittedAuthUrl.set(mapped.authUrl)
                    }
                    _sessionState.value = AcpSessionState.AWAITING_AUTH
                }
                is AcpEvent.SessionStateChanged -> {
                    _sessionState.value = mapped.state
                }
                else -> Unit
            }
            _events.emit(mapped)
        }

        override suspend fun onServerRequest(request: IncomingJsonRpcMessage.ServerRequest) {
            val currentSessionId = _activeSession.value?.sessionId
            when (request.method) {
                METHOD_REQUEST_PERMISSION -> {
                    val parsed = eventMapper.parsePermissionRequest(
                        rpcId = request.id,
                        params = request.params,
                        fallbackSessionId = currentSessionId,
                    )
                    pendingPermissions[parsed.event.requestId] = parsed
                    updateState(
                        state = AcpSessionState.AWAITING_PERMISSION,
                        sessionId = parsed.event.sessionId,
                        detail = "Awaiting approval for ${parsed.event.toolName}",
                    )
                    _events.emit(parsed.event)
                }
                else -> {
                    // Reply with Method Not Found so the server never hangs awaiting an unsupported request.
                    activeTransport?.takeIf { !it.isClosed }?.sendResponse(
                        id = request.id,
                        error = JsonRpcError(
                            code = JsonRpcErrorCodes.METHOD_NOT_FOUND,
                            message = "Unsupported client method: ${request.method}",
                        ),
                    )
                }
            }
        }

        override suspend fun onMalformedLine(rawLine: String, cause: Throwable) {
            val currentSessionId = _activeSession.value?.sessionId
            inspectDiagnosticLineForAuth(rawLine, sessionId = currentSessionId)
            _events.emit(
                AcpEvent.Error(
                    sessionId = currentSessionId,
                    code = JsonRpcErrorCodes.PARSE_ERROR,
                    message = "Malformed ACP stdout frame: ${cause.message ?: rawLine.take(120)}",
                    recoverable = true,
                ),
            )
        }

        override suspend fun onStderrLine(line: String) {
            inspectDiagnosticLineForAuth(line, sessionId = _activeSession.value?.sessionId)
        }

        override suspend fun onUnexpectedExit(exitCode: Int?, stderrTail: List<String>) {
            val currentSessionId = _activeSession.value?.sessionId
            pendingPermissions.clear()
            activeTurnInFlight.set(false)

            val tailSummary = stderrTail.takeLast(5).joinToString(" | ")
            val detail = buildString {
                append("ACP process exited unexpectedly")
                if (exitCode != null) append(" (code=$exitCode)")
                if (tailSummary.isNotBlank()) append(": $tailSummary")
            }

            _events.emit(
                AcpEvent.Error(
                    sessionId = currentSessionId,
                    code = exitCode,
                    message = detail,
                    recoverable = true,
                ),
            )
            updateState(
                state = AcpSessionState.ERROR,
                sessionId = currentSessionId,
                detail = detail,
                emitEvent = currentSessionId != null,
            )
        }
    }

    private class NoOpTransportCallbacks(
        private val onStderr: suspend (String) -> Unit,
    ) : JsonRpcTransportCallbacks {
        override suspend fun onNotification(notification: IncomingJsonRpcMessage.Notification) = Unit
        override suspend fun onServerRequest(request: IncomingJsonRpcMessage.ServerRequest) = Unit
        override suspend fun onMalformedLine(rawLine: String, cause: Throwable) = Unit
        override suspend fun onStderrLine(line: String) = onStderr(line)
        override suspend fun onUnexpectedExit(exitCode: Int?, stderrTail: List<String>) = Unit
    }

    companion object {
        const val ACP_PROTOCOL_VERSION = 1
        const val CLIENT_NAME = "antigravity-mobile"
        const val CLIENT_VERSION = "1.0.0"

        const val METHOD_INITIALIZE = "initialize"
        const val METHOD_SESSION_NEW = "session/new"
        const val METHOD_SESSION_LIST = "session/list"
        const val METHOD_SESSION_LOAD = "session/load"
        const val METHOD_SESSION_PROMPT = "session/prompt"
        const val METHOD_SESSION_CANCEL = "session/cancel"
        const val METHOD_REQUEST_PERMISSION = "session/request_permission"

        const val DEFAULT_REQUEST_TIMEOUT_MS = 30_000L
        const val DEFAULT_PROMPT_TIMEOUT_MS = 600_000L
        private const val EVENT_BUFFER_CAPACITY = 256

        private val TERMINAL_OR_AUTH_STATES = setOf(
            AcpSessionState.CLOSED,
            AcpSessionState.ERROR,
            AcpSessionState.AWAITING_AUTH,
        )
    }
}
