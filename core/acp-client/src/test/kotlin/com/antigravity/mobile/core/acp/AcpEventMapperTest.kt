package com.antigravity.mobile.core.acp

import com.antigravity.mobile.contracts.AcpEvent
import com.antigravity.mobile.contracts.AcpModel
import com.antigravity.mobile.contracts.AcpSessionConfig
import com.antigravity.mobile.contracts.AcpSessionState
import com.antigravity.mobile.contracts.ContractsJson
import com.antigravity.mobile.contracts.McpToolNames
import com.antigravity.mobile.contracts.McpToolRisk
import com.antigravity.mobile.contracts.SwarmBoardMessage
import com.antigravity.mobile.contracts.SwarmBoardState
import com.antigravity.mobile.contracts.SwarmInteractionEdge
import com.antigravity.mobile.contracts.SwarmMemberInfo
import com.antigravity.mobile.contracts.SwarmMemberStatus
import com.antigravity.mobile.contracts.ToolCallStatus
import java.time.Instant
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AcpEventMapperTest {
    private val mapper = AcpEventMapper(json = ContractsJson, clock = { 1_700_000_000_000L })

    @Test
    fun `maps session_update agent_message_chunk to TextDelta`() {
        val params = buildJsonObject {
            put("sessionId", "sess-1")
            put(
                "update",
                buildJsonObject {
                    put("sessionUpdate", "agent_message_chunk")
                    put("messageId", "msg-7")
                    put("isFinal", true)
                    put(
                        "content",
                        buildJsonObject {
                            put("type", "text")
                            put("text", "Merhaba")
                        },
                    )
                },
            )
        }

        val event = mapper.mapNotification("session/update", params, null)
        assertEquals(
            AcpEvent.TextDelta(
                sessionId = "sess-1",
                delta = "Merhaba",
                messageId = "msg-7",
                isFinal = true,
            ),
            event,
        )
    }

    @Test
    fun `maps session_update agent_thought_chunk to ThoughtDelta`() {
        val params = buildJsonObject {
            put("sessionId", "sess-1")
            put(
                "update",
                buildJsonObject {
                    put("sessionUpdate", "agent_thought_chunk")
                    put("delta", "Inspecting accessibility tree...")
                    put("isFinal", false)
                },
            )
        }

        val event = mapper.mapNotification("session/update", params, null)
        assertEquals(
            AcpEvent.ThoughtDelta(
                sessionId = "sess-1",
                delta = "Inspecting accessibility tree...",
                isFinal = false,
            ),
            event,
        )
    }

    @Test
    fun `maps tool_call_update with normalized status and arguments`() {
        val params = buildJsonObject {
            put("sessionId", "sess-1")
            put(
                "update",
                buildJsonObject {
                    put("sessionUpdate", "tool_call_update")
                    put("toolCallId", "call-99")
                    put("toolName", McpToolNames.UI_GET_TREE)
                    put("status", "in_progress")
                    put(
                        "rawInput",
                        buildJsonObject {
                            put("max_depth", 20)
                        },
                    )
                    put("outputPreview", "{\"nodes\":12}")
                },
            )
        }

        val event = mapper.mapNotification("session/update", params, null) as AcpEvent.ToolCallUpdate
        assertEquals("sess-1", event.sessionId)
        assertEquals("call-99", event.callId)
        assertEquals(McpToolNames.UI_GET_TREE, event.toolName)
        assertEquals(ToolCallStatus.RUNNING, event.status)
        assertEquals("{\"max_depth\":20}", event.argumentsJson)
        assertEquals("{\"nodes\":12}", event.outputPreview)
    }

    @Test
    fun `maps artifact_update and swarm_update notifications`() {
        val artifactParams = buildJsonObject {
            put("sessionId", "sess-2")
            put(
                "update",
                buildJsonObject {
                    put("sessionUpdate", "artifact_update")
                    put("artifactId", "art-1")
                    put("title", "Implementation Plan")
                    put("mimeType", "text/markdown")
                    put("content", "# Plan\n- Step 1")
                    put("filePath", "/mnt/sdcard/plan.md")
                },
            )
        }

        val artifactEvent = mapper.mapNotification("session/update", artifactParams, null)
        assertEquals(
            AcpEvent.ArtifactUpdate(
                sessionId = "sess-2",
                artifactId = "art-1",
                title = "Implementation Plan",
                mimeType = "text/markdown",
                content = "# Plan\n- Step 1",
                filePath = "/mnt/sdcard/plan.md",
            ),
            artifactEvent,
        )

        val expectedBoard = SwarmBoardState(
            swarmTitle = "Phase 1",
            members = listOf(
                SwarmMemberInfo(
                    name = "worker-acp-engine",
                    role = "ACP Engine",
                    model = "gemini-4-neon",
                    status = SwarmMemberStatus.WORKING,
                    ownedModules = listOf("core:acp-client"),
                ),
            ),
            messages = listOf(
                SwarmBoardMessage(
                    seq = 6,
                    sender = "coordinator",
                    text = "@worker-acp-engine start Phase 1",
                    mentions = listOf("worker-acp-engine"),
                ),
            ),
            edges = listOf(SwarmInteractionEdge("coordinator", "worker-acp-engine", 1)),
        )
        val swarmParams = buildJsonObject {
            put("sessionId", "sess-2")
            put(
                "update",
                buildJsonObject {
                    put("sessionUpdate", "swarm_update")
                    put("boardState", ContractsJson.encodeToJsonElement(expectedBoard))
                },
            )
        }

        val swarmEvent = mapper.mapNotification("session/update", swarmParams, null)
        assertEquals(
            AcpEvent.SwarmUpdate(
                sessionId = "sess-2",
                boardState = expectedBoard,
            ),
            swarmEvent,
        )
    }

    @Test
    fun `parses permission request with custom option IDs and inferred CRITICAL risk`() {
        val params = buildJsonObject {
            put("sessionId", "sess-5")
            put("requestId", "perm-42")
            put(
                "toolCall",
                buildJsonObject {
                    put("toolCallId", "call-42")
                    put("toolName", McpToolNames.PHONE_CALL_CONTROL)
                    put(
                        "rawInput",
                        buildJsonObject {
                            put("action", "call")
                            put("phone_number", "+905551112233")
                        },
                    )
                },
            )
            put("description", "Initiate outbound phone call")
            put(
                "options",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("optionId", "opt-allow-1")
                            put("kind", "allow_once")
                        },
                    )
                    add(
                        buildJsonObject {
                            put("optionId", "opt-allow-all")
                            put("kind", "allow_always")
                        },
                    )
                    add(
                        buildJsonObject {
                            put("optionId", "opt-deny")
                            put("kind", "reject_once")
                        },
                    )
                },
            )
        }

        val parsed = mapper.parsePermissionRequest(JsonPrimitive(77), params, null)
        assertEquals(JsonPrimitive(77), parsed.rpcId)
        assertEquals("opt-allow-1", parsed.allowOnceOptionId)
        assertEquals("opt-allow-all", parsed.allowAlwaysOptionId)
        assertEquals("opt-deny", parsed.rejectOptionId)
        assertEquals(McpToolRisk.CRITICAL, parsed.event.riskLevel)
        assertEquals("perm-42", parsed.event.requestId)
        assertEquals(McpToolNames.PHONE_CALL_CONTROL, parsed.event.toolName)
        assertTrue(parsed.event.argumentsJson.contains("+905551112233"))
    }

    @Test
    fun `parses session info and session list across all models`() {
        val config = AcpSessionConfig(
            profileId = "prof-1",
            workspaceDir = "/mnt/sdcard/Repo",
            model = AcpModel.ORCHESTRATOR,
        )
        val newResult = buildJsonObject {
            put("sessionId", "sess-orch")
            put("title", "Repo Task")
            put("model", "orchestrator")
        }

        val info = mapper.parseSessionInfo(newResult, config)
        assertEquals("sess-orch", info.sessionId)
        assertEquals("prof-1", info.profileId)
        assertEquals("Repo Task", info.title)
        assertEquals(AcpModel.ORCHESTRATOR, info.model)
        assertEquals(AcpSessionState.READY, info.state)

        val listResult = buildJsonObject {
            put(
                "sessions",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("sessionId", "s1")
                            put("model", "gemini-4-hydrogen")
                            put("title", "First")
                        },
                    )
                    add(
                        buildJsonObject {
                            put("sessionId", "s2")
                            put("model", "GEMINI_4_CARBON")
                            put("title", "Second")
                        },
                    )
                },
            )
        }

        val sessions = mapper.parseSessionList(listResult, "prof-1")
        assertEquals(2, sessions.size)
        assertEquals(AcpModel.GEMINI_4_HYDROGEN, sessions[0].model)
        assertEquals(AcpModel.GEMINI_4_CARBON, sessions[1].model)
    }

    @Test
    fun `parses live agy acp session_new configOptions models block and ISO updatedAt`() {
        val config = AcpSessionConfig(
            profileId = "prof-live",
            workspaceDir = "/mnt/sdcard/Proj",
            model = AcpModel.GEMINI_4_CARBON,
        )
        val liveSessionNew = buildJsonObject {
            put("sessionId", "3fa40d0b-51cc-4be1-9cbe-810ea1b6005a")
            put(
                "models",
                buildJsonObject {
                    put("currentModelId", "gemini-4-neon-high")
                },
            )
            put(
                "configOptions",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("id", "model")
                            put("currentValue", "gemini-4-neon")
                        },
                    )
                    add(
                        buildJsonObject {
                            put("id", "thought_level")
                            put("currentValue", "high")
                        },
                    )
                },
            )
        }

        val info = mapper.parseSessionInfo(liveSessionNew, config)
        assertEquals("3fa40d0b-51cc-4be1-9cbe-810ea1b6005a", info.sessionId)
        assertEquals(AcpModel.GEMINI_4_NEON, info.model)

        // Fallback to models.currentModelId with effort suffix stripped
        val modelsOnlyResult = buildJsonObject {
            put("sessionId", "sess-models-only")
            put(
                "models",
                buildJsonObject {
                    put("currentModelId", "gemini-4-hydrogen-xhigh")
                },
            )
        }
        assertEquals(
            AcpModel.GEMINI_4_HYDROGEN,
            mapper.parseSessionInfo(modelsOnlyResult, config).model,
        )

        // ISO-8601 updatedAt in live session/list
        val liveList = buildJsonObject {
            put(
                "sessions",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("sessionId", "hist-iso")
                            put("cwd", "/mnt/sdcard/Work")
                            put("title", "Website Login And Tasks")
                            put("updatedAt", "2026-11-15T20:30:00Z")
                        },
                    )
                },
            )
        }
        val parsedList = mapper.parseSessionList(liveList, "prof-live")
        assertEquals(1, parsedList.size)
        assertEquals("Website Login And Tasks", parsedList[0].title)
        assertEquals(
            Instant.parse("2026-11-15T20:30:00Z").toEpochMilli(),
            parsedList[0].updatedAtEpochMs,
        )
    }

    @Test
    fun `extracts session_info_update title and silently ignores available_commands_update and user_message_chunk`() {
        val infoParams = buildJsonObject {
            put("sessionId", "sess-live")
            put(
                "update",
                buildJsonObject {
                    put("sessionUpdate", "session_info_update")
                    put("title", "Acknowledge Instruction Test")
                },
            )
        }
        assertEquals(
            SessionInfoUpdate(sessionId = "sess-live", title = "Acknowledge Instruction Test"),
            mapper.extractSessionInfoUpdate("session/update", infoParams, null),
        )
        assertNull(mapper.mapNotification("session/update", infoParams, null))

        val commandsParams = buildJsonObject {
            put("sessionId", "sess-live")
            put(
                "update",
                buildJsonObject {
                    put("sessionUpdate", "available_commands_update")
                    put(
                        "availableCommands",
                        buildJsonArray {
                            add(buildJsonObject { put("name", "plan") })
                        },
                    )
                },
            )
        }
        assertNull(mapper.mapNotification("session/update", commandsParams, null))
        assertNull(mapper.extractSessionInfoUpdate("session/update", commandsParams, null))

        val userChunkParams = buildJsonObject {
            put("sessionId", "sess-live")
            put(
                "update",
                buildJsonObject {
                    put("sessionUpdate", "user_message_chunk")
                    put(
                        "content",
                        buildJsonObject {
                            put("type", "text")
                            put("text", "Reply with just OK")
                        },
                    )
                },
            )
        }
        assertNull(mapper.mapNotification("session/update", userChunkParams, null))
        assertNull(mapper.extractSessionInfoUpdate("session/update", userChunkParams, null))
    }
}
