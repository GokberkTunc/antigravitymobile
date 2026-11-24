package com.antigravity.mobile.core.mcp

import com.antigravity.mobile.contracts.ApprovalRequest
import com.antigravity.mobile.contracts.ContractsJson
import com.antigravity.mobile.contracts.McpToolCall
import com.antigravity.mobile.contracts.McpToolDefinition
import com.antigravity.mobile.contracts.McpToolNames
import com.antigravity.mobile.contracts.McpToolResult
import com.antigravity.mobile.contracts.OverlayController
import com.antigravity.mobile.contracts.PolicyDecision
import com.antigravity.mobile.contracts.RootExecutor
import com.antigravity.mobile.contracts.SecurityPolicyEngine
import com.antigravity.mobile.contracts.UiActionRequest
import com.antigravity.mobile.contracts.UiActionType
import com.antigravity.mobile.contracts.UiNode
import com.antigravity.mobile.contracts.UiTreeProvider
import com.antigravity.mobile.core.accessibility.NotificationBridge
import com.antigravity.mobile.core.accessibility.NotificationRecord
import com.antigravity.mobile.core.root.RootFileListResult
import com.antigravity.mobile.core.root.RootFileReadResult
import com.antigravity.mobile.core.root.RootFileWriteResult
import com.antigravity.mobile.core.root.SuFileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Executes all 13 mandatory Android System MCP tool categories behind an **unbypassable**
 * [SecurityPolicyEngine] + [OverlayController] safety gate.
 *
 * Execution flow for every `tools/call`:
 * 1. Resolve tool definition & foreground package.
 * 2. Call `SecurityPolicyEngine.evaluate(toolName, riskLevel, args, foregroundPackage, projectId)`.
 * 3. `DENY` -> reject immediately without executing tool.
 * 4. `REQUIRE_APPROVAL` -> prompt user via `OverlayController.requestUserApproval(...)`;
 *    execute ONLY if approved, else reject immediately.
 * 5. `ALLOW` (or approved) -> execute tool strictly on [ioDispatcher] (`Dispatchers.IO`).
 */
