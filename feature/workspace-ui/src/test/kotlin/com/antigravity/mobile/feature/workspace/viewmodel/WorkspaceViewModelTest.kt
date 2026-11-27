package com.antigravity.mobile.feature.workspace.viewmodel

import com.antigravity.mobile.contracts.AccountPlanTier
import com.antigravity.mobile.contracts.AccountProfile
import com.antigravity.mobile.contracts.AccountRepository
import com.antigravity.mobile.contracts.AcpClient
import com.antigravity.mobile.contracts.AcpEvent
import com.antigravity.mobile.contracts.AcpModel
import com.antigravity.mobile.contracts.AcpSessionConfig
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
import com.antigravity.mobile.contracts.RootfsSource
import com.antigravity.mobile.contracts.ScheduledTask
import com.antigravity.mobile.contracts.ScheduledTaskRun
import com.antigravity.mobile.contracts.SecurityPolicyEngine
import com.antigravity.mobile.contracts.ShellResult
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
import com.antigravity.mobile.feature.workspace.model.AppDestination
import com.antigravity.mobile.feature.workspace.model.AppThemeMode
import com.antigravity.mobile.feature.workspace.model.DEVICE_FALLBACK_PROJECT
import com.antigravity.mobile.feature.workspace.model.InterfaceLayoutMode
import com.antigravity.mobile.feature.workspace.model.OverviewTab
import com.antigravity.mobile.feature.workspace.model.QuotaBucketParser
import com.antigravity.mobile.feature.workspace.model.QuotaWindowKind
import com.antigravity.mobile.feature.workspace.model.RootfsSourceOption
import com.antigravity.mobile.feature.workspace.model.SettingsCategory
import com.antigravity.mobile.feature.workspace.model.WorkspaceAction
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import java.time.Instant
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
    fun `conversations CRUD supports draft creation, auto-title, select across projects, rename, pin, and genuine delete`() = runTest {
        advanceUntilIdle()

        // First-ever launch starts clean on Hub with 0 phantom conversations
        assertEquals(AppDestination.Hub, viewModel.uiState.value.currentDestination)
        assertTrue(viewModel.uiState.value.conversations.isEmpty())
        assertNull(viewModel.uiState.value.activeConversationId)

        // CreateConversation without explicit title opens a fresh draft without cluttering conversations
        viewModel.onAction(WorkspaceAction.CreateConversation(projectId = "proj-sdcard"))
        advanceUntilIdle()
        assertEquals(AppDestination.Conversation(null), viewModel.uiState.value.currentDestination)
        assertNull(viewModel.uiState.value.activeConversationId)
        assertTrue(viewModel.uiState.value.conversations.isEmpty())

        // Sending first prompt materializes and auto-titles the conversation and updates destination
        viewModel.onAction(WorkspaceAction.SendMessage("Android 15 ayarlarını optimize et"))
        advanceUntilIdle()
        val firstConvId = viewModel.uiState.value.activeConversationId
        assertNotNull(firstConvId)
        assertEquals(AppDestination.Conversation(firstConvId), viewModel.uiState.value.currentDestination)
        assertEquals("Android 15 ayarlarını optimize et", viewModel.uiState.value.activeConversation?.title)
        assertEquals(1, viewModel.uiState.value.activeConversation?.messages?.size)

        // Create a second project and create an explicitly titled conversation under it
        viewModel.onAction(
            WorkspaceAction.CreateProject(
                name = "Projeler",
                rootPath = "/mnt/sdcard/Projects",
            ),
        )
        advanceUntilIdle()
        val secondProjectId = viewModel.uiState.value.selectedProjectId!!
        viewModel.onAction(WorkspaceAction.CreateConversation(projectId = secondProjectId, title = "Compose Refactor"))
        advanceUntilIdle()
        coVerify { acpClient.cancelSession("sess-1") }
        val secondConv = viewModel.uiState.value.activeConversation
        assertNotNull(secondConv)
        assertEquals(secondProjectId, viewModel.uiState.value.activeProject.id)
        assertEquals("Compose Refactor", secondConv?.title)
        assertTrue(viewModel.uiState.value.messages.isEmpty())
        assertTrue(secondProjectId in viewModel.uiState.value.expandedProjectIds)

        // Rename & pin conversation
        viewModel.onAction(WorkspaceAction.RenameConversation(secondConv!!.id, "UI Mimari Yükseltmesi"))
        viewModel.onAction(WorkspaceAction.TogglePinConversation(secondConv.id))
        assertEquals("UI Mimari Yükseltmesi", viewModel.uiState.value.activeConversation?.title)
        assertTrue(viewModel.uiState.value.activeConversation?.isPinned == true)

        // Selecting first conversation switches selectedProject back and restores its messages
        viewModel.onAction(WorkspaceAction.SelectConversation(firstConvId!!))
        advanceUntilIdle()
        assertEquals("proj-sdcard", viewModel.uiState.value.activeProject.id)
        assertEquals(firstConvId, viewModel.uiState.value.activeConversationId)
        assertEquals(1, viewModel.uiState.value.messages.size)

        // Deleting active conversation when it's the only one in proj-sdcard leaves NO phantom row and returns to Hub
        viewModel.onAction(WorkspaceAction.DeleteConversation(firstConvId))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.conversations.none { it.id == firstConvId })
        assertNull(viewModel.uiState.value.activeConversationId)
        assertTrue(viewModel.uiState.value.messages.isEmpty())
        assertEquals(AppDestination.Hub, viewModel.uiState.value.currentDestination)

        // Deleting the last remaining conversation leaves conversations genuinely empty
        viewModel.onAction(WorkspaceAction.DeleteConversation(secondConv.id))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.conversations.isEmpty())
        assertNull(viewModel.uiState.value.activeConversationId)
    }

    @Test
    fun `projects CRUD supports create, rename, expand toggle, search, cascading delete, and genuine empty state`() = runTest {
        advanceUntilIdle()

        viewModel.onAction(
            WorkspaceAction.CreateProject(
                name = "KernelSU Modülü",
                rootPath = "/mnt/sdcard/Projects/ksu",
                defaultPermissionMode = PermissionMode.PLAN,
            ),
        )
        advanceUntilIdle()

        val createdProject = viewModel.uiState.value.selectedProject!!
        assertEquals("KernelSU Modülü", createdProject.name)
        assertTrue(createdProject.id in viewModel.uiState.value.expandedProjectIds)
        assertTrue(viewModel.uiState.value.conversations.isEmpty())

        // Add two conversations under createdProject
        viewModel.onAction(WorkspaceAction.CreateConversation(createdProject.id, "Hook Tasarımı"))
        viewModel.onAction(WorkspaceAction.CreateConversation(createdProject.id, "SELinux Analizi"))
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

        // Delete createdProject cascades its conversations and switches cleanly to remaining proj-sdcard without phantom chat
        viewModel.onAction(WorkspaceAction.DeleteProject(createdProject.id))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.projects.none { it.id == createdProject.id })
        assertTrue(viewModel.uiState.value.conversations.none { it.projectId == createdProject.id })
        assertEquals("proj-sdcard", viewModel.uiState.value.selectedProjectId)
        assertNull(viewModel.uiState.value.activeConversationId)

        // Deleting the last project leaves projects genuinely empty and selectedProject null
        viewModel.onAction(WorkspaceAction.DeleteProject("proj-sdcard"))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.projects.isEmpty())
        assertNull(viewModel.uiState.value.selectedProjectId)
        assertNull(viewModel.uiState.value.selectedProject)
        assertNull(viewModel.uiState.value.activeConversationId)
        assertTrue(viewModel.uiState.value.messages.isEmpty())
        assertEquals(DEVICE_FALLBACK_PROJECT, viewModel.uiState.value.activeProject)
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

    @Test
    fun `navigation back-stack unwinds menus, sheets, Onboarding, SettingsDetail, SettingsList, Conversation to Hub`() = runTest {
        advanceUntilIdle()

        assertEquals(listOf(AppDestination.Hub), viewModel.uiState.value.backStack)
        assertEquals(AppDestination.Hub, viewModel.uiState.value.currentDestination)

        // Hub -> Conversation -> SettingsList -> SettingsDetail(MOBILE_ROOT) -> Onboarding
        viewModel.onAction(WorkspaceAction.NavigateToConversation(null))
        assertEquals(
            listOf(AppDestination.Hub, AppDestination.Conversation(null)),
            viewModel.uiState.value.backStack,
        )

        viewModel.onAction(WorkspaceAction.OpenSettingsList)
        assertEquals(AppDestination.SettingsList, viewModel.uiState.value.currentDestination)
        assertTrue(viewModel.uiState.value.showSettings)

        viewModel.onAction(WorkspaceAction.OpenSettingsDetail(SettingsCategory.MOBILE_ROOT))
        assertEquals(
            AppDestination.SettingsDetail(SettingsCategory.MOBILE_ROOT),
            viewModel.uiState.value.currentDestination,
        )
        assertEquals(SettingsCategory.MOBILE_ROOT, viewModel.uiState.value.selectedSettingsCategory)

        viewModel.onAction(WorkspaceAction.OpenOnboardingFromSettings(fromSettings = true))
        assertEquals(AppDestination.Onboarding, viewModel.uiState.value.currentDestination)
        assertTrue(viewModel.uiState.value.showOnboarding)
        assertFalse(viewModel.uiState.value.showSettings)

        // Back unwinds Onboarding -> SettingsDetail(MOBILE_ROOT) -> SettingsList -> Conversation
        viewModel.onAction(WorkspaceAction.NavigateBack)
        assertEquals(
            AppDestination.SettingsDetail(SettingsCategory.MOBILE_ROOT),
            viewModel.uiState.value.currentDestination,
        )
        assertFalse(viewModel.uiState.value.showOnboarding)
        assertTrue(viewModel.uiState.value.showSettings)

        viewModel.onAction(WorkspaceAction.NavigateBack)
        assertEquals(AppDestination.SettingsList, viewModel.uiState.value.currentDestination)
        assertTrue(viewModel.uiState.value.showSettings)

        viewModel.onAction(WorkspaceAction.NavigateBack)
        assertEquals(AppDestination.Conversation(null), viewModel.uiState.value.currentDestination)
        assertFalse(viewModel.uiState.value.showSettings)

        // Open overlays/menus on Conversation and verify exact dismissal priority
        viewModel.onAction(WorkspaceAction.OpenOverviewSheet(OverviewTab.SWARM))
        viewModel.onAction(WorkspaceAction.OpenQuotaSheet)
        viewModel.onAction(WorkspaceAction.OpenComposerToolsSheet)
        viewModel.onAction(WorkspaceAction.OpenModelPickerSheet)
        // Re-open slash menu on top to verify #1 priority
        viewModel.onAction(WorkspaceAction.OpenSlashMenu)
        // Also ensure composer tools sheet is active along with model picker for full chain test
        // (OpenModelPickerSheet closed tools sheet, so test tools sheet separately right after)
        viewModel.onAction(WorkspaceAction.NavigateBack) // 1. slash menu
        assertFalse(viewModel.uiState.value.showSlashMenu)
        assertTrue(viewModel.uiState.value.showModelPickerSheet)

        viewModel.onAction(WorkspaceAction.NavigateBack) // 2. model picker sheet
        assertFalse(viewModel.uiState.value.showModelPickerSheet)

        viewModel.onAction(WorkspaceAction.OpenComposerToolsSheet)
        assertTrue(viewModel.uiState.value.showComposerToolsSheet)
        viewModel.onAction(WorkspaceAction.NavigateBack) // 3. composer tools sheet
        assertFalse(viewModel.uiState.value.showComposerToolsSheet)
        assertTrue(viewModel.uiState.value.showQuotaSheet)

        viewModel.onAction(WorkspaceAction.NavigateBack) // 4. quota sheet
        assertFalse(viewModel.uiState.value.showQuotaSheet)
        assertTrue(viewModel.uiState.value.isOverviewSheetVisible)

        viewModel.onAction(WorkspaceAction.NavigateBack) // 5. overview sheet
        assertFalse(viewModel.uiState.value.isOverviewSheetVisible)
        assertEquals(AppDestination.Conversation(null), viewModel.uiState.value.currentDestination)

        // 6. Back from Conversation returns to Hub (NEVER exits app!)
        viewModel.onAction(WorkspaceAction.NavigateBack)
        assertEquals(AppDestination.Hub, viewModel.uiState.value.currentDestination)
        assertFalse(viewModel.uiState.value.shouldExitApp)
        assertFalse(viewModel.uiState.value.exitConfirmPending)

        // Hub -> Onboarding -> Back returns to Hub
        viewModel.onAction(WorkspaceAction.OpenOnboardingFromSettings(fromSettings = false))
        assertEquals(AppDestination.Onboarding, viewModel.uiState.value.currentDestination)
        viewModel.onAction(WorkspaceAction.NavigateBack)
        assertEquals(AppDestination.Hub, viewModel.uiState.value.currentDestination)
    }

    @Test
    fun `double-back-to-exit on Hub exits within 2000ms window and resets after timeout`() = runTest {
        var fakeNow = 10_000L
        val timedVm = WorkspaceViewModel(
            accountRepository = accountRepository,
            acpClient = acpClient,
            androidMcpServer = androidMcpServer,
            automationScheduler = automationScheduler,
            securityPolicyEngine = securityPolicyEngine,
            voiceTranscriber = voiceTranscriber,
            chrootManager = chrootManager,
            rootExecutor = rootExecutor,
            clockMs = { fakeNow },
        )
        advanceUntilIdle()

        // First back press on Hub arms confirmation
        timedVm.onAction(WorkspaceAction.NavigateBack)
        assertTrue(timedVm.uiState.value.exitConfirmPending)
        assertEquals(WorkspaceViewModel.EXIT_CONFIRM_MESSAGE, timedVm.uiState.value.exitConfirmMessage)
        assertFalse(timedVm.uiState.value.shouldExitApp)

        // Second press after > 2000ms does NOT exit; re-arms confirmation
        fakeNow = 12_500L
        timedVm.onAction(WorkspaceAction.NavigateBack)
        assertTrue(timedVm.uiState.value.exitConfirmPending)
        assertFalse(timedVm.uiState.value.shouldExitApp)

        // DismissExitConfirm clears prompt cleanly
        timedVm.onAction(WorkspaceAction.DismissExitConfirm)
        assertFalse(timedVm.uiState.value.exitConfirmPending)
        assertNull(timedVm.uiState.value.exitConfirmMessage)

        // Re-arm and press again within 2000ms -> shouldExitApp becomes true
        fakeNow = 20_000L
        timedVm.onAction(WorkspaceAction.NavigateBack)
        assertTrue(timedVm.uiState.value.exitConfirmPending)
        fakeNow = 21_500L
        timedVm.onAction(WorkspaceAction.NavigateBack)
        assertTrue(timedVm.uiState.value.shouldExitApp)
        assertFalse(timedVm.uiState.value.exitConfirmPending)
        assertNull(timedVm.uiState.value.exitConfirmMessage)

        timedVm.onAction(WorkspaceAction.ConsumeExitApp)
        assertFalse(timedVm.uiState.value.shouldExitApp)
    }

    @Test
    fun `deleting all projects persists empty projects and conversations across reloads without re-seeding`() = runTest {
        val tempDir = Files.createTempDirectory("ag_empty_projects_persist_test").toFile()
        try {
            val storeFile = File(tempDir, "workspace_state.json")
            val fileStore = FileWorkspaceLocalStore(file = storeFile, ioDispatcher = testDispatcher)

            val firstVm = WorkspaceViewModel(
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

            // First-ever launch seeds 1 starter project and 0 conversations
            assertEquals(1, firstVm.uiState.value.projects.size)
            assertEquals("proj-sdcard", firstVm.uiState.value.selectedProjectId)
            assertTrue(firstVm.uiState.value.conversations.isEmpty())

            // Delete the starter project
            firstVm.onAction(WorkspaceAction.DeleteProject("proj-sdcard"))
            advanceUntilIdle()
            assertTrue(firstVm.uiState.value.projects.isEmpty())
            assertNull(firstVm.uiState.value.selectedProject)

            // Reload into a brand-new ViewModel from the same persisted file -> MUST stay empty!
            val reloadedVm = WorkspaceViewModel(
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

            assertTrue(reloadedVm.uiState.value.projects.isEmpty())
            assertNull(reloadedVm.uiState.value.selectedProjectId)
            assertNull(reloadedVm.uiState.value.selectedProject)
            assertTrue(reloadedVm.uiState.value.conversations.isEmpty())
            assertNull(reloadedVm.uiState.value.activeConversationId)
            assertEquals(DEVICE_FALLBACK_PROJECT, reloadedVm.uiState.value.activeProject)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `QuotaBucketParser parses all 4 real TSV rows, treats 1970 epoch as null, and prefers 5h headline`() {
        val realTsv = """
            Gemini Models	Weekly Limit Remaining	52%	2026-12-01T08:19:42Z
            Gemini Models	Five Hour Limit Remaining	91%	2026-11-25T17:30:55Z
            Claude and GPT models	Weekly Limit Remaining	50%	2026-12-01T11:11:27Z
            Claude and GPT models	Five Hour Limit Remaining	100%	1970-01-01T00:00:00Z
        """.trimIndent()

        val quota = UsageQuota(
            planTier = AccountPlanTier.PRO,
            remainingPercent = 52.0,
            rawSummary = realTsv,
        )

        val buckets = QuotaBucketParser.parse(quota)
        assertEquals(4, buckets.size)

        val geminiWeekly = buckets[0]
        assertEquals("Gemini Modelleri", geminiWeekly.displayGroup)
        assertEquals(QuotaWindowKind.WEEKLY, geminiWeekly.windowKind)
        assertEquals("Haftalık Limit", geminiWeekly.windowLabel)
        assertEquals(52.0, geminiWeekly.remainingPercent, 0.001)
        assertEquals(Instant.parse("2026-12-01T08:19:42Z").toEpochMilli(), geminiWeekly.resetAtEpochMs)

        val geminiFiveHour = buckets[1]
        assertEquals("Gemini Modelleri", geminiFiveHour.displayGroup)
        assertEquals(QuotaWindowKind.FIVE_HOUR, geminiFiveHour.windowKind)
        assertEquals("5 Saatlik Limit", geminiFiveHour.windowLabel)
        assertEquals(91.0, geminiFiveHour.remainingPercent, 0.001)
        assertEquals(Instant.parse("2026-11-25T17:30:55Z").toEpochMilli(), geminiFiveHour.resetAtEpochMs)

        val claudeWeekly = buckets[2]
        assertEquals("Claude & GPT", claudeWeekly.displayGroup)
        assertEquals(QuotaWindowKind.WEEKLY, claudeWeekly.windowKind)
        assertEquals(50.0, claudeWeekly.remainingPercent, 0.001)

        val claudeFiveHour = buckets[3]
        assertEquals("Claude & GPT", claudeFiveHour.displayGroup)
        assertEquals(QuotaWindowKind.FIVE_HOUR, claudeFiveHour.windowKind)
        assertEquals(100.0, claudeFiveHour.remainingPercent, 0.001)
        assertNull(claudeFiveHour.resetAtEpochMs)
        assertTrue(claudeFiveHour.isFullUnused)

        // Primary headline MUST return Gemini 5-hour (91.0), NOT Gemini weekly (52.0)
        assertEquals(91.0, QuotaBucketParser.primaryHeadlinePercent(quota, AcpModel.GEMINI_4_NEON)!!, 0.001)
        assertEquals(52.0, QuotaBucketParser.weeklyPercent(quota, AcpModel.GEMINI_4_NEON)!!, 0.001)

        // Fallback to remainingPercent when rawSummary is empty
        val simpleQuota = UsageQuota(planTier = AccountPlanTier.FREE, remainingPercent = 73.5)
        assertEquals(73.5, QuotaBucketParser.primaryHeadlinePercent(simpleQuota, AcpModel.GEMINI_4_NEON)!!, 0.001)
        assertNull(QuotaBucketParser.weeklyPercent(simpleQuota, AcpModel.GEMINI_4_NEON))
    }

    @Test
    fun `ensureActiveSession starts fresh session on ERROR, workspace change, or profile change and stores sessionId`() = runTest {
        advanceUntilIdle()

        // 1. Previous session in ERROR -> must NOT reuse; must call startSession and persist new sessionId
        val freshAfterError = AcpSessionInfo(
            sessionId = "sess-recovered",
            profileId = "prof-1",
            workspaceDir = "/mnt/sdcard",
            state = AcpSessionState.READY,
        )
        acpSessionStateFlow.value = AcpSessionState.ERROR
        coEvery { acpClient.startSession(any()) } answers {
            activeSessionFlow.value = freshAfterError
            acpSessionStateFlow.value = AcpSessionState.READY
            Result.success(freshAfterError)
        }
        advanceUntilIdle()

        viewModel.onAction(WorkspaceAction.SendMessage("Hata sonrası yeni oturum başlat"))
        advanceUntilIdle()

        coVerify(exactly = 1) { acpClient.startSession(any()) }
        coVerify { acpClient.sendMessage("sess-recovered", "Hata sonrası yeni oturum başlat", emptyList()) }
        assertEquals("sess-recovered", viewModel.uiState.value.activeConversation?.sessionId)

        // 2. Switching workspace directory -> must NOT reuse sess-recovered; must start fresh session for new workspaceDir
        viewModel.onAction(
            WorkspaceAction.CreateProject(
                name = "App Workspace",
                rootPath = "/mnt/sdcard/Projects/app",
            ),
        )
        advanceUntilIdle()

        val workspaceConfigSlot = slot<AcpSessionConfig>()
        val freshForWorkspace = AcpSessionInfo(
            sessionId = "sess-workspace-2",
            profileId = "prof-1",
            workspaceDir = "/mnt/sdcard/Projects/app",
            state = AcpSessionState.READY,
        )
        coEvery { acpClient.startSession(capture(workspaceConfigSlot)) } answers {
            activeSessionFlow.value = freshForWorkspace
            acpSessionStateFlow.value = AcpSessionState.READY
            Result.success(freshForWorkspace)
        }

        viewModel.onAction(WorkspaceAction.SendMessage("Yeni proje dizininde analiz yap"))
        advanceUntilIdle()

        assertEquals("/mnt/sdcard/Projects/app", workspaceConfigSlot.captured.workspaceDir)
        coVerify { acpClient.sendMessage("sess-workspace-2", "Yeni proje dizininde analiz yap", emptyList()) }
        assertEquals("sess-workspace-2", viewModel.uiState.value.activeConversation?.sessionId)

        // 3. Switching active profile -> must NOT reuse sess-workspace-2 (belongs to prof-1)
        activeProfileFlow.value = backupProfile
        advanceUntilIdle()

        val profileConfigSlot = slot<AcpSessionConfig>()
        val freshForProfile = AcpSessionInfo(
            sessionId = "sess-prof-2",
            profileId = "prof-2",
            workspaceDir = "/mnt/sdcard/Projects/app",
            state = AcpSessionState.READY,
        )
        coEvery { acpClient.startSession(capture(profileConfigSlot)) } answers {
            activeSessionFlow.value = freshForProfile
            acpSessionStateFlow.value = AcpSessionState.READY
            Result.success(freshForProfile)
        }

        viewModel.onAction(WorkspaceAction.SendMessage("Yedek profil ile devam et"))
        advanceUntilIdle()

        assertEquals("prof-2", profileConfigSlot.captured.profileId)
        coVerify { acpClient.sendMessage("sess-prof-2", "Yedek profil ile devam et", emptyList()) }
        assertEquals("sess-prof-2", viewModel.uiState.value.activeConversation?.sessionId)

        // 4. AWAITING_AUTH state -> must NOT reuse; must start fresh session
        acpSessionStateFlow.value = AcpSessionState.AWAITING_AUTH
        advanceUntilIdle()
        val freshAfterAuth = AcpSessionInfo(
            sessionId = "sess-post-auth",
            profileId = "prof-2",
            workspaceDir = "/mnt/sdcard/Projects/app",
            state = AcpSessionState.READY,
        )
        coEvery { acpClient.startSession(any()) } answers {
            activeSessionFlow.value = freshAfterAuth
            acpSessionStateFlow.value = AcpSessionState.READY
            Result.success(freshAfterAuth)
        }
        viewModel.onAction(WorkspaceAction.SendMessage("Auth sonrası devam"))
        advanceUntilIdle()
        coVerify { acpClient.sendMessage("sess-post-auth", "Auth sonrası devam", emptyList()) }
        assertEquals("sess-post-auth", viewModel.uiState.value.activeConversation?.sessionId)

        // 5. Conversation sessionId mismatch -> must NOT reuse existing session; must start/resume conversation's sessionId
        val mismatchedExisting = AcpSessionInfo(
            sessionId = "sess-unrelated",
            profileId = "prof-2",
            workspaceDir = "/mnt/sdcard/Projects/app",
            state = AcpSessionState.READY,
        )
        activeSessionFlow.value = mismatchedExisting
        acpSessionStateFlow.value = AcpSessionState.READY
        advanceUntilIdle()

        val mismatchSlot = slot<AcpSessionConfig>()
        val resumedSession = AcpSessionInfo(
            sessionId = "sess-post-auth",
            profileId = "prof-2",
            workspaceDir = "/mnt/sdcard/Projects/app",
            state = AcpSessionState.READY,
        )
        coEvery { acpClient.startSession(capture(mismatchSlot)) } answers {
            activeSessionFlow.value = resumedSession
            Result.success(resumedSession)
        }
        viewModel.onAction(WorkspaceAction.SendMessage("Mevcut sohbet oturumuna dön"))
        advanceUntilIdle()
        assertEquals("sess-post-auth", mismatchSlot.captured.resumeConversationId)
        coVerify { acpClient.sendMessage("sess-post-auth", "Mevcut sohbet oturumuna dön", emptyList()) }
    }

    @Test
    fun `permission request shows card when fullAccessEnabled is false`() = runTest {
        advanceUntilIdle()

        val request = AcpEvent.PermissionRequest(
            sessionId = "sess-1",
            requestId = "perm-ask-1",
            toolName = "ls -la /mnt/sdcard\nuname -a",
            riskLevel = McpToolRisk.HIGH,
            description = "Run shell inspection",
            argumentsJson = """{"cmd":"uname -a"}""",
        )
        acpEventsFlow.emit(request)
        advanceUntilIdle()

        assertEquals(request, viewModel.uiState.value.pendingPermissionRequest)
        assertEquals(0, viewModel.uiState.value.autoApprovedToolCount)
        coVerify(exactly = 0) { acpClient.respondToPermission(any()) }
    }

    @Test
    fun `permission request auto-approves with rememberForSession when fullAccessEnabled is true`() = runTest {
        advanceUntilIdle()

        viewModel.onAction(WorkspaceAction.UpdateSettings { it.copy(fullAccessEnabled = true) })
        advanceUntilIdle()

        val request = AcpEvent.PermissionRequest(
            sessionId = "sess-1",
            requestId = "perm-auto-1",
            toolName = "bash",
            riskLevel = McpToolRisk.CRITICAL,
            description = "Analyze device state",
            argumentsJson = """{"cmd":"free -h"}""",
        )
        acpEventsFlow.emit(request)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.pendingPermissionRequest)
        assertEquals(1, viewModel.uiState.value.autoApprovedToolCount)
        coVerify(exactly = 1) {
            acpClient.respondToPermission(
                PermissionResponse(
                    requestId = "perm-auto-1",
                    approved = true,
                    rememberForSession = true,
                ),
            )
        }
    }

    @Test
    fun `enabling fullAccessEnabled while permission is pending auto-approves it immediately`() = runTest {
        advanceUntilIdle()

        val pending = AcpEvent.PermissionRequest(
            sessionId = "sess-1",
            requestId = "perm-pending-1",
            toolName = "echo test",
            riskLevel = McpToolRisk.MEDIUM,
            description = "Pending tool call",
        )
        acpEventsFlow.emit(pending)
        advanceUntilIdle()
        assertEquals(pending, viewModel.uiState.value.pendingPermissionRequest)

        viewModel.onAction(WorkspaceAction.UpdateSettings { it.copy(fullAccessEnabled = true) })
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.pendingPermissionRequest)
        assertTrue(viewModel.uiState.value.settings.fullAccessEnabled)
        assertEquals(1, viewModel.uiState.value.autoApprovedToolCount)
        coVerify(exactly = 1) {
            acpClient.respondToPermission(
                PermissionResponse(
                    requestId = "perm-pending-1",
                    approved = true,
                    rememberForSession = true,
                ),
            )
        }
    }

    @Test
    fun `RunFullDiagnostics populates root, live permissions, chroot deps, and setupReadyCount`() = runTest {
        val pkg = "com.antigravity.mobile"
        val rootEnv = RootEnvironmentInfo(
            isRootGranted = true,
            provider = RootProviderType.KERNEL_SU,
            suVersion = "KernelSU 1.0",
            mountMasterSupported = true,
        )
        coEvery { rootExecutor.detectEnvironment() } returns rootEnv
        coEvery { rootExecutor.exec("appops get $pkg ACCESS_RESTRICTED_SETTINGS", any(), any()) } returns ShellResult(
            exitCode = 0,
            stdout = "Uid mode: ACCESS_RESTRICTED_SETTINGS: allow",
            stderr = "",
        )
        coEvery { rootExecutor.exec("settings get secure enabled_accessibility_services", any(), any()) } returns ShellResult(
            exitCode = 0,
            stdout = "$pkg/.service.AntigravityAccessibilityService",
            stderr = "",
        )
        coEvery { rootExecutor.exec("settings get secure enabled_notification_listeners", any(), any()) } returns ShellResult(
            exitCode = 0,
            stdout = "$pkg/.service.AntigravityNotificationListener",
            stderr = "",
        )
        coEvery { rootExecutor.exec("dumpsys deviceidle whitelist", any(), any()) } returns ShellResult(
            exitCode = 0,
            stdout = "user,$pkg,10234",
            stderr = "",
        )
        coEvery { rootExecutor.exec(match { it.contains("command -v curl") && it.contains("ca-certificates.crt") }, any(), any()) } returns ShellResult(
            exitCode = 0,
            stdout = "CURL\nCERTS\nNC\n",
            stderr = "",
        )
        coEvery { chrootManager.getMountStatus() } returns MountStatus(
            procMounted = true,
            sysMounted = true,
            devMounted = true,
            devPtsMounted = true,
            sdcardMounted = true,
            profilesMounted = true,
        )
        coEvery { chrootManager.getAgyVersion() } returns Result.success("1.4.11")
        advanceUntilIdle()

        viewModel.onAction(
            WorkspaceAction.RunFullDiagnostics(
                packageName = pkg,
                isOverlayGranted = true,
                isDeviceAdminActive = true,
            ),
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val onboarding = state.onboarding
        assertTrue(onboarding.diagnosticsChecked)
        assertFalse(onboarding.isRunningDiagnostics)
        assertTrue(onboarding.isRootReady)
        assertTrue(onboarding.isPermissionsReady)
        assertTrue(onboarding.bootstrapReport?.notificationListenerEnabled == true)
        assertTrue(onboarding.bootstrapReport?.batteryOptimizationExempted == true)
        assertTrue(onboarding.isOverlayPermissionGranted)
        assertTrue(onboarding.isDeviceAdminActive)
        assertTrue(onboarding.isChrootMounted)
        assertEquals("1.4.11", onboarding.agyVersion)
        assertTrue(onboarding.chrootCurlInstalled)
        assertTrue(onboarding.chrootCaCertsInstalled)
        assertTrue(onboarding.chrootNetcatInstalled)
        assertTrue(onboarding.chrootDependenciesReady)
        assertTrue(onboarding.isAgyReady)
        assertEquals(5, state.setupReadyCount)
        assertEquals(5, state.setupTotalSteps)
        assertTrue(state.isSetupFullyReady)
        assertFalse(state.hasMissingCriticalSetup)
        // Fully ready -> should stay on Hub, NOT auto-open Onboarding
        assertEquals(AppDestination.Hub, state.currentDestination)
    }

    @Test
    fun `RunFullDiagnostics auto-opens Onboarding only once when critical prerequisites are missing`() = runTest {
        coEvery { rootExecutor.detectEnvironment() } returns RootEnvironmentInfo(
            isRootGranted = false,
            provider = RootProviderType.NONE,
        )
        coEvery { chrootManager.getMountStatus() } returns MountStatus()
        coEvery { chrootManager.getAgyVersion() } returns Result.success(null)
        advanceUntilIdle()

        viewModel.onAction(WorkspaceAction.RunFullDiagnostics(packageName = "com.antigravity.mobile"))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hasMissingCriticalSetup)
        assertEquals(AppDestination.Onboarding, viewModel.uiState.value.currentDestination)

        // User dismisses Onboarding -> subsequent diagnostics MUST NOT force-reopen Onboarding
        viewModel.onAction(WorkspaceAction.DismissOnboarding)
        assertEquals(AppDestination.Hub, viewModel.uiState.value.currentDestination)

        viewModel.onAction(WorkspaceAction.RunFullDiagnostics(packageName = "com.antigravity.mobile"))
        advanceUntilIdle()
        assertEquals(AppDestination.Hub, viewModel.uiState.value.currentDestination)
    }

    @Test
    fun `AutoFixAllPrerequisites runs full sequential pipeline and falls back from BundledAsset to RemoteDownload`() = runTest {
        val pkg = "com.antigravity.mobile"
        val rootEnv = RootEnvironmentInfo(
            isRootGranted = true,
            provider = RootProviderType.MAGISK,
            suVersion = "27.0:MAGISKSU",
            mountMasterSupported = true,
        )
        val readyMount = MountStatus(
            procMounted = true,
            sysMounted = true,
            devMounted = true,
            devPtsMounted = true,
            sdcardMounted = true,
            profilesMounted = true,
        )
        var isMountedNow = false
        var agyInstalled = false

        coEvery { rootExecutor.detectEnvironment() } returns rootEnv
        coEvery { rootExecutor.bootstrapPermissions(pkg, null, null) } returns BootstrapReport(
            packageName = pkg,
            environment = rootEnv,
            restrictedSettingsGranted = true,
            accessibilityEnabled = true,
            notificationListenerEnabled = true,
            batteryOptimizationExempted = true,
        )
        coEvery { chrootManager.ensureRootfsReady(ofType(RootfsSource.BundledAsset::class)) } returns
            Result.failure(FileNotFoundException("Bundled asset rootfs/arm64-rootfs.tar.gz not found"))
        coEvery { chrootManager.ensureRootfsReady(ofType(RootfsSource.RemoteDownload::class)) } returns
            Result.success(Unit)
        coEvery { chrootManager.mountAll() } answers {
            isMountedNow = true
            Result.success(readyMount)
        }
        coEvery { chrootManager.getMountStatus() } answers {
            if (isMountedNow) readyMount else MountStatus()
        }
        coEvery { chrootManager.installOrUpdateAgy() } answers {
            agyInstalled = true
            Result.success("1.5.0")
        }
        coEvery { chrootManager.getAgyVersion() } answers {
            Result.success(if (agyInstalled) "1.5.0" else null)
        }
        coEvery { rootExecutor.exec(match { it.contains("command -v curl") }, any(), any()) } returns ShellResult(
            exitCode = 0,
            stdout = "CURL\nCERTS\nNC\n",
            stderr = "",
        )
        // Unauthenticated profile so completion message prompts 1-tap Google Sign-In
        val unauthed = primaryProfile.copy(isAuthenticated = false)
        profilesFlow.value = listOf(unauthed)
        activeProfileFlow.value = unauthed
        advanceUntilIdle()

        viewModel.onAction(WorkspaceAction.AutoFixAllPrerequisites(pkg))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val onboarding = state.onboarding
        coVerify(exactly = 1) { chrootManager.ensureRootfsReady(ofType(RootfsSource.BundledAsset::class)) }
        coVerify(exactly = 1) { chrootManager.ensureRootfsReady(ofType(RootfsSource.RemoteDownload::class)) }
        coVerify(exactly = 1) { chrootManager.mountAll() }
        coVerify(exactly = 1) { chrootManager.installOrUpdateAgy() }
        assertFalse(onboarding.isAutoFixingAll)
        assertFalse(onboarding.isBusy)
        assertNull(onboarding.autoFixStageLabel)
        assertNull(onboarding.errorMessage)
        assertTrue(onboarding.diagnosticsChecked)
        assertTrue(onboarding.isRootReady)
        assertTrue(onboarding.isPermissionsReady)
        assertTrue(onboarding.isChrootMounted)
        assertEquals("1.5.0", onboarding.agyVersion)
        assertTrue(onboarding.isAgyReady)
        assertEquals(RootfsSourceOption.REMOTE_DOWNLOAD, onboarding.selectedRootfsSource)
        assertEquals(
            "Tüm sistem bileşenleri hazır! Şimdi tek dokunuşla Google hesabınıza giriş yapabilirsiniz.",
            onboarding.statusMessage,
        )
        assertEquals(4, state.setupReadyCount)
    }

    @Test
    fun `QuickGoogleSignIn auto-creates default profile when empty and triggers startLoginFlow`() = runTest {
        profilesFlow.value = emptyList()
        activeProfileFlow.value = null
        chrootStateFlow.value = ChrootState.Mounted(
            agyVersion = "1.4.11",
            mountStatus = MountStatus(
                procMounted = true,
                sysMounted = true,
                devMounted = true,
                devPtsMounted = true,
                sdcardMounted = true,
                profilesMounted = true,
            ),
        )
        val createdProfile = AccountProfile(
            id = "prof-auto-1",
            displayName = WorkspaceViewModel.DEFAULT_QUICK_PROFILE_NAME,
            email = null,
            isAuthenticated = false,
            isActive = true,
            paths = ProfilePaths.forProfileId("prof-auto-1"),
        )
        coEvery { accountRepository.createProfile(WorkspaceViewModel.DEFAULT_QUICK_PROFILE_NAME, null) } returns
            Result.success(createdProfile)
        coEvery { accountRepository.switchActiveProfile("prof-auto-1") } answers {
            profilesFlow.value = listOf(createdProfile)
            activeProfileFlow.value = createdProfile
            Result.success(createdProfile)
        }
        coEvery { accountRepository.startLoginFlow("prof-auto-1", null) } returns
            Result.success(
                LoginChallenge(
                    profileId = "prof-auto-1",
                    authUrl = "https://accounts.google.com/o/oauth2/auth?client_id=test",
                ),
            )
        advanceUntilIdle()

        viewModel.onAction(WorkspaceAction.QuickGoogleSignIn)
        advanceUntilIdle()

        coVerify(exactly = 1) { accountRepository.createProfile("Ana Google Hesabım", null) }
        coVerify(exactly = 1) { accountRepository.switchActiveProfile("prof-auto-1") }
        coVerify(exactly = 1) { accountRepository.startLoginFlow("prof-auto-1", null) }
        assertEquals("prof-auto-1", viewModel.uiState.value.activeProfile?.id)
        assertEquals(
            "https://accounts.google.com/o/oauth2/auth?client_id=test",
            viewModel.uiState.value.pendingChromeCustomTabUrl,
        )
    }

    @Test
    fun `QuickGoogleSignIn shows Turkish guidance error when chroot or agy is missing`() = runTest {
        chrootStateFlow.value = ChrootState.NotInstalled
        coEvery { chrootManager.getMountStatus() } returns MountStatus()
        coEvery { chrootManager.getAgyVersion() } returns Result.success(null)
        advanceUntilIdle()

        viewModel.onAction(WorkspaceAction.QuickGoogleSignIn)
        advanceUntilIdle()

        coVerify(exactly = 0) { accountRepository.startLoginFlow(any(), any()) }
        assertEquals(
            "Google girişi için önce Chroot ve agy CLI kurulu olmalıdır. Yukarıdaki 'Tüm Eksikleri Otomatik Düzelt' butonuna basın.",
            viewModel.uiState.value.onboarding.errorMessage,
        )
    }

    @Test
    fun `HandleClipboardAuthText auto-submits Google code or callback URL once, deduplicates, ignores normal text, and updates profile on auth`() = runTest {
        val rawCode = "4/0AanRRrtAbCdEfGhIjKlMnOpQrStUvWxYz123456"
        val callbackUrl = "https://antigravity.google/oauth-callback?state=xyz&code=4%2F0AanRRrtZzYyXxWwVvUuTtSsRrQqPpOoNnMm987654"
        val extractedFromUrl = "4/0AanRRrtZzYyXxWwVvUuTtSsRrQqPpOoNnMm987654"

        // Matcher sanity checks for trusted vs untrusted/unrelated text
        assertEquals(rawCode, ClipboardOAuthMatcher.extractCode("  $rawCode  "))
        assertEquals(extractedFromUrl, ClipboardOAuthMatcher.extractCode(callbackUrl))
        assertEquals(rawCode, ClipboardOAuthMatcher.extractCode("http://127.0.0.1:43123/callback?code=$rawCode"))
        assertEquals(rawCode, ClipboardOAuthMatcher.extractCode("http://localhost:8080/oauth?code=$rawCode"))
        assertEquals(rawCode, ClipboardOAuthMatcher.extractCode("antigravity://oauth/callback?code=$rawCode"))
        assertNull(ClipboardOAuthMatcher.extractCode("Merhaba normal pano metni"))
        assertNull(ClipboardOAuthMatcher.extractCode("4/tooShort"))
        assertNull(ClipboardOAuthMatcher.extractCode("https://evil.example.com/oauth-callback?code=$rawCode"))

        val genericProfile = primaryProfile.copy(
            displayName = WorkspaceViewModel.DEFAULT_QUICK_PROFILE_NAME,
            email = null,
            isAuthenticated = false,
        )
        profilesFlow.value = listOf(genericProfile)
        activeProfileFlow.value = genericProfile
        authFlowState.value = AuthFlowState.AwaitingBrowserAuth(
            LoginChallenge(
                profileId = genericProfile.id,
                authUrl = "https://accounts.google.com/o/oauth2/auth",
            ),
        )
        coEvery { accountRepository.submitAuthCode(genericProfile.id, rawCode) } returns
            Result.success(genericProfile.copy(isAuthenticated = true, email = "dev@gmail.com"))
        coEvery { accountRepository.submitAuthCode(genericProfile.id, extractedFromUrl) } returns
            Result.success(genericProfile.copy(isAuthenticated = true, email = "dev@gmail.com"))
        coEvery { accountRepository.refreshUsage(genericProfile.id) } returns
            Result.success(UsageQuota(planTier = AccountPlanTier.PRO, remainingPercent = 95.0, updatedAtEpochMs = 5_000L))
        advanceUntilIdle()

        // 1. Ordinary clipboard text is silently ignored
        viewModel.onAction(WorkspaceAction.HandleClipboardAuthText("Sadece rastgele bir not"))
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.onboarding.clipboardAutoAuthHint)
        assertNull(viewModel.uiState.value.onboarding.lastAutoSubmittedClipboardCode)
        coVerify(exactly = 0) { accountRepository.submitAuthCode(any(), any()) }

        // 2. Valid raw Google OAuth code is auto-submitted once
        viewModel.onAction(WorkspaceAction.HandleClipboardAuthText(rawCode, autoSubmit = true))
        advanceUntilIdle()
        assertEquals(rawCode, viewModel.uiState.value.onboarding.lastAutoSubmittedClipboardCode)
        coVerify(exactly = 1) { accountRepository.submitAuthCode(genericProfile.id, rawCode) }

        // 3. Repeated focus event with the same clipboard code is deduplicated
        viewModel.onAction(WorkspaceAction.HandleClipboardAuthText(rawCode, autoSubmit = true))
        advanceUntilIdle()
        coVerify(exactly = 1) { accountRepository.submitAuthCode(genericProfile.id, rawCode) }

        // 4. Valid antigravity.google callback URL with a new code extracts and submits decoded code
        viewModel.onAction(WorkspaceAction.HandleClipboardAuthText(callbackUrl, autoSubmit = true))
        advanceUntilIdle()
        assertEquals(extractedFromUrl, viewModel.uiState.value.onboarding.lastAutoSubmittedClipboardCode)
        coVerify(exactly = 1) { accountRepository.submitAuthCode(genericProfile.id, extractedFromUrl) }

        // 5. AuthFlowState.Authenticated updates generic profile displayName, clears hint, and refreshes quota
        authFlowState.value = AuthFlowState.Authenticated(profileId = genericProfile.id, email = "dev@gmail.com")
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.onboarding.clipboardAutoAuthHint)
        assertEquals("dev@gmail.com", viewModel.uiState.value.activeProfile?.displayName)
        assertEquals("dev@gmail.com", viewModel.uiState.value.activeProfile?.email)
        coVerify(atLeast = 1) { accountRepository.refreshUsage(genericProfile.id) }
    }

    @Test
    fun `run_workflow tool call and completion log project orchestrator, phases, and subagents into overview tabs`() = runTest {
        advanceUntilIdle()

        // 1. Initial tool_call when run_workflow starts with ScriptPath
        acpEventsFlow.emit(
            AcpEvent.ToolCallUpdate(
                sessionId = "sess-wf",
                callId = "call-wf-1",
                toolName = "run_workflow",
                status = ToolCallStatus.RUNNING,
                argumentsJson = """{"Description":"Build dagflow package","ScriptPath":"/tmp/orchestrate_dagflow.py"}""",
                outputPreview = null,
            ),
        )
        advanceUntilIdle()

        assertEquals("Build dagflow package", viewModel.uiState.value.swarmBoardState.swarmTitle)
        assertTrue(viewModel.uiState.value.subagents.any { it.name == "Workflow Orchestrator" && it.status == SwarmMemberStatus.WORKING })

        // 2. Follow-up tool_call_update omits rawInput (`{}`) and toolName (`unknown_tool`) but sends workflow log output
        val workflowOutput = """
            - [phase] 1. Architecture & Spec
            - [agent:self] System Architect: done (badc0ab2-528a): Designed models and engine
            - [phase] 2. Parallel Implementation
            - [agent:self] Core Engine Developer: done (9b09cd68-1111): Implemented DAGRunner
            - [agent:self] Test Engineer: done (4d38c288-2222): Added 51 pytest tests
        """.trimIndent()
        acpEventsFlow.emit(
            AcpEvent.ToolCallUpdate(
                sessionId = "sess-wf",
                callId = "call-wf-1",
                toolName = "unknown_tool",
                status = ToolCallStatus.COMPLETED,
                argumentsJson = "{}",
                outputPreview = workflowOutput,
            ),
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val names = state.subagents.map { it.name }
        assertTrue("System Architect" in names)
        assertTrue("Core Engine Developer" in names)
        assertTrue("Test Engineer" in names)
        assertEquals(SwarmMemberStatus.DONE, state.subagents.first { it.name == "System Architect" }.status)
        assertEquals("badc0ab2-528a", state.subagents.first { it.name == "System Architect" }.id)
        assertTrue(state.swarmBoardState.members.any { it.name == "Core Engine Developer" && it.status == SwarmMemberStatus.DONE })
        assertTrue(state.swarmBoardState.messages.any { it.text.contains("1. Architecture & Spec") })

        // Merged tool summary on message preserved original toolName and argumentsJson
        val toolCall = state.messages.last().toolCalls.first { it.callId == "call-wf-1" }
        assertEquals("run_workflow", toolCall.toolName)
        assertTrue(toolCall.argumentsPreview.contains("orchestrate_dagflow.py"))
        assertEquals(ToolCallStatus.COMPLETED, toolCall.status)
    }

    @Test
    fun `SessionStateChanged READY closes normal RUNNING tools but preserves background run_workflow until ERROR`() = runTest {
        advanceUntilIdle()

        acpEventsFlow.emit(
            AcpEvent.ToolCallUpdate(
                sessionId = "sess-ready",
                callId = "call-cmd-1",
                toolName = "run_command",
                status = ToolCallStatus.RUNNING,
                argumentsJson = """{"CommandLine":"pytest"}""",
            ),
        )
        acpEventsFlow.emit(
            AcpEvent.ToolCallUpdate(
                sessionId = "sess-ready",
                callId = "call-wf-bg",
                toolName = "run_workflow",
                status = ToolCallStatus.RUNNING,
                argumentsJson = """{"Description":"Background pipeline","ScriptPath":"/tmp/wf.py"}""",
            ),
        )
        advanceUntilIdle()

        acpEventsFlow.emit(AcpEvent.SessionStateChanged(sessionId = "sess-ready", state = AcpSessionState.READY))
        advanceUntilIdle()

        val afterReady = viewModel.uiState.value.messages.last()
        assertFalse(afterReady.isStreaming)
        assertEquals(ToolCallStatus.COMPLETED, afterReady.toolCalls.first { it.callId == "call-cmd-1" }.status)
        assertEquals(ToolCallStatus.RUNNING, afterReady.toolCalls.first { it.callId == "call-wf-bg" }.status)

        acpEventsFlow.emit(AcpEvent.SessionStateChanged(sessionId = "sess-ready", state = AcpSessionState.ERROR))
        advanceUntilIdle()
        assertEquals(
            ToolCallStatus.FAILED,
            viewModel.uiState.value.messages.last().toolCalls.first { it.callId == "call-wf-bg" }.status,
        )
    }

    @Test
    fun `unrelated script tool is ignored, inline workflow script and manage_task keep unique subagent IDs, and nested markdown renders`() = runTest {
        advanceUntilIdle()

        // 1. Unrelated tool with a `script` argument must NOT pollute subagents/swarm
        acpEventsFlow.emit(
            AcpEvent.ToolCallUpdate(
                sessionId = "sess-misc",
                callId = "call-eval",
                toolName = "eval_js",
                status = ToolCallStatus.COMPLETED,
                argumentsJson = """{"script":"1 + 1"}""",
            ),
        )
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.subagents.isEmpty())
        assertTrue(viewModel.uiState.value.swarmBoardState.swarmTitle.isBlank())

        // 2. Inline run_workflow Script + follow-up manage_task log reusing a conversation UUID must keep IDs unique
        acpEventsFlow.emit(
            AcpEvent.ToolCallUpdate(
                sessionId = "sess-misc",
                callId = "call-inline-wf",
                toolName = "run_workflow",
                status = ToolCallStatus.RUNNING,
                argumentsJson = """{"Description":"Inline flow","Script":"await agent(role=\"Reviewer\")"}""",
            ),
        )
        acpEventsFlow.emit(
            AcpEvent.ToolCallUpdate(
                sessionId = "sess-misc",
                callId = "call-status-1",
                toolName = "manage_task",
                status = ToolCallStatus.COMPLETED,
                argumentsJson = """{"Action":"status"}""",
                outputPreview = """
                    - [phase] 1. Review
                    - [agent:self] Reviewer: done (shared-uuid): First pass
                    - [agent:self] Reviewer Recheck: done (shared-uuid): Second pass
                """.trimIndent(),
            ),
        )
        advanceUntilIdle()

        val subagents = viewModel.uiState.value.subagents
        assertEquals(subagents.size, subagents.map { it.id }.distinct().size)
        assertTrue(subagents.any { it.id == "shared-uuid" && it.status == SwarmMemberStatus.DONE })

        // 3. Recursive renderInlineMarkdown strips nested backticks inside bold and tolerates unclosed markers
        val rendered = com.antigravity.mobile.feature.workspace.ui.chat.renderInlineMarkdown(
            text = "**Python (`dagflow`)** ve **kapanmamış *italik",
            inlineCodeBg = androidx.compose.ui.graphics.Color.Gray,
            inlineCodeFg = androidx.compose.ui.graphics.Color.Cyan,
            linkColor = androidx.compose.ui.graphics.Color.Blue,
        )
        assertFalse(rendered.text.contains('`'))
        assertTrue(rendered.text.contains("dagflow"))
        assertTrue(rendered.text.contains("kapanmamış"))
    }

    @Test
    fun `AddGoogleAccount creates a fresh isolated profile and immediately starts Google OAuth flow`() = runTest {
        val newProfile = primaryProfile.copy(
            id = "prof-new-google",
            displayName = WorkspaceViewModel.DEFAULT_QUICK_PROFILE_NAME,
            email = null,
            isAuthenticated = false,
        )
        chrootStateFlow.value = ChrootState.Mounted(
            agyVersion = "1.5.18",
            mountStatus = MountStatus(
                procMounted = true,
                sysMounted = true,
                devMounted = true,
                devPtsMounted = true,
                sdcardMounted = true,
                profilesMounted = true,
            ),
        )
        coEvery { accountRepository.createProfile(WorkspaceViewModel.DEFAULT_QUICK_PROFILE_NAME, null) } returns
            Result.success(newProfile)
        coEvery { accountRepository.switchActiveProfile(newProfile.id) } returns Result.success(newProfile)
        coEvery { accountRepository.startLoginFlow(newProfile.id, null) } returns Result.success(
            LoginChallenge(
                profileId = newProfile.id,
                authUrl = "https://accounts.google.com/o/oauth2/auth?state=xyz",
            ),
        )
        advanceUntilIdle()

        viewModel.onAction(WorkspaceAction.AddGoogleAccount())
        advanceUntilIdle()

        coVerify(exactly = 1) { accountRepository.createProfile(WorkspaceViewModel.DEFAULT_QUICK_PROFILE_NAME, null) }
        coVerify(exactly = 1) { accountRepository.switchActiveProfile(newProfile.id) }
        coVerify(exactly = 1) { accountRepository.startLoginFlow(newProfile.id, null) }
        assertEquals("https://accounts.google.com/o/oauth2/auth?state=xyz", viewModel.uiState.value.pendingChromeCustomTabUrl)
    }
}
