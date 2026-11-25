package com.antigravity.mobile.core.acp

import com.antigravity.mobile.contracts.AcpEvent
import com.antigravity.mobile.contracts.AcpModel
import com.antigravity.mobile.contracts.AcpSessionConfig
import com.antigravity.mobile.contracts.AcpSessionInfo
import com.antigravity.mobile.contracts.AcpSessionState
import com.antigravity.mobile.contracts.ContractsJson
import com.antigravity.mobile.contracts.McpToolNames
import com.antigravity.mobile.contracts.McpToolRisk
import com.antigravity.mobile.contracts.SwarmBoardState
import com.antigravity.mobile.contracts.ToolCallStatus
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Metadata update parsed from `session/update` (`sessionUpdate = "session_info_update"`).
 */
data class SessionInfoUpdate(
    val sessionId: String,
    val title: String,
)

/**
 * Parsed server-to-client `session/request_permission` call preserving the JSON-RPC [rpcId]
 * and server-advertised permission option IDs for accurate bidirectional replies.
 */
data class ParsedPermissionRequest(
    val rpcId: JsonElement,
    val event: AcpEvent.PermissionRequest,
    val allowOnceOptionId: String = DEFAULT_ALLOW_ONCE_OPTION_ID,
    val allowAlwaysOptionId: String = DEFAULT_ALLOW_ALWAYS_OPTION_ID,
    val rejectOptionId: String = DEFAULT_REJECT_ONCE_OPTION_ID,
) {
    companion object {
        const val DEFAULT_ALLOW_ONCE_OPTION_ID = "allow_once"
        const val DEFAULT_ALLOW_ALWAYS_OPTION_ID = "allow_always"
        const val DEFAULT_REJECT_ONCE_OPTION_ID = "reject_once"
    }
}

/**
 * Converts ACP JSON-RPC notifications, server requests, and session responses into strongly
 * typed `:core:contracts` models and [AcpEvent]s.
 */