@Singleton
class McpToolDispatcher(
    private val rootExecutor: RootExecutor,
    private val suFileRepository: SuFileRepository,
    private val uiTreeProvider: UiTreeProvider,
    private val notificationBridge: NotificationBridge,
    private val systemToolGateway: AndroidSystemToolGateway,
    private val securityPolicyEngine: SecurityPolicyEngine,
    private val overlayController: OverlayController,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    @Inject
    constructor(
        rootExecutor: RootExecutor,
        suFileRepository: SuFileRepository,
        uiTreeProvider: UiTreeProvider,
        notificationBridge: NotificationBridge,
        systemToolGateway: AndroidSystemToolGateway,
        securityPolicyEngine: SecurityPolicyEngine,
        overlayController: OverlayController,
    ) : this(
        rootExecutor = rootExecutor,
        suFileRepository = suFileRepository,
        uiTreeProvider = uiTreeProvider,
        notificationBridge = notificationBridge,
        systemToolGateway = systemToolGateway,
        securityPolicyEngine = securityPolicyEngine,
        overlayController = overlayController,
        ioDispatcher = Dispatchers.IO,
    )

    fun registeredTools(): List<McpToolDefinition> = McpToolCatalog.allTools

    suspend fun dispatch(call: McpToolCall): McpToolResult {
        val startedAt = System.nanoTime()
        val toolDef = McpToolCatalog.byName[call.toolName]
            ?: return McpToolResult(
                callId = call.callId,
                toolName = call.toolName,
                isError = true,
                textContent = "Unknown MCP tool: ${call.toolName}",
                contentJson = buildErrorJson("Unknown MCP tool: ${call.toolName}"),
                durationMs = elapsedMs(startedAt),
            )

        val argsObject: JsonObject = try {
            val raw = call.argumentsJson.trim()
            if (raw.isEmpty()) JsonObject(emptyMap())
            else ContractsJson.parseToJsonElement(raw).jsonObject
        } catch (t: Throwable) {
            return McpToolResult(
                callId = call.callId,
                toolName = call.toolName,
                isError = true,
                textContent = "Invalid arguments JSON: ${t.message}",
                contentJson = buildErrorJson("Invalid arguments JSON: ${t.message}"),
                durationMs = elapsedMs(startedAt),
            )
        }

        val foregroundPackage = try {
            uiTreeProvider.getActivePackageName()
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            null
        }

        val flattenedArgs = flattenArguments(argsObject)

        // MANDATORY SECURITY GATE — NEVER BYPASSED
        val evaluation = try {
            securityPolicyEngine.evaluate(
                toolName = toolDef.name,
                riskLevel = toolDef.riskLevel,
                args = flattenedArgs,
                foregroundPackage = foregroundPackage,
                projectId = call.projectId,
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            return McpToolResult(
                callId = call.callId,
                toolName = toolDef.name,
                isError = true,
                textContent = "Security policy evaluation failed: ${t.message}",
                contentJson = buildErrorJson("Security policy evaluation failed: ${t.message}"),
                durationMs = elapsedMs(startedAt),
            )
        }

        when (evaluation.decision) {
            PolicyDecision.DENY -> {
                val msg = "Policy denied '${toolDef.name}': ${evaluation.reason}"
                return McpToolResult(
                    callId = call.callId,
                    toolName = toolDef.name,
                    isError = true,
                    textContent = msg,
                    contentJson = buildErrorJson(msg, decision = "DENY"),
                    durationMs = elapsedMs(startedAt),
                )
            }

            PolicyDecision.REQUIRE_APPROVAL -> {
                val approvalReq = ApprovalRequest(
                    requestId = UUID.randomUUID().toString(),
                    toolName = toolDef.name,
                    riskLevel = toolDef.riskLevel,
                    title = "Approve ${toolDef.name} (${toolDef.riskLevel})",
                    summary = evaluation.reason,
                    argumentsPreview = call.argumentsJson.take(400),
                    foregroundPackage = foregroundPackage,
                )
                val approved = try {
                    overlayController.requestUserApproval(approvalReq)
                } catch (ce: CancellationException) {
                    throw ce
                } catch (_: Throwable) {
                    false
                }
                if (!approved) {
                    val msg = "User denied approval for '${toolDef.name}': ${evaluation.reason}"
                    return McpToolResult(
                        callId = call.callId,
                        toolName = toolDef.name,
                        isError = true,
                        textContent = msg,
                        contentJson = buildErrorJson(msg, decision = "REJECTED"),
                        durationMs = elapsedMs(startedAt),
                    )
                }
            }

            PolicyDecision.ALLOW -> Unit
        }

        // Approved / allowed -> execute strictly on ioDispatcher
        return withContext(ioDispatcher) {
            try {
                try {
                    overlayController.updateTicker("Running ${toolDef.name}…")
                } catch (ce: CancellationException) {
                    throw ce
                } catch (_: Throwable) {
                    // Overlay ticker update is best-effort.
                }

                val jsonPayload = executeTool(toolDef.name, argsObject)
                McpToolResult(
                    callId = call.callId,
                    toolName = toolDef.name,
                    isError = false,
                    contentJson = jsonPayload,
                    textContent = jsonPayload,
                    durationMs = elapsedMs(startedAt),
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                val errMsg = t.message ?: t::class.java.simpleName
                McpToolResult(
                    callId = call.callId,
                    toolName = toolDef.name,
                    isError = true,
                    contentJson = buildErrorJson(errMsg),
                    textContent = errMsg,
                    durationMs = elapsedMs(startedAt),
                )
            }
        }
    }

    private suspend fun executeTool(toolName: String, args: JsonObject): String {
        return when (toolName) {
            McpToolNames.ROOT_EXEC -> executeRootExec(args)
            McpToolNames.FS_READ -> executeFsRead(args)
            McpToolNames.FS_WRITE -> executeFsWrite(args)
            McpToolNames.FS_LIST -> executeFsList(args)
            McpToolNames.UI_GET_TREE -> executeUiGetTree(args)
            McpToolNames.UI_ACTION -> executeUiAction(args)
            McpToolNames.UI_INPUT_RAW -> executeUiInputRaw(args)
            McpToolNames.SCREEN_CAPTURE -> executeScreenCapture(args)
            McpToolNames.APP_LAUNCH_OR_MANAGE -> executeAppLaunchOrManage(args)
            McpToolNames.PHONE_CALL_CONTROL -> executePhoneCallControl(args)
            McpToolNames.SMS_MANAGE -> executeSmsManage(args)
            McpToolNames.CONTACTS_CALENDAR_QUERY -> executeContactsCalendarQuery(args)
            McpToolNames.NOTIFICATIONS_MANAGE -> executeNotificationsManage(args)
            McpToolNames.DEVICE_CONTROL -> executeDeviceControl(args)
            McpToolNames.MEDIA_LOCATION_CAPTURE -> executeMediaLocationCapture(args)
            else -> throw IllegalArgumentException("Unhandled MCP tool: $toolName")
        }
    }

    private suspend fun executeRootExec(args: JsonObject): String {
        val command = args.requireString("command")
        val timeoutMs = args.optionalLong("timeout_ms") ?: 30_000L
        val cwd = args.optionalString("cwd")
        val result = rootExecutor.exec(command = command, timeoutMs = timeoutMs, cwd = cwd)
        return buildJsonObject {
            put("exitCode", result.exitCode)
            put("stdout", result.stdout)
            put("stderr", result.stderr)
            put("timedOut", result.timedOut)
            put("durationMs", result.durationMs)
        }.toString()
    }

    private suspend fun executeFsRead(args: JsonObject): String {
        val path = args.requireString("path")
        val offset = args.optionalLong("offset") ?: 0L
        val limit = args.optionalInt("limit") ?: SuFileRepository.DEFAULT_READ_LIMIT
        val res = suFileRepository.readFile(path = path, offset = offset, limit = limit).getOrThrow()
        return ContractsJson.encodeToString(RootFileReadResult.serializer(), res)
    }

    private suspend fun executeFsWrite(args: JsonObject): String {
        val path = args.requireString("path")
        val content = args.requireString("content")
        val append = args.optionalBoolean("append") ?: false
        val res = suFileRepository.writeFile(path = path, content = content, append = append).getOrThrow()
        return ContractsJson.encodeToString(RootFileWriteResult.serializer(), res)
    }

    private suspend fun executeFsList(args: JsonObject): String {
        val path = args.requireString("path")
        val offset = args.optionalInt("offset") ?: 0
        val limit = args.optionalInt("limit") ?: SuFileRepository.DEFAULT_LIST_LIMIT
        val res = suFileRepository.listDirectory(path = path, offset = offset, limit = limit).getOrThrow()
        return ContractsJson.encodeToString(RootFileListResult.serializer(), res)
    }

    private suspend fun executeUiGetTree(args: JsonObject): String {
        val includeInvisible = args.optionalBoolean("include_invisible") ?: false
        val maxDepth = args.optionalInt("max_depth") ?: 30
        val maxNodes = args.optionalInt("max_nodes") ?: 500
        val root = uiTreeProvider.captureUiTree(
            includeInvisible = includeInvisible,
            maxDepth = maxDepth,
            maxNodes = maxNodes,
        )
        return if (root == null) {
            buildJsonObject {
                put("connected", uiTreeProvider.isServiceConnected.value)
                put("tree", JsonPrimitive(null as String?))
            }.toString()
        } else {
            ContractsJson.encodeToString(UiNode.serializer(), root)
        }
    }

    private suspend fun executeUiAction(args: JsonObject): String {
        val actionStr = args.requireString("action").uppercase()
        val actionType = UiActionType.valueOf(actionStr)
        val req = UiActionRequest(
            action = actionType,
            nodeId = args.optionalString("node_id"),
            targetText = args.optionalString("text"),
            targetResourceId = args.optionalString("res_id"),
            value = args.optionalString("value"),
        )
        val result = uiTreeProvider.performAction(req)
        return buildJsonObject {
            put("success", result.success)
            put("message", result.message)
            result.matchedNodeId?.let { put("matched_node_id", it) }
        }.toString()
    }

    private suspend fun executeUiInputRaw(args: JsonObject): String {
        val type = args.requireString("type").lowercase()
        val argv = when (type) {
            "tap" -> {
                val x1 = args.requireInt("x1")
                val y1 = args.requireInt("y1")
                listOf("input", "tap", x1.toString(), y1.toString())
            }

            "swipe" -> {
                val x1 = args.requireInt("x1")
                val y1 = args.requireInt("y1")
                val x2 = args.requireInt("x2")
                val y2 = args.requireInt("y2")
                val duration = args.optionalInt("duration_ms")
                buildList {
                    add("input")
                    add("swipe")
                    add(x1.toString())
                    add(y1.toString())
                    add(x2.toString())
                    add(y2.toString())
                    if (duration != null && duration > 0) add(duration.toString())
                }
            }

            "keyevent" -> {
                val keycode = args.requireString("keycode")
                listOf("input", "keyevent", keycode)
            }

            "text" -> {
                val text = args.requireString("text").replace(" ", "%s")
                listOf("input", "text", text)
            }

            else -> throw IllegalArgumentException("Unsupported ui_input_raw type: $type")
        }
        val res = rootExecutor.execArgv(argv)
        return buildJsonObject {
            put("success", res.isSuccess)
            put("exitCode", res.exitCode)
            put("stdout", res.stdout)
            put("stderr", res.stderr)
        }.toString()
    }

    private suspend fun executeScreenCapture(args: JsonObject): String {
        val scale = args.optionalDouble("scale") ?: 0.5
        val format = args.optionalString("format") ?: "webp"
        return systemToolGateway.screenCapture(scale = scale, format = format).toString()
    }

    private suspend fun executeAppLaunchOrManage(args: JsonObject): String {
        val action = args.requireString("action")
        val pkg = args.optionalString("package_name")
        val intentUri = args.optionalString("intent_uri")
        return systemToolGateway.appLaunchOrManage(action, pkg, intentUri).toString()
    }

    private suspend fun executePhoneCallControl(args: JsonObject): String {
        val action = args.requireString("action")
        val phoneNumber = args.optionalString("phone_number")
        val enabled = args.optionalBoolean("enabled")
        return systemToolGateway.phoneCallControl(action, phoneNumber, enabled).toString()
    }

    private suspend fun executeSmsManage(args: JsonObject): String {
        val action = args.requireString("action")
        val address = args.optionalString("address")
        val body = args.optionalString("body")
        val limit = args.optionalInt("limit") ?: 20
        return systemToolGateway.smsManage(action, address, body, limit).toString()
    }

    private suspend fun executeContactsCalendarQuery(args: JsonObject): String {
        val target = args.requireString("target")
        val action = args.requireString("action")
        val query = args.optionalString("query")
        val payload = args["payload"]?.let { it as? JsonObject }
        val limit = args.optionalInt("limit") ?: 50
        return systemToolGateway.contactsCalendarQuery(target, action, query, payload, limit).toString()
    }

    private suspend fun executeNotificationsManage(args: JsonObject): String {
        val action = args.requireString("action").lowercase()
        return when (action) {
            "list" -> {
                val items = notificationBridge.listActiveNotifications()
                val encodedItems = ContractsJson.encodeToString(
                    ListSerializer(NotificationRecord.serializer()),
                    items,
                )
                """{"connected":${notificationBridge.isConnected.value},"count":${items.size},"notifications":$encodedItems}"""
            }

            "dismiss" -> {
                val key = args.requireString("notification_key")
                val ok = notificationBridge.dismissNotification(key)
                buildJsonObject {
                    put("action", "dismiss")
                    put("notification_key", key)
                    put("success", ok)
                }.toString()
            }

            "reply" -> {
                val key = args.requireString("notification_key")
                val replyText = args.requireString("reply_text")
                val ok = notificationBridge.replyToNotification(key, replyText)
                buildJsonObject {
                    put("action", "reply")
                    put("notification_key", key)
                    put("success", ok)
                }.toString()
            }

            else -> throw IllegalArgumentException("Unsupported notifications_manage action: $action")
        }
    }

    private suspend fun executeDeviceControl(args: JsonObject): String {
        val action = args.requireString("action")
        val key = args.optionalString("key")
        val value = args.optionalString("value")
        return systemToolGateway.deviceControl(action, key, value).toString()
    }

    private suspend fun executeMediaLocationCapture(args: JsonObject): String {
        val action = args.requireString("action")
        val lens = args.optionalString("lens")
        val durationSec = args.optionalInt("duration_sec") ?: 5
        return systemToolGateway.mediaLocationCapture(action, lens, durationSec).toString()
    }

    private fun flattenArguments(obj: JsonObject): Map<String, String> {
        val out = LinkedHashMap<String, String>(obj.size)
        for ((k, v) in obj) {
            out[k] = when (v) {
                is JsonPrimitive -> v.contentOrNull.orEmpty()
                else -> v.toString()
            }
        }
        return out
    }

    private fun buildErrorJson(message: String, decision: String? = null): String =
        buildJsonObject {
            put("error", message)
            if (decision != null) put("policy_decision", decision)
        }.toString()

    private fun elapsedMs(startedAtNanos: Long): Long =
        ((System.nanoTime() - startedAtNanos) / 1_000_000L).coerceAtLeast(0L)

    private fun JsonObject.requireString(key: String): String =
        optionalString(key)?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Missing required string parameter '$key'")

    private fun JsonObject.optionalString(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.requireInt(key: String): Int =
        optionalInt(key) ?: throw IllegalArgumentException("Missing required integer parameter '$key'")

    private fun JsonObject.optionalInt(key: String): Int? =
        (this[key] as? JsonPrimitive)?.intOrNull
            ?: (this[key] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()

    private fun JsonObject.optionalLong(key: String): Long? =
        (this[key] as? JsonPrimitive)?.longOrNull
            ?: (this[key] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()

    private fun JsonObject.optionalDouble(key: String): Double? =
        (this[key] as? JsonPrimitive)?.doubleOrNull
            ?: (this[key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()

    private fun JsonObject.optionalBoolean(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.booleanOrNull
            ?: (this[key] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()
}
