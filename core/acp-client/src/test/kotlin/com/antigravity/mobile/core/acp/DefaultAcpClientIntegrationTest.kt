package com.antigravity.mobile.core.acp

import app.cash.turbine.test
import com.antigravity.mobile.contracts.AcpEvent
import com.antigravity.mobile.contracts.AcpModel
import com.antigravity.mobile.contracts.AcpSessionConfig
import com.antigravity.mobile.contracts.AcpSessionState
import com.antigravity.mobile.contracts.ChrootManager
import com.antigravity.mobile.contracts.ChrootState
import com.antigravity.mobile.contracts.ContractsJson
import com.antigravity.mobile.contracts.McpToolNames
import com.antigravity.mobile.contracts.McpToolRisk
import com.antigravity.mobile.contracts.MountStatus
import com.antigravity.mobile.contracts.PermissionMode
import com.antigravity.mobile.contracts.PermissionResponse
import com.antigravity.mobile.contracts.SwarmBoardMessage
import com.antigravity.mobile.contracts.SwarmBoardState
import com.antigravity.mobile.contracts.SwarmMemberInfo
import com.antigravity.mobile.contracts.SwarmMemberStatus
import com.antigravity.mobile.contracts.ThinkingEffort
import com.antigravity.mobile.contracts.ToolCallStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultAcpClientIntegrationTest {

    @Test
    fun `startSession runs initialize and session_new with model effort and permissionMode`() = runBlocking {
        val capturedRequests = CopyOnWriteArrayList<JsonObject>()
        val launcher = FakeAcpProcessLauncher {
            FakeAcpProcess().also { proc ->
                startScriptedServer(proc) { msg ->
                    capturedRequests.add(msg)
                    val id = msg["id"]
                    when (msg["method"]?.jsonPrimitive?.contentOrNull) {
                        "initialize" -> proc.writeServerStdoutLine(
                            """{"jsonrpc":"2.0","id":$id,"result":{"protocolVersion":1,"serverInfo":{"name":"agy-mock","version":"1.4.11"}}}""",
                        )
                        "session/new" -> proc.writeServerStdoutLine(
                            """{"jsonrpc":"2.0","id":$id,"result":{"sessionId":"sess-001","title":"Mobile Agent","model":"gemini-4-carbon"}}""",
                        )
                    }
                }
            }
        }

        val client = DefaultAcpClient(
            commandBuilder = AcpLaunchCommandBuilder(),
            processLauncher = launcher,
            ioDispatcher = Dispatchers.IO,
        )

        try {
            val result = client.startSession(
                AcpSessionConfig(
                    profileId = "google-work",
                    workspaceDir = "/mnt/sdcard/Projects/App",
                    additionalDirs = listOf("/mnt/sdcard"),
                    model = AcpModel.GEMINI_4_CARBON,
                    thinkingEffort = ThinkingEffort.EXTRA,
                    permissionMode = PermissionMode.PLAN,
                    ideMcpPort = 39991,
                ),
            )

            assertTrue(result.isSuccess)
            val info = result.getOrThrow()
            assertEquals("sess-001", info.sessionId)
            assertEquals("google-work", info.profileId)
            assertEquals(AcpModel.GEMINI_4_CARBON, info.model)
            assertEquals(AcpSessionState.READY, client.sessionState.value)
            assertEquals(info, client.activeSession.value)

            val sessionNewReq = capturedRequests.first { it["method"]?.jsonPrimitive?.contentOrNull == "session/new" }
            val params = sessionNewReq["params"]!!.jsonObject
            assertEquals("gemini-4-carbon", params["model"]?.jsonPrimitive?.content)
            assertEquals("xhigh", params["thinkingEffort"]?.jsonPrimitive?.content)
            assertEquals("plan", params["permissionMode"]?.jsonPrimitive?.content)
            assertEquals("/mnt/sdcard/Projects/App", params["cwd"]?.jsonPrimitive?.content)
        } finally {
            client.close()
        }

        assertEquals(AcpSessionState.CLOSED, client.sessionState.value)
        assertNull(client.activeSession.value)
        assertTrue(launcher.launchedProcesses.single().destroyCount.get() >= 1)
    }

    @Test
    fun `sendMessage streams text thought tool artifact and swarm updates`() = runBlocking {
        val boardState = SwarmBoardState(
            swarmTitle = "Android Build Swarm",
            members = listOf(
                SwarmMemberInfo(
                    name = "worker-acp-engine",
                    role = "ACP Client",
                    status = SwarmMemberStatus.WORKING,
                ),
            ),
            messages = listOf(
                SwarmBoardMessage(seq = 1, sender = "coordinator", text = "Build ACP client"),
            ),
        )
        val boardJson = ContractsJson.encodeToJsonElement(boardState)

        val launcher = FakeAcpProcessLauncher {
            FakeAcpProcess().also { proc ->
                startScriptedServer(proc) { msg ->
                    val id = msg["id"]
                    when (msg["method"]?.jsonPrimitive?.contentOrNull) {
                        "initialize" -> proc.writeServerStdoutLine("""{"jsonrpc":"2.0","id":$id,"result":{"protocolVersion":1}}""")
                        "session/new" -> proc.writeServerStdoutLine("""{"jsonrpc":"2.0","id":$id,"result":{"sessionId":"sess-stream"}}""")
                        "session/prompt" -> {
                            val params = msg["params"]!!.jsonObject
                            assertEquals("sess-stream", params["sessionId"]?.jsonPrimitive?.content)
                            assertEquals(2, params["prompt"]!!.jsonArray.size)

                            proc.writeServerStdoutLine(
                                """{"jsonrpc":"2.0","method":"session/update","params":{"sessionId":"sess-stream","update":{"sessionUpdate":"agent_thought_chunk","delta":"Checking UI tree..."}}}""",
                            )
                            proc.writeServerStdoutLine(
                                """{"jsonrpc":"2.0","method":"session/update","params":{"sessionId":"sess-stream","update":{"sessionUpdate":"tool_call_update","toolCallId":"call-1","toolName":"ui_get_tree","status":"completed","outputPreview":"{\"nodes\":5}"}}}""",
                            )
                            proc.writeServerStdoutLine(
                                """{"jsonrpc":"2.0","method":"session/update","params":{"sessionId":"sess-stream","update":{"sessionUpdate":"artifact_update","artifactId":"diff-1","title":"MainActivity.kt","mimeType":"text/x-diff","content":"+println(1)"}}}""",
                            )
                            val swarmNotification = buildJsonObject {
                                put("jsonrpc", "2.0")
                                put("method", "session/update")
                                put(
                                    "params",
                                    buildJsonObject {
                                        put("sessionId", "sess-stream")
                                        put(
                                            "update",
                                            buildJsonObject {
                                                put("sessionUpdate", "swarm_update")
                                                put("boardState", boardJson)
                                            },
                                        )
                                    },
                                )
                            }
                            proc.writeServerStdoutLine(swarmNotification.toString())
                            proc.writeServerStdoutLine(
                                """{"jsonrpc":"2.0","method":"session/update","params":{"sessionId":"sess-stream","update":{"sessionUpdate":"agent_message_chunk","delta":"Done!","isFinal":true}}}""",
                            )
                            proc.writeServerStdoutLine(
                                """{"jsonrpc":"2.0","id":$id,"result":{"stopReason":"end_turn"}}""",
                            )
                        }
                    }
                }
            }
        }

        val client = DefaultAcpClient(AcpLaunchCommandBuilder(), launcher, Dispatchers.IO)
        try {
            val session = client.startSession(AcpSessionConfig(profileId = "p-stream")).getOrThrow()

            client.events.test {
                val promptResult = client.sendMessage(
                    sessionId = session.sessionId,
                    prompt = "Inspect screen",
                    attachments = listOf("/mnt/sdcard/shot.png"),
                )
                assertTrue(promptResult.isSuccess)

                val received = mutableListOf<AcpEvent>()
                while (true) {
                    val item = awaitItem()
                    received.add(item)
                    if (item is AcpEvent.SessionStateChanged && item.state == AcpSessionState.READY) {
                        break
                    }
                }

                assertTrue(received.any { it is AcpEvent.ThoughtDelta && it.delta == "Checking UI tree..." })
                assertTrue(
                    received.any {
                        it is AcpEvent.ToolCallUpdate &&
                            it.callId == "call-1" &&
                            it.toolName == "ui_get_tree" &&
                            it.status == ToolCallStatus.COMPLETED
                    },
                )
                assertTrue(
                    received.any {
                        it is AcpEvent.ArtifactUpdate &&
                            it.artifactId == "diff-1" &&
                            it.title == "MainActivity.kt"
                    },
                )
                assertTrue(
                    received.any {
                        it is AcpEvent.SwarmUpdate &&
                            it.boardState.swarmTitle == "Android Build Swarm"
                    },
                )
                assertTrue(
                    received.any {
                        it is AcpEvent.TextDelta && it.delta == "Done!" && it.isFinal
                    },
                )
                cancelAndIgnoreRemainingEvents()
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun `handles -32000 Authentication required with extracted OAuth URL`() = runBlocking {
        val expectedAuthUrl = "https://accounts.google.com/o/oauth2/v2/auth?client_id=agy&state=12345"
        val launcher = FakeAcpProcessLauncher {
            FakeAcpProcess().also { proc ->
                startScriptedServer(proc) { msg ->
                    val id = msg["id"]
                    when (msg["method"]?.jsonPrimitive?.contentOrNull) {
                        "initialize" -> proc.writeServerStdoutLine("""{"jsonrpc":"2.0","id":$id,"result":{"protocolVersion":1}}""")
                        "session/new" -> proc.writeServerStdoutLine(
                            """{"jsonrpc":"2.0","id":$id,"error":{"code":-32000,"message":"Authentication required","data":{"authUrl":"$expectedAuthUrl"}}}""",
                        )
                    }
                }
            }
        }

        val client = DefaultAcpClient(AcpLaunchCommandBuilder(), launcher, Dispatchers.IO)
        try {
            client.events.test {
                val result = client.startSession(AcpSessionConfig(profileId = "p-auth"))
                assertTrue(result.isFailure)
                val err = result.exceptionOrNull() as JsonRpcException
                assertEquals(-32000, err.code)
                assertEquals(AcpSessionState.AWAITING_AUTH, client.sessionState.value)

                val authEvent = awaitItem() as AcpEvent.AuthRequired
                assertEquals(-32000, authEvent.errorCode)
                assertEquals(expectedAuthUrl, authEvent.authUrl)
                cancelAndIgnoreRemainingEvents()
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun `stderr OAuth URL emits AuthRequired automatically`() = runBlocking {
        val expectedUrl = "https://accounts.google.com/o/oauth2/v2/auth?client_id=stderr-flow"
        val launcher = FakeAcpProcessLauncher {
            FakeAcpProcess().also { proc ->
                startScriptedServer(proc) { msg ->
                    val id = msg["id"]
                    when (msg["method"]?.jsonPrimitive?.contentOrNull) {
                        "initialize" -> proc.writeServerStdoutLine("""{"jsonrpc":"2.0","id":$id,"result":{"protocolVersion":1}}""")
                        "session/new" -> proc.writeServerStdoutLine("""{"jsonrpc":"2.0","id":$id,"result":{"sessionId":"sess-stderr"}}""")
                    }
                }
            }
        }

        val client = DefaultAcpClient(AcpLaunchCommandBuilder(), launcher, Dispatchers.IO)
        try {
            client.startSession(AcpSessionConfig(profileId = "p-stderr")).getOrThrow()

            val authDeferred = async {
                client.events.first { it is AcpEvent.AuthRequired } as AcpEvent.AuthRequired
            }
            delay(50)
            launcher.launchedProcesses.single().writeServerStderrLine(
                "Sign in using browser: $expectedUrl",
            )

            val authEvent = authDeferred.await()
            assertEquals("sess-stderr", authEvent.sessionId)
            assertEquals(expectedUrl, authEvent.authUrl)
            assertEquals(AcpSessionState.AWAITING_AUTH, client.sessionState.value)
        } finally {
            client.close()
        }
    }

    @Test
    fun `bidirectional permission request round-trip replies with matching RPC id and outcome`() = runBlocking {
        val permissionReplyReceived = CompletableDeferred<JsonObject>()

        val launcher = FakeAcpProcessLauncher {
            FakeAcpProcess().also { proc ->
                startScriptedServer(proc) { msg ->
                    val id = msg["id"]
                    val method = msg["method"]?.jsonPrimitive?.contentOrNull
                    when {
                        method == "initialize" ->
                            proc.writeServerStdoutLine("""{"jsonrpc":"2.0","id":$id,"result":{"protocolVersion":1}}""")
                        method == "session/new" ->
                            proc.writeServerStdoutLine("""{"jsonrpc":"2.0","id":$id,"result":{"sessionId":"sess-perm"}}""")
                        method == "session/prompt" -> {
                            proc.writeServerStdoutLine(
                                """{"jsonrpc":"2.0","id":901,"method":"session/request_permission","params":{"sessionId":"sess-perm","requestId":"req-sms-1","toolName":"sms_manage","description":"Send SMS to +905551234567","arguments":{"action":"send"},"options":[{"optionId":"opt_once","kind":"allow_once"},{"optionId":"opt_always","kind":"allow_always"},{"optionId":"opt_reject","kind":"reject_once"}]}}""",
                            )
                        }
                        method == null && id?.jsonPrimitive?.int == 901 -> {
                            permissionReplyReceived.complete(msg)
                            proc.writeServerStdoutLine(
                                """{"jsonrpc":"2.0","id":3,"result":{"stopReason":"end_turn"}}""",
                            )
                        }
                    }
                }
            }
        }

        val client = DefaultAcpClient(AcpLaunchCommandBuilder(), launcher, Dispatchers.IO)
        try {
            client.startSession(AcpSessionConfig(profileId = "p-perm")).getOrThrow()

            val permEventDeferred = async {
                client.events.first { it is AcpEvent.PermissionRequest } as AcpEvent.PermissionRequest
            }
            val promptJob = async {
                client.sendMessage("sess-perm", "Send confirmation SMS")
            }

            val permEvent = withTimeout(5_000) { permEventDeferred.await() }
            assertEquals("req-sms-1", permEvent.requestId)
            assertEquals(McpToolNames.SMS_MANAGE, permEvent.toolName)
            assertEquals(McpToolRisk.CRITICAL, permEvent.riskLevel)
            assertEquals(AcpSessionState.AWAITING_PERMISSION, client.sessionState.value)

            val replyResult = client.respondToPermission(
                PermissionResponse(
                    requestId = "req-sms-1",
                    approved = true,
                    rememberForSession = true,
                    reason = "Approved via floating card",
                ),
            )
            assertTrue(replyResult.isSuccess)

            val replyJson = withTimeout(5_000) { permissionReplyReceived.await() }
            assertEquals(901, replyJson["id"]?.jsonPrimitive?.int)
            val resultObj = replyJson["result"]!!.jsonObject
            assertEquals("opt_always", resultObj["outcome"]!!.jsonObject["optionId"]?.jsonPrimitive?.content)
            assertEquals("true", resultObj["approved"]?.jsonPrimitive?.content)

            val promptResult = withTimeout(5_000) { promptJob.await() }
            assertTrue(promptResult.isSuccess)
            assertEquals(AcpSessionState.READY, client.sessionState.value)

            // Unknown permission request fails cleanly
            assertTrue(
                client.respondToPermission(PermissionResponse("non-existent", approved = false)).isFailure,
            )
        } finally {
            client.close()
        }
    }

    @Test
    fun `respondToPermission with rememberForSession sends allow when agy 1_5_17 omits allow_always`() = runBlocking {
        val permissionReplyReceived = CompletableDeferred<JsonObject>()

        val launcher = FakeAcpProcessLauncher {
            FakeAcpProcess().also { proc ->
                startScriptedServer(proc) { msg ->
                    val id = msg["id"]
                    val method = msg["method"]?.jsonPrimitive?.contentOrNull
                    when {
                        method == "initialize" ->
                            proc.writeServerStdoutLine("""{"jsonrpc":"2.0","id":$id,"result":{"protocolVersion":1}}""")
                        method == "session/new" ->
                            proc.writeServerStdoutLine("""{"jsonrpc":"2.0","id":$id,"result":{"sessionId":"sess-agy1517"}}""")
                        method == "session/prompt" -> {
                            proc.writeServerStdoutLine(
                                """{"jsonrpc":"2.0","id":"1","method":"session/request_permission","params":{"sessionId":"sess-agy1517","toolCall":{"sessionUpdate":"tool_call","toolCallId":"call_1517","name":"run_command","title":"uname -a","rawInput":{"CommandLine":"uname -a"}},"options":[{"optionId":"allow","name":"Allow","kind":"allow_once"},{"optionId":"deny","name":"Deny","kind":"reject_once"}]}}""",
                            )
                        }
                        method == null && id?.jsonPrimitive?.content == "1" -> {
                            permissionReplyReceived.complete(msg)
                            proc.writeServerStdoutLine(
                                """{"jsonrpc":"2.0","id":3,"result":{"stopReason":"end_turn"}}""",
                            )
                        }
                    }
                }
            }
        }

        val client = DefaultAcpClient(AcpLaunchCommandBuilder(), launcher, Dispatchers.IO)
        try {
            client.startSession(AcpSessionConfig(profileId = "p-1517")).getOrThrow()

            val permEventDeferred = async {
                client.events.first { it is AcpEvent.PermissionRequest } as AcpEvent.PermissionRequest
            }
            val promptJob = async {
                client.sendMessage("sess-agy1517", "Run uname -a")
            }

            val permEvent = withTimeout(5_000) { permEventDeferred.await() }
            assertEquals("call_1517", permEvent.requestId)

            val replyResult = client.respondToPermission(
                PermissionResponse(
                    requestId = "call_1517",
                    approved = true,
                    rememberForSession = true,
                ),
            )
            assertTrue(replyResult.isSuccess)

            val replyJson = withTimeout(5_000) { permissionReplyReceived.await() }
            assertEquals("1", replyJson["id"]?.jsonPrimitive?.content)
            val resultObj = replyJson["result"]!!.jsonObject
            assertEquals("allow", resultObj["outcome"]!!.jsonObject["optionId"]?.jsonPrimitive?.content)
            assertTrue(withTimeout(5_000) { promptJob.await() }.isSuccess)
        } finally {
            client.close()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `cancelSession sends session_cancel notification for Hardware Kill-Switch`() = runBlocking {
        val cancelReceived = CompletableDeferred<Unit>()
        val promptReceived = CompletableDeferred<Int>()

        val launcher = FakeAcpProcessLauncher {
            FakeAcpProcess().also { proc ->
                startScriptedServer(proc) { msg ->
                    val id = msg["id"]
                    when (msg["method"]?.jsonPrimitive?.contentOrNull) {
                        "initialize" -> proc.writeServerStdoutLine("""{"jsonrpc":"2.0","id":$id,"result":{"protocolVersion":1}}""")
                        "session/new" -> proc.writeServerStdoutLine("""{"jsonrpc":"2.0","id":$id,"result":{"sessionId":"sess-kill"}}""")
                        "session/prompt" -> {
                            promptReceived.complete(id!!.jsonPrimitive.int)
                        }
                        "session/cancel" -> {
                            assertEquals("sess-kill", msg["params"]!!.jsonObject["sessionId"]?.jsonPrimitive?.content)
                            cancelReceived.complete(Unit)
                            val pId = promptReceived.getCompleted()
                            proc.writeServerStdoutLine(
                                """{"jsonrpc":"2.0","id":$pId,"error":{"code":-32800,"message":"Request cancelled"}}""",
                            )
                        }
                    }
                }
            }
        }

        val client = DefaultAcpClient(AcpLaunchCommandBuilder(), launcher, Dispatchers.IO)
        try {
            client.startSession(AcpSessionConfig(profileId = "p-kill")).getOrThrow()

            val promptJob = async { client.sendMessage("sess-kill", "Long running task") }
            withTimeout(5_000) { promptReceived.await() }

            val cancelResult = client.cancelSession("sess-kill")
            assertTrue(cancelResult.isSuccess)
            withTimeout(5_000) { cancelReceived.await() }

            val promptResult = withTimeout(5_000) { promptJob.await() }
            assertTrue(promptResult.isFailure)
            assertEquals(-32800, (promptResult.exceptionOrNull() as JsonRpcException).code)
            assertEquals(AcpSessionState.READY, client.sessionState.value)
        } finally {
            client.close()
        }
    }

    @Test
    fun `listSessions and loadSession work across ephemeral and active processes`() = runBlocking {
        val launcher = FakeAcpProcessLauncher {
            FakeAcpProcess().also { proc ->
                startScriptedServer(proc) { msg ->
                    val id = msg["id"]
                    when (msg["method"]?.jsonPrimitive?.contentOrNull) {
                        "initialize" -> proc.writeServerStdoutLine("""{"jsonrpc":"2.0","id":$id,"result":{"protocolVersion":1}}""")
                        "session/list" -> proc.writeServerStdoutLine(
                            """{"jsonrpc":"2.0","id":$id,"result":{"sessions":[{"sessionId":"hist-1","title":"Yesterday","model":"gemini-4-neon"},{"sessionId":"hist-2","title":"Swarm","model":"orchestrator"}]}}""",
                        )
                        "session/load" -> {
                            val reqSessionId = msg["params"]!!.jsonObject["sessionId"]?.jsonPrimitive?.content
                            proc.writeServerStdoutLine(
                                """{"jsonrpc":"2.0","id":$id,"result":{"sessionId":"$reqSessionId","title":"Resumed Session","model":"gemini-4-hydrogen"}}""",
                            )
                        }
                    }
                }
            }
        }

        val client = DefaultAcpClient(AcpLaunchCommandBuilder(), launcher, Dispatchers.IO)
        try {
            // Disconnected listSessions spawns an ephemeral process and closes it cleanly
            val listed = client.listSessions("p-history").getOrThrow()
            assertEquals(2, listed.size)
            assertEquals("hist-1", listed[0].sessionId)
            assertEquals(AcpModel.ORCHESTRATOR, listed[1].model)
            assertEquals(1, launcher.launchedProcesses.size)
            assertTrue(launcher.launchedProcesses[0].destroyCount.get() >= 1)

            // loadSession starts and keeps active session ready
            val loaded = client.loadSession(
                sessionId = "hist-2",
                config = AcpSessionConfig(
                    profileId = "p-history",
                    model = AcpModel.GEMINI_4_HYDROGEN,
                    thinkingEffort = ThinkingEffort.LOW,
                    permissionMode = PermissionMode.ASK,
                ),
            ).getOrThrow()

            assertEquals("hist-2", loaded.sessionId)
            assertEquals("Resumed Session", loaded.title)
            assertEquals(AcpSessionState.READY, client.sessionState.value)
            assertEquals(2, launcher.launchedProcesses.size)

            // Subsequent listSessions for same profile reuses active process (no 3rd process)
            val listedAgain = client.listSessions("p-history").getOrThrow()
            assertEquals(2, listedAgain.size)
            assertEquals(2, launcher.launchedProcesses.size)
        } finally {
            client.close()
        }
    }

    @Test
    fun `malformed JSON stdout line emits recoverable error without breaking stream`() = runBlocking {
        val launcher = FakeAcpProcessLauncher {
            FakeAcpProcess().also { proc ->
                startScriptedServer(proc) { msg ->
                    val id = msg["id"]
                    when (msg["method"]?.jsonPrimitive?.contentOrNull) {
                        "initialize" -> proc.writeServerStdoutLine("""{"jsonrpc":"2.0","id":$id,"result":{"protocolVersion":1}}""")
                        "session/new" -> proc.writeServerStdoutLine("""{"jsonrpc":"2.0","id":$id,"result":{"sessionId":"sess-resilient"}}""")
                        "session/prompt" -> {
                            proc.writeServerStdoutLine("NOT-VALID-JSON banner from chroot")
                            proc.writeServerStdoutLine(
                                """{"jsonrpc":"2.0","method":"session/update","params":{"sessionId":"sess-resilient","update":{"sessionUpdate":"agent_message_chunk","delta":"Recovered!","isFinal":true}}}""",
                            )
                            proc.writeServerStdoutLine("""{"jsonrpc":"2.0","id":$id,"result":{"stopReason":"end_turn"}}""")
                        }
                    }
                }
            }
        }

        val client = DefaultAcpClient(AcpLaunchCommandBuilder(), launcher, Dispatchers.IO)
        try {
            client.startSession(AcpSessionConfig(profileId = "p-resilient")).getOrThrow()

            client.events.test {
                val res = client.sendMessage("sess-resilient", "hello")
                assertTrue(res.isSuccess)

                val events = mutableListOf<AcpEvent>()
                while (true) {
                    val ev = awaitItem()
                    events.add(ev)
                    if (ev is AcpEvent.SessionStateChanged && ev.state == AcpSessionState.READY) break
                }

                val parseErr = events.filterIsInstance<AcpEvent.Error>().firstOrNull()
                assertNotNull(parseErr)
                assertEquals(JsonRpcErrorCodes.PARSE_ERROR, parseErr!!.code)
                assertTrue(parseErr.recoverable)

                val text = events.filterIsInstance<AcpEvent.TextDelta>().firstOrNull()
                assertNotNull(text)
                assertEquals("Recovered!", text!!.delta)
                cancelAndIgnoreRemainingEvents()
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun `unexpected process exit captures stderr diagnostics and fails pending requests`() = runBlocking {
        val launcher = FakeAcpProcessLauncher {
            FakeAcpProcess().also { proc ->
                startScriptedServer(proc) { msg ->
                    val id = msg["id"]
                    when (msg["method"]?.jsonPrimitive?.contentOrNull) {
                        "initialize" -> proc.writeServerStdoutLine("""{"jsonrpc":"2.0","id":$id,"result":{"protocolVersion":1}}""")
                        "session/new" -> proc.writeServerStdoutLine("""{"jsonrpc":"2.0","id":$id,"result":{"sessionId":"sess-crash"}}""")
                        "session/prompt" -> {
                            proc.writeServerStderrLine("FATAL: glibc segfault in worker")
                            Thread.sleep(50)
                            proc.terminateWithExitCode(139)
                        }
                    }
                }
            }
        }

        val client = DefaultAcpClient(AcpLaunchCommandBuilder(), launcher, Dispatchers.IO)
        try {
            client.startSession(AcpSessionConfig(profileId = "p-crash")).getOrThrow()

            val errorEventDeferred = async {
                client.events.first { it is AcpEvent.Error } as AcpEvent.Error
            }

            val promptResult = client.sendMessage("sess-crash", "trigger crash")
            assertTrue(promptResult.isFailure)
            assertTrue(promptResult.exceptionOrNull() is AcpProcessTerminatedException)

            val errorEvent = errorEventDeferred.await()
            assertTrue(errorEvent.message.contains("glibc segfault") || errorEvent.message.contains("139"))
            assertEquals(AcpSessionState.ERROR, client.sessionState.value)
        } finally {
            client.close()
        }
    }

    @Test
    fun `request timeout returns AcpRequestTimeoutException and coroutine cancellation is never swallowed`() = runBlocking {
        val launcher = FakeAcpProcessLauncher {
            FakeAcpProcess().also { proc ->
                startScriptedServer(proc) { msg ->
                    val id = msg["id"]
                    when (msg["method"]?.jsonPrimitive?.contentOrNull) {
                        "initialize" -> proc.writeServerStdoutLine("""{"jsonrpc":"2.0","id":$id,"result":{"protocolVersion":1}}""")
                        "session/new" -> proc.writeServerStdoutLine("""{"jsonrpc":"2.0","id":$id,"result":{"sessionId":"sess-timeout"}}""")
                        // Intentionally ignore session/list and session/prompt to test timeout and cancellation
                    }
                }
            }
        }

        val client = DefaultAcpClient(
            commandBuilder = AcpLaunchCommandBuilder(),
            processLauncher = launcher,
            ioDispatcher = Dispatchers.IO,
            requestTimeoutMs = 150L,
            promptTimeoutMs = 5_000L,
        )
        try {
            client.startSession(AcpSessionConfig(profileId = "p-timeout")).getOrThrow()

            val timeoutResult = client.listSessions("p-timeout")
            assertTrue(timeoutResult.isFailure)
            assertTrue(timeoutResult.exceptionOrNull() is AcpRequestTimeoutException)

            var caughtCancellation = false
            val job = launch {
                try {
                    client.sendMessage("sess-timeout", "hang forever")
                } catch (e: CancellationException) {
                    caughtCancellation = true
                    throw e
                }
            }
            delay(80)
            job.cancelAndJoin()
            assertTrue("CancellationException must propagate and never be swallowed", caughtCancellation)
        } finally {
            client.close()
        }
    }

    @Test
    fun `startSession forwards all model thinkingEffort and permissionMode enum combinations`() = runBlocking {
        val combinations = listOf(
            Triple(AcpModel.GEMINI_4_NEON, ThinkingEffort.LOW, PermissionMode.AUTO),
            Triple(AcpModel.GEMINI_4_HYDROGEN, ThinkingEffort.MEDIUM, PermissionMode.ASK),
            Triple(AcpModel.GEMINI_4_CARBON, ThinkingEffort.HIGH, PermissionMode.PLAN),
            Triple(AcpModel.ORCHESTRATOR, ThinkingEffort.EXTRA, PermissionMode.AUTO),
        )

        for ((model, effort, mode) in combinations) {
            val capturedSessionParams = CompletableDeferred<JsonObject>()
            val launcher = FakeAcpProcessLauncher {
                FakeAcpProcess().also { proc ->
                    startScriptedServer(proc) { msg ->
                        val id = msg["id"]
                        when (msg["method"]?.jsonPrimitive?.contentOrNull) {
                            "initialize" -> proc.writeServerStdoutLine("""{"jsonrpc":"2.0","id":$id,"result":{"protocolVersion":1}}""")
                            "session/new" -> {
                                capturedSessionParams.complete(msg["params"]!!.jsonObject)
                                proc.writeServerStdoutLine("""{"jsonrpc":"2.0","id":$id,"result":{"sessionId":"sess-${model.cliId}","model":"${model.cliId}"}}""")
                            }
                        }
                    }
                }
            }

            val client = DefaultAcpClient(AcpLaunchCommandBuilder(), launcher, Dispatchers.IO)
            try {
                val session = client.startSession(
                    AcpSessionConfig(
                        profileId = "p-matrix",
                        model = model,
                        thinkingEffort = effort,
                        permissionMode = mode,
                    ),
                ).getOrThrow()

                assertEquals(model, session.model)
                val params = withTimeout(5_000) { capturedSessionParams.await() }
                assertEquals(model.cliId, params["model"]?.jsonPrimitive?.content)
                assertEquals(effort.cliFlagValue, params["thinkingEffort"]?.jsonPrimitive?.content)
                assertEquals(mode.cliFlagValue, params["permissionMode"]?.jsonPrimitive?.content)
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `live agy acp protocol streams session_new and session_prompt cleanly with relative dir flags`() = runBlocking {
        val launcher = FakeAcpProcessLauncher {
            FakeAcpProcess().also { proc ->
                startScriptedServer(proc) { msg ->
                    val id = msg["id"]
                    when (msg["method"]?.jsonPrimitive?.contentOrNull) {
                        "initialize" -> proc.writeServerStdoutLine(
                            """{"jsonrpc":"2.0","id":$id,"result":{"protocolVersion":1,"agentInfo":{"name":"Antigravity ACP Server","version":"1.4.11"}}}""",
                        )
                        "session/new" -> proc.writeServerStdoutLine(
                            """{"jsonrpc":"2.0","id":$id,"result":{"sessionId":"live-sess-1","models":{"currentModelId":"gemini-4-neon-high"},"configOptions":[{"id":"model","currentValue":"gemini-4-neon"},{"id":"thought_level","currentValue":"high"}]}}""",
                        )
                        "session/prompt" -> {
                            proc.writeServerStdoutLine(
                                """{"jsonrpc":"2.0","method":"session/update","params":{"sessionId":"live-sess-1","update":{"sessionUpdate":"available_commands_update","availableCommands":[{"name":"plan","description":"Plan task"}]}}}""",
                            )
                            proc.writeServerStdoutLine(
                                """{"jsonrpc":"2.0","method":"session/update","params":{"sessionId":"live-sess-1","update":{"sessionUpdate":"session_info_update","title":"Live ACP Chat"}}}""",
                            )
                            proc.writeServerStdoutLine(
                                """{"jsonrpc":"2.0","method":"session/update","params":{"sessionId":"live-sess-1","update":{"sessionUpdate":"user_message_chunk","content":{"type":"text","text":"Say hello"}}}}""",
                            )
                            proc.writeServerStdoutLine(
                                """{"jsonrpc":"2.0","method":"session/update","params":{"sessionId":"live-sess-1","update":{"sessionUpdate":"agent_thought_chunk","content":{"type":"text","text":"Planning concise greeting"}}}}""",
                            )
                            proc.writeServerStdoutLine(
                                """{"jsonrpc":"2.0","method":"session/update","params":{"sessionId":"live-sess-1","update":{"sessionUpdate":"tool_call","toolCallId":"tc-1","title":"ui_get_tree","status":"pending","rawInput":{"max_depth":10}}}}""",
                            )
                            proc.writeServerStdoutLine(
                                """{"jsonrpc":"2.0","method":"session/update","params":{"sessionId":"live-sess-1","update":{"sessionUpdate":"tool_call_update","toolCallId":"tc-1","title":"ui_get_tree","status":"completed","rawOutput":{"nodes":3}}}}""",
                            )
                            proc.writeServerStdoutLine(
                                """{"jsonrpc":"2.0","method":"session/update","params":{"sessionId":"live-sess-1","update":{"sessionUpdate":"agent_message_chunk","content":{"type":"text","text":"Hello from live agy!"}}}}""",
                            )
                            proc.writeServerStdoutLine(
                                """{"jsonrpc":"2.0","id":$id,"result":{"stopReason":"end_turn","usage":{"inputTokens":120,"outputTokens":5,"totalTokens":125}}}""",
                            )
                        }
                    }
                }
            }
        }

        val client = DefaultAcpClient(AcpLaunchCommandBuilder(), launcher, Dispatchers.IO)
        try {
            val session = client.startSession(
                AcpSessionConfig(
                    profileId = "48ed61eb-d59a-47cb-a068-29103797bb90",
                    workspaceDir = "/mnt/sdcard/Projects/Demo",
                ),
            ).getOrThrow()

            assertEquals("live-sess-1", session.sessionId)
            assertEquals(AcpModel.GEMINI_4_NEON, session.model)

            val launchArgs = launcher.recordedRequests.single().commandSpec.args
            val appDataDir = launchArgs[launchArgs.indexOf("--app-data-dir") + 1]
            val geminiDir = launchArgs[launchArgs.indexOf("--gemini-dir") + 1]
            assertEquals("antigravity-cli", appDataDir)
            assertEquals(".gemini", geminiDir)
            assertFalse(appDataDir.startsWith("/"))
            assertFalse(geminiDir.startsWith("/"))

            client.events.test {
                val promptResult = client.sendMessage("live-sess-1", "Say hello")
                assertTrue(promptResult.isSuccess)

                val received = mutableListOf<AcpEvent>()
                while (true) {
                    val ev = awaitItem()
                    received.add(ev)
                    if (ev is AcpEvent.SessionStateChanged && ev.state == AcpSessionState.READY) break
                }

                assertTrue(
                    "Live notifications must never produce error events",
                    received.none { it is AcpEvent.Error },
                )
                assertTrue(received.any { it is AcpEvent.ThoughtDelta && it.delta == "Planning concise greeting" })
                assertTrue(received.any { it is AcpEvent.ToolCallUpdate && it.callId == "tc-1" && it.status == ToolCallStatus.COMPLETED })
                assertTrue(received.any { it is AcpEvent.TextDelta && it.delta == "Hello from live agy!" })
                assertEquals("Live ACP Chat", client.activeSession.value?.title)
                assertEquals(AcpSessionState.READY, client.sessionState.value)
                cancelAndIgnoreRemainingEvents()
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun `startSession auto-mounts chroot via commandBuilder chrootManager when unmounted`() = runBlocking {
        val chrootStateFlow = MutableStateFlow<ChrootState>(ChrootState.Unmounted())
        val chrootManager = mockk<ChrootManager>()
        every { chrootManager.state } returns chrootStateFlow
        coEvery { chrootManager.mountAll() } coAnswers {
            val status = MountStatus(
                procMounted = true,
                sysMounted = true,
                devMounted = true,
                devPtsMounted = true,
                sdcardMounted = true,
                profilesMounted = true,
            )
            chrootStateFlow.value = ChrootState.Mounted(mountStatus = status)
            Result.success(status)
        }
        coEvery { chrootManager.buildChrootCommand(any()) } returns listOf(
            "su",
            "--mount-master",
            "-c",
            "chroot /data/local/ag_rootfs /usr/local/bin/agy acp --stdio",
        )

        val launcher = FakeAcpProcessLauncher {
            FakeAcpProcess().also { proc ->
                startScriptedServer(proc) { msg ->
                    val id = msg["id"]
                    when (msg["method"]?.jsonPrimitive?.contentOrNull) {
                        "initialize" -> proc.writeServerStdoutLine("""{"jsonrpc":"2.0","id":$id,"result":{"protocolVersion":1}}""")
                        "session/new" -> proc.writeServerStdoutLine("""{"jsonrpc":"2.0","id":$id,"result":{"sessionId":"sess-mounted"}}""")
                    }
                }
            }
        }

        // DefaultAcpClient inherits chrootManager from commandBuilder automatically
        val client = DefaultAcpClient(
            commandBuilder = AcpLaunchCommandBuilder(chrootManager = chrootManager),
            processLauncher = launcher,
            ioDispatcher = Dispatchers.IO,
        )
        try {
            val first = client.startSession(AcpSessionConfig(profileId = "p-mount"))
            assertTrue(first.isSuccess)
            coVerify(exactly = 1) { chrootManager.mountAll() }

            // Second startSession sees ChrootState.Mounted and skips remount
            val second = client.startSession(AcpSessionConfig(profileId = "p-mount"))
            assertTrue(second.isSuccess)
            coVerify(exactly = 1) { chrootManager.mountAll() }
        } finally {
            client.close()
        }
    }

    @Test
    fun `startSession fails cleanly when chroot mount fails or session_new returns non-auth RPC error`() = runBlocking {
        val chrootManager = mockk<ChrootManager>()
        every { chrootManager.state } returns MutableStateFlow(ChrootState.Unmounted())
        coEvery { chrootManager.mountAll() } returns Result.failure(IllegalStateException("Rootfs missing"))

        val neverUsedLauncher = FakeAcpProcessLauncher {
            error("Process must not spawn when chroot mount fails")
        }
        val mountFailClient = DefaultAcpClient(
            commandBuilder = AcpLaunchCommandBuilder(),
            processLauncher = neverUsedLauncher,
            ioDispatcher = Dispatchers.IO,
            chrootManager = chrootManager,
        )
        try {
            val res = mountFailClient.startSession(AcpSessionConfig(profileId = "p-fail-mount"))
            assertTrue(res.isFailure)
            assertEquals(AcpSessionState.ERROR, mountFailClient.sessionState.value)
            assertTrue(neverUsedLauncher.recordedRequests.isEmpty())
        } finally {
            mountFailClient.close()
        }

        val rpcErrLauncher = FakeAcpProcessLauncher {
            FakeAcpProcess().also { proc ->
                startScriptedServer(proc) { msg ->
                    val id = msg["id"]
                    when (msg["method"]?.jsonPrimitive?.contentOrNull) {
                        "initialize" -> proc.writeServerStdoutLine("""{"jsonrpc":"2.0","id":$id,"result":{"protocolVersion":1}}""")
                        "session/new" -> proc.writeServerStdoutLine(
                            """{"jsonrpc":"2.0","id":$id,"error":{"code":-32603,"message":"Internal failure"}}""",
                        )
                    }
                }
            }
        }
        val rpcErrClient = DefaultAcpClient(AcpLaunchCommandBuilder(), rpcErrLauncher, Dispatchers.IO)
        try {
            val res = rpcErrClient.startSession(AcpSessionConfig(profileId = "p-rpc-err"))
            assertTrue(res.isFailure)
            assertEquals(AcpSessionState.ERROR, rpcErrClient.sessionState.value)
        } finally {
            rpcErrClient.close()
        }
    }

    private fun startScriptedServer(
        process: FakeAcpProcess,
        onClientMessage: (JsonObject) -> Unit,
    ) {
        thread(isDaemon = true, name = "fake-acp-server") {
            try {
                while (process.isAlive) {
                    val msg = process.readClientJson(ContractsJson) ?: break
                    onClientMessage(msg)
                }
            } catch (_: Throwable) {
                // Pipe closed on teardown
            }
        }
    }
}
