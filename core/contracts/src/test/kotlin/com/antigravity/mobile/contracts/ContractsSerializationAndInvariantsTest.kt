package com.antigravity.mobile.contracts

import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ContractsSerializationAndInvariantsTest {

    @Test
    fun `ChrootState and RootfsSource sealed hierarchies roundtrip cleanly`() {
        val sources: List<RootfsSource> = listOf(
            RootfsSource.BundledAsset(),
            RootfsSource.RemoteDownload(url = "https://example.com/rootfs.tar.gz", sha256 = "abc123"),
            RootfsSource.LocalFile(absolutePath = "/sdcard/Download/rootfs.tar.gz"),
        )
        sources.forEach { source ->
            val json = ContractsJson.encodeToString(source)
            val decoded = ContractsJson.decodeFromString<RootfsSource>(json)
            assertEquals(source, decoded)
        }

        val mountStatus = MountStatus(
            procMounted = true,
            sysMounted = true,
            devMounted = true,
            devPtsMounted = true,
            sdcardMounted = true,
            androidRootMounted = true,
            profilesMounted = true,
            dnsConfigured = true,
            loopbackReachable = true,
        )
        assertTrue(mountStatus.allCoreMounted)
        assertFalse(mountStatus.copy(devPtsMounted = false).allCoreMounted)

        val states: List<ChrootState> = listOf(
            ChrootState.NotInstalled,
            ChrootState.Installing(stage = "Extracting rootfs", progressPercent = 45),
            ChrootState.Mounted(agyVersion = "1.4.11", mountStatus = mountStatus),
            ChrootState.Unmounted(agyVersion = "1.4.11"),
            ChrootState.Error(message = "Mount failed", recoverable = true),
        )
        states.forEach { state ->
            val json = ContractsJson.encodeToString(state)
            val decoded = ContractsJson.decodeFromString<ChrootState>(json)
            assertEquals(state, decoded)
        }

        val spec = ChrootCommandSpec(
            args = listOf("acp", "--process-per-session", "--ide-mcp-port", "41234"),
            environment = mapOf("HOME" to "/profiles/p1/home"),
            workingDirectory = "/mnt/sdcard",
        )
        assertEquals(spec, ContractsJson.decodeFromString<ChrootCommandSpec>(ContractsJson.encodeToString(spec)))
    }

    @Test
    fun `AccountProfile, ProfilePaths isolation, and AuthFlowState invariants`() {
        val paths = ProfilePaths.forProfileId("prof-01")
        assertEquals("/data/local/ag_profiles/prof-01", paths.hostRootDir)
        assertEquals("/profiles/prof-01/home", paths.chrootHome)
        val env = paths.toChrootEnvironment()
        assertEquals("/profiles/prof-01/home", env["HOME"])
        assertEquals("/profiles/prof-01/config", env["XDG_CONFIG_HOME"])
        assertEquals("/profiles/prof-01/data", env["XDG_DATA_HOME"])
        assertEquals("/profiles/prof-01/cache", env["XDG_CACHE_HOME"])
        assertEquals("/profiles/prof-01/data/conversations", env["AGY_CONVERSATIONS_DIR"])

        assertThrows(IllegalArgumentException::class.java) {
            ProfilePaths.forProfileId("")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProfilePaths.forProfileId("../escape")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProfilePaths.forProfileId("nested/id")
        }

        val quotaNear = UsageQuota(planTier = AccountPlanTier.PRO, remainingPercent = 12.5, usedRequests = 875, limitRequests = 1000)
        val quotaHealthy = UsageQuota(planTier = AccountPlanTier.ULTRA, remainingPercent = 80.0)
        assertTrue(quotaNear.isNearLimit)
        assertFalse(quotaHealthy.isNearLimit)

        val profile = AccountProfile(
            id = "prof-01",
            displayName = "Personal",
            email = "user@example.com",
            isAuthenticated = true,
            isActive = true,
            paths = paths,
            quota = quotaNear,
            createdAtEpochMs = 1_700_000_000_000L,
            lastUsedAtEpochMs = 1_700_000_005_000L,
        )
        assertEquals(profile, ContractsJson.decodeFromString<AccountProfile>(ContractsJson.encodeToString(profile)))

        val challenge = LoginChallenge(
            profileId = "prof-01",
            authUrl = "https://accounts.google.com/o/oauth2/v2/auth?client_id=test",
            callbackPort = 43210,
            instructions = "Sign in with Chrome Custom Tabs",
            supportsManualCode = true,
            issuedAtEpochMs = 1_700_000_010_000L,
        )
        val authStates: List<AuthFlowState> = listOf(
            AuthFlowState.Idle,
            AuthFlowState.Starting("prof-01"),
            AuthFlowState.AwaitingBrowserAuth(challenge),
            AuthFlowState.SubmittingCode("prof-01"),
            AuthFlowState.Authenticated("prof-01", "user@example.com"),
            AuthFlowState.Failed("prof-01", "Cancelled by user"),
        )
        authStates.forEach { state ->
            val json = ContractsJson.encodeToString(state)
            assertEquals(state, ContractsJson.decodeFromString<AuthFlowState>(json))
        }
    }

    @Test
    fun `AcpEvent sealed interface and session models roundtrip with agy CLI flag alignment`() {
        assertEquals("low", ThinkingEffort.LOW.cliFlagValue)
        assertEquals("medium", ThinkingEffort.MEDIUM.cliFlagValue)
        assertEquals("high", ThinkingEffort.HIGH.cliFlagValue)
        assertEquals("xhigh", ThinkingEffort.EXTRA.cliFlagValue)

        assertEquals("auto", PermissionMode.AUTO.cliFlagValue)
        assertEquals("accept-edits", PermissionMode.ASK.cliFlagValue)
        assertEquals("plan", PermissionMode.PLAN.cliFlagValue)

        val config = AcpSessionConfig(
            profileId = "prof-01",
            model = AcpModel.ORCHESTRATOR,
            thinkingEffort = ThinkingEffort.EXTRA,
            permissionMode = PermissionMode.PLAN,
            ideMcpPort = 39812,
        )
        assertEquals(config, ContractsJson.decodeFromString<AcpSessionConfig>(ContractsJson.encodeToString(config)))

        val info = AcpSessionInfo(
            sessionId = "sess-1",
            profileId = "prof-01",
            title = "Device Automation",
            model = AcpModel.GEMINI_4_CARBON,
            state = AcpSessionState.STREAMING,
        )
        assertEquals(info, ContractsJson.decodeFromString<AcpSessionInfo>(ContractsJson.encodeToString(info)))

        val permResponse = PermissionResponse(requestId = "req-1", approved = true, rememberForSession = false)
        assertEquals(permResponse, ContractsJson.decodeFromString<PermissionResponse>(ContractsJson.encodeToString(permResponse)))

        val boardState = SwarmBoardState(
            swarmTitle = "Phase 1 Build",
            members = listOf(
                SwarmMemberInfo(
                    name = "worker-acp",
                    role = "ACP Client",
                    model = "gemini-4-neon",
                    status = SwarmMemberStatus.WORKING,
                    ownedModules = listOf(":core:acp-client"),
                ),
            ),
            messages = listOf(
                SwarmBoardMessage(seq = 1, sender = "coordinator", text = "@worker-acp start", mentions = listOf("worker-acp")),
            ),
            edges = listOf(SwarmInteractionEdge(fromMember = "coordinator", toMember = "worker-acp", messageCount = 1)),
        )

        val events: List<AcpEvent> = listOf(
            AcpEvent.TextDelta(sessionId = "sess-1", delta = "Hello ", messageId = "m1"),
            AcpEvent.ThoughtDelta(sessionId = "sess-1", delta = "Checking UI tree..."),
            AcpEvent.ToolCallUpdate(
                sessionId = "sess-1",
                callId = "c1",
                toolName = McpToolNames.UI_GET_TREE,
                status = ToolCallStatus.COMPLETED,
                outputPreview = "{\"nodeId\":\"0\"}",
            ),
            AcpEvent.ArtifactUpdate(
                sessionId = "sess-1",
                artifactId = "art-1",
                title = "Implementation Plan",
                content = "# Plan",
            ),
            AcpEvent.SwarmUpdate(sessionId = "sess-1", boardState = boardState),
            AcpEvent.PermissionRequest(
                sessionId = "sess-1",
                requestId = "req-99",
                toolName = McpToolNames.PHONE_CALL_CONTROL,
                riskLevel = McpToolRisk.CRITICAL,
                description = "Place phone call",
                argumentsJson = "{\"action\":\"call\"}",
            ),
            AcpEvent.AuthRequired(
                sessionId = "sess-1",
                errorCode = -32000,
                message = "Authentication required",
                authUrl = "https://accounts.google.com/o/oauth2/v2/auth",
            ),
            AcpEvent.SessionStateChanged(sessionId = "sess-1", state = AcpSessionState.READY),
            AcpEvent.Error(sessionId = "sess-1", code = -32603, message = "Internal error", recoverable = false),
        )
        events.forEach { event ->
            val encoded = ContractsJson.encodeToString(event)
            val decoded = ContractsJson.decodeFromString<AcpEvent>(encoded)
            assertEquals(event, decoded)
        }
    }

    @Test
    fun `RootEnvironmentInfo, ShellResult, and BootstrapReport invariants`() {
        val env = RootEnvironmentInfo(
            isRootGranted = true,
            provider = RootProviderType.KERNEL_SU,
            suVersion = "KernelSU 1.0.5",
            mountMasterSupported = true,
            recommendedSuArgs = listOf("--mount-master"),
            managerPackageName = "me.weishu.kernelsu",
            guidanceMessage = "Grant root in KernelSU Manager",
        )
        val report = BootstrapReport(
            packageName = "com.antigravity.mobile",
            environment = env,
            runtimePermissionsGranted = listOf("android.permission.CAMERA"),
            appOpsGranted = listOf("SYSTEM_ALERT_WINDOW", "ACCESS_RESTRICTED_SETTINGS"),
            restrictedSettingsGranted = true,
            accessibilityEnabled = true,
            notificationListenerEnabled = true,
            batteryOptimizationExempted = true,
            steps = listOf(BootstrapStepResult("restricted_settings", true, "Allowed")),
        )
        assertTrue(report.allCriticalReady)
        assertFalse(report.copy(restrictedSettingsGranted = false).allCriticalReady)
        assertEquals(report, ContractsJson.decodeFromString<BootstrapReport>(ContractsJson.encodeToString(report)))

        assertTrue(ShellResult(exitCode = 0, stdout = "uid=0(root)", stderr = "").isSuccess)
        assertFalse(ShellResult(exitCode = 1, stdout = "", stderr = "denied").isSuccess)
        assertFalse(ShellResult(exitCode = 0, stdout = "", stderr = "", timedOut = true).isSuccess)
    }

    @Test
    fun `McpEndpoint enforces loopback-only host, valid port, and high-entropy bearer token`() {
        val valid = McpEndpoint(
            host = "127.0.0.1",
            port = 45123,
            bearerToken = "0123456789abcdef0123456789abcdef",
        )
        assertEquals("http://127.0.0.1:45123/mcp", valid.url)
        assertEquals(valid, ContractsJson.decodeFromString<McpEndpoint>(ContractsJson.encodeToString(valid)))

        assertThrows(IllegalArgumentException::class.java) {
            McpEndpoint(host = "0.0.0.0", port = 45123, bearerToken = "0123456789abcdef0123456789abcdef")
        }
        assertThrows(IllegalArgumentException::class.java) {
            McpEndpoint(host = "192.168.1.10", port = 45123, bearerToken = "0123456789abcdef0123456789abcdef")
        }
        assertThrows(IllegalArgumentException::class.java) {
            McpEndpoint(host = "127.0.0.1", port = 0, bearerToken = "0123456789abcdef0123456789abcdef")
        }
        assertThrows(IllegalArgumentException::class.java) {
            McpEndpoint(host = "127.0.0.1", port = 45123, bearerToken = "short-token")
        }

        val health = McpServerHealth(
            isRunning = true,
            endpointPort = 45123,
            registeredToolCount = 13,
            registeredTools = listOf(McpToolNames.ROOT_EXEC, McpToolNames.UI_GET_TREE),
            totalCallsHandled = 42L,
        )
        assertEquals(health, ContractsJson.decodeFromString<McpServerHealth>(ContractsJson.encodeToString(health)))
    }

    @Test
    fun `UiTree, SecurityPolicy, OverlayVoice, and WorkspaceAutomation models roundtrip cleanly`() {
        val bounds = UiBounds(left = 10, top = 20, right = 110, bottom = 80)
        assertEquals(100, bounds.width)
        assertEquals(60, bounds.height)
        assertEquals(60, bounds.centerX)
        assertEquals(50, bounds.centerY)

        val rootNode = UiNode(
            nodeId = "n0",
            className = "android.widget.FrameLayout",
            packageName = "com.android.dialer",
            bounds = bounds,
            children = listOf(
                UiNode(
                    nodeId = "n1",
                    className = "android.widget.Button",
                    packageName = "com.android.dialer",
                    text = "Call",
                    resourceId = "com.android.dialer:id/call_button",
                    bounds = UiBounds(20, 30, 90, 70),
                    clickable = true,
                ),
            ),
        )
        assertEquals(rootNode, ContractsJson.decodeFromString<UiNode>(ContractsJson.encodeToString(rootNode)))

        val actionReq = UiActionRequest(action = UiActionType.CLICK, nodeId = "n1")
        assertEquals(actionReq, ContractsJson.decodeFromString<UiActionRequest>(ContractsJson.encodeToString(actionReq)))

        val eval = PolicyEvaluation(
            toolName = McpToolNames.SMS_MANAGE,
            riskLevel = McpToolRisk.CRITICAL,
            decision = PolicyDecision.REQUIRE_APPROVAL,
            reason = "CRITICAL tool always requires user confirmation",
            canBeOverridden = false,
        )
        assertEquals(eval, ContractsJson.decodeFromString<PolicyEvaluation>(ContractsJson.encodeToString(eval)))

        val approval = ApprovalRequest(
            requestId = "apr-1",
            toolName = McpToolNames.SMS_MANAGE,
            riskLevel = McpToolRisk.CRITICAL,
            title = "Send SMS",
            summary = "Send SMS to +905550000000",
        )
        val overlay = OverlayState(
            isVisible = true,
            isExpanded = true,
            tickerText = "Waiting for confirmation...",
            isAgentRunning = true,
            pendingApproval = approval,
            voiceState = VoiceState.Listening(partialText = "ara", rmsDb = 4.5f),
        )
        assertEquals(overlay, ContractsJson.decodeFromString<OverlayState>(ContractsJson.encodeToString(overlay)))

        val voiceStates: List<VoiceState> = listOf(
            VoiceState.Idle,
            VoiceState.Listening("merhaba", 6.2f),
            VoiceState.Processing("merhaba"),
            VoiceState.Result("merhaba dünya", "tr-TR"),
            VoiceState.Error("No speech match", 7),
        )
        voiceStates.forEach { vs ->
            assertEquals(vs, ContractsJson.decodeFromString<VoiceState>(ContractsJson.encodeToString(vs)))
        }

        assertThrows(IllegalArgumentException::class.java) {
            ScheduledTask(
                id = "bad",
                name = "Missing schedule",
                prompt = "noop",
                profileId = "prof-01",
                cronExpression = null,
                triggerAtEpochMs = null,
            )
        }

        val task = ScheduledTask(
            id = "task-1",
            name = "Morning Briefing",
            prompt = "Summarize unread notifications",
            profileId = "prof-01",
            cronExpression = "0 8 * * *",
            exactTiming = true,
        )
        assertEquals(task, ContractsJson.decodeFromString<ScheduledTask>(ContractsJson.encodeToString(task)))

        val diff = DiffItem(
            id = "diff-1",
            filePath = "app/src/main/AndroidManifest.xml",
            changeType = DiffChangeType.MODIFIED,
            unifiedDiff = "@@ -1,1 +1,2 @@\n+<!-- added -->",
            additions = 1,
            deletions = 0,
        )
        assertEquals(diff, ContractsJson.decodeFromString<DiffItem>(ContractsJson.encodeToString(diff)))

        val message = WorkspaceChatMessage(
            id = "msg-1",
            sessionId = "sess-1",
            role = ChatMessageRole.ASSISTANT,
            content = "Done.",
            thinkingContent = "Verified policy.",
            toolCalls = listOf(ToolCallSummary("c1", McpToolNames.UI_GET_TREE, ToolCallStatus.COMPLETED)),
        )
        assertEquals(message, ContractsJson.decodeFromString<WorkspaceChatMessage>(ContractsJson.encodeToString(message)))
    }
}
