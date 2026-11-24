package com.antigravity.mobile.core.acp

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

object JsonRpcErrorCodes {
    const val PARSE_ERROR = -32700
    const val INVALID_REQUEST = -32600
    const val METHOD_NOT_FOUND = -32601
    const val INVALID_PARAMS = -32602
    const val INTERNAL_ERROR = -32603
    const val AUTH_REQUIRED = -32000
    const val REQUEST_CANCELLED = -32800
}

@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null,
)

@Serializable
data class JsonRpcRequest(
    val jsonrpc: String = JSON_RPC_VERSION,
    val id: JsonPrimitive,
    val method: String,
    val params: JsonElement? = null,
)

@Serializable
data class JsonRpcNotification(
    val jsonrpc: String = JSON_RPC_VERSION,
    val method: String,
    val params: JsonElement? = null,
)

@Serializable
data class JsonRpcResponse(
    val jsonrpc: String = JSON_RPC_VERSION,
    val id: JsonElement? = null,
    val result: JsonElement? = null,
    val error: JsonRpcError? = null,
)

class JsonRpcException(
    val code: Int,
    override val message: String,
    val data: JsonElement? = null,
) : Exception("JSON-RPC error $code: $message")

class AcpRequestTimeoutException(
    val method: String,
    val timeoutMs: Long,
) : Exception("ACP JSON-RPC request '$method' timed out after ${timeoutMs}ms")

class AcpProcessTerminatedException(
    val exitCode: Int?,
    val stderrSummary: String,
) : Exception(
    buildString {
        append("ACP process terminated unexpectedly")
        if (exitCode != null) append(" (exitCode=$exitCode)")
        if (stderrSummary.isNotBlank()) append(": $stderrSummary")
    },
)

/**
 * Classified incoming JSON-RPC 2.0 frame parsed from a single NDJSON stdout line.
 */
sealed interface IncomingJsonRpcMessage {
    data class Response(
        val idKey: String,
        val response: JsonRpcResponse,
    ) : IncomingJsonRpcMessage

    data class ServerRequest(
        val id: JsonElement,
        val method: String,
        val params: JsonObject?,
    ) : IncomingJsonRpcMessage

    data class Notification(
        val method: String,
        val params: JsonObject?,
    ) : IncomingJsonRpcMessage
}

internal const val JSON_RPC_VERSION = "2.0"

internal fun JsonElement.rpcIdKey(): String? = when (this) {
    is JsonPrimitive -> contentOrNull
    else -> null
}

internal fun parseJsonRpcError(element: JsonElement?): JsonRpcError? {
    val obj = element as? JsonObject ?: return null
    val code = (obj["code"] as? JsonPrimitive)?.intOrNull ?: JsonRpcErrorCodes.INTERNAL_ERROR
    val message = (obj["message"] as? JsonPrimitive)?.contentOrNull ?: "Unknown JSON-RPC error"
    return JsonRpcError(
        code = code,
        message = message,
        data = obj["data"],
    )
}