class AcpEventMapper(
    private val json: Json = ContractsJson,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /**
     * Extracts title metadata from real `agy acp` `session_info_update` notifications without
     * perturbing [AcpSessionState] during active streaming turns.
     */
    fun extractSessionInfoUpdate(
        method: String,
        params: JsonObject?,
        fallbackSessionId: String?,
    ): SessionInfoUpdate? {
        if (params == null) return null
        if (method != "session/update" && method != "acp/session/update") return null
        val update = (params["update"] as? JsonObject) ?: params
        val kind = (update.stringOf("sessionUpdate", "type", "kind", "event")
            ?: params.stringOf("type", "kind"))
            ?.lowercase()
            ?: return null
        if (kind != "session_info_update") return null
        val title = update.stringOf("title", "name")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val sessionId = params.stringOf("sessionId", "session_id") ?: fallbackSessionId.orEmpty()
        return SessionInfoUpdate(sessionId = sessionId, title = title)
    }

    fun mapNotification(
        method: String,
        params: JsonObject?,
        fallbackSessionId: String?,
    ): AcpEvent? {
        if (params == null) return null
        val sessionId = params.stringOf("sessionId", "session_id") ?: fallbackSessionId.orEmpty()

        return when (method) {
            "session/update", "acp/session/update" -> mapSessionUpdate(sessionId, params)
            "session/text_delta", "acp/text_delta" -> mapTextDelta(sessionId, params)
            "session/thought_delta", "acp/thought_delta" -> mapThoughtDelta(sessionId, params)
            "session/tool_call", "session/tool_call_update", "acp/tool_call_update" ->
                mapToolCallUpdate(sessionId, params)
            "session/artifact_update", "acp/artifact_update" -> mapArtifactUpdate(sessionId, params)
            "session/swarm_update", "acp/swarm_update" -> mapSwarmUpdate(sessionId, params)
            "session/auth_required", "acp/auth_required" -> mapAuthRequired(sessionId, params)
            "session/state_changed", "acp/session_state_changed" ->
                mapSessionStateChanged(sessionId, params)
            "session/error", "acp/error" -> mapErrorEvent(sessionId, params)
            else -> null
        }
    }

    fun parsePermissionRequest(
        rpcId: JsonElement,
        params: JsonObject?,
        fallbackSessionId: String?,
    ): ParsedPermissionRequest {
        val obj = params ?: JsonObject(emptyMap())
        val toolCallObj = obj["toolCall"] as? JsonObject
        val sessionId = obj.stringOf("sessionId", "session_id") ?: fallbackSessionId.orEmpty()
        val requestId = obj.stringOf("requestId", "request_id")
            ?: toolCallObj?.stringOf("toolCallId", "callId", "id")
            ?: rpcId.rpcIdKey()
            ?: "perm-${clock()}"
        val toolName = obj.stringOf("toolName", "tool_name", "name")
            ?: toolCallObj?.stringOf("toolName", "title", "name")
            ?: "unknown_tool"
        val riskLevel = parseRiskLevel(
            raw = obj.stringOf("riskLevel", "risk_level", "risk")
                ?: toolCallObj?.stringOf("riskLevel", "risk_level"),
            toolName = toolName,
        )
        val description = obj.stringOf("description", "message", "reason")
            ?: toolCallObj?.stringOf("description", "title")
            ?: "Permission requested for tool '$toolName'"
        val argumentsJson = serializeJsonPayload(
            obj["argumentsJson"]
                ?: obj["arguments"]
                ?: obj["rawInput"]
                ?: toolCallObj?.get("rawInput")
                ?: toolCallObj?.get("arguments"),
        )

        var allowOnceId = ParsedPermissionRequest.DEFAULT_ALLOW_ONCE_OPTION_ID
        var allowAlwaysId = ParsedPermissionRequest.DEFAULT_ALLOW_ALWAYS_OPTION_ID
        var rejectId = ParsedPermissionRequest.DEFAULT_REJECT_ONCE_OPTION_ID
        var foundAllowOnce = false
        var foundAllowAlways = false

        val options = obj["options"] as? JsonArray
        options?.forEach { item ->
            val opt = item as? JsonObject ?: return@forEach
            val optionId = opt.stringOf("optionId", "id") ?: return@forEach
            val kind = opt.stringOf("kind", "type")?.lowercase().orEmpty()
            when {
                kind == "allow_always" || optionId.contains("always", ignoreCase = true) -> {
                    allowAlwaysId = optionId
                    foundAllowAlways = true
                }
                kind == "allow_once" || kind.startsWith("allow") -> {
                    allowOnceId = optionId
                    foundAllowOnce = true
                }
                kind == "reject_once" || kind == "reject_always" || kind.startsWith("reject") ||
                    kind.startsWith("deny") ->
                    rejectId = optionId
            }
        }

        // Real `agy 1.5.x` omits `allow_always` and only provides `allow` (`allow_once`) + `deny`
        // (`reject_once`). When no `allow_always` option exists, fall back `allowAlwaysId` to the
        // matched `allowOnceId` so `rememberForSession = true` still sends a valid allow `optionId`.
        if (!foundAllowAlways && foundAllowOnce) {
            allowAlwaysId = allowOnceId
        }

        return ParsedPermissionRequest(
            rpcId = rpcId,
            event = AcpEvent.PermissionRequest(
                sessionId = sessionId,
                requestId = requestId,
                toolName = toolName,
                riskLevel = riskLevel,
                description = description,
                argumentsJson = argumentsJson,
            ),
            allowOnceOptionId = allowOnceId,
            allowAlwaysOptionId = allowAlwaysId,
            rejectOptionId = rejectId,
        )
    }

    fun parseSessionInfo(
        result: JsonElement?,
        config: AcpSessionConfig,
        fallbackSessionId: String? = null,
    ): AcpSessionInfo {
        val obj = result as? JsonObject ?: JsonObject(emptyMap())
        val nested = obj["session"] as? JsonObject ?: obj
        val now = clock()
        val sessionId = nested.stringOf("sessionId", "session_id", "id")
            ?: obj.stringOf("sessionId", "session_id", "id")
            ?: fallbackSessionId
            ?: error("ACP response did not contain a sessionId")

        val rawModel = nested.stringOf("model")
            ?: extractModelFromConfigOptions(nested)
            ?: extractModelFromModelsObject(nested)

        return AcpSessionInfo(
            sessionId = sessionId,
            profileId = nested.stringOf("profileId", "profile_id") ?: config.profileId,
            title = nested.stringOf("title", "name").orEmpty(),
            workspaceDir = nested.stringOf("workspaceDir", "workspace_dir", "cwd")
                ?: config.workspaceDir,
            model = parseModel(rawModel) ?: config.model,
            state = parseSessionState(nested.stringOf("state")) ?: AcpSessionState.READY,
            createdAtEpochMs = nested.timestampOf("createdAtEpochMs", "createdAt", "created_at") ?: now,
            updatedAtEpochMs = nested.timestampOf("updatedAtEpochMs", "updatedAt", "updated_at") ?: now,
        )
    }

    fun parseSessionList(
        result: JsonElement?,
        profileId: String,
    ): List<AcpSessionInfo> {
        val array = when (result) {
            is JsonArray -> result
            is JsonObject -> (result["sessions"] as? JsonArray)
                ?: (result["items"] as? JsonArray)
                ?: JsonArray(emptyList())
            else -> JsonArray(emptyList())
        }
        val now = clock()
        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val sessionId = obj.stringOf("sessionId", "session_id", "id") ?: return@mapNotNull null
            val rawModel = obj.stringOf("model")
                ?: extractModelFromConfigOptions(obj)
                ?: extractModelFromModelsObject(obj)
            AcpSessionInfo(
                sessionId = sessionId,
                profileId = obj.stringOf("profileId", "profile_id") ?: profileId,
                title = obj.stringOf("title", "name").orEmpty(),
                workspaceDir = obj.stringOf("workspaceDir", "workspace_dir", "cwd") ?: "/mnt/sdcard",
                model = parseModel(rawModel) ?: AcpModel.GEMINI_4_NEON,
                state = parseSessionState(obj.stringOf("state")) ?: AcpSessionState.READY,
                createdAtEpochMs = obj.timestampOf("createdAtEpochMs", "createdAt", "created_at") ?: now,
                updatedAtEpochMs = obj.timestampOf("updatedAtEpochMs", "updatedAt", "updated_at") ?: now,
            )
        }
    }

    private fun extractModelFromConfigOptions(obj: JsonObject): String? {
        val options = obj["configOptions"] as? JsonArray ?: return null
        for (item in options) {
            val opt = item as? JsonObject ?: continue
            val id = opt.stringOf("id", "category")?.lowercase()
            if (id == "model") {
                return opt.stringOf("currentValue", "value")
            }
        }
        return null
    }

    private fun extractModelFromModelsObject(obj: JsonObject): String? {
        val modelsObj = obj["models"] as? JsonObject ?: return null
        return modelsObj.stringOf("currentModelId", "current_model_id", "modelId")
    }

    private fun mapSessionUpdate(sessionId: String, params: JsonObject): AcpEvent? {
        val update = (params["update"] as? JsonObject) ?: params
        val kind = (update.stringOf("sessionUpdate", "type", "kind", "event")
            ?: params.stringOf("type", "kind"))
            ?.lowercase()
            ?: return null

        return when (kind) {
            "agent_message_chunk", "text_delta", "message_delta", "text" ->
                mapTextDelta(sessionId, update)
            "agent_thought_chunk", "thought_delta", "thinking_delta", "thought" ->
                mapThoughtDelta(sessionId, update)
            "tool_call", "tool_call_update" ->
                mapToolCallUpdate(sessionId, update)
            "artifact", "artifact_update", "diff", "diff_update" ->
                mapArtifactUpdate(sessionId, update)
            "swarm_update", "swarm_board_update", "swarm" ->
                mapSwarmUpdate(sessionId, update)
            "auth_required" ->
                mapAuthRequired(sessionId, update)
            "session_state_changed", "state_changed", "session_state" ->
                mapSessionStateChanged(sessionId, update)
            "session_info_update" ->
                mapSessionStateChanged(sessionId, update)
            "available_commands_update",
            "user_message_chunk",
            "current_mode_update",
            "config_option_update",
            "plan",
            -> null
            "error" ->
                mapErrorEvent(sessionId, update)
            else -> null
        }
    }

    private fun mapTextDelta(sessionId: String, obj: JsonObject): AcpEvent.TextDelta {
        val contentObj = obj["content"] as? JsonObject
        val delta = obj.stringOf("delta", "text")
            ?: contentObj?.stringOf("text", "delta")
            ?: ""
        val messageId = obj.stringOf("messageId", "message_id")
        val isFinal = obj.boolOf("isFinal", "is_final", "done", "final") ?: false
        return AcpEvent.TextDelta(
            sessionId = sessionId,
            delta = delta,
            messageId = messageId,
            isFinal = isFinal,
        )
    }

    private fun mapThoughtDelta(sessionId: String, obj: JsonObject): AcpEvent.ThoughtDelta {
        val contentObj = obj["content"] as? JsonObject
        val delta = obj.stringOf("delta", "text", "thought")
            ?: contentObj?.stringOf("text", "delta")
            ?: ""
        val isFinal = obj.boolOf("isFinal", "is_final", "done", "final") ?: false
        return AcpEvent.ThoughtDelta(
            sessionId = sessionId,
            delta = delta,
            isFinal = isFinal,
        )
    }

    private fun mapToolCallUpdate(sessionId: String, obj: JsonObject): AcpEvent.ToolCallUpdate {
        val callId = obj.stringOf("callId", "toolCallId", "call_id", "id") ?: "call-${clock()}"
        val toolName = obj.stringOf("toolName", "tool_name", "name", "title") ?: "unknown_tool"
        val status = parseToolCallStatus(obj.stringOf("status", "state"))
        val argumentsJson = serializeJsonPayload(
            obj["argumentsJson"] ?: obj["arguments"] ?: obj["rawInput"] ?: obj["input"],
        )
        val outputPreview = extractOutputPreview(
            obj["outputPreview"] ?: obj["output_preview"] ?: obj["rawOutput"] ?: obj["output"] ?: obj["content"],
        )
        val errorMessage = obj.stringOf("errorMessage", "error_message", "error")
        return AcpEvent.ToolCallUpdate(
            sessionId = sessionId,
            callId = callId,
            toolName = toolName,
            status = status,
            argumentsJson = argumentsJson,
            outputPreview = outputPreview,
            errorMessage = errorMessage,
        )
    }

    private fun mapArtifactUpdate(sessionId: String, obj: JsonObject): AcpEvent.ArtifactUpdate {
        val artifactId = obj.stringOf("artifactId", "artifact_id", "id") ?: "artifact-${clock()}"
        val title = obj.stringOf("title", "name") ?: artifactId
        val mimeType = obj.stringOf("mimeType", "mime_type") ?: "text/markdown"
        val content = obj.stringOf("content", "diff", "text", "body").orEmpty()
        val filePath = obj.stringOf("filePath", "file_path", "path")
        return AcpEvent.ArtifactUpdate(
            sessionId = sessionId,
            artifactId = artifactId,
            title = title,
            mimeType = mimeType,
            content = content,
            filePath = filePath,
        )
    }

    private fun mapSwarmUpdate(sessionId: String, obj: JsonObject): AcpEvent.SwarmUpdate {
        val boardElement = obj["boardState"] ?: obj["board_state"] ?: obj["board"] ?: obj
        val boardState = runCatching {
            json.decodeFromJsonElement(SwarmBoardState.serializer(), boardElement)
        }.getOrElse {
            SwarmBoardState(
                swarmTitle = (boardElement as? JsonObject)?.stringOf("swarmTitle", "title").orEmpty(),
            )
        }
        return AcpEvent.SwarmUpdate(
            sessionId = sessionId,
            boardState = boardState,
        )
    }

    private fun mapAuthRequired(sessionId: String, obj: JsonObject): AcpEvent.AuthRequired {
        val code = obj.intOf("errorCode", "error_code", "code") ?: JsonRpcErrorCodes.AUTH_REQUIRED
        val message = obj.stringOf("message", "reason") ?: "Authentication required"
        val authUrl = obj.stringOf("authUrl", "auth_url", "url")
            ?: OAuthUrlExtractor.extractFromJsonElement(obj)
            ?: OAuthUrlExtractor.extractAnyUrl(message)
        return AcpEvent.AuthRequired(
            sessionId = sessionId.ifBlank { null },
            errorCode = code,
            message = message,
            authUrl = authUrl,
        )
    }

    private fun mapSessionStateChanged(
        sessionId: String,
        obj: JsonObject,
    ): AcpEvent.SessionStateChanged? {
        val state = parseSessionState(obj.stringOf("state", "status")) ?: return null
        val detail = obj.stringOf("detail", "message", "reason", "title")
        return AcpEvent.SessionStateChanged(
            sessionId = sessionId,
            state = state,
            detail = detail,
        )
    }

    private fun mapErrorEvent(sessionId: String, obj: JsonObject): AcpEvent.Error {
        val code = obj.intOf("code", "errorCode", "error_code")
        val message = obj.stringOf("message", "error", "reason") ?: "Unknown ACP error"
        val recoverable = obj.boolOf("recoverable") ?: true
        return AcpEvent.Error(
            sessionId = sessionId.ifBlank { null },
            code = code,
            message = message,
            recoverable = recoverable,
        )
    }

    private fun serializeJsonPayload(element: JsonElement?): String = when (element) {
        null -> "{}"
        is JsonPrimitive -> element.contentOrNull?.takeIf { it.isNotBlank() } ?: "{}"
        else -> element.toString()
    }

    private fun extractOutputPreview(element: JsonElement?): String? = when (element) {
        null -> null
        is JsonPrimitive -> element.contentOrNull
        is JsonObject -> element.stringOf("text", "preview", "output") ?: element.toString()
        is JsonArray -> {
            val texts = element.mapNotNull { item ->
                (item as? JsonObject)?.let { it.stringOf("text") ?: (it["content"] as? JsonObject)?.stringOf("text") }
                    ?: (item as? JsonPrimitive)?.contentOrNull
            }
            if (texts.isNotEmpty()) texts.joinToString("\n") else element.toString()
        }
    }

    companion object {
        private val EFFORT_SUFFIXES = listOf("-low", "-medium", "-high", "-xhigh", "-max")

        fun parseModel(raw: String?): AcpModel? {
            if (raw.isNullOrBlank()) return null
            val normalized = raw.trim().lowercase()
            AcpModel.entries.firstOrNull {
                it.cliId.equals(normalized, ignoreCase = true) ||
                    it.name.equals(normalized, ignoreCase = true) ||
                    it.displayName.equals(normalized, ignoreCase = true)
            }?.let { return it }

            val baseModel = EFFORT_SUFFIXES.firstNotNullOfOrNull { suffix ->
                if (normalized.endsWith(suffix)) normalized.removeSuffix(suffix) else null
            } ?: return null

            return AcpModel.entries.firstOrNull {
                it.cliId.equals(baseModel, ignoreCase = true)
            }
        }

        fun parseSessionState(raw: String?): AcpSessionState? {
            if (raw.isNullOrBlank()) return null
            val normalized = raw.trim().uppercase().replace('-', '_')
            return AcpSessionState.entries.firstOrNull { it.name == normalized }
        }

        fun parseToolCallStatus(raw: String?): ToolCallStatus {
            return when (raw?.trim()?.lowercase()?.replace('-', '_')) {
                "pending", "queued" -> ToolCallStatus.PENDING
                "running", "in_progress", "executing" -> ToolCallStatus.RUNNING
                "awaiting_approval", "waiting", "waiting_approval", "needs_approval" ->
                    ToolCallStatus.AWAITING_APPROVAL
                "completed", "succeeded", "success", "done" -> ToolCallStatus.COMPLETED
                "failed", "error" -> ToolCallStatus.FAILED
                "cancelled", "canceled", "aborted" -> ToolCallStatus.CANCELLED
                else -> ToolCallStatus.PENDING
            }
        }

        fun parseRiskLevel(raw: String?, toolName: String): McpToolRisk {
            if (!raw.isNullOrBlank()) {
                val normalized = raw.trim().uppercase()
                McpToolRisk.entries.firstOrNull { it.name == normalized }?.let { return it }
            }
            return when (toolName) {
                McpToolNames.ROOT_EXEC,
                McpToolNames.PHONE_CALL_CONTROL,
                McpToolNames.SMS_MANAGE,
                -> McpToolRisk.CRITICAL
                McpToolNames.FS_READ,
                McpToolNames.FS_WRITE,
                McpToolNames.FS_LIST,
                McpToolNames.APP_LAUNCH_OR_MANAGE,
                McpToolNames.DEVICE_CONTROL,
                McpToolNames.MEDIA_LOCATION_CAPTURE,
                -> McpToolRisk.HIGH
                McpToolNames.UI_GET_TREE,
                McpToolNames.SCREEN_CAPTURE,
                -> McpToolRisk.LOW
                else -> McpToolRisk.MEDIUM
            }
        }
    }
}

private fun JsonObject.stringOf(vararg keys: String): String? {
    for (key in keys) {
        val value = (this[key] as? JsonPrimitive)?.contentOrNull
        if (value != null) return value
    }
    return null
}

private fun JsonObject.intOf(vararg keys: String): Int? {
    for (key in keys) {
        val value = (this[key] as? JsonPrimitive)?.intOrNull
        if (value != null) return value
    }
    return null
}

private fun JsonObject.timestampOf(vararg keys: String): Long? {
    for (key in keys) {
        val primitive = this[key] as? JsonPrimitive ?: continue
        primitive.longOrNull?.let { return it }
        val text = primitive.contentOrNull?.trim()?.takeIf { it.isNotEmpty() } ?: continue
        runCatching { Instant.parse(text).toEpochMilli() }.getOrNull()?.let { return it }
    }
    return null
}

private fun JsonObject.boolOf(vararg keys: String): Boolean? {
    for (key in keys) {
        val value = (this[key] as? JsonPrimitive)?.booleanOrNull
        if (value != null) return value
    }
    return null
}
