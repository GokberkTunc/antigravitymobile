package com.antigravity.mobile.feature.workspace.viewmodel

import com.antigravity.mobile.contracts.AccountPlanTier
import com.antigravity.mobile.contracts.AccountProfile
import com.antigravity.mobile.contracts.AccountRepository
import com.antigravity.mobile.contracts.AcpClient
import com.antigravity.mobile.contracts.AcpEvent
import com.antigravity.mobile.contracts.AcpModel
import com.antigravity.mobile.contracts.AcpSessionInfo
import com.antigravity.mobile.contracts.AcpSessionState
import com.antigravity.mobile.contracts.AndroidMcpServer
import com.antigravity.mobile.contracts.AuthFlowState
import com.antigravity.mobile.contracts.AutomationScheduler
import com.antigravity.mobile.contracts.BootstrapReport
import com.antigravity.mobile.contracts.ChatMessageRole
import com.antigravity.mobile.contracts.ChrootManager
import com.antigravity.mobile.contracts.ChrootState
import com.antigravity.mobile.contracts.DiffApprovalStatus
import com.antigravity.mobile.contracts.LoginChallenge
import com.antigravity.mobile.contracts.McpServerHealth
import com.antigravity.mobile.contracts.McpToolDefinition
import com.antigravity.mobile.contracts.McpToolRisk
import com.antigravity.mobile.contracts.MountStatus
import com.antigravity.mobile.contracts.PermissionMode
import com.antigravity.mobile.contracts.PermissionResponse
import com.antigravity.mobile.contracts.ProfilePaths
import com.antigravity.mobile.contracts.RootEnvironmentInfo
import com.antigravity.mobile.contracts.RootExecutor
import com.antigravity.mobile.contracts.RootProviderType
import com.antigravity.mobile.contracts.ScheduledTask
import com.antigravity.mobile.contracts.ScheduledTaskRun
import com.antigravity.mobile.contracts.SecurityPolicyEngine
import com.antigravity.mobile.contracts.SwarmBoardMessage
import com.antigravity.mobile.contracts.SwarmBoardState
import com.antigravity.mobile.contracts.SwarmInteractionEdge
import com.antigravity.mobile.contracts.SwarmMemberInfo
import com.antigravity.mobile.contracts.SwarmMemberStatus
import com.antigravity.mobile.contracts.ToolCallStatus
import com.antigravity.mobile.contracts.UsageQuota
import com.antigravity.mobile.contracts.VoiceState
import com.antigravity.mobile.contracts.VoiceTranscriber
import com.antigravity.mobile.feature.workspace.data.FileWorkspaceLocalStore
import com.antigravity.mobile.feature.workspace.data.InMemoryWorkspaceLocalStore
import com.antigravity.mobile.feature.workspace.model.AppThemeMode
import com.antigravity.mobile.feature.workspace.model.InterfaceLayoutMode
import com.antigravity.mobile.feature.workspace.model.OverviewTab
import com.antigravity.mobile.feature.workspace.model.RootfsSourceOption
import com.antigravity.mobile.feature.workspace.model.SettingsCategory
import com.antigravity.mobile.feature.workspace.model.WorkspaceAction
import com.antigravity.mobile.feature.workspace.model.WorkspaceConversation
import java.io.File
import java.nio.file.Files
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkspaceViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var accountRepository: AccountRepository
    private lateinit var acpClient: AcpClient
    private lateinit var androidMcpServer: AndroidMcpServer
    private lateinit var automationScheduler: AutomationScheduler
    private lateinit var securityPolicyEngine: SecurityPolicyEngine
    private lateinit var voiceTranscriber: VoiceTranscriber
    private lateinit var chrootManager: ChrootManager
    private lateinit var rootExecutor: RootExecutor

    private lateinit var profilesFlow: MutableStateFlow<List<AccountProfile>>
    private lateinit var activeProfileFlow: MutableStateFlow<AccountProfile?>
    private lateinit var authFlowState: MutableStateFlow<AuthFlowState>
    private lateinit var acpEventsFlow: MutableSharedFlow<AcpEvent>
    private lateinit var acpSessionStateFlow: MutableStateFlow<AcpSessionState>
    private lateinit var activeSessionFlow: MutableStateFlow<AcpSessionInfo?>
    private lateinit var mcpHealthFlow: MutableStateFlow<McpServerHealth>
    private lateinit var tasksFlow: MutableStateFlow<List<ScheduledTask>>
    private lateinit var runsFlow: MutableStateFlow<List<ScheduledTaskRun>>
    private lateinit var denylistFlow: MutableStateFlow<Set<String>>
    private lateinit var permissionModeFlow: MutableStateFlow<PermissionMode>
    private lateinit var voiceStateFlow: MutableStateFlow<VoiceState>
    private lateinit var chrootStateFlow: MutableStateFlow<ChrootState>

    private lateinit var viewModel: WorkspaceViewModel

    private val primaryProfile = AccountProfile(
        id = "prof-1",
        displayName = "Kişisel Hesap",
        email = "user1@gmail.com",
        isAuthenticated = true,
        isActive = true,
        paths = ProfilePaths.forProfileId("prof-1"),
        quota = UsageQuota(planTier = AccountPlanTier.PRO, remainingPercent = 65.0),
    )

    private val backupProfile = AccountProfile(
        id = "prof-2",
        displayName = "Yedek Hesap",
        email = "user2@gmail.com",
        isAuthenticated = true,
        isActive = false,
        paths = ProfilePaths.forProfileId("prof-2"),
        quota = UsageQuota(planTier = AccountPlanTier.ULTRA, remainingPercent = 92.0),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        profilesFlow = MutableStateFlow(listOf(primaryProfile, backupProfile))
        activeProfileFlow = MutableStateFlow(primaryProfile)
        authFlowState = MutableStateFlow(AuthFlowState.Idle)
        acpEventsFlow = MutableSharedFlow(extraBufferCapacity = 32)
        acpSessionStateFlow = MutableStateFlow(AcpSessionState.READY)
        activeSessionFlow = MutableStateFlow(
            AcpSessionInfo(
                sessionId = "sess-1",
                profileId = "prof-1",
                state = AcpSessionState.READY,
            ),
        )
        mcpHealthFlow = MutableStateFlow(
            McpServerHealth(
                isRunning = true,
                endpointHost = "127.0.0.1",
                endpointPort = 43123,
                registeredToolCount = 1,
                registeredTools = listOf("ui_get_tree"),
            ),
        )
        tasksFlow = MutableStateFlow(emptyList())
        runsFlow = MutableStateFlow(emptyList())
        denylistFlow = MutableStateFlow(setOf("com.ziraat.ziraatmobil"))
        permissionModeFlow = MutableStateFlow(PermissionMode.ASK)
        voiceStateFlow = MutableStateFlow(VoiceState.Idle)
        chrootStateFlow = MutableStateFlow(ChrootState.NotInstalled)

        accountRepository = mockk(relaxed = true)
        every { accountRepository.profiles } returns profilesFlow
        every { accountRepository.activeProfile } returns activeProfileFlow
        every { accountRepository.authFlowState } returns authFlowState
        coEvery { accountRepository.suggestFallbackProfile(any()) } returns backupProfile

        acpClient = mockk(relaxed = true)
        every { acpClient.events } returns acpEventsFlow
        every { acpClient.sessionState } returns acpSessionStateFlow
        every { acpClient.activeSession } returns activeSessionFlow
        coEvery { acpClient.startSession(any()) } returns Result.success(activeSessionFlow.value!!)
        coEvery { acpClient.sendMessage(any(), any(), any()) } returns Result.success(Unit)
        coEvery { acpClient.cancelSession(any()) } returns Result.success(Unit)
        coEvery { acpClient.respondToPermission(any()) } returns Result.success(Unit)

        androidMcpServer = mockk(relaxed = true)
        every { androidMcpServer.health } returns mcpHealthFlow
        every { androidMcpServer.registeredTools() } returns listOf(
            McpToolDefinition(name = "ui_get_tree", description = "UI ağacı", riskLevel = McpToolRisk.LOW),
        )

        automationScheduler = mockk(relaxed = true)
        every { automationScheduler.tasks } returns tasksFlow
        every { automationScheduler.recentRuns } returns runsFlow
        coEvery { automationScheduler.scheduleTask(any()) } answers { Result.success(firstArg()) }

        securityPolicyEngine = mockk(relaxed = true)
        every { securityPolicyEngine.denylistPackages } returns denylistFlow
        every { securityPolicyEngine.permissionMode } returns permissionModeFlow
        coEvery { securityPolicyEngine.setPermissionMode(any()) } answers {
            permissionModeFlow.value = firstArg()
        }
        coEvery { securityPolicyEngine.triggerEmergencyKillSwitch(any()) } returns Result.success(Unit)

        voiceTranscriber = mockk(relaxed = true)
        every { voiceTranscriber.state } returns voiceStateFlow
        every { voiceTranscriber.isAvailable() } returns true

        chrootManager = mockk(relaxed = true)
        every { chrootManager.state } returns chrootStateFlow

        rootExecutor = mockk(relaxed = true)

        viewModel = WorkspaceViewModel(
            accountRepository = accountRepository,
            acpClient = acpClient,
            androidMcpServer = androidMcpServer,
            automationScheduler = automationScheduler,
            securityPolicyEngine = securityPolicyEngine,
            voiceTranscriber = voiceTranscriber,
            chrootManager = chrootManager,
            rootExecutor = rootExecutor,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `ACP streaming events assemble thought blocks, tool pills, and assistant markdown text`() = runTest {
        advanceUntilIdle()

        acpEventsFlow.emit(AcpEvent.ThoughtDelta(sessionId = "sess-1", delta = "UI ağacını incelemeliyim."))
        acpEventsFlow.emit(
            AcpEvent.ToolCallUpdate(
                sessionId = "sess-1",
                callId = "call-1",
                toolName = "ui_get_tree",
                status = ToolCallStatus.RUNNING,
                argumentsJson = """{"max_depth":30}""",
            ),
        )
        acpEventsFlow.emit(
            AcpEvent.ToolCallUpdate(
                sessionId = "sess-1",
                callId = "call-1",
                toolName = "ui_get_tree",
                status = ToolCallStatus.COMPLETED,
                argumentsJson = """{"max_depth":30}""",
                outputPreview = """{"nodes":12}""",
            ),
        )
        acpEventsFlow.emit(AcpEvent.TextDelta(sessionId = "sess-1", delta = "Merhaba "))
        acpEventsFlow.emit(AcpEvent.TextDelta(sessionId = "sess-1", delta = "Antigravity!", isFinal = true))
        advanceUntilIdle()

        val assistantMsg = viewModel.uiState.value.messages.single { it.role == ChatMessageRole.ASSISTANT }
        assertEquals("UI ağacını incelemeliyim.", assistantMsg.thinkingContent)
        assertEquals("Merhaba Antigravity!", assistantMsg.content)
        assertFalse(assistantMsg.isStreaming)
        assertEquals(1, assistantMsg.toolCalls.size)
        assertEquals(ToolCallStatus.COMPLETED, assistantMsg.toolCalls.first().status)
        assertEquals("""{"nodes":12}""", assistantMsg.toolCalls.first().outputPreview)
    }

    @Test
    fun `ACP SwarmUpdate and ArtifactUpdate populate swarm board, subagents, and diff approve-reject`() = runTest {
        advanceUntilIdle()

        val board = SwarmBoardState(
            swarmTitle = "Phase 1 Build",
            members = listOf(
                SwarmMemberInfo(
                    name = "worker-ui-overlay",
                    role = "UI & Overlay",
                    model = "Gemini 4 Neon",
                    status = SwarmMemberStatus.WORKING,
                    ownedModules = listOf("feature:workspace-ui"),
                ),
            ),
            messages = listOf(SwarmBoardMessage(seq = 1, sender = "coordinator", text = "Start phase 1")),
            edges = listOf(SwarmInteractionEdge("coordinator", "worker-ui-overlay", 2)),
        )
        acpEventsFlow.emit(AcpEvent.SwarmUpdate("sess-1", board))
        acpEventsFlow.emit(
            AcpEvent.ArtifactUpdate(
                sessionId = "sess-1",
                artifactId = "diff-1",
                title = "Main.kt",
                mimeType = "text/x-diff",
                filePath = "src/Main.kt",
                content = "@@ -1,2 +1,2 @@\n-val a = 1\n+val a = 2\n+val b = 3",
            ),
        )
        advanceUntilIdle()

        assertEquals("Phase 1 Build", viewModel.uiState.value.swarmBoardState.swarmTitle)
        assertEquals(1, viewModel.uiState.value.subagents.size)
        assertEquals("worker-ui-overlay", viewModel.uiState.value.subagents.first().name)

        val diff = viewModel.uiState.value.diffs.single()
        assertEquals(2, diff.additions)
        assertEquals(1, diff.deletions)
        assertEquals(DiffApprovalStatus.PENDING, diff.status)

        viewModel.onAction(WorkspaceAction.ApproveDiff("diff-1"))
        assertEquals(DiffApprovalStatus.APPROVED, viewModel.uiState.value.diffs.single().status)

        viewModel.onAction(WorkspaceAction.RejectDiff("diff-1"))
        assertEquals(DiffApprovalStatus.REJECTED, viewModel.uiState.value.diffs.single().status)
    }

    @Test
    fun `ACP AuthRequired -32000 shows banner and OAuth flow launches Chrome Custom Tabs and clears banner`() = runTest {
        coEvery { accountRepository.startLoginFlow("prof-1", "https://accounts.google.com/o/oauth2/auth") } returns
            Result.success(LoginChallenge(profileId = "prof-1", authUrl = "https://accounts.google.com/o/oauth2/auth"))
        coEvery { accountRepository.submitAuthCode("prof-1", "CODE123") } returns Result.success(primaryProfile)

        advanceUntilIdle()

        acpEventsFlow.emit(
            AcpEvent.AuthRequired(
                sessionId = "sess-1",
                errorCode = -32000,
                message = "Authentication required",
                authUrl = "https://accounts.google.com/o/oauth2/auth",
            ),
        )
        advanceUntilIdle()

        val banner = viewModel.uiState.value.authBanner
        assertNotNull(banner)
        assertEquals(-32000, banner?.errorCode)

        viewModel.onAction(WorkspaceAction.StartOAuthLogin("prof-1", banner?.authUrl))
        advanceUntilIdle()
        assertEquals("https://accounts.google.com/o/oauth2/auth", viewModel.uiState.value.pendingChromeCustomTabUrl)

        viewModel.onAction(WorkspaceAction.ConsumeChromeCustomTabUrl)
        assertNull(viewModel.uiState.value.pendingChromeCustomTabUrl)

        viewModel.onAction(WorkspaceAction.SubmitAuthCode("prof-1", "CODE123"))
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.authBanner)
    }

    @Test
    fun `ACP PermissionRequest routes response to AcpClient and clears pending approval`() = runTest {
        advanceUntilIdle()

        val perm = AcpEvent.PermissionRequest(
            sessionId = "sess-1",
            requestId = "perm-99",
            toolName = "root_exec",
            riskLevel = McpToolRisk.CRITICAL,
            description = "Run id command",
        )
        acpEventsFlow.emit(perm)
        advanceUntilIdle()

        assertEquals(perm, viewModel.uiState.value.pendingPermissionRequest)

        viewModel.onAction(WorkspaceAction.RespondToPermission("perm-99", approved = true, rememberForSession = false))
        advanceUntilIdle()

        coVerify {
            acpClient.respondToPermission(
                PermissionResponse(requestId = "perm-99", approved = true, rememberForSession = false),
            )
        }
        assertNull(viewModel.uiState.value.pendingPermissionRequest)
    }

    @Test
    fun `slash commands route plan, swarm, diff, agents, schedule, usage, and compact properly`() = runTest {
        coEvery { accountRepository.refreshUsage("prof-1") } returns Result.success(
            UsageQuota(
                planTier = AccountPlanTier.PRO,
                remainingPercent = 10.0,
                usedRequests = 90,
                limitRequests = 100,
            ),
        )
        advanceUntilIdle()

        // /plan
        viewModel.onAction(WorkspaceAction.SendMessage("/plan Yeni ekran tasarla"))
        advanceUntilIdle()
        assertEquals(PermissionMode.PLAN, viewModel.uiState.value.permissionMode)
        coVerify { securityPolicyEngine.setPermissionMode(PermissionMode.PLAN) }
        coVerify { acpClient.sendMessage("sess-1", "/plan Yeni ekran tasarla", emptyList()) }

        // /swarm
        viewModel.onAction(WorkspaceAction.SendMessage("/swarm"))
        assertTrue(viewModel.uiState.value.isOverviewSheetVisible)
        assertEquals(OverviewTab.SWARM, viewModel.uiState.value.selectedOverviewTab)

        // /diff
        viewModel.onAction(WorkspaceAction.SendMessage("/diff"))
        assertEquals(OverviewTab.ARTIFACTS_DIFFS, viewModel.uiState.value.selectedOverviewTab)

        // /agents
        viewModel.onAction(WorkspaceAction.SendMessage("/agents"))
        assertEquals(OverviewTab.SUBAGENTS, viewModel.uiState.value.selectedOverviewTab)

        // /schedule with cron
        val taskSlot = slot<ScheduledTask>()
        viewModel.onAction(WorkspaceAction.SendMessage("/schedule 0 9 * * * Sabah özeti hazırla"))
        advanceUntilIdle()
        coVerify { automationScheduler.scheduleTask(capture(taskSlot)) }
        assertEquals("0 9 * * *", taskSlot.captured.cronExpression)
        assertEquals("Sabah özeti hazırla", taskSlot.captured.prompt)

        // /usage triggers quota warning & fallback suggestion
        viewModel.onAction(WorkspaceAction.SendMessage("/usage"))
        advanceUntilIdle()
        val quotaWarning = viewModel.uiState.value.quotaWarning
        assertNotNull(quotaWarning)
        assertEquals("prof-2", quotaWarning?.suggestedFallbackProfile?.id)
        assertTrue(viewModel.uiState.value.messages.any { it.content.contains("10.0%") })

        // /compact trims message history when > 4 messages
        repeat(4) { idx ->
            viewModel.onAction(WorkspaceAction.SendMessage("Mesaj $idx"))
        }
        advanceUntilIdle()
        val beforeCompactCount = viewModel.uiState.value.messages.size
        assertTrue(beforeCompactCount > 5)

        viewModel.onAction(WorkspaceAction.SendMessage("/compact"))
        assertEquals(5, viewModel.uiState.value.messages.size)
        assertTrue(viewModel.uiState.value.messages.first().content.contains("/compact"))
    }

    @Test
    fun `account switching cancels current session and clears quota warning`() = runTest {
        val lowQuotaProfile = primaryProfile.copy(
            quota = UsageQuota(planTier = AccountPlanTier.FREE, remainingPercent = 5.0),
        )
        activeProfileFlow.value = lowQuotaProfile
        coEvery { accountRepository.switchActiveProfile("prof-2") } returns Result.success(backupProfile)
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.quotaWarning)

        viewModel.onAction(WorkspaceAction.SwitchProfile("prof-2"))
        advanceUntilIdle()

        coVerify { acpClient.cancelSession("sess-1") }
        coVerify { accountRepository.switchActiveProfile("prof-2") }
        assertEquals("prof-2", viewModel.uiState.value.activeProfile?.id)
        assertNull(viewModel.uiState.value.quotaWarning)
    }

    @Test
    fun `voice result appends transcript into composer and toggle calls transcriber`() = runTest {
        advanceUntilIdle()

        viewModel.onAction(WorkspaceAction.ToggleVoiceListening)
        advanceUntilIdle()
        coVerify { voiceTranscriber.startListening("tr-TR") }

        voiceStateFlow.value = VoiceState.Result(transcript = "Pil durumunu kontrol et", localeTag = "tr-TR")
        advanceUntilIdle()
        assertEquals("Pil durumunu kontrol et", viewModel.uiState.value.composerText)
    }

    @Test
    fun `onboarding detects KernelSU root, runs bootstrap, mounts chroot, and checks agy version`() = runTest {
        val rootEnv = RootEnvironmentInfo(
            isRootGranted = true,
            provider = RootProviderType.KERNEL_SU,
            suVersion = "KernelSU 1.0",
            mountMasterSupported = true,
        )
        coEvery { rootExecutor.detectEnvironment() } returns rootEnv
        coEvery {
            rootExecutor.bootstrapPermissions("com.antigravity.mobile", null, null)
        } returns BootstrapReport(
            packageName = "com.antigravity.mobile",
            environment = rootEnv,
            restrictedSettingsGranted = true,
            accessibilityEnabled = true,
            notificationListenerEnabled = true,
            batteryOptimizationExempted = true,
        )
        coEvery { chrootManager.ensureRootfsReady(any()) } returns Result.success(Unit)
        coEvery { chrootManager.mountAll() } returns Result.success(
            MountStatus(
                procMounted = true,
                sysMounted = true,
                devMounted = true,
                devPtsMounted = true,
                sdcardMounted = true,
                profilesMounted = true,
            ),
        )
        coEvery { chrootManager.getAgyVersion() } returns Result.success("1.4.11")
        advanceUntilIdle()

        viewModel.onAction(WorkspaceAction.CheckRootStatus)
        advanceUntilIdle()
        assertEquals(RootProviderType.KERNEL_SU, viewModel.uiState.value.onboarding.rootEnvironment?.provider)

        viewModel.onAction(WorkspaceAction.RunRootBootstrap("com.antigravity.mobile"))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.onboarding.bootstrapReport?.allCriticalReady == true)

        viewModel.onAction(WorkspaceAction.SelectRootfsSource(RootfsSourceOption.BUNDLED_ASSET))
        viewModel.onAction(WorkspaceAction.SetupChroot)
        advanceUntilIdle()
        coVerify { chrootManager.ensureRootfsReady(any()) }
        coVerify { chrootManager.mountAll() }

        viewModel.onAction(WorkspaceAction.CheckOrInstallAgy)
        advanceUntilIdle()
        assertEquals("1.4.11", viewModel.uiState.value.onboarding.agyVersion)
    }

    @Test
    fun `composer slash and mention menus activate on prefix typing`() = runTest {
        advanceUntilIdle()

        viewModel.onAction(WorkspaceAction.UpdateComposerText("/sw"))
        assertTrue(viewModel.uiState.value.showSlashMenu)
        assertEquals(1, viewModel.uiState.value.slashSuggestions.size)

        viewModel.onAction(WorkspaceAction.UpdateComposerText("Dosyayı oku @sd"))
        assertFalse(viewModel.uiState.value.showSlashMenu)
        assertTrue(viewModel.uiState.value.showMentionMenu)
        assertTrue(viewModel.uiState.value.mentionSuggestions.any { it.token == "@sdcard" })

        viewModel.onAction(
            WorkspaceAction.SelectMention(
                viewModel.uiState.value.mentionSuggestions.first { it.token == "@sdcard" },
            ),
        )
        assertEquals("Dosyayı oku @sdcard ", viewModel.uiState.value.composerText)
        assertFalse(viewModel.uiState.value.showMentionMenu)
    }

    @Test
    fun `emergency stop cancels voice, cancels session, and triggers security kill switch`() = runTest {
        advanceUntilIdle()

        viewModel.onAction(WorkspaceAction.SelectModel(AcpModel.ORCHESTRATOR))
        viewModel.onAction(WorkspaceAction.TriggerEmergencyStop)
        advanceUntilIdle()

        coVerify { voiceTranscriber.cancel() }
        coVerify { acpClient.cancelSession("sess-1") }
        coVerify { securityPolicyEngine.triggerEmergencyKillSwitch(any()) }
    }

    @Test
    fun `conversations CRUD supports create, auto-title, select across projects, rename, pin, and delete`() = runTest {
        advanceUntilIdle()

        // Initial default conversation is hydrated for proj-sdcard
        val initialConvId = viewModel.uiState.value.activeConversationId
        assertNotNull(initialConvId)
        assertEquals(WorkspaceConversation.DEFAULT_TITLE, viewModel.uiState.value.activeConversation?.title)

        // Sending first prompt auto-titles the default conversation and persists message
        viewModel.onAction(WorkspaceAction.SendMessage("Android 15 ayarlarını optimize et"))
        advanceUntilIdle()
        assertEquals("Android 15 ayarlarını optimize et", viewModel.uiState.value.activeConversation?.title)
        assertEquals(1, viewModel.uiState.value.activeConversation?.messages?.size)

        // Create a new conversation under another project cancels previous session and switches cleanly
        viewModel.onAction(WorkspaceAction.CreateConversation(projectId = "proj-projects", title = "Compose Refactor"))
        advanceUntilIdle()
        coVerify { acpClient.cancelSession("sess-1") }
        val secondConv = viewModel.uiState.value.activeConversation
        assertNotNull(secondConv)
        assertEquals("proj-projects", viewModel.uiState.value.activeProject.id)
        assertEquals("Compose Refactor", secondConv?.title)
        assertTrue(viewModel.uiState.value.messages.isEmpty())
        assertTrue("proj-projects" in viewModel.uiState.value.expandedProjectIds)

        // Rename & pin conversation
        viewModel.onAction(WorkspaceAction.RenameConversation(secondConv!!.id, "UI Mimari Yükseltmesi"))
        viewModel.onAction(WorkspaceAction.TogglePinConversation(secondConv.id))
        assertEquals("UI Mimari Yükseltmesi", viewModel.uiState.value.activeConversation?.title)
        assertTrue(viewModel.uiState.value.activeConversation?.isPinned == true)

        // Selecting initial conversation switches activeProject back and restores its messages
        viewModel.onAction(WorkspaceAction.SelectConversation(initialConvId!!))
        advanceUntilIdle()
        assertEquals("proj-sdcard", viewModel.uiState.value.activeProject.id)
        assertEquals(initialConvId, viewModel.uiState.value.activeConversationId)
        assertEquals(1, viewModel.uiState.value.messages.size)

        // Deleting active conversation when it's the only one in proj-sdcard creates a fresh empty one
        viewModel.onAction(WorkspaceAction.DeleteConversation(initialConvId))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.conversations.none { it.id == initialConvId })
        assertNotNull(viewModel.uiState.value.activeConversationId)
        assertTrue(viewModel.uiState.value.messages.isEmpty())
        assertEquals("proj-sdcard", viewModel.uiState.value.activeConversation?.projectId)

        // Deleting secondConv (non-active) removes it cleanly without changing activeConversationId
        val activeBefore = viewModel.uiState.value.activeConversationId
        viewModel.onAction(WorkspaceAction.DeleteConversation(secondConv.id))
        advanceUntilIdle()
        assertEquals(activeBefore, viewModel.uiState.value.activeConversationId)
        assertTrue(viewModel.uiState.value.conversations.none { it.id == secondConv.id })
    }

    @Test
    fun `projects CRUD supports create, rename, expand toggle, search, and cascading delete`() = runTest {
        advanceUntilIdle()

        viewModel.onAction(
            WorkspaceAction.CreateProject(
                name = "KernelSU Modülü",
                rootPath = "/mnt/sdcard/Projects/ksu",
                defaultPermissionMode = PermissionMode.PLAN,
            ),
        )
        advanceUntilIdle()

        val createdProject = viewModel.uiState.value.activeProject
        assertEquals("KernelSU Modülü", createdProject.name)
        assertTrue(createdProject.id in viewModel.uiState.value.expandedProjectIds)

        // Add another conversation under createdProject
        viewModel.onAction(WorkspaceAction.CreateConversation(createdProject.id, "Hook Tasarımı"))
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.conversations.count { it.projectId == createdProject.id })

        // Rename project
        viewModel.onAction(WorkspaceAction.RenameProject(createdProject.id, "KernelSU Pro"))
        assertEquals("KernelSU Pro", viewModel.uiState.value.activeProject.name)

        // Toggle expand & sidebar search
        viewModel.onAction(WorkspaceAction.ToggleProjectExpanded(createdProject.id))
        assertFalse(createdProject.id in viewModel.uiState.value.expandedProjectIds)
        viewModel.onAction(WorkspaceAction.UpdateSidebarSearch("Hook"))
        assertEquals("Hook", viewModel.uiState.value.sidebarSearchQuery)

        // Delete project cascades and removes all its conversations, switching to another remaining project
        viewModel.onAction(WorkspaceAction.DeleteProject(createdProject.id))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.projects.none { it.id == createdProject.id })
        assertTrue(viewModel.uiState.value.conversations.none { it.projectId == createdProject.id })
        assertTrue(viewModel.uiState.value.activeProject.id != createdProject.id)
        assertNotNull(viewModel.uiState.value.activeConversationId)
    }

    @Test
    fun `settings, local persistence round-trip, and onboarding back-navigation work seamlessly`() = runTest {
        val tempDir = Files.createTempDirectory("ag_workspace_store_test").toFile()
        try {
            val storeFile = File(tempDir, "workspace_state.json")
            val fileStore = FileWorkspaceLocalStore(file = storeFile, ioDispatcher = testDispatcher)
            val persistentVm = WorkspaceViewModel(
                accountRepository = accountRepository,
                acpClient = acpClient,
                androidMcpServer = androidMcpServer,
                automationScheduler = automationScheduler,
                securityPolicyEngine = securityPolicyEngine,
                voiceTranscriber = voiceTranscriber,
                chrootManager = chrootManager,
                rootExecutor = rootExecutor,
                localStore = fileStore,
            )
            advanceUntilIdle()

            persistentVm.onAction(WorkspaceAction.OpenSettings(SettingsCategory.MODELS_AGENT))
            assertTrue(persistentVm.uiState.value.showSettings)
            assertEquals(SettingsCategory.MODELS_AGENT, persistentVm.uiState.value.selectedSettingsCategory)

            persistentVm.onAction(
                WorkspaceAction.UpdateSettings {
                    it.copy(
                        themeMode = AppThemeMode.LIGHT,
                        useDynamicColor = true,
                        layoutMode = InterfaceLayoutMode.COMPACT_MOBILE,
                        acpSandboxEnabled = true,
                        overlayOpacity = 0.75f,
                    )
                },
            )
            advanceUntilIdle()

            // Open onboarding from Settings -> DismissOnboarding returns to Settings
            persistentVm.onAction(WorkspaceAction.OpenOnboardingFromSettings(fromSettings = true))
            assertTrue(persistentVm.uiState.value.showOnboarding)
            assertFalse(persistentVm.uiState.value.showSettings)
            assertTrue(persistentVm.uiState.value.onboardingReturnToSettings)

            persistentVm.onAction(WorkspaceAction.DismissOnboarding)
            assertFalse(persistentVm.uiState.value.showOnboarding)
            assertTrue(persistentVm.uiState.value.showSettings)
            assertFalse(persistentVm.uiState.value.onboardingReturnToSettings)

            persistentVm.onAction(WorkspaceAction.CloseSettings)
            assertFalse(persistentVm.uiState.value.showSettings)

            // Re-hydrate a second ViewModel from the same file store and verify settings survived
            val rehydratedVm = WorkspaceViewModel(
                accountRepository = accountRepository,
                acpClient = acpClient,
                androidMcpServer = androidMcpServer,
                automationScheduler = automationScheduler,
                securityPolicyEngine = securityPolicyEngine,
                voiceTranscriber = voiceTranscriber,
                chrootManager = chrootManager,
                rootExecutor = rootExecutor,
                localStore = fileStore,
            )
            advanceUntilIdle()

            assertEquals(AppThemeMode.LIGHT, rehydratedVm.uiState.value.settings.themeMode)
            assertTrue(rehydratedVm.uiState.value.settings.useDynamicColor)
            assertEquals(InterfaceLayoutMode.COMPACT_MOBILE, rehydratedVm.uiState.value.settings.layoutMode)
            assertTrue(rehydratedVm.uiState.value.settings.acpSandboxEnabled)
            assertEquals(0.75f, rehydratedVm.uiState.value.settings.overlayOpacity, 0.001f)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `quota sheet opens, refreshes all authenticated profiles, and auto-refreshes unfetched active profile`() = runTest {
        coEvery { accountRepository.refreshUsage("prof-1") } returns Result.success(
            UsageQuota(planTier = AccountPlanTier.PRO, remainingPercent = 82.5, updatedAtEpochMs = 1_000L),
        )
        coEvery { accountRepository.refreshUsage("prof-2") } returns Result.success(
            UsageQuota(planTier = AccountPlanTier.ULTRA, remainingPercent = 97.0, updatedAtEpochMs = 1_000L),
        )
        advanceUntilIdle()

        viewModel.onAction(WorkspaceAction.OpenQuotaSheet)
        assertTrue(viewModel.uiState.value.showQuotaSheet)

        viewModel.onAction(WorkspaceAction.RefreshAllQuotas)
        advanceUntilIdle()

        coVerify(exactly = 1) { accountRepository.refreshUsage("prof-1") }
        coVerify(exactly = 1) { accountRepository.refreshUsage("prof-2") }
        assertFalse(viewModel.uiState.value.isRefreshingQuota)
        assertEquals(82.5, viewModel.uiState.value.activeProfile?.quota?.remainingPercent ?: 0.0, 0.01)
        assertEquals(
            97.0,
            viewModel.uiState.value.profiles.first { it.id == "prof-2" }.quota.remainingPercent ?: 0.0,
            0.01,
        )

        viewModel.onAction(WorkspaceAction.CloseQuotaSheet)
        assertFalse(viewModel.uiState.value.showQuotaSheet)

        // When active profile has unfetched quota (remainingPercent == null && updatedAtEpochMs == 0L),
        // opening quota sheet auto-refreshes it
        val unfetchedProfile = primaryProfile.copy(quota = UsageQuota())
        profilesFlow.value = listOf(unfetchedProfile, backupProfile)
        activeProfileFlow.value = unfetchedProfile
        advanceUntilIdle()

        viewModel.onAction(WorkspaceAction.OpenQuotaSheet)
        advanceUntilIdle()
        coVerify(atLeast = 2) { accountRepository.refreshUsage("prof-1") }
    }

    @Test
    fun `deleteProfile and unmountChroot delegate to repositories and update state`() = runTest {
        coEvery { accountRepository.deleteProfile("prof-2") } returns Result.success(Unit)
        coEvery { chrootManager.unmountSafely(force = false) } returns Result.success(MountStatus())
        advanceUntilIdle()

        viewModel.onAction(WorkspaceAction.DeleteProfile("prof-2"))
        advanceUntilIdle()
        coVerify(exactly = 1) { accountRepository.deleteProfile("prof-2") }
        assertTrue(viewModel.uiState.value.profiles.none { it.id == "prof-2" })

        viewModel.onAction(WorkspaceAction.UnmountChroot)
        advanceUntilIdle()
        coVerify(exactly = 1) { chrootManager.unmountSafely(force = false) }
        assertFalse(viewModel.uiState.value.onboarding.isBusy)
        assertNull(viewModel.uiState.value.onboarding.errorMessage)
        assertNotNull(viewModel.uiState.value.onboarding.statusMessage)
    }
}
