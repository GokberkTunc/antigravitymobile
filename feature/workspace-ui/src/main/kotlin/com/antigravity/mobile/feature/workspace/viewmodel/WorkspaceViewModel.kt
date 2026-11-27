package com.antigravity.mobile.feature.workspace.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import com.antigravity.mobile.contracts.ChrootConstants
import com.antigravity.mobile.contracts.ChrootManager
import com.antigravity.mobile.contracts.ChrootState
import com.antigravity.mobile.contracts.DiffApprovalStatus
import com.antigravity.mobile.contracts.DiffChangeType
import com.antigravity.mobile.contracts.DiffItem
import com.antigravity.mobile.contracts.PermissionMode
import com.antigravity.mobile.contracts.PermissionResponse
import com.antigravity.mobile.contracts.RootExecutor
import com.antigravity.mobile.contracts.RootfsSource
import com.antigravity.mobile.contracts.ScheduledTask
import com.antigravity.mobile.contracts.SecurityPolicyEngine
import com.antigravity.mobile.contracts.ToolCallSummary
import com.antigravity.mobile.contracts.VoiceState
import com.antigravity.mobile.contracts.VoiceTranscriber
import com.antigravity.mobile.contracts.WorkspaceChatMessage
import com.antigravity.mobile.contracts.WorkspaceProject
import com.antigravity.mobile.feature.workspace.data.InMemoryWorkspaceLocalStore
import com.antigravity.mobile.feature.workspace.data.WorkspaceLocalStore
import com.antigravity.mobile.feature.workspace.data.WorkspaceSnapshot
import com.antigravity.mobile.feature.workspace.model.AppDestination
import com.antigravity.mobile.feature.workspace.model.AppSettings
import com.antigravity.mobile.feature.workspace.model.AuthBannerState
import com.antigravity.mobile.feature.workspace.model.DEVICE_FALLBACK_PROJECT
import com.antigravity.mobile.feature.workspace.model.MentionSuggestion
import com.antigravity.mobile.feature.workspace.model.OverviewTab
import com.antigravity.mobile.feature.workspace.model.QuotaBucketParser
import com.antigravity.mobile.feature.workspace.model.QuotaWarningState
import com.antigravity.mobile.feature.workspace.model.RootfsSourceOption
import com.antigravity.mobile.feature.workspace.model.SettingsCategory
import com.antigravity.mobile.feature.workspace.model.SlashCommand
import com.antigravity.mobile.feature.workspace.model.SubagentItem
import com.antigravity.mobile.feature.workspace.model.WorkspaceAction
import com.antigravity.mobile.feature.workspace.model.WorkspaceArtifact
import com.antigravity.mobile.feature.workspace.model.WorkspaceConversation
import com.antigravity.mobile.feature.workspace.model.WorkspaceUiState
import com.antigravity.mobile.feature.workspace.model.defaultMentions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.FileNotFoundException
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

/**
 * Unidirectional Data Flow ViewModel coordinating all Antigravity Mobile workspace capabilities:
 * - Streaming [AcpClient.events] into chat messages, thought blocks, tool pills, artifacts/diffs,
 *   swarm state, permission prompts, and `-32000` auth challenges.
 * - Routing slash commands (`/plan`, `/swarm`, `/goal`, `/schedule`, `/diff`, `/agents`, `/usage`, `/compact`)
 *   and `@` context mentions.
 * - Managing multi-account Google profiles, quota warnings, fallback switching, MCP server health,
 *   scheduled automations, protected package denylist, voice dictation, and onboarding.
 */
@HiltViewModel
class WorkspaceViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val acpClient: AcpClient,
    private val androidMcpServer: AndroidMcpServer,
    private val automationScheduler: AutomationScheduler,
    private val securityPolicyEngine: SecurityPolicyEngine,
    private val voiceTranscriber: VoiceTranscriber,
    private val chrootManager: ChrootManager,
    private val rootExecutor: RootExecutor,
    private val localStore: WorkspaceLocalStore = InMemoryWorkspaceLocalStore(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkspaceUiState())
    val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()

    internal var clockMs: () -> Long = System::currentTimeMillis

    private var hasAutoRefreshedQuota = false
    private var hasAutoOpenedOnboarding = false
    private var lastHandledAuthSuccess: AuthFlowState.Authenticated? = null
    private var lastRootBackPressEpochMs: Long = 0L

    internal constructor(
        accountRepository: AccountRepository,
        acpClient: AcpClient,
        androidMcpServer: AndroidMcpServer,
        automationScheduler: AutomationScheduler,
        securityPolicyEngine: SecurityPolicyEngine,
        voiceTranscriber: VoiceTranscriber,
        chrootManager: ChrootManager,
        rootExecutor: RootExecutor,
        localStore: WorkspaceLocalStore = InMemoryWorkspaceLocalStore(),
        clockMs: () -> Long,
    ) : this(
        accountRepository = accountRepository,
        acpClient = acpClient,
        androidMcpServer = androidMcpServer,
        automationScheduler = automationScheduler,
        securityPolicyEngine = securityPolicyEngine,
        voiceTranscriber = voiceTranscriber,
        chrootManager = chrootManager,
        rootExecutor = rootExecutor,
        localStore = localStore,
    ) {
        this.clockMs = clockMs
    }

    init {
        hydrateFromLocalStore()
        refreshMcpTools()
        observeCoreFlows()
        observeAcpEvents()
    }

    fun onAction(action: WorkspaceAction) {
        when (action) {
            is WorkspaceAction.UpdateComposerText -> handleComposerTextChange(action.text)
            is WorkspaceAction.SendMessage -> handleSendMessage(action.overrideText)
            is WorkspaceAction.SelectSlashCommand -> handleSlashSelection(action.command)
            is WorkspaceAction.SelectMention -> handleMentionSelection(action.mention.token)
            is WorkspaceAction.AddAttachment -> addAttachment(action.pathOrLabel)
            is WorkspaceAction.RemoveAttachment -> removeAttachment(action.pathOrLabel)
            WorkspaceAction.DismissComposerMenus -> _uiState.update {
                it.copy(showSlashMenu = false, showMentionMenu = false)
            }

            WorkspaceAction.OpenSlashMenu -> _uiState.update {
                it.copy(
                    showSlashMenu = true,
                    showMentionMenu = false,
                    slashSuggestions = SlashCommand.entries,
                )
            }

            WorkspaceAction.OpenMentionMenu -> _uiState.update {
                it.copy(
                    showMentionMenu = true,
                    showSlashMenu = false,
                    mentionSuggestions = buildMentionSuggestions(it.selectedProject),
                )
            }

            is WorkspaceAction.SelectModel -> _uiState.update { it.copy(selectedModel = action.model) }
            is WorkspaceAction.SelectEffort -> _uiState.update { it.copy(selectedEffort = action.effort) }
            is WorkspaceAction.SelectPermissionMode -> handleSelectPermissionMode(action.mode)
            WorkspaceAction.CancelActiveSession -> handleCancelSession()
            WorkspaceAction.TriggerEmergencyStop -> handleEmergencyStop()
            WorkspaceAction.ToggleVoiceListening -> handleToggleVoice()
            is WorkspaceAction.ChangeVoiceLocale -> _uiState.update { it.copy(voiceLocaleTag = action.localeTag) }
            is WorkspaceAction.CreateProfile -> handleCreateProfile(action.displayName, action.email)
            is WorkspaceAction.SwitchProfile -> handleSwitchProfile(action.profileId)
            is WorkspaceAction.StartOAuthLogin -> handleStartOAuthLogin(action.profileId, action.capturedAuthUrl)
            is WorkspaceAction.SubmitAuthCode -> handleSubmitAuthCode(action.profileId, action.code)
            is WorkspaceAction.ImportDesktopProfile -> handleImportDesktopProfile(action.profileId, action.sourcePath)
            is WorkspaceAction.RefreshUsage -> handleRefreshUsage(action.profileId)
            WorkspaceAction.DismissAuthBanner -> _uiState.update { it.copy(authBanner = null) }
            WorkspaceAction.ConsumeChromeCustomTabUrl -> _uiState.update { it.copy(pendingChromeCustomTabUrl = null) }
            WorkspaceAction.DismissQuotaWarning -> _uiState.update { it.copy(quotaWarning = null) }
            is WorkspaceAction.SelectProject -> handleSelectProject(action.projectId)
            is WorkspaceAction.CreateProject -> handleCreateProject(action)
            is WorkspaceAction.DeleteProject -> handleDeleteProject(action.projectId)
            is WorkspaceAction.RenameProject -> handleRenameProject(action.projectId, action.newName)
            is WorkspaceAction.ToggleProjectExpanded -> handleToggleProjectExpanded(action.projectId)
            is WorkspaceAction.UpdateSidebarSearch -> _uiState.update { it.copy(sidebarSearchQuery = action.query) }
            is WorkspaceAction.CreateConversation -> handleCreateConversation(action.projectId, action.title)
            is WorkspaceAction.SelectConversation -> handleSelectConversation(action.conversationId)
            is WorkspaceAction.DeleteConversation -> handleDeleteConversation(action.conversationId)
            is WorkspaceAction.RenameConversation -> handleRenameConversation(action.conversationId, action.newTitle)
            is WorkspaceAction.TogglePinConversation -> handleTogglePinConversation(action.conversationId)

            // Explicit Back-Stack Navigation & Modal Sheets
            WorkspaceAction.NavigateBack -> handleNavigateBack()
            WorkspaceAction.NavigateToHub -> handleNavigateToHub()
            is WorkspaceAction.NavigateToConversation -> handleNavigateToConversation(action.conversationId)
            WorkspaceAction.OpenSettingsList -> handleOpenSettingsList()
            is WorkspaceAction.OpenSettingsDetail -> handleOpenSettingsDetail(action.category)
            WorkspaceAction.OpenModelPickerSheet -> _uiState.update {
                it.copy(
                    showModelPickerSheet = true,
                    showComposerToolsSheet = false,
                    showSlashMenu = false,
                    showMentionMenu = false,
                )
            }

            WorkspaceAction.CloseModelPickerSheet -> _uiState.update { it.copy(showModelPickerSheet = false) }
            WorkspaceAction.OpenComposerToolsSheet -> _uiState.update {
                it.copy(
                    showComposerToolsSheet = true,
                    showModelPickerSheet = false,
                    showSlashMenu = false,
                    showMentionMenu = false,
                )
            }

            WorkspaceAction.CloseComposerToolsSheet -> _uiState.update { it.copy(showComposerToolsSheet = false) }
            WorkspaceAction.ConsumeExitApp -> {
                lastRootBackPressEpochMs = 0L
                _uiState.update {
                    it.copy(
                        shouldExitApp = false,
                        exitConfirmPending = false,
                        exitConfirmMessage = null,
                    )
                }
            }

            WorkspaceAction.DismissExitConfirm -> {
                lastRootBackPressEpochMs = 0L
                _uiState.update {
                    it.copy(
                        exitConfirmPending = false,
                        exitConfirmMessage = null,
                    )
                }
            }

            is WorkspaceAction.OpenSettings -> handleOpenSettings(action.category)
            WorkspaceAction.CloseSettings -> handleCloseSettings()
            is WorkspaceAction.SelectSettingsCategory -> handleSelectSettingsCategory(action.category)
            is WorkspaceAction.UpdateSettings -> handleUpdateSettings(action.transform)
            WorkspaceAction.OpenQuotaSheet -> handleOpenQuotaSheet()
            WorkspaceAction.CloseQuotaSheet -> _uiState.update { it.copy(showQuotaSheet = false) }
            WorkspaceAction.RefreshAllQuotas -> handleRefreshAllQuotas()
            WorkspaceAction.DismissOnboarding -> handleDismissOnboarding()
            is WorkspaceAction.OpenOnboardingFromSettings -> handleOpenOnboardingFromSettings(action.fromSettings)

            WorkspaceAction.UnmountChroot -> handleUnmountChroot()
            is WorkspaceAction.DeleteProfile -> handleDeleteProfile(action.profileId)
            is WorkspaceAction.RespondToPermission -> handleRespondToPermission(action)
            is WorkspaceAction.ApproveDiff -> updateDiffStatus(action.diffId, DiffApprovalStatus.APPROVED)
            is WorkspaceAction.RejectDiff -> updateDiffStatus(action.diffId, DiffApprovalStatus.REJECTED)
            is WorkspaceAction.ScheduleTask -> handleScheduleTask(action)
            is WorkspaceAction.CancelScheduledTask -> handleCancelScheduledTask(action.taskId)
            is WorkspaceAction.ToggleScheduledTask -> handleToggleScheduledTask(action.taskId, action.enabled)
            is WorkspaceAction.TriggerScheduledTask -> handleTriggerScheduledTask(action.taskId)
            is WorkspaceAction.AddProtectedPackage -> handleAddProtectedPackage(action.packageName)
            is WorkspaceAction.RemoveProtectedPackage -> handleRemoveProtectedPackage(action.packageName)
            is WorkspaceAction.ToggleCustomization -> handleToggleCustomization(action.itemId)
            WorkspaceAction.RefreshMcpTools -> refreshMcpTools()
            is WorkspaceAction.OpenOverviewSheet -> _uiState.update {
                it.copy(isOverviewSheetVisible = true, selectedOverviewTab = action.tab)
            }

            WorkspaceAction.CloseOverviewSheet -> _uiState.update { it.copy(isOverviewSheetVisible = false) }
            is WorkspaceAction.SelectOverviewTab -> _uiState.update { it.copy(selectedOverviewTab = action.tab) }
            is WorkspaceAction.SelectSwarmSubTab -> _uiState.update { it.copy(selectedSwarmSubTab = action.subTab) }
            is WorkspaceAction.SetShowOnboarding -> handleSetShowOnboarding(action.show)
            WorkspaceAction.CheckRootStatus -> handleCheckRootStatus()
            is WorkspaceAction.RunRootBootstrap -> handleRunRootBootstrap(action)
            is WorkspaceAction.SelectRootfsSource -> _uiState.update {
                it.copy(onboarding = it.onboarding.copy(selectedRootfsSource = action.option))
            }

            is WorkspaceAction.UpdateRemoteRootfsUrl -> _uiState.update {
                it.copy(onboarding = it.onboarding.copy(remoteRootfsUrl = action.url))
            }

            is WorkspaceAction.UpdateLocalRootfsPath -> _uiState.update {
                it.copy(onboarding = it.onboarding.copy(localRootfsPath = action.path))
            }

            WorkspaceAction.SetupChroot -> handleSetupChroot()
            WorkspaceAction.CheckOrInstallAgy -> handleCheckOrInstallAgy()
            WorkspaceAction.CompleteOnboarding -> handleCompleteOnboarding()
            is WorkspaceAction.RunFullDiagnostics -> handleRunFullDiagnostics(action)
            is WorkspaceAction.AutoFixAllPrerequisites -> handleAutoFixAllPrerequisites(action.packageName)
            is WorkspaceAction.SyncAndroidSetupStatus -> _uiState.update { state ->
                state.copy(
                    onboarding = state.onboarding.copy(
                        isOverlayPermissionGranted = action.isOverlayGranted,
                        isDeviceAdminActive = action.isDeviceAdminActive,
                    ),
                )
            }

            WorkspaceAction.QuickGoogleSignIn -> handleQuickGoogleSignIn()
            is WorkspaceAction.HandleClipboardAuthText -> handleClipboardAuthText(action.rawText, action.autoSubmit)
            WorkspaceAction.ClearClipboardAuthHint -> _uiState.update { state ->
                state.copy(onboarding = state.onboarding.copy(clipboardAutoAuthHint = null))
            }

            WorkspaceAction.DismissError -> _uiState.update { it.copy(errorMessage = null) }
        }
    }

    private fun observeCoreFlows() {
        viewModelScope.launch {
            accountRepository.profiles.collect { profiles ->
                _uiState.update { it.copy(profiles = profiles) }
            }
        }
        viewModelScope.launch {
            accountRepository.activeProfile.collect { activeProfile ->
                _uiState.update { it.copy(activeProfile = activeProfile) }
                evaluateQuotaWarning(activeProfile)
                if (!hasAutoRefreshedQuota && _uiState.value.settings.autoRefreshQuotaOnLaunch &&
                    activeProfile != null && activeProfile.isAuthenticated &&
                    activeProfile.quota.updatedAtEpochMs == 0L && activeProfile.quota.remainingPercent == null
                ) {
                    hasAutoRefreshedQuota = true
                    handleRefreshAllQuotas()
                }
            }
        }
        viewModelScope.launch {
            accountRepository.authFlowState.collect { authState ->
                var refreshQuotaProfileId: String? = null
                _uiState.update { state ->
                    val customTabUrl = (authState as? AuthFlowState.AwaitingBrowserAuth)
                        ?.challenge
                        ?.authUrl
                    val authenticatedState = authState as? AuthFlowState.Authenticated
                    val isAuthenticated = authenticatedState != null
                    val clearedBanner = if (isAuthenticated) null else state.authBanner
                    val detectedEmail = authenticatedState?.email?.trim()?.ifBlank { null }
                    val updatedProfiles = if (authenticatedState != null && detectedEmail != null) {
                        state.profiles.map { profile ->
                            if (profile.id == authenticatedState.profileId) {
                                applyDetectedEmailIfGeneric(profile, detectedEmail)
                            } else {
                                profile
                            }
                        }
                    } else {
                        state.profiles
                    }
                    val updatedActiveProfile = if (authenticatedState != null && detectedEmail != null &&
                        state.activeProfile?.id == authenticatedState.profileId
                    ) {
                        applyDetectedEmailIfGeneric(state.activeProfile, detectedEmail)
                    } else {
                        state.activeProfile
                    }
                    val updatedOnboarding = if (isAuthenticated) {
                        state.onboarding.copy(clipboardAutoAuthHint = null)
                    } else {
                        state.onboarding
                    }
                    if (authenticatedState != null && lastHandledAuthSuccess != authenticatedState) {
                        lastHandledAuthSuccess = authenticatedState
                        refreshQuotaProfileId = authenticatedState.profileId
                    }
                    state.copy(
                        profiles = updatedProfiles,
                        activeProfile = updatedActiveProfile,
                        authFlowState = authState,
                        authBanner = clearedBanner,
                        pendingChromeCustomTabUrl = customTabUrl ?: state.pendingChromeCustomTabUrl,
                        onboarding = updatedOnboarding,
                    )
                }
                refreshQuotaProfileId?.let { profileId ->
                    handleRefreshUsage(profileId = profileId, postChatSummary = false)
                }
            }
        }
        viewModelScope.launch {
            acpClient.sessionState.collect { sessionState ->
                _uiState.update { it.copy(sessionState = sessionState) }
            }
        }
        viewModelScope.launch {
            acpClient.activeSession.collect { sessionInfo ->
                _uiState.update { it.copy(activeSession = sessionInfo) }
            }
        }
        viewModelScope.launch {
            androidMcpServer.health.collect { health ->
                _uiState.update {
                    it.copy(
                        mcpHealth = health,
                        mcpTools = androidMcpServer.registeredTools(),
                    )
                }
            }
        }
        viewModelScope.launch {
            automationScheduler.tasks.collect { tasks ->
                _uiState.update { it.copy(scheduledTasks = tasks) }
            }
        }
        viewModelScope.launch {
            automationScheduler.recentRuns.collect { runs ->
                _uiState.update { it.copy(recentTaskRuns = runs) }
            }
        }
        viewModelScope.launch {
            securityPolicyEngine.denylistPackages.collect { packages ->
                _uiState.update { it.copy(denylistPackages = packages) }
            }
        }
        viewModelScope.launch {
            securityPolicyEngine.permissionMode.collect { mode ->
                _uiState.update { it.copy(permissionMode = mode) }
            }
        }
        viewModelScope.launch {
            voiceTranscriber.state.collect { voiceState ->
                _uiState.update { current ->
                    val updatedComposer = if (voiceState is VoiceState.Result && voiceState.transcript.isNotBlank()) {
                        if (current.composerText.isBlank()) {
                            voiceState.transcript
                        } else {
                            "${current.composerText.trimEnd()} ${voiceState.transcript}"
                        }
                    } else {
                        current.composerText
                    }
                    current.copy(
                        voiceState = voiceState,
                        composerText = updatedComposer,
                    )
                }
            }
        }
        viewModelScope.launch {
            chrootManager.state.collect { chrootState ->
                _uiState.update { state ->
                    val agyVer = when (chrootState) {
                        is ChrootState.Mounted -> chrootState.agyVersion ?: state.onboarding.agyVersion
                        is ChrootState.Unmounted -> chrootState.agyVersion ?: state.onboarding.agyVersion
                        else -> state.onboarding.agyVersion
                    }
                    state.copy(
                        onboarding = state.onboarding.copy(
                            chrootState = chrootState,
                            agyVersion = agyVer,
                        ),
                    )
                }
            }
        }
    }

    private fun observeAcpEvents() {
        viewModelScope.launch {
            acpClient.events.collect { event ->
                handleAcpEvent(event)
            }
        }
    }

    private fun handleAcpEvent(event: AcpEvent) {
        when (event) {
            is AcpEvent.TextDelta -> {
                _uiState.update { state ->
                    val updatedMessages = upsertStreamingAssistantMessage(
                        messages = state.messages,
                        sessionId = event.sessionId,
                        preferredMessageId = event.messageId,
                    ) { existing ->
                        existing.copy(
                            content = existing.content + event.delta,
                            isStreaming = !event.isFinal,
                        )
                    }
                    syncActiveConversation(state, updatedMessages, sessionId = event.sessionId)
                }
                if (event.isFinal) {
                    persistSnapshot()
                }
            }

            is AcpEvent.ThoughtDelta -> {
                _uiState.update { state ->
                    val updatedMessages = upsertStreamingAssistantMessage(
                        messages = state.messages,
                        sessionId = event.sessionId,
                        preferredMessageId = null,
                    ) { existing ->
                        val nextThought = (existing.thinkingContent.orEmpty()) + event.delta
                        existing.copy(
                            thinkingContent = nextThought,
                            isStreaming = existing.isStreaming || !event.isFinal,
                        )
                    }
                    syncActiveConversation(state, updatedMessages, sessionId = event.sessionId)
                }
                if (event.isFinal) {
                    persistSnapshot()
                }
            }

            is AcpEvent.ToolCallUpdate -> {
                _uiState.update { state ->
                    val summary = ToolCallSummary(
                        callId = event.callId,
                        toolName = event.toolName,
                        status = event.status,
                        argumentsPreview = event.argumentsJson,
                        outputPreview = event.outputPreview ?: event.errorMessage,
                    )
                    val updatedMessages = upsertStreamingAssistantMessage(
                        messages = state.messages,
                        sessionId = event.sessionId,
                        preferredMessageId = null,
                    ) { existing ->
                        val existingIndex = existing.toolCalls.indexOfFirst { it.callId == event.callId }
                        val updatedCalls = if (existingIndex >= 0) {
                            existing.toolCalls.toMutableList().apply { set(existingIndex, summary) }
                        } else {
                            existing.toolCalls + summary
                        }
                        existing.copy(toolCalls = updatedCalls)
                    }
                    syncActiveConversation(state, updatedMessages, sessionId = event.sessionId)
                }
            }

            is AcpEvent.ArtifactUpdate -> {
                _uiState.update { state ->
                    val artifact = WorkspaceArtifact(
                        id = event.artifactId,
                        title = event.title,
                        mimeType = event.mimeType,
                        content = event.content,
                        filePath = event.filePath,
                        updatedAtEpochMs = System.currentTimeMillis(),
                    )
                    val updatedArtifacts = state.artifacts
                        .filterNot { it.id == artifact.id } + artifact

                    val isDiffArtifact = event.mimeType.contains("diff", ignoreCase = true) ||
                        event.content.contains("@@") && (event.content.contains("\n+") || event.content.contains("\n-"))
                    val updatedDiffs = if (isDiffArtifact) {
                        val diffItem = parseDiffArtifact(event)
                        state.diffs.filterNot { it.id == diffItem.id } + diffItem
                    } else {
                        state.diffs
                    }

                    state.copy(
                        artifacts = updatedArtifacts,
                        diffs = updatedDiffs,
                    )
                }
            }

            is AcpEvent.SwarmUpdate -> {
                _uiState.update { state ->
                    val subagents = event.boardState.members.map { member ->
                        SubagentItem(
                            id = member.name,
                            name = member.name,
                            role = member.role,
                            model = member.model.ifBlank { state.selectedModel.displayName },
                            status = member.status,
                            lastUpdate = member.lastUpdateText,
                        )
                    }
                    state.copy(
                        swarmBoardState = event.boardState,
                        subagents = subagents,
                    )
                }
            }

            is AcpEvent.PermissionRequest -> {
                if (_uiState.value.settings.fullAccessEnabled) {
                    _uiState.update { state ->
                        state.copy(
                            pendingPermissionRequest = null,
                            autoApprovedToolCount = state.autoApprovedToolCount + 1,
                        )
                    }
                    autoApprovePermission(event)
                } else {
                    _uiState.update { state ->
                        state.copy(pendingPermissionRequest = event)
                    }
                }
            }

            is AcpEvent.AuthRequired -> {
                _uiState.update { state ->
                    state.copy(
                        authBanner = AuthBannerState(
                            errorCode = event.errorCode,
                            message = event.message,
                            authUrl = event.authUrl,
                            profileId = state.activeProfile?.id,
                        ),
                    )
                }
            }

            is AcpEvent.SessionStateChanged -> {
                val finalizeStreaming = event.state == AcpSessionState.READY ||
                    event.state == AcpSessionState.ERROR ||
                    event.state == AcpSessionState.CLOSED
                _uiState.update { state ->
                    val updatedMessages = if (finalizeStreaming) {
                        state.messages.map { msg ->
                            if (msg.isStreaming) msg.copy(isStreaming = false) else msg
                        }
                    } else {
                        state.messages
                    }
                    val synced = if (finalizeStreaming) {
                        syncActiveConversation(state, updatedMessages, sessionId = event.sessionId)
                    } else {
                        state.copy(messages = updatedMessages)
                    }
                    synced.copy(sessionState = event.state)
                }
                if (finalizeStreaming) {
                    persistSnapshot()
                }
            }

            is AcpEvent.Error -> {
                _uiState.update { state ->
                    if (!event.recoverable) {
                        val nextMessages = state.messages + systemMessage(
                            sessionId = event.sessionId ?: currentSessionId(state),
                            content = "ACP Hatası (${event.code ?: "?"}): ${event.message}",
                        )
                        syncActiveConversation(state, nextMessages).copy(errorMessage = event.message)
                    } else {
                        state.copy(errorMessage = event.message)
                    }
                }
                if (!event.recoverable) {
                    persistSnapshot()
                }
            }
        }
    }

    private fun handleComposerTextChange(text: String) {
        val trimmedStart = text.trimStart()
        val isSlashActive = trimmedStart.startsWith("/") && !trimmedStart.contains(' ')
        val slashMatches = if (isSlashActive) {
            SlashCommand.entries.filter {
                it.command.startsWith(trimmedStart, ignoreCase = true)
            }
        } else {
            SlashCommand.entries
        }

        val lastToken = text.substringAfterLast(' ', missingDelimiterValue = text)
        val isMentionActive = !isSlashActive && lastToken.startsWith("@")
        val allMentions = buildMentionSuggestions(_uiState.value.selectedProject)
        val mentionMatches = if (isMentionActive) {
            allMentions.filter {
                it.token.startsWith(lastToken, ignoreCase = true) ||
                    it.label.contains(lastToken.removePrefix("@"), ignoreCase = true)
            }
        } else {
            allMentions
        }

        _uiState.update {
            it.copy(
                composerText = text,
                showSlashMenu = isSlashActive && slashMatches.isNotEmpty(),
                slashSuggestions = slashMatches,
                showMentionMenu = isMentionActive && mentionMatches.isNotEmpty(),
                mentionSuggestions = mentionMatches,
            )
        }
    }

    private fun handleSlashSelection(command: SlashCommand) {
        val immediateCommands = setOf(
            SlashCommand.DIFF,
            SlashCommand.AGENTS,
            SlashCommand.USAGE,
            SlashCommand.COMPACT,
        )
        if (command in immediateCommands) {
            _uiState.update { it.copy(composerText = "", showSlashMenu = false) }
            executeSlashCommand(command, argument = "")
        } else {
            _uiState.update {
                it.copy(
                    composerText = "${command.command} ",
                    showSlashMenu = false,
                )
            }
        }
    }

    private fun handleMentionSelection(token: String) {
        _uiState.update { state ->
            val text = state.composerText
            val lastAt = text.lastIndexOf('@')
            val updatedText = if (lastAt >= 0) {
                text.substring(0, lastAt) + "$token "
            } else {
                val sep = if (text.isBlank() || text.endsWith(" ")) "" else " "
                "$text$sep$token "
            }
            state.copy(
                composerText = updatedText,
                showMentionMenu = false,
            )
        }
    }

    private fun handleSendMessage(overrideText: String?) {
        val rawText = (overrideText ?: _uiState.value.composerText).trim()
        val attachments = _uiState.value.attachments
        if (rawText.isEmpty() && attachments.isEmpty()) return

        _uiState.update {
            it.copy(
                composerText = "",
                attachments = emptyList(),
                showSlashMenu = false,
                showMentionMenu = false,
            )
        }

        if (rawText.startsWith("/")) {
            val commandToken = rawText.substringBefore(' ').lowercase()
            val argument = rawText.substringAfter(' ', missingDelimiterValue = "").trim()
            val matched = SlashCommand.entries.firstOrNull { it.command == commandToken }
            if (matched != null) {
                executeSlashCommand(matched, argument)
                return
            }
        }

        sendPromptToAcp(prompt = rawText, attachments = attachments)
    }

    private fun executeSlashCommand(command: SlashCommand, argument: String) {
        when (command) {
            SlashCommand.PLAN -> {
                handleSelectPermissionMode(PermissionMode.PLAN)
                appendSystemMessage("Plan modu etkinleştirildi (`PermissionMode.PLAN`).")
                if (argument.isNotBlank()) {
                    sendPromptToAcp("/plan $argument", emptyList())
                }
            }

            SlashCommand.SWARM -> {
                _uiState.update {
                    it.copy(
                        isOverviewSheetVisible = true,
                        selectedOverviewTab = OverviewTab.SWARM,
                    )
                }
                if (argument.isNotBlank()) {
                    sendPromptToAcp("/swarm $argument", emptyList())
                }
            }

            SlashCommand.GOAL -> {
                val goalText = argument.ifBlank { "Belirlenen hedefi adım adım tamamla." }
                appendSystemMessage("Uzun hedefli otonom mod (`/goal`) başlatıldı.")
                sendPromptToAcp("/goal $goalText", emptyList())
            }

            SlashCommand.SCHEDULE -> {
                if (argument.isBlank()) {
                    appendSystemMessage("Kullanım: `/schedule <cron veya görev açıklaması>` — Örn: `/schedule 0 9 * * * Sabah özeti hazırla`")
                } else {
                    scheduleFromSlashCommand(argument)
                }
            }

            SlashCommand.DIFF -> {
                _uiState.update {
                    it.copy(
                        isOverviewSheetVisible = true,
                        selectedOverviewTab = OverviewTab.ARTIFACTS_DIFFS,
                    )
                }
            }

            SlashCommand.AGENTS -> {
                _uiState.update {
                    it.copy(
                        isOverviewSheetVisible = true,
                        selectedOverviewTab = OverviewTab.SUBAGENTS,
                    )
                }
            }

            SlashCommand.USAGE -> {
                handleRefreshUsage(profileId = null, postChatSummary = true)
            }

            SlashCommand.COMPACT -> {
                handleCompactConversation()
            }
        }
    }

    private fun scheduleFromSlashCommand(argument: String) {
        val parts = argument.split(Regex("\\s+"))
        val hasCronPrefix = parts.size >= 6 && parts.take(5).all { token ->
            token.matches(Regex("^[0-9*/,-]+$"))
        }
        val cron = if (hasCronPrefix) parts.take(5).joinToString(" ") else null
        val prompt = if (hasCronPrefix) parts.drop(5).joinToString(" ") else argument
        val triggerAt = if (cron == null) System.currentTimeMillis() + 60_000L else null

        handleScheduleTask(
            WorkspaceAction.ScheduleTask(
                name = prompt.take(36),
                prompt = prompt,
                cronExpression = cron,
                triggerAtEpochMs = triggerAt,
                exactTiming = true,
            ),
        )
    }

    private fun handleCompactConversation() {
        val currentMessages = _uiState.value.messages
        val keepCount = 4
        if (currentMessages.size <= keepCount) {
            appendSystemMessage("Sohbet geçmişi zaten kompakt (${currentMessages.size} mesaj).")
            return
        }
        val compactedCount = currentMessages.size - keepCount
        val summaryMsg = systemMessage(
            sessionId = currentSessionId(_uiState.value),
            content = "Önceki $compactedCount mesaj bağlam tasarrufu için sıkıştırıldı (`/compact`).",
        )
        _uiState.update { state ->
            syncActiveConversation(state, listOf(summaryMsg) + currentMessages.takeLast(keepCount))
        }
        persistSnapshot()
    }

    private fun sendPromptToAcp(prompt: String, attachments: List<String>) {
        val sessionIdBefore = currentSessionId(_uiState.value)
        val userMessage = WorkspaceChatMessage(
            id = "user-${UUID.randomUUID()}",
            sessionId = sessionIdBefore,
            role = ChatMessageRole.USER,
            content = prompt,
            attachments = attachments,
            timestampEpochMs = clockMs(),
            isStreaming = false,
        )
        _uiState.update { state ->
            syncActiveConversation(
                state = state,
                newMessages = state.messages + userMessage,
                sessionId = state.activeConversation?.sessionId,
                userPromptForAutoTitle = prompt,
            )
        }
        persistSnapshot()

        viewModelScope.launch {
            runSuspendCatching {
                val activeSession = ensureActiveSession()
                acpClient.sendMessage(
                    sessionId = activeSession.sessionId,
                    prompt = prompt,
                    attachments = attachments,
                ).getOrThrow()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(errorMessage = error.message ?: "Mesaj gönderilemedi")
                }
            }
        }
    }

    private suspend fun ensureActiveSession(): AcpSessionInfo {
        val state = _uiState.value
        val profileId = state.activeProfile?.id ?: "default"
        val workspaceDir = state.activeProject.rootPath
        val activeConvSessionId = state.activeConversation?.sessionId
        val existing = acpClient.activeSession.value ?: state.activeSession

        val canReuseExisting = existing != null &&
            state.sessionState !in NON_REUSABLE_SESSION_STATES &&
            existing.state !in NON_REUSABLE_SESSION_STATES &&
            existing.profileId == profileId &&
            existing.workspaceDir == workspaceDir &&
            (activeConvSessionId == null || existing.sessionId == activeConvSessionId)

        if (canReuseExisting) {
            attachSessionIdToActiveConversation(existing)
            return existing
        }

        val shouldDropResumeId = state.sessionState in setOf(AcpSessionState.ERROR, AcpSessionState.AWAITING_AUTH) &&
            existing?.sessionId != null &&
            existing.sessionId == activeConvSessionId
        val resumeId = if (shouldDropResumeId) null else activeConvSessionId
        val mcpPort = state.mcpHealth.endpointPort ?: 0
        val config = AcpSessionConfig(
            profileId = profileId,
            workspaceDir = workspaceDir,
            additionalDirs = listOf("/mnt/sdcard", workspaceDir).distinct(),
            model = state.selectedModel,
            thinkingEffort = state.selectedEffort,
            permissionMode = state.permissionMode,
            ideMcpPort = mcpPort,
            processPerSession = state.settings.acpProcessPerSession,
            resumeConversationId = resumeId,
            sandboxed = state.settings.acpSandboxEnabled,
        )
        val started = acpClient.startSession(config).getOrThrow()
        attachSessionIdToActiveConversation(started)
        return started
    }

    private fun attachSessionIdToActiveConversation(session: AcpSessionInfo) {
        var didUpdateConversation = false
        _uiState.update { state ->
            val activeId = state.activeConversationId
            val updatedConversations = if (activeId != null) {
                state.conversations.map { conv ->
                    if (conv.id == activeId && conv.sessionId != session.sessionId) {
                        didUpdateConversation = true
                        conv.copy(sessionId = session.sessionId)
                    } else {
                        conv
                    }
                }
            } else {
                state.conversations
            }
            state.copy(
                activeSession = session,
                conversations = updatedConversations,
            )
        }
        if (didUpdateConversation) {
            persistSnapshot()
        }
    }

    private fun handleSelectPermissionMode(mode: PermissionMode) {
        _uiState.update { it.copy(permissionMode = mode) }
        viewModelScope.launch {
            runSuspendCatching {
                securityPolicyEngine.setPermissionMode(mode)
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message) }
            }
        }
    }

    private fun handleCancelSession() {
        val sessionId = _uiState.value.activeSession?.sessionId ?: return
        viewModelScope.launch {
            runSuspendCatching {
                acpClient.cancelSession(sessionId).getOrThrow()
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "Oturum iptal edilemedi") }
            }
        }
    }

    private fun handleEmergencyStop() {
        viewModelScope.launch {
            runSuspendCatching {
                voiceTranscriber.cancel()
                _uiState.value.activeSession?.sessionId?.let { id ->
                    acpClient.cancelSession(id)
                }
                securityPolicyEngine.triggerEmergencyKillSwitch("UI Acil Durdurma")
            }.onSuccess {
                appendSystemMessage("Acil durdurma uygulandı; aktif ajan süreçleri sonlandırıldı.")
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "Acil durdurma başarısız") }
            }
        }
    }

    private fun handleToggleVoice() {
        viewModelScope.launch {
            runSuspendCatching {
                val current = voiceTranscriber.state.value
                if (current is VoiceState.Listening || current is VoiceState.Processing) {
                    voiceTranscriber.stopListening()
                } else {
                    voiceTranscriber.startListening(_uiState.value.voiceLocaleTag)
                }
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "Sesli dikte hatası") }
            }
        }
    }

    private fun handleCreateProfile(displayName: String, email: String?) {
        val cleanName = displayName.trim()
        if (cleanName.isEmpty()) return
        viewModelScope.launch {
            runSuspendCatching {
                val created = accountRepository.createProfile(cleanName, email?.trim()?.ifBlank { null }).getOrThrow()
                accountRepository.switchActiveProfile(created.id).getOrThrow()
                created
            }.onSuccess { created ->
                appendSystemMessage(
                    content = "Yeni profil oluşturuldu ve etkinleştirildi: ${created.displayName}",
                    onlyIfConversationActive = true,
                )
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "Profil oluşturulamadı") }
            }
        }
    }

    private fun handleSwitchProfile(profileId: String) {
        viewModelScope.launch {
            runSuspendCatching {
                _uiState.value.activeSession?.sessionId?.let { id ->
                    acpClient.cancelSession(id)
                }
                val switched = accountRepository.switchActiveProfile(profileId).getOrThrow()
                _uiState.update { state ->
                    state.copy(
                        activeProfile = switched,
                        authBanner = null,
                        quotaWarning = null,
                    )
                }
                evaluateQuotaWarning(switched)
                switched
            }.onSuccess { switched ->
                appendSystemMessage(
                    content = "Aktif Google hesabı değiştirildi: ${switched.displayName}",
                    onlyIfConversationActive = true,
                )
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "Hesap değiştirilemedi") }
            }
        }
    }

    private fun handleStartOAuthLogin(profileId: String, capturedAuthUrl: String?) {
        viewModelScope.launch {
            startOAuthLoginInternal(profileId, capturedAuthUrl)
        }
    }

    private suspend fun startOAuthLoginInternal(profileId: String, capturedAuthUrl: String?) {
        _uiState.update { state ->
            state.copy(
                errorMessage = null,
                onboarding = state.onboarding.copy(
                    isBusy = true,
                    statusMessage = "Google OAuth bağlantısı hazırlanıyor...",
                    errorMessage = null,
                    clipboardAutoAuthHint = null,
                    lastAutoSubmittedClipboardCode = null,
                ),
            )
        }
        runSuspendCatching {
            accountRepository.startLoginFlow(profileId, capturedAuthUrl).getOrThrow()
        }.onSuccess { challenge ->
            _uiState.update { state ->
                state.copy(
                    pendingChromeCustomTabUrl = challenge.authUrl,
                    onboarding = state.onboarding.copy(
                        isBusy = false,
                        statusMessage = "Google giriş sayfası açıldı. Giriş sonrası aldığınız kodu (veya yönlendirilen tam URL'yi) aşağıya yapıştırıp Onayla'ya basın.",
                        errorMessage = null,
                    ),
                )
            }
        }.onFailure { error ->
            val msg = error.message ?: "OAuth girişi başlatılamadı"
            _uiState.update { state ->
                state.copy(
                    errorMessage = msg,
                    onboarding = state.onboarding.copy(
                        isBusy = false,
                        errorMessage = msg,
                    ),
                )
            }
        }
    }

    private fun handleSubmitAuthCode(profileId: String, code: String) {
        val cleanCode = code.trim()
        if (cleanCode.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    errorMessage = null,
                    onboarding = state.onboarding.copy(
                        isBusy = true,
                        statusMessage = "Doğrulama kodu kontrol ediliyor...",
                        errorMessage = null,
                    ),
                )
            }
            runSuspendCatching {
                accountRepository.submitAuthCode(profileId, cleanCode).getOrThrow()
            }.onSuccess { profile ->
                val normalizedProfile = profile.email
                    ?.trim()
                    ?.ifBlank { null }
                    ?.let { applyDetectedEmailIfGeneric(profile, it) }
                    ?: profile
                val verifiedLabel = normalizedProfile.email ?: normalizedProfile.displayName
                _uiState.update { state ->
                    val updatedProfiles = state.profiles.map { existing ->
                        if (existing.id == normalizedProfile.id) normalizedProfile else existing
                    }
                    state.copy(
                        profiles = updatedProfiles,
                        authBanner = null,
                        activeProfile = normalizedProfile,
                        onboarding = state.onboarding.copy(
                            isBusy = false,
                            statusMessage = "Google hesabı doğrulandı: $verifiedLabel",
                            errorMessage = null,
                            clipboardAutoAuthHint = null,
                        ),
                    )
                }
                appendSystemMessage(
                    content = "Google hesabı doğrulandı: $verifiedLabel",
                    onlyIfConversationActive = true,
                )
            }.onFailure { error ->
                val msg = error.message ?: "Doğrulama kodu reddedildi"
                _uiState.update { state ->
                    state.copy(
                        errorMessage = msg,
                        onboarding = state.onboarding.copy(
                            isBusy = false,
                            errorMessage = msg,
                            clipboardAutoAuthHint = null,
                        ),
                    )
                }
            }
        }
    }

    private fun handleImportDesktopProfile(profileId: String, sourcePath: String) {
        val cleanPath = sourcePath.trim()
        if (cleanPath.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    errorMessage = null,
                    onboarding = state.onboarding.copy(
                        isBusy = true,
                        statusMessage = "Masaüstü ~/.gemini profili içe aktarılıyor...",
                        errorMessage = null,
                    ),
                )
            }
            runSuspendCatching {
                accountRepository.importDesktopProfile(profileId, cleanPath).getOrThrow()
            }.onSuccess { profile ->
                _uiState.update { state ->
                    state.copy(
                        authBanner = null,
                        activeProfile = profile,
                        onboarding = state.onboarding.copy(
                            isBusy = false,
                            statusMessage = "Masaüstü ~/.gemini profili içe aktarıldı: ${profile.displayName}",
                            errorMessage = null,
                        ),
                    )
                }
                appendSystemMessage(
                    content = "Masaüstü ~/.gemini profili içe aktarıldı: ${profile.displayName}",
                    onlyIfConversationActive = true,
                )
            }.onFailure { error ->
                val msg = error.message ?: "Masaüstü profili içe aktarılamadı"
                _uiState.update { state ->
                    state.copy(
                        errorMessage = msg,
                        onboarding = state.onboarding.copy(
                            isBusy = false,
                            errorMessage = msg,
                        ),
                    )
                }
            }
        }
    }

    private fun handleRefreshUsage(profileId: String?, postChatSummary: Boolean = false) {
        val targetProfile = if (profileId != null) {
            _uiState.value.profiles.firstOrNull { it.id == profileId }
                ?: _uiState.value.activeProfile?.takeIf { it.id == profileId }
        } else {
            _uiState.value.activeProfile
        } ?: return

        viewModelScope.launch {
            runSuspendCatching {
                val quota = accountRepository.refreshUsage(targetProfile.id).getOrThrow()
                val updatedProfile = targetProfile.copy(quota = quota)
                evaluateQuotaWarning(updatedProfile)
                quota
            }.onSuccess { quota ->
                if (postChatSummary) {
                    val selectedModel = _uiState.value.selectedModel
                    val headline = QuotaBucketParser.primaryHeadlinePercent(quota, selectedModel)
                    val weekly = QuotaBucketParser.weeklyPercent(quota, selectedModel)
                    val percentStr = headline?.let { "%.1f%%".format(Locale.US, it) } ?: "Bilinmiyor"
                    val summary = buildString {
                        append("`/usage` — **${targetProfile.displayName}** (${quota.planTier.name})\n")
                        append("Kalan kota: $percentStr")
                        if (weekly != null) {
                            append(" • Haftalık: ${"%.0f%%".format(Locale.US, weekly)}")
                        }
                        if (quota.usedRequests != null && quota.limitRequests != null) {
                            append(" (${quota.usedRequests}/${quota.limitRequests} istek)")
                        }
                        if (quota.rawSummary.isNotBlank()) {
                            append("\n${quota.rawSummary}")
                        }
                    }
                    appendSystemMessage(summary)
                }
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "Kota bilgisi yenilenemedi") }
            }
        }
    }

    private suspend fun evaluateQuotaWarning(profile: AccountProfile?) {
        if (profile == null) {
            _uiState.update { it.copy(quotaWarning = null) }
            return
        }
        val effectiveRemaining = QuotaBucketParser.primaryHeadlinePercent(
            quota = profile.quota,
            selectedModel = _uiState.value.selectedModel,
        ) ?: profile.quota.remainingPercent
        if (effectiveRemaining == null || effectiveRemaining > 15.0) {
            _uiState.update { it.copy(quotaWarning = null) }
            return
        }
        val fallback = runSuspendCatching {
            accountRepository.suggestFallbackProfile(profile.id)
        }.getOrNull()

        _uiState.update {
            it.copy(
                quotaWarning = QuotaWarningState(
                    currentProfile = profile,
                    remainingPercent = effectiveRemaining,
                    suggestedFallbackProfile = fallback,
                ),
            )
        }
    }

    private fun handleSelectProject(projectId: String) {
        val target = _uiState.value.projects.firstOrNull { it.id == projectId } ?: return
        _uiState.update { state ->
            val currentConv = state.activeConversation
            if (currentConv != null && currentConv.projectId == target.id) {
                state.copy(
                    selectedProjectId = target.id,
                    expandedProjectIds = state.expandedProjectIds + target.id,
                    mentionSuggestions = buildMentionSuggestions(target),
                )
            } else {
                val existingConv = state.conversations
                    .filter { it.projectId == target.id }
                    .maxByOrNull { it.updatedAtEpochMs }
                state.copy(
                    selectedProjectId = target.id,
                    activeConversationId = existingConv?.id,
                    messages = existingConv?.messages.orEmpty(),
                    expandedProjectIds = state.expandedProjectIds + target.id,
                    mentionSuggestions = buildMentionSuggestions(target),
                )
            }
        }
        handleSelectPermissionMode(target.defaultPermissionMode)
        persistSnapshot()
    }

    private fun handleCreateProject(action: WorkspaceAction.CreateProject) {
        val name = action.name.trim()
        val path = action.rootPath.trim()
        if (name.isEmpty() || path.isEmpty()) return
        val newProject = WorkspaceProject(
            id = "proj-${UUID.randomUUID().toString().take(8)}",
            name = name,
            rootPath = path,
            profileId = _uiState.value.activeProfile?.id ?: "default",
            defaultPermissionMode = action.defaultPermissionMode,
            lastOpenedAtEpochMs = clockMs(),
        )
        _uiState.update { state ->
            state.copy(
                projects = state.projects + newProject,
                selectedProjectId = newProject.id,
                activeConversationId = null,
                messages = emptyList(),
                expandedProjectIds = state.expandedProjectIds + newProject.id,
                mentionSuggestions = buildMentionSuggestions(newProject),
            )
        }
        persistSnapshot()
    }

    private fun handleRenameProject(projectId: String, newName: String) {
        val cleanName = newName.trim()
        if (cleanName.isEmpty()) return
        _uiState.update { state ->
            val updatedProjects = state.projects.map { project ->
                if (project.id == projectId) project.copy(name = cleanName) else project
            }
            val selected = updatedProjects.firstOrNull { it.id == state.selectedProjectId }
            state.copy(
                projects = updatedProjects,
                mentionSuggestions = buildMentionSuggestions(selected),
            )
        }
        persistSnapshot()
    }

    private fun handleDeleteProject(projectId: String) {
        _uiState.update { state ->
            if (state.projects.none { it.id == projectId }) return@update state
            val remainingProjects = state.projects.filterNot { it.id == projectId }
            val remainingConversations = state.conversations.filterNot { it.projectId == projectId }
            val updatedExpanded = state.expandedProjectIds - projectId
            val activeConvWasDeleted = state.activeConversation?.projectId == projectId

            if (remainingProjects.isEmpty()) {
                val nextStack = if (state.currentDestination is AppDestination.Conversation) {
                    listOf(AppDestination.Hub)
                } else {
                    state.backStack
                }
                withBackStack(
                    state = state.copy(
                        projects = emptyList(),
                        selectedProjectId = null,
                        conversations = remainingConversations,
                        activeConversationId = null,
                        messages = emptyList(),
                        expandedProjectIds = emptySet(),
                        mentionSuggestions = defaultMentions(),
                    ),
                    rawStack = nextStack,
                )
            } else if (state.selectedProjectId == projectId) {
                val nextProject = remainingProjects.first()
                val existingConv = remainingConversations
                    .filter { it.projectId == nextProject.id }
                    .maxByOrNull { it.updatedAtEpochMs }
                val nextStack = if (state.currentDestination is AppDestination.Conversation) {
                    if (existingConv != null) {
                        state.backStack.dropLast(1) + AppDestination.Conversation(existingConv.id)
                    } else {
                        listOf(AppDestination.Hub)
                    }
                } else {
                    state.backStack
                }
                withBackStack(
                    state = state.copy(
                        projects = remainingProjects,
                        selectedProjectId = nextProject.id,
                        conversations = remainingConversations,
                        activeConversationId = existingConv?.id,
                        messages = existingConv?.messages.orEmpty(),
                        expandedProjectIds = updatedExpanded + nextProject.id,
                        mentionSuggestions = buildMentionSuggestions(nextProject),
                    ),
                    rawStack = nextStack,
                )
            } else {
                val nextActiveConvId = if (activeConvWasDeleted) null else state.activeConversationId
                val nextMessages = if (activeConvWasDeleted) emptyList() else state.messages
                val nextStack = if (activeConvWasDeleted && state.currentDestination is AppDestination.Conversation) {
                    listOf(AppDestination.Hub)
                } else {
                    state.backStack
                }
                withBackStack(
                    state = state.copy(
                        projects = remainingProjects,
                        conversations = remainingConversations,
                        activeConversationId = nextActiveConvId,
                        messages = nextMessages,
                        expandedProjectIds = updatedExpanded,
                    ),
                    rawStack = nextStack,
                )
            }
        }
        persistSnapshot()
    }

    private fun handleToggleProjectExpanded(projectId: String) {
        _uiState.update { state ->
            val next = if (projectId in state.expandedProjectIds) {
                state.expandedProjectIds - projectId
            } else {
                state.expandedProjectIds + projectId
            }
            state.copy(expandedProjectIds = next)
        }
    }

    private fun handleCreateConversation(projectId: String?, title: String?) {
        val currentState = _uiState.value
        val activeSessionId = currentState.activeSession?.sessionId
        if (activeSessionId != null &&
            currentState.sessionState != AcpSessionState.DISCONNECTED &&
            currentState.sessionState != AcpSessionState.CLOSED
        ) {
            viewModelScope.launch {
                runSuspendCatching { acpClient.cancelSession(activeSessionId) }
            }
        }

        val targetProject = projectId?.let { id ->
            currentState.projects.firstOrNull { it.id == id }
        } ?: currentState.selectedProject ?: currentState.projects.firstOrNull()
        val cleanTitle = title?.trim()?.ifBlank { null }

        if (cleanTitle == null) {
            // Fresh draft conversation: avoid cluttering the list with empty "Yeni Sohbet" rows.
            _uiState.update { state ->
                val nextExpanded = if (targetProject != null) {
                    state.expandedProjectIds + targetProject.id
                } else {
                    state.expandedProjectIds
                }
                withBackStack(
                    state = state.copy(
                        selectedProjectId = targetProject?.id ?: state.selectedProjectId,
                        activeConversationId = null,
                        messages = emptyList(),
                        expandedProjectIds = nextExpanded,
                        mentionSuggestions = buildMentionSuggestions(targetProject),
                        showSlashMenu = false,
                        showMentionMenu = false,
                        showModelPickerSheet = false,
                        showComposerToolsSheet = false,
                    ),
                    rawStack = listOf(AppDestination.Hub, AppDestination.Conversation(null)),
                )
            }
            return
        }

        val targetProjectId = targetProject?.id ?: DEVICE_FALLBACK_PROJECT.id
        val newConv = newEmptyConversation(
            projectId = targetProjectId,
            title = cleanTitle,
        )

        _uiState.update { state ->
            val nextExpanded = if (targetProject != null) {
                state.expandedProjectIds + targetProject.id
            } else {
                state.expandedProjectIds
            }
            withBackStack(
                state = state.copy(
                    selectedProjectId = targetProject?.id ?: state.selectedProjectId,
                    conversations = listOf(newConv) + state.conversations,
                    activeConversationId = newConv.id,
                    messages = emptyList(),
                    expandedProjectIds = nextExpanded,
                    mentionSuggestions = buildMentionSuggestions(targetProject),
                    showSlashMenu = false,
                    showMentionMenu = false,
                    showModelPickerSheet = false,
                    showComposerToolsSheet = false,
                ),
                rawStack = listOf(AppDestination.Hub, AppDestination.Conversation(newConv.id)),
            )
        }
        persistSnapshot()
    }

    private fun handleSelectConversation(conversationId: String) {
        val current = _uiState.value
        val target = current.conversations.firstOrNull { it.id == conversationId } ?: return
        val targetProject = current.projects.firstOrNull { it.id == target.projectId }
        val projectChanged = targetProject != null && targetProject.id != current.selectedProjectId

        _uiState.update { state ->
            val nextExpanded = if (targetProject != null) {
                state.expandedProjectIds + targetProject.id
            } else {
                state.expandedProjectIds
            }
            withBackStack(
                state = state.copy(
                    selectedProjectId = targetProject?.id ?: state.selectedProjectId,
                    activeConversationId = target.id,
                    messages = target.messages,
                    expandedProjectIds = nextExpanded,
                    mentionSuggestions = buildMentionSuggestions(targetProject ?: state.selectedProject),
                    showSlashMenu = false,
                    showMentionMenu = false,
                ),
                rawStack = listOf(AppDestination.Hub, AppDestination.Conversation(target.id)),
            )
        }
        if (projectChanged) {
            handleSelectPermissionMode(targetProject.defaultPermissionMode)
        }
        persistSnapshot()
    }

    private fun handleDeleteConversation(conversationId: String) {
        _uiState.update { state ->
            val target = state.conversations.firstOrNull { it.id == conversationId } ?: return@update state
            val remaining = state.conversations.filterNot { it.id == conversationId }

            if (state.activeConversationId != conversationId) {
                state.copy(conversations = remaining)
            } else {
                val nextInProject = remaining
                    .filter { it.projectId == target.projectId }
                    .maxByOrNull { it.updatedAtEpochMs }
                if (nextInProject != null) {
                    val nextStack = if (state.currentDestination is AppDestination.Conversation) {
                        state.backStack.dropLast(1) + AppDestination.Conversation(nextInProject.id)
                    } else {
                        state.backStack
                    }
                    withBackStack(
                        state = state.copy(
                            conversations = remaining,
                            activeConversationId = nextInProject.id,
                            messages = nextInProject.messages,
                        ),
                        rawStack = nextStack,
                    )
                } else {
                    val nextStack = if (state.currentDestination is AppDestination.Conversation) {
                        state.backStack.dropLast(1).ifEmpty { listOf(AppDestination.Hub) }
                    } else {
                        state.backStack
                    }
                    withBackStack(
                        state = state.copy(
                            conversations = remaining,
                            activeConversationId = null,
                            messages = emptyList(),
                        ),
                        rawStack = nextStack,
                    )
                }
            }
        }
        persistSnapshot()
    }

    private fun handleRenameConversation(conversationId: String, newTitle: String) {
        val cleanTitle = newTitle.trim()
        if (cleanTitle.isEmpty()) return
        val now = clockMs()
        _uiState.update { state ->
            state.copy(
                conversations = state.conversations.map { conv ->
                    if (conv.id == conversationId) {
                        conv.copy(title = cleanTitle, updatedAtEpochMs = now)
                    } else {
                        conv
                    }
                },
            )
        }
        persistSnapshot()
    }

    private fun handleTogglePinConversation(conversationId: String) {
        _uiState.update { state ->
            state.copy(
                conversations = state.conversations.map { conv ->
                    if (conv.id == conversationId) conv.copy(isPinned = !conv.isPinned) else conv
                },
            )
        }
        persistSnapshot()
    }

    // --- Deterministic Navigation Back-Stack Handlers ---

    private fun handleNavigateBack() {
        _uiState.update { state ->
            when {
                // 1. Composer menus
                state.showSlashMenu || state.showMentionMenu -> {
                    state.copy(showSlashMenu = false, showMentionMenu = false)
                }

                // 2. Model picker bottom sheet
                state.showModelPickerSheet -> {
                    state.copy(showModelPickerSheet = false)
                }

                // 3. Composer tools bottom sheet
                state.showComposerToolsSheet -> {
                    state.copy(showComposerToolsSheet = false)
                }

                // 4. Quota dashboard bottom sheet
                state.showQuotaSheet -> {
                    state.copy(showQuotaSheet = false)
                }

                // 5. Overview bottom sheet (Subagents / Swarm / Diffs)
                state.isOverviewSheetVisible -> {
                    state.copy(isOverviewSheetVisible = false)
                }

                // 6. Pop back-stack destination cleanly
                state.backStack.size > 1 -> {
                    val popped = state.backStack.dropLast(1)
                    val newTop = popped.last()
                    val syncedState = if (newTop is AppDestination.Conversation && newTop.conversationId != null) {
                        val conv = state.conversations.firstOrNull { it.id == newTop.conversationId }
                        if (conv != null && state.activeConversationId != conv.id) {
                            state.copy(activeConversationId = conv.id, messages = conv.messages)
                        } else {
                            state
                        }
                    } else {
                        state
                    }
                    withBackStack(syncedState, popped)
                }

                // 7. Root Hub -> Double-Back-to-Exit (2000ms window)
                else -> {
                    val now = clockMs()
                    val elapsed = now - lastRootBackPressEpochMs
                    if (state.exitConfirmPending && lastRootBackPressEpochMs > 0L && elapsed in 0L..EXIT_CONFIRM_WINDOW_MS) {
                        lastRootBackPressEpochMs = 0L
                        state.copy(
                            exitConfirmPending = false,
                            exitConfirmMessage = null,
                            shouldExitApp = true,
                        )
                    } else {
                        lastRootBackPressEpochMs = now
                        state.copy(
                            exitConfirmPending = true,
                            exitConfirmMessage = EXIT_CONFIRM_MESSAGE,
                            shouldExitApp = false,
                        )
                    }
                }
            }
        }
    }

    private fun handleNavigateToHub() {
        _uiState.update { state ->
            withBackStack(
                state = state.copy(
                    showSlashMenu = false,
                    showMentionMenu = false,
                    showModelPickerSheet = false,
                    showComposerToolsSheet = false,
                    showQuotaSheet = false,
                    isOverviewSheetVisible = false,
                ),
                rawStack = listOf(AppDestination.Hub),
            )
        }
    }

    private fun handleNavigateToConversation(conversationId: String?) {
        if (conversationId != null && _uiState.value.conversations.any { it.id == conversationId }) {
            handleSelectConversation(conversationId)
            return
        }
        _uiState.update { state ->
            withBackStack(
                state = state.copy(
                    activeConversationId = null,
                    messages = emptyList(),
                    showSlashMenu = false,
                    showMentionMenu = false,
                    showModelPickerSheet = false,
                    showComposerToolsSheet = false,
                    showQuotaSheet = false,
                    isOverviewSheetVisible = false,
                ),
                rawStack = listOf(AppDestination.Hub, AppDestination.Conversation(null)),
            )
        }
    }

    private fun handleOpenSettingsList() {
        _uiState.update { state ->
            val existingIdx = state.backStack.indexOf(AppDestination.SettingsList)
            val nextStack = if (existingIdx >= 0) {
                state.backStack.take(existingIdx + 1)
            } else {
                val base = state.backStack
                    .dropLastWhile {
                        it is AppDestination.Onboarding ||
                            it is AppDestination.SettingsDetail ||
                            it is AppDestination.SettingsList
                    }
                    .ifEmpty { listOf(AppDestination.Hub) }
                base + AppDestination.SettingsList
            }
            withBackStack(
                state = state.copy(
                    showSlashMenu = false,
                    showMentionMenu = false,
                    showModelPickerSheet = false,
                    showComposerToolsSheet = false,
                    showQuotaSheet = false,
                    isOverviewSheetVisible = false,
                ),
                rawStack = nextStack,
            )
        }
    }

    private fun handleOpenSettingsDetail(category: SettingsCategory) {
        _uiState.update { state ->
            val existingListIdx = state.backStack.indexOf(AppDestination.SettingsList)
            val nextStack = if (existingListIdx >= 0) {
                state.backStack.take(existingListIdx + 1) + AppDestination.SettingsDetail(category)
            } else {
                val base = state.backStack
                    .dropLastWhile {
                        it is AppDestination.Onboarding ||
                            it is AppDestination.SettingsDetail ||
                            it is AppDestination.SettingsList
                    }
                    .ifEmpty { listOf(AppDestination.Hub) }
                base + AppDestination.SettingsList + AppDestination.SettingsDetail(category)
            }
            withBackStack(
                state = state.copy(
                    showSlashMenu = false,
                    showMentionMenu = false,
                    showModelPickerSheet = false,
                    showComposerToolsSheet = false,
                    showQuotaSheet = false,
                    isOverviewSheetVisible = false,
                ),
                rawStack = nextStack,
            )
        }
        if (category == SettingsCategory.ACCOUNTS_QUOTA) {
            maybeAutoRefreshActiveProfileQuota()
        }
    }

    private fun handleOpenSettings(category: SettingsCategory) {
        handleOpenSettingsDetail(category)
    }

    private fun handleCloseSettings() {
        _uiState.update { state ->
            val base = state.backStack
                .dropLastWhile {
                    it is AppDestination.Onboarding ||
                        it is AppDestination.SettingsDetail ||
                        it is AppDestination.SettingsList
                }
                .ifEmpty { listOf(AppDestination.Hub) }
            withBackStack(state, base)
        }
    }

    private fun handleSelectSettingsCategory(category: SettingsCategory) {
        _uiState.update { state ->
            val nextStack = when (state.currentDestination) {
                is AppDestination.SettingsDetail -> {
                    state.backStack.dropLast(1) + AppDestination.SettingsDetail(category)
                }

                AppDestination.SettingsList -> {
                    state.backStack + AppDestination.SettingsDetail(category)
                }

                else -> state.backStack
            }
            withBackStack(
                state = state.copy(selectedSettingsCategory = category),
                rawStack = nextStack,
            )
        }
        if (category == SettingsCategory.ACCOUNTS_QUOTA) {
            maybeAutoRefreshActiveProfileQuota()
        }
    }

    private fun handleUpdateSettings(transform: (AppSettings) -> AppSettings) {
        var pendingToApprove: AcpEvent.PermissionRequest? = null
        _uiState.update { state ->
            val updated = transform(state.settings)
            val shouldAutoApprove = updated.fullAccessEnabled && state.pendingPermissionRequest != null
            if (shouldAutoApprove) {
                pendingToApprove = state.pendingPermissionRequest
            }
            state.copy(
                settings = updated,
                pendingPermissionRequest = if (shouldAutoApprove) null else state.pendingPermissionRequest,
                autoApprovedToolCount = if (shouldAutoApprove) {
                    state.autoApprovedToolCount + 1
                } else {
                    state.autoApprovedToolCount
                },
            )
        }
        pendingToApprove?.let { autoApprovePermission(it) }
        persistSnapshot()
    }

    private fun handleOpenQuotaSheet() {
        _uiState.update { it.copy(showQuotaSheet = true) }
        maybeAutoRefreshActiveProfileQuota()
    }

    private fun handleOpenOnboardingFromSettings(fromSettings: Boolean) {
        _uiState.update { state ->
            val nextStack = if (fromSettings) {
                when (state.currentDestination) {
                    is AppDestination.SettingsDetail, AppDestination.SettingsList -> {
                        state.backStack + AppDestination.Onboarding
                    }

                    AppDestination.Onboarding -> state.backStack
                    else -> {
                        val base = state.backStack.ifEmpty { listOf(AppDestination.Hub) }
                        base + AppDestination.SettingsList +
                            AppDestination.SettingsDetail(SettingsCategory.MOBILE_ROOT) +
                            AppDestination.Onboarding
                    }
                }
            } else {
                val base = state.backStack
                    .dropLastWhile { it is AppDestination.Onboarding }
                    .ifEmpty { listOf(AppDestination.Hub) }
                base + AppDestination.Onboarding
            }
            withBackStack(
                state = state.copy(
                    showSlashMenu = false,
                    showMentionMenu = false,
                    showModelPickerSheet = false,
                    showComposerToolsSheet = false,
                    showQuotaSheet = false,
                    isOverviewSheetVisible = false,
                ),
                rawStack = nextStack,
            )
        }
    }

    private fun handleSetShowOnboarding(show: Boolean) {
        if (!show) {
            hasAutoOpenedOnboarding = true
        }
        _uiState.update { state ->
            val nextStack = if (show) {
                if (state.currentDestination is AppDestination.Onboarding) {
                    state.backStack
                } else {
                    state.backStack + AppDestination.Onboarding
                }
            } else {
                state.backStack.filterNot { it is AppDestination.Onboarding }.ifEmpty { listOf(AppDestination.Hub) }
            }
            withBackStack(state, nextStack)
        }
    }

    private fun handleDismissOnboarding() {
        hasAutoOpenedOnboarding = true
        _uiState.update { state ->
            val nextStack = if (state.currentDestination is AppDestination.Onboarding && state.backStack.size > 1) {
                state.backStack.dropLast(1)
            } else {
                state.backStack.filterNot { it is AppDestination.Onboarding }.ifEmpty { listOf(AppDestination.Hub) }
            }
            withBackStack(state, nextStack)
        }
    }

    private fun handleCompleteOnboarding() {
        hasAutoOpenedOnboarding = true
        _uiState.update { state ->
            val nextStack = if (state.currentDestination is AppDestination.Onboarding && state.backStack.size > 1) {
                state.backStack.dropLast(1)
            } else {
                state.backStack.filterNot { it is AppDestination.Onboarding }.ifEmpty { listOf(AppDestination.Hub) }
            }
            withBackStack(
                state = state.copy(onboarding = state.onboarding.copy(isCompleted = true)),
                rawStack = nextStack,
            )
        }
    }

    private fun withBackStack(
        state: WorkspaceUiState,
        rawStack: List<AppDestination>,
        clearExitConfirm: Boolean = true,
    ): WorkspaceUiState {
        val normalized = if (rawStack.isEmpty()) listOf(AppDestination.Hub) else rawStack
        val top = normalized.last()
        val previous = normalized.dropLast(1).lastOrNull()
        val isSettingsTop = top is AppDestination.SettingsList || top is AppDestination.SettingsDetail
        val isOnboardingTop = top is AppDestination.Onboarding
        val nextCategory = (top as? AppDestination.SettingsDetail)?.category
            ?: state.selectedSettingsCategory
        val returnToSettings = isOnboardingTop &&
            (previous is AppDestination.SettingsList || previous is AppDestination.SettingsDetail)
        if (clearExitConfirm) {
            lastRootBackPressEpochMs = 0L
        }
        return state.copy(
            backStack = normalized,
            showSettings = isSettingsTop,
            showOnboarding = isOnboardingTop,
            selectedSettingsCategory = nextCategory,
            onboardingReturnToSettings = returnToSettings,
            exitConfirmPending = if (clearExitConfirm) false else state.exitConfirmPending,
            exitConfirmMessage = if (clearExitConfirm) null else state.exitConfirmMessage,
            shouldExitApp = if (clearExitConfirm) false else state.shouldExitApp,
        )
    }

    private fun maybeAutoRefreshActiveProfileQuota(state: WorkspaceUiState = _uiState.value) {
        val active = state.activeProfile ?: return
        if (!active.isAuthenticated) return
        if (active.quota.updatedAtEpochMs == 0L && active.quota.remainingPercent == null && !state.isRefreshingQuota) {
            handleRefreshAllQuotas()
        }
    }

    private fun handleRefreshAllQuotas() {
        if (_uiState.value.isRefreshingQuota) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshingQuota = true) }
            try {
                val snapshot = _uiState.value
                val targets = snapshot.profiles
                    .filter { it.isAuthenticated }
                    .ifEmpty { listOfNotNull(snapshot.activeProfile) }
                if (targets.isEmpty()) return@launch

                var successCount = 0
                var lastError: String? = null
                for (profile in targets) {
                    runSuspendCatching {
                        accountRepository.refreshUsage(profile.id).getOrThrow()
                    }.onSuccess { quota ->
                        successCount++
                        val updatedProfile = profile.copy(quota = quota)
                        _uiState.update { state ->
                            state.copy(
                                profiles = state.profiles.map { p ->
                                    if (p.id == profile.id) p.copy(quota = quota) else p
                                },
                                activeProfile = if (state.activeProfile?.id == profile.id) {
                                    state.activeProfile.copy(quota = quota)
                                } else {
                                    state.activeProfile
                                },
                            )
                        }
                        if (_uiState.value.activeProfile?.id == profile.id) {
                            evaluateQuotaWarning(updatedProfile)
                        }
                    }.onFailure { error ->
                        lastError = error.message
                    }
                }
                if (successCount == 0 && lastError != null) {
                    _uiState.update { it.copy(errorMessage = lastError ?: "Kota bilgileri yenilenemedi") }
                }
            } finally {
                _uiState.update { it.copy(isRefreshingQuota = false) }
            }
        }
    }

    private fun handleDeleteProfile(profileId: String) {
        viewModelScope.launch {
            runSuspendCatching {
                if (_uiState.value.activeProfile?.id == profileId) {
                    _uiState.value.activeSession?.sessionId?.let { id ->
                        acpClient.cancelSession(id)
                    }
                }
                accountRepository.deleteProfile(profileId).getOrThrow()
            }.onSuccess {
                _uiState.update { state ->
                    val remaining = state.profiles.filterNot { it.id == profileId }
                    val nextActive = if (state.activeProfile?.id == profileId) {
                        remaining.firstOrNull()
                    } else {
                        state.activeProfile
                    }
                    state.copy(
                        profiles = remaining,
                        activeProfile = nextActive,
                        authBanner = if (state.authBanner?.profileId == profileId) null else state.authBanner,
                        quotaWarning = if (state.quotaWarning?.currentProfile?.id == profileId) null else state.quotaWarning,
                    )
                }
                appendSystemMessage(
                    content = "Google profili silindi.",
                    onlyIfConversationActive = true,
                )
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "Profil silinemedi") }
            }
        }
    }

    private fun handleUnmountChroot() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(onboarding = it.onboarding.copy(isBusy = true, errorMessage = null))
            }
            runSuspendCatching {
                chrootManager.unmountSafely(force = false).getOrThrow()
            }.onSuccess {
                _uiState.update { state ->
                    state.copy(
                        onboarding = state.onboarding.copy(
                            isBusy = false,
                            statusMessage = "ARM64 chroot güvenli şekilde ayrıldı",
                            errorMessage = null,
                        ),
                    )
                }
            }.onFailure { error ->
                val msg = error.message ?: "Chroot ayrılamadı"
                _uiState.update { state ->
                    state.copy(
                        errorMessage = msg,
                        onboarding = state.onboarding.copy(
                            isBusy = false,
                            errorMessage = msg,
                        ),
                    )
                }
            }
        }
    }

    private fun autoApprovePermission(request: AcpEvent.PermissionRequest) {
        val response = PermissionResponse(
            requestId = request.requestId,
            approved = true,
            rememberForSession = true,
        )
        viewModelScope.launch {
            runSuspendCatching {
                acpClient.respondToPermission(response).getOrThrow()
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(
                        pendingPermissionRequest = state.pendingPermissionRequest ?: request,
                        errorMessage = error.message ?: "Otomatik izin yanıtı iletilemedi",
                    )
                }
            }
        }
    }

    private fun handleRespondToPermission(action: WorkspaceAction.RespondToPermission) {
        val response = PermissionResponse(
            requestId = action.requestId,
            approved = action.approved,
            rememberForSession = action.rememberForSession,
        )
        viewModelScope.launch {
            runSuspendCatching {
                acpClient.respondToPermission(response).getOrThrow()
            }.onSuccess {
                _uiState.update { state ->
                    if (state.pendingPermissionRequest?.requestId == action.requestId) {
                        state.copy(pendingPermissionRequest = null)
                    } else {
                        state
                    }
                }
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "İzin yanıtı iletilemedi") }
            }
        }
    }

    private fun updateDiffStatus(diffId: String, status: DiffApprovalStatus) {
        _uiState.update { state ->
            state.copy(
                diffs = state.diffs.map { item ->
                    if (item.id == diffId) item.copy(status = status) else item
                },
            )
        }
    }

    private fun handleScheduleTask(action: WorkspaceAction.ScheduleTask) {
        val profileId = _uiState.value.activeProfile?.id ?: "default"
        val task = ScheduledTask(
            id = "task-${UUID.randomUUID().toString().take(8)}",
            name = action.name.ifBlank { action.prompt.take(32) },
            prompt = action.prompt,
            profileId = profileId,
            projectId = _uiState.value.selectedProjectId,
            cronExpression = action.cronExpression,
            triggerAtEpochMs = action.triggerAtEpochMs ?: if (action.cronExpression == null) {
                System.currentTimeMillis() + 60_000L
            } else {
                null
            },
            enabled = true,
            exactTiming = action.exactTiming,
            createdAtEpochMs = System.currentTimeMillis(),
        )
        viewModelScope.launch {
            runSuspendCatching {
                automationScheduler.scheduleTask(task).getOrThrow()
            }.onSuccess { scheduled ->
                appendSystemMessage("Otomasyon zamanlandı (`/schedule`): **${scheduled.name}**")
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "Görev zamanlanamadı") }
            }
        }
    }

    private fun handleCancelScheduledTask(taskId: String) {
        viewModelScope.launch {
            runSuspendCatching { automationScheduler.cancelTask(taskId).getOrThrow() }
                .onFailure { error -> _uiState.update { it.copy(errorMessage = error.message) } }
        }
    }

    private fun handleToggleScheduledTask(taskId: String, enabled: Boolean) {
        viewModelScope.launch {
            runSuspendCatching { automationScheduler.setTaskEnabled(taskId, enabled).getOrThrow() }
                .onFailure { error -> _uiState.update { it.copy(errorMessage = error.message) } }
        }
    }

    private fun handleTriggerScheduledTask(taskId: String) {
        viewModelScope.launch {
            runSuspendCatching { automationScheduler.triggerNow(taskId).getOrThrow() }
                .onSuccess { run ->
                    appendSystemMessage("Zamanlanmış görev tetiklendi: `${run.taskId}` (${run.status.name})")
                }
                .onFailure { error -> _uiState.update { it.copy(errorMessage = error.message) } }
        }
    }

    private fun handleAddProtectedPackage(packageName: String) {
        val clean = packageName.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            runSuspendCatching { securityPolicyEngine.addProtectedPackage(clean) }
                .onFailure { error -> _uiState.update { it.copy(errorMessage = error.message) } }
        }
    }

    private fun handleRemoveProtectedPackage(packageName: String) {
        viewModelScope.launch {
            runSuspendCatching { securityPolicyEngine.removeProtectedPackage(packageName) }
                .onFailure { error -> _uiState.update { it.copy(errorMessage = error.message) } }
        }
    }

    private fun handleToggleCustomization(itemId: String) {
        _uiState.update { state ->
            state.copy(
                customizations = state.customizations.map { item ->
                    if (item.id == itemId) item.copy(enabled = !item.enabled) else item
                },
            )
        }
    }

    private fun refreshMcpTools() {
        _uiState.update { state ->
            state.copy(mcpTools = androidMcpServer.registeredTools())
        }
    }

    // --- Onboarding Actions ---

    private fun handleCheckRootStatus() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(onboarding = it.onboarding.copy(isBusy = true, errorMessage = null))
            }
            runSuspendCatching {
                rootExecutor.detectEnvironment()
            }.onSuccess { env ->
                _uiState.update { state ->
                    state.copy(
                        onboarding = state.onboarding.copy(
                            rootEnvironment = env,
                            isBusy = false,
                            statusMessage = if (env.isRootGranted) {
                                "${env.provider.name} root erişimi doğrulandı"
                            } else {
                                env.guidanceMessage.ifBlank {
                                    "KernelSU Manager / Magisk içinden Antigravity Mobile uygulamasına Süper Kullanıcı yetkisi verin."
                                }
                            },
                        ),
                    )
                }
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(
                        onboarding = state.onboarding.copy(
                            isBusy = false,
                            errorMessage = error.message ?: "Root durumu denetlenemedi",
                        ),
                    )
                }
            }
        }
    }

    private fun handleRunRootBootstrap(action: WorkspaceAction.RunRootBootstrap) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(onboarding = it.onboarding.copy(isBusy = true, errorMessage = null))
            }
            runSuspendCatching {
                rootExecutor.bootstrapPermissions(
                    packageName = action.packageName,
                    accessibilityServiceComponent = action.accessibilityServiceComponent,
                    notificationListenerComponent = action.notificationListenerComponent,
                )
            }.onSuccess { report ->
                _uiState.update { state ->
                    state.copy(
                        onboarding = state.onboarding.copy(
                            rootEnvironment = report.environment,
                            bootstrapReport = report,
                            isBusy = false,
                            statusMessage = if (report.allCriticalReady) {
                                "Tüm kritik izinler ve sistem servisleri etkinleştirildi"
                            } else {
                                "İzin otomasyonu kısmi tamamlandı; uyarıları kontrol edin"
                            },
                        ),
                    )
                }
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(
                        onboarding = state.onboarding.copy(
                            isBusy = false,
                            errorMessage = error.message ?: "Root izin otomasyonu başarısız",
                        ),
                    )
                }
            }
        }
    }

    private fun handleSetupChroot() {
        val onboarding = _uiState.value.onboarding
        val source: RootfsSource = when (onboarding.selectedRootfsSource) {
            RootfsSourceOption.BUNDLED_ASSET -> RootfsSource.BundledAsset()
            RootfsSourceOption.REMOTE_DOWNLOAD -> RootfsSource.RemoteDownload(url = onboarding.remoteRootfsUrl)
            RootfsSourceOption.LOCAL_FILE -> RootfsSource.LocalFile(absolutePath = onboarding.localRootfsPath)
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(onboarding = it.onboarding.copy(isBusy = true, errorMessage = null))
            }
            runSuspendCatching {
                chrootManager.ensureRootfsReady(source).getOrThrow()
                chrootManager.mountAll().getOrThrow()
            }.onSuccess { mountStatus ->
                _uiState.update { state ->
                    state.copy(
                        onboarding = state.onboarding.copy(
                            isBusy = false,
                            statusMessage = if (mountStatus.allCoreMounted) {
                                "ARM64 glibc chroot hazır ve tüm bind-mount noktaları bağlı"
                            } else {
                                "Rootfs kuruldu ancak bazı mount noktaları eksik"
                            },
                        ),
                    )
                }
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(
                        onboarding = state.onboarding.copy(
                            isBusy = false,
                            errorMessage = error.message ?: "ARM64 rootfs kurulumu başarısız",
                        ),
                    )
                }
            }
        }
    }

    private fun handleCheckOrInstallAgy() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(onboarding = it.onboarding.copy(isBusy = true, errorMessage = null))
            }
            runSuspendCatching {
                val existingVersion = chrootManager.getAgyVersion().getOrNull()
                if (!existingVersion.isNullOrBlank()) {
                    existingVersion
                } else {
                    chrootManager.installOrUpdateAgy().getOrThrow()
                }
            }.onSuccess { version ->
                _uiState.update { state ->
                    state.copy(
                        onboarding = state.onboarding.copy(
                            agyVersion = version,
                            isBusy = false,
                            statusMessage = "Antigravity CLI doğrulandı: agy $version",
                        ),
                    )
                }
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(
                        onboarding = state.onboarding.copy(
                            isBusy = false,
                            errorMessage = error.message ?: "agy kurulumu/sürüm kontrolü başarısız",
                        ),
                    )
                }
            }
        }
    }

    private fun handleRunFullDiagnostics(action: WorkspaceAction.RunFullDiagnostics) {
        if (_uiState.value.onboarding.isRunningDiagnostics) return
        viewModelScope.launch {
            runDiagnosticsInternal(
                packageName = action.packageName,
                isOverlayGranted = action.isOverlayGranted,
                isDeviceAdminActive = action.isDeviceAdminActive,
                allowAutoOpenOnboarding = true,
                preserveVerifiedState = false,
            )
        }
    }

    private suspend fun runDiagnosticsInternal(
        packageName: String,
        isOverlayGranted: Boolean,
        isDeviceAdminActive: Boolean,
        allowAutoOpenOnboarding: Boolean,
        preserveVerifiedState: Boolean,
    ) {
        _uiState.update { state ->
            state.copy(
                onboarding = state.onboarding.copy(
                    isRunningDiagnostics = true,
                    isOverlayPermissionGranted = isOverlayGranted,
                    isDeviceAdminActive = isDeviceAdminActive,
                ),
            )
        }
        try {
            val detectedEnv = runSuspendCatching { rootExecutor.detectEnvironment() }.getOrNull()
            val effectiveRootEnv = detectedEnv ?: _uiState.value.onboarding.rootEnvironment
            val rootGranted = effectiveRootEnv?.isRootGranted == true

            val cleanPkg = packageName.trim().filter { it.isLetterOrDigit() || it == '.' || it == '_' }
            val existingReport = _uiState.value.onboarding.bootstrapReport
            val liveBootstrapReport = if (effectiveRootEnv != null && effectiveRootEnv.isRootGranted) {
                if (cleanPkg.isNotEmpty()) {
                    val restrictedRes = runSuspendCatching {
                        rootExecutor.exec("appops get $cleanPkg ACCESS_RESTRICTED_SETTINGS")
                    }.getOrNull()
                    val accessibilityRes = runSuspendCatching {
                        rootExecutor.exec("settings get secure enabled_accessibility_services")
                    }.getOrNull()
                    val notifRes = runSuspendCatching {
                        rootExecutor.exec("settings get secure enabled_notification_listeners")
                    }.getOrNull()
                    val batteryRes = runSuspendCatching {
                        rootExecutor.exec("dumpsys deviceidle whitelist")
                    }.getOrNull()

                    val liveRestricted = restrictedRes?.let {
                        it.isSuccess && it.stdout.contains("allow", ignoreCase = true)
                    } ?: false
                    val liveAccessibility = accessibilityRes?.let {
                        it.isSuccess && it.stdout.contains(cleanPkg)
                    } ?: false
                    val liveNotif = notifRes?.let {
                        it.isSuccess && it.stdout.contains(cleanPkg)
                    } ?: false
                    val liveBattery = batteryRes?.let {
                        it.isSuccess && it.stdout.contains(cleanPkg)
                    } ?: false

                    val keepRestricted = preserveVerifiedState &&
                        restrictedRes?.stdout.isNullOrBlank() &&
                        existingReport?.restrictedSettingsGranted == true
                    val keepAccessibility = preserveVerifiedState &&
                        accessibilityRes?.stdout.isNullOrBlank() &&
                        existingReport?.accessibilityEnabled == true
                    val keepNotif = preserveVerifiedState &&
                        notifRes?.stdout.isNullOrBlank() &&
                        existingReport?.notificationListenerEnabled == true
                    val keepBattery = preserveVerifiedState &&
                        batteryRes?.stdout.isNullOrBlank() &&
                        existingReport?.batteryOptimizationExempted == true

                    BootstrapReport(
                        packageName = cleanPkg,
                        environment = effectiveRootEnv,
                        runtimePermissionsGranted = if (preserveVerifiedState) {
                            existingReport?.runtimePermissionsGranted.orEmpty()
                        } else {
                            emptyList()
                        },
                        appOpsGranted = if (preserveVerifiedState) {
                            existingReport?.appOpsGranted.orEmpty()
                        } else {
                            emptyList()
                        },
                        restrictedSettingsGranted = liveRestricted || keepRestricted,
                        accessibilityEnabled = liveAccessibility || keepAccessibility,
                        notificationListenerEnabled = liveNotif || keepNotif,
                        batteryOptimizationExempted = liveBattery || keepBattery,
                        steps = if (preserveVerifiedState) existingReport?.steps.orEmpty() else emptyList(),
                        warnings = if (preserveVerifiedState) existingReport?.warnings.orEmpty() else emptyList(),
                    )
                } else {
                    existingReport
                }
            } else {
                null
            }

            val mountStatus = runSuspendCatching { chrootManager.getMountStatus() }.getOrNull()
            val detectedAgyVersion = runSuspendCatching { chrootManager.getAgyVersion().getOrNull() }
                .getOrNull()
                ?.trim()
                ?.ifBlank { null }
            val currentChroot = _uiState.value.onboarding.chrootState
            val rootfsPath = when (currentChroot) {
                is ChrootState.Mounted -> currentChroot.rootfsPath
                is ChrootState.Unmounted -> currentChroot.rootfsPath
                else -> ChrootConstants.DEFAULT_ROOTFS_PATH
            }
            val resolvedAgyVersion = detectedAgyVersion
                ?: if (preserveVerifiedState) _uiState.value.onboarding.agyVersion else null
            val resolvedChrootState = when {
                mountStatus?.allCoreMounted == true -> ChrootState.Mounted(
                    rootfsPath = rootfsPath,
                    agyVersion = resolvedAgyVersion,
                    mountStatus = mountStatus,
                )

                preserveVerifiedState && currentChroot is ChrootState.Mounted -> currentChroot.copy(
                    agyVersion = resolvedAgyVersion ?: currentChroot.agyVersion,
                )

                else -> currentChroot
            }

            var curlInstalled = false
            var caCertsInstalled = false
            var netcatInstalled = false
            if (rootGranted) {
                val depsCommand =
                    """chroot $rootfsPath /bin/sh -c "command -v curl >/dev/null 2>&1 && echo CURL; [ -f /etc/ssl/certs/ca-certificates.crt ] && echo CERTS; command -v nc >/dev/null 2>&1 && echo NC""""
                val depsResult = runSuspendCatching { rootExecutor.exec(depsCommand) }.getOrNull()
                if (depsResult != null && !depsResult.timedOut) {
                    val tokens = depsResult.stdout
                        .lines()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .toSet()
                    curlInstalled = "CURL" in tokens
                    caCertsInstalled = "CERTS" in tokens
                    netcatInstalled = "NC" in tokens
                }
            }
            val depsReady = curlInstalled && caCertsInstalled && netcatInstalled

            _uiState.update { state ->
                val updatedState = state.copy(
                    onboarding = state.onboarding.copy(
                        rootEnvironment = effectiveRootEnv,
                        bootstrapReport = liveBootstrapReport,
                        chrootState = resolvedChrootState,
                        agyVersion = resolvedAgyVersion,
                        diagnosticsChecked = true,
                        isRunningDiagnostics = false,
                        isOverlayPermissionGranted = isOverlayGranted,
                        isDeviceAdminActive = isDeviceAdminActive,
                        chrootCurlInstalled = curlInstalled,
                        chrootCaCertsInstalled = caCertsInstalled,
                        chrootNetcatInstalled = netcatInstalled,
                        chrootDependenciesReady = depsReady,
                    ),
                )
                val shouldAutoOpenOnboarding = allowAutoOpenOnboarding &&
                    !hasAutoOpenedOnboarding &&
                    !updatedState.onboarding.isCompleted &&
                    !updatedState.onboarding.isAutoFixingAll &&
                    updatedState.hasMissingCriticalSetup &&
                    updatedState.currentDestination == AppDestination.Hub &&
                    updatedState.messages.isEmpty()

                if (shouldAutoOpenOnboarding) {
                    hasAutoOpenedOnboarding = true
                    withBackStack(
                        state = updatedState,
                        rawStack = listOf(AppDestination.Hub, AppDestination.Onboarding),
                    )
                } else {
                    updatedState
                }
            }
        } finally {
            _uiState.update { state ->
                if (state.onboarding.isRunningDiagnostics) {
                    state.copy(onboarding = state.onboarding.copy(isRunningDiagnostics = false))
                } else {
                    state
                }
            }
        }
    }

    private fun handleAutoFixAllPrerequisites(packageName: String) {
        if (_uiState.value.onboarding.isAutoFixingAll) return
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    errorMessage = null,
                    onboarding = state.onboarding.copy(
                        isAutoFixingAll = true,
                        isBusy = true,
                        autoFixStageLabel = "Root ortamı denetleniyor...",
                        errorMessage = null,
                    ),
                )
            }
            try {
                // Stage 1: Root environment detection
                val rootEnv = runSuspendCatching { rootExecutor.detectEnvironment() }
                    .getOrElse { error ->
                        setAutoFixError(error.message ?: "Root ortamı denetlenemedi.")
                        return@launch
                    }
                _uiState.update { state ->
                    state.copy(onboarding = state.onboarding.copy(rootEnvironment = rootEnv))
                }
                if (!rootEnv.isRootGranted) {
                    val guidance = rootEnv.guidanceMessage.ifBlank {
                        "Root erişimi bulunamadı. KernelSU Manager veya Magisk içinden Antigravity Mobile uygulamasına Süper Kullanıcı (root) yetkisi verip tekrar deneyin."
                    }
                    setAutoFixError(guidance)
                    return@launch
                }

                // Stage 2: Android system permissions & services bootstrap
                updateAutoFixStage("Sistem izinleri ve servisler yapılandırılıyor...")
                val report = runSuspendCatching {
                    rootExecutor.bootstrapPermissions(packageName = packageName)
                }.getOrElse { error ->
                    setAutoFixError(error.message ?: "Sistem izinleri yapılandırılamadı.")
                    return@launch
                }
                _uiState.update { state ->
                    state.copy(
                        onboarding = state.onboarding.copy(
                            rootEnvironment = report.environment,
                            bootstrapReport = report,
                        ),
                    )
                }

                // Stage 3: ARM64 glibc chroot installation & mount
                updateAutoFixStage("ARM64 glibc chroot kuruluyor ve bağlanıyor...")
                val alreadyMounted = _uiState.value.onboarding.isChrootMounted ||
                    (runSuspendCatching { chrootManager.getMountStatus() }.getOrNull()?.allCoreMounted == true)
                if (!alreadyMounted) {
                    val onboardingSnapshot = _uiState.value.onboarding
                    val initialSource: RootfsSource = when (onboardingSnapshot.selectedRootfsSource) {
                        RootfsSourceOption.BUNDLED_ASSET -> RootfsSource.BundledAsset()
                        RootfsSourceOption.REMOTE_DOWNLOAD -> RootfsSource.RemoteDownload(
                            url = onboardingSnapshot.remoteRootfsUrl,
                        )
                        RootfsSourceOption.LOCAL_FILE -> RootfsSource.LocalFile(
                            absolutePath = onboardingSnapshot.localRootfsPath,
                        )
                    }
                    val rootfsResult = runSuspendCatching {
                        chrootManager.ensureRootfsReady(initialSource).getOrThrow()
                    }
                    if (rootfsResult.isFailure) {
                        val failure = rootfsResult.exceptionOrNull()!!
                        if (onboardingSnapshot.selectedRootfsSource == RootfsSourceOption.BUNDLED_ASSET &&
                            isAssetNotFoundFailure(failure)
                        ) {
                            _uiState.update { state ->
                                state.copy(
                                    onboarding = state.onboarding.copy(
                                        selectedRootfsSource = RootfsSourceOption.REMOTE_DOWNLOAD,
                                    ),
                                )
                            }
                            runSuspendCatching {
                                chrootManager.ensureRootfsReady(
                                    RootfsSource.RemoteDownload(url = onboardingSnapshot.remoteRootfsUrl),
                                ).getOrThrow()
                            }.getOrElse { fallbackError ->
                                setAutoFixError(fallbackError.message ?: "Uzak rootfs indirilemedi.")
                                return@launch
                            }
                        } else {
                            setAutoFixError(failure.message ?: "ARM64 rootfs kurulumu başarısız.")
                            return@launch
                        }
                    }

                    val mountStatus = runSuspendCatching {
                        chrootManager.mountAll().getOrThrow()
                    }.getOrElse { error ->
                        setAutoFixError(error.message ?: "Chroot bağlanamadı.")
                        return@launch
                    }
                    if (mountStatus.allCoreMounted) {
                        _uiState.update { state ->
                            state.copy(
                                onboarding = state.onboarding.copy(
                                    chrootState = ChrootState.Mounted(
                                        agyVersion = state.onboarding.agyVersion,
                                        mountStatus = mountStatus,
                                    ),
                                ),
                            )
                        }
                    }
                }

                // Stage 4: Chroot packages & agy CLI installation
                updateAutoFixStage("Chroot paketleri ve agy CLI kuruluyor...")
                val preInstallOnboarding = _uiState.value.onboarding
                if (preInstallOnboarding.agyVersion.isNullOrBlank() || !preInstallOnboarding.chrootDependenciesReady) {
                    val installedVersion = runSuspendCatching {
                        chrootManager.installOrUpdateAgy().getOrThrow()
                    }.getOrElse { error ->
                        setAutoFixError(error.message ?: "agy CLI kurulumu başarısız.")
                        return@launch
                    }
                    _uiState.update { state ->
                        val updatedChroot = when (val chroot = state.onboarding.chrootState) {
                            is ChrootState.Mounted -> chroot.copy(agyVersion = installedVersion)
                            else -> chroot
                        }
                        state.copy(
                            onboarding = state.onboarding.copy(
                                agyVersion = installedVersion,
                                chrootState = updatedChroot,
                            ),
                        )
                    }
                }

                // Stage 5: Re-run live diagnostics so every step reflects current truth
                val currentOnboarding = _uiState.value.onboarding
                runDiagnosticsInternal(
                    packageName = packageName,
                    isOverlayGranted = currentOnboarding.isOverlayPermissionGranted,
                    isDeviceAdminActive = currentOnboarding.isDeviceAdminActive,
                    allowAutoOpenOnboarding = false,
                    preserveVerifiedState = true,
                )

                val completionMessage = if (_uiState.value.activeProfile?.isAuthenticated == true) {
                    "Kurulum tamamen hazır!"
                } else {
                    "Tüm sistem bileşenleri hazır! Şimdi tek dokunuşla Google hesabınıza giriş yapabilirsiniz."
                }
                _uiState.update { state ->
                    state.copy(
                        onboarding = state.onboarding.copy(
                            isAutoFixingAll = false,
                            isBusy = false,
                            autoFixStageLabel = null,
                            errorMessage = null,
                            statusMessage = completionMessage,
                        ),
                    )
                }
            } finally {
                _uiState.update { state ->
                    if (state.onboarding.isAutoFixingAll || state.onboarding.isBusy || state.onboarding.autoFixStageLabel != null) {
                        state.copy(
                            onboarding = state.onboarding.copy(
                                isAutoFixingAll = false,
                                isBusy = false,
                                autoFixStageLabel = null,
                            ),
                        )
                    } else {
                        state
                    }
                }
            }
        }
    }

    private fun updateAutoFixStage(label: String) {
        _uiState.update { state ->
            state.copy(onboarding = state.onboarding.copy(autoFixStageLabel = label))
        }
    }

    private fun setAutoFixError(message: String) {
        _uiState.update { state ->
            state.copy(
                errorMessage = message,
                onboarding = state.onboarding.copy(
                    isAutoFixingAll = false,
                    isBusy = false,
                    autoFixStageLabel = null,
                    errorMessage = message,
                ),
            )
        }
    }

    private fun handleQuickGoogleSignIn() {
        viewModelScope.launch {
            runSuspendCatching {
                val currentState = _uiState.value
                val targetProfile = currentState.activeProfile
                    ?: currentState.profiles.firstOrNull()?.let { existing ->
                        accountRepository.switchActiveProfile(existing.id).getOrThrow()
                    }
                    ?: run {
                        val created = accountRepository.createProfile(DEFAULT_QUICK_PROFILE_NAME, null).getOrThrow()
                        accountRepository.switchActiveProfile(created.id).getOrThrow()
                    }

                _uiState.update { state ->
                    val alreadyListed = state.profiles.any { it.id == targetProfile.id }
                    state.copy(
                        profiles = if (alreadyListed) state.profiles else state.profiles + targetProfile,
                        activeProfile = targetProfile,
                    )
                }

                var isChrootMounted = _uiState.value.onboarding.isChrootMounted
                if (!isChrootMounted) {
                    val liveMount = runSuspendCatching { chrootManager.getMountStatus() }.getOrNull()
                    if (liveMount?.allCoreMounted == true) {
                        isChrootMounted = true
                        _uiState.update { state ->
                            state.copy(
                                onboarding = state.onboarding.copy(
                                    chrootState = ChrootState.Mounted(
                                        agyVersion = state.onboarding.agyVersion,
                                        mountStatus = liveMount,
                                    ),
                                ),
                            )
                        }
                    }
                }

                var isAgyAvailable = !_uiState.value.onboarding.agyVersion.isNullOrBlank()
                if (!isAgyAvailable && isChrootMounted) {
                    val liveAgy = runSuspendCatching { chrootManager.getAgyVersion().getOrNull() }
                        .getOrNull()
                        ?.trim()
                        ?.ifBlank { null }
                    if (liveAgy != null) {
                        isAgyAvailable = true
                        _uiState.update { state ->
                            state.copy(onboarding = state.onboarding.copy(agyVersion = liveAgy))
                        }
                    }
                }

                if (!isChrootMounted || !isAgyAvailable) {
                    val msg = "Google girişi için önce Chroot ve agy CLI kurulu olmalıdır. Yukarıdaki 'Tüm Eksikleri Otomatik Düzelt' butonuna basın."
                    _uiState.update { state ->
                        state.copy(
                            errorMessage = msg,
                            onboarding = state.onboarding.copy(
                                isBusy = false,
                                errorMessage = msg,
                            ),
                        )
                    }
                    return@runSuspendCatching
                }

                startOAuthLoginInternal(profileId = targetProfile.id, capturedAuthUrl = null)
            }.onFailure { error ->
                val msg = error.message ?: "Hızlı Google girişi başlatılamadı"
                _uiState.update { state ->
                    state.copy(
                        errorMessage = msg,
                        onboarding = state.onboarding.copy(
                            isBusy = false,
                            errorMessage = msg,
                        ),
                    )
                }
            }
        }
    }

    private fun handleClipboardAuthText(rawText: String, autoSubmit: Boolean) {
        val code = ClipboardOAuthMatcher.extractCode(rawText) ?: return
        val state = _uiState.value
        val awaitingAuth = state.authFlowState as? AuthFlowState.AwaitingBrowserAuth ?: return
        if (code == state.onboarding.lastAutoSubmittedClipboardCode) return

        val targetProfileId = awaitingAuth.challenge.profileId.ifBlank {
            state.activeProfile?.id ?: return
        }
        _uiState.update { current ->
            current.copy(
                onboarding = current.onboarding.copy(
                    lastAutoSubmittedClipboardCode = code,
                    clipboardAutoAuthHint = "Panodan Google doğrulama kodu algılandı ve gönderiliyor...",
                ),
            )
        }
        if (autoSubmit) {
            handleSubmitAuthCode(targetProfileId, code)
        }
    }

    private fun applyDetectedEmailIfGeneric(profile: AccountProfile, detectedEmail: String): AccountProfile {
        val normalizedCurrent = profile.displayName.trim().lowercase(Locale.ROOT)
        val isGeneric = normalizedCurrent.isEmpty() || normalizedCurrent in GENERIC_PROFILE_NAMES
        return profile.copy(
            email = profile.email?.takeIf { it.isNotBlank() } ?: detectedEmail,
            displayName = if (isGeneric) detectedEmail else profile.displayName,
            isAuthenticated = true,
        )
    }

    private fun isAssetNotFoundFailure(error: Throwable): Boolean {
        if (error is FileNotFoundException) return true
        val combinedMessage = generateSequence(error) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" ")
            .lowercase(Locale.US)
        return combinedMessage.contains("asset") ||
            combinedMessage.contains("not found") ||
            combinedMessage.contains("bulunamad") ||
            combinedMessage.contains("no such file") ||
            combinedMessage.contains("enoent")
    }

    // --- Helpers ---

    private fun addAttachment(pathOrLabel: String) {
        val clean = pathOrLabel.trim()
        if (clean.isEmpty()) return
        _uiState.update { state ->
            if (clean in state.attachments) state else state.copy(attachments = state.attachments + clean)
        }
    }

    private fun removeAttachment(pathOrLabel: String) {
        _uiState.update { state ->
            state.copy(attachments = state.attachments.filterNot { it == pathOrLabel })
        }
    }

    private fun hydrateFromLocalStore() {
        viewModelScope.launch {
            val snapshot = runSuspendCatching { localStore.load() }.getOrNull()
            _uiState.update { state ->
                if (snapshot != null) {
                    val restoredProjects = snapshot.projects
                    val restoredSelectedProjectId = snapshot.activeProjectId
                        ?.takeIf { id -> restoredProjects.any { it.id == id } }
                        ?: restoredProjects.firstOrNull()?.id
                    val validProjectIds = restoredProjects.map { it.id }.toSet()
                    val restoredConversations = snapshot.conversations.filter { conv ->
                        conv.projectId in validProjectIds ||
                            (restoredProjects.isEmpty() && conv.projectId == DEVICE_FALLBACK_PROJECT.id)
                    }
                    val activeConv = snapshot.activeConversationId
                        ?.let { id -> restoredConversations.firstOrNull { it.id == id } }
                    val hydratedMessages = if (state.messages.isNotEmpty()) {
                        state.messages
                    } else {
                        activeConv?.messages.orEmpty()
                    }
                    val selectedProjectObj = restoredProjects.firstOrNull { it.id == restoredSelectedProjectId }
                    val updatedConversations = if (activeConv != null) {
                        restoredConversations.map { conv ->
                            if (conv.id == activeConv.id) conv.copy(messages = hydratedMessages) else conv
                        }
                    } else {
                        restoredConversations
                    }
                    state.copy(
                        projects = restoredProjects,
                        selectedProjectId = restoredSelectedProjectId,
                        conversations = updatedConversations,
                        activeConversationId = activeConv?.id,
                        expandedProjectIds = restoredSelectedProjectId?.let { setOf(it) } ?: emptySet(),
                        messages = hydratedMessages,
                        settings = snapshot.settings,
                        mentionSuggestions = buildMentionSuggestions(selectedProjectObj),
                    )
                } else {
                    // First-ever launch (no snapshot file yet): keep the starter project without
                    // creating phantom empty conversations.
                    state.copy(
                        mentionSuggestions = buildMentionSuggestions(state.selectedProject),
                    )
                }
            }

            val current = _uiState.value
            if (current.settings.autoRefreshQuotaOnLaunch) {
                maybeAutoRefreshActiveProfileQuota(current)
            }
        }
    }

    private fun persistSnapshot(state: WorkspaceUiState = _uiState.value) {
        val snapshot = WorkspaceSnapshot(
            projects = state.projects,
            conversations = state.conversations,
            activeProjectId = state.selectedProjectId,
            activeConversationId = state.activeConversationId,
            settings = state.settings,
            workspaceInitialized = true,
        )
        viewModelScope.launch {
            runSuspendCatching {
                localStore.save(snapshot)
            }
        }
    }

    private fun newEmptyConversation(
        projectId: String,
        title: String = WorkspaceConversation.DEFAULT_TITLE,
        messages: List<WorkspaceChatMessage> = emptyList(),
    ): WorkspaceConversation {
        val now = clockMs()
        return WorkspaceConversation(
            id = "conv-${UUID.randomUUID().toString().take(8)}",
            projectId = projectId,
            title = title,
            messages = messages,
            isPinned = false,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
        )
    }

    private fun syncActiveConversation(
        state: WorkspaceUiState,
        newMessages: List<WorkspaceChatMessage>,
        sessionId: String? = state.activeSession?.sessionId,
        userPromptForAutoTitle: String? = null,
    ): WorkspaceUiState {
        val now = clockMs()
        val existing = state.activeConversation
        if (existing != null) {
            val nextTitle = if (userPromptForAutoTitle != null &&
                (existing.title.isBlank() || existing.title == WorkspaceConversation.DEFAULT_TITLE)
            ) {
                deriveConversationTitle(userPromptForAutoTitle)
            } else {
                existing.title
            }
            val updated = existing.copy(
                title = nextTitle,
                sessionId = sessionId ?: existing.sessionId,
                messages = newMessages,
                updatedAtEpochMs = now,
            )
            val updatedList = state.conversations.map { conv ->
                if (conv.id == existing.id) updated else conv
            }
            return state.copy(
                messages = newMessages,
                conversations = updatedList,
            )
        }

        val initialTitle = userPromptForAutoTitle?.let(::deriveConversationTitle)
            ?: WorkspaceConversation.DEFAULT_TITLE
        val created = WorkspaceConversation(
            id = "conv-${UUID.randomUUID().toString().take(8)}",
            projectId = state.selectedProjectId ?: state.activeProject.id,
            title = initialTitle,
            sessionId = sessionId,
            messages = newMessages,
            isPinned = false,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
        )
        val currentDest = state.currentDestination
        val nextStack = if (currentDest is AppDestination.Conversation && currentDest.conversationId == null) {
            state.backStack.dropLast(1) + AppDestination.Conversation(created.id)
        } else {
            state.backStack
        }
        return withBackStack(
            state = state.copy(
                messages = newMessages,
                conversations = listOf(created) + state.conversations,
                activeConversationId = created.id,
            ),
            rawStack = nextStack,
            clearExitConfirm = false,
        )
    }

    private fun deriveConversationTitle(prompt: String): String {
        val normalized = prompt.replace(Regex("\\s+"), " ").trim()
        if (normalized.isEmpty()) return WorkspaceConversation.DEFAULT_TITLE
        return if (normalized.length <= 42) normalized else "${normalized.take(41).trimEnd()}…"
    }

    private fun appendSystemMessage(
        content: String,
        onlyIfConversationActive: Boolean = false,
    ) {
        var didModify = false
        _uiState.update { state ->
            if (onlyIfConversationActive && state.activeConversation == null) {
                return@update state
            }
            didModify = true
            val updatedMessages = state.messages + systemMessage(
                sessionId = currentSessionId(state),
                content = content,
            )
            syncActiveConversation(state, updatedMessages)
        }
        if (didModify) {
            persistSnapshot()
        }
    }

    private fun systemMessage(sessionId: String, content: String) = WorkspaceChatMessage(
        id = "sys-${UUID.randomUUID()}",
        sessionId = sessionId,
        role = ChatMessageRole.SYSTEM,
        content = content,
        timestampEpochMs = clockMs(),
        isStreaming = false,
    )

    private fun currentSessionId(state: WorkspaceUiState): String =
        state.activeSession?.sessionId ?: "local-session"

    private fun upsertStreamingAssistantMessage(
        messages: List<WorkspaceChatMessage>,
        sessionId: String,
        preferredMessageId: String?,
        transform: (WorkspaceChatMessage) -> WorkspaceChatMessage,
    ): List<WorkspaceChatMessage> {
        val targetIndex = when {
            preferredMessageId != null -> messages.indexOfLast { it.id == preferredMessageId }
            else -> messages.indexOfLast { it.role == ChatMessageRole.ASSISTANT && it.isStreaming }
        }

        if (targetIndex >= 0) {
            return messages.toMutableList().apply {
                set(targetIndex, transform(get(targetIndex)))
            }
        }

        val fresh = WorkspaceChatMessage(
            id = preferredMessageId ?: "assistant-${UUID.randomUUID()}",
            sessionId = sessionId,
            role = ChatMessageRole.ASSISTANT,
            content = "",
            timestampEpochMs = clockMs(),
            isStreaming = true,
        )
        return messages + transform(fresh)
    }

    private fun parseDiffArtifact(event: AcpEvent.ArtifactUpdate): DiffItem {
        val lines = event.content.lines()
        val additions = lines.count { it.startsWith("+") && !it.startsWith("+++") }
        val deletions = lines.count { it.startsWith("-") && !it.startsWith("---") }
        val changeType = when {
            additions > 0 && deletions == 0 -> DiffChangeType.ADDED
            deletions > 0 && additions == 0 -> DiffChangeType.DELETED
            else -> DiffChangeType.MODIFIED
        }
        return DiffItem(
            id = event.artifactId,
            filePath = event.filePath ?: event.title,
            changeType = changeType,
            unifiedDiff = event.content,
            additions = additions,
            deletions = deletions,
            status = DiffApprovalStatus.PENDING,
        )
    }

    private fun buildMentionSuggestions(project: WorkspaceProject?): List<MentionSuggestion> {
        if (project == null) return defaultMentions()
        return listOf(
            MentionSuggestion(
                token = "@project:${project.rootPath}",
                label = project.name,
                category = "Proje",
            ),
        ) + defaultMentions()
    }

    /**
     * Cancellation-safe wrapper adhering to `compose-concurrency.md`:
     * never swallows [CancellationException].
     */
    private suspend inline fun <T> runSuspendCatching(crossinline block: suspend () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    companion object {
        internal const val EXIT_CONFIRM_WINDOW_MS = 2_000L
        internal const val EXIT_CONFIRM_MESSAGE = "Uygulamadan çıkmak için tekrar geri tuşuna basın"
        internal const val DEFAULT_QUICK_PROFILE_NAME = "Ana Google Hesabım"

        private val GENERIC_PROFILE_NAMES = setOf(
            "ana google hesabım",
            "ana google hesabim",
            "varsayılan hesap",
            "varsayilan hesap",
            "google hesabı",
            "google hesabi",
            "yeni hesap",
            "default",
        )

        private val NON_REUSABLE_SESSION_STATES = setOf(
            AcpSessionState.ERROR,
            AcpSessionState.AWAITING_AUTH,
            AcpSessionState.DISCONNECTED,
            AcpSessionState.CLOSED,
        )
    }
}

/**
 * Pure, dependency-free matcher that strictly extracts Google OAuth authorization codes
 * (`4/[0-9A-Za-z_-]{20,}`) or `code=` parameters from trusted Antigravity/loopback OAuth
 * callback URLs while ignoring ordinary clipboard content.
 */
internal object ClipboardOAuthMatcher {
    private val RAW_GOOGLE_CODE_REGEX = Regex("^4/[0-9A-Za-z_-]{20,}$")
    private val ENCODED_GOOGLE_CODE_REGEX = Regex("^4%2[Ff][0-9A-Za-z_-]{20,}$")

    fun extractCode(rawText: String): String? {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return null

        if (RAW_GOOGLE_CODE_REGEX.matches(trimmed)) {
            return trimmed
        }
        if (ENCODED_GOOGLE_CODE_REGEX.matches(trimmed)) {
            return decodeUtf8(trimmed)?.takeIf { RAW_GOOGLE_CODE_REGEX.matches(it) }
        }

        // Reject multi-word clipboard snippets that are not a single URL
        if (trimmed.any { it.isWhitespace() }) return null

        val normalizedUrl = if (trimmed.startsWith("antigravity.google/oauth-callback", ignoreCase = true)) {
            "https://$trimmed"
        } else {
            trimmed
        }

        val uri = try {
            URI(normalizedUrl)
        } catch (_: Exception) {
            return null
        }

        val scheme = uri.scheme?.lowercase(Locale.US) ?: return null
        val host = uri.host?.lowercase(Locale.US) ?: return null
        val path = uri.path.orEmpty()

        val isTrustedCallback = when (scheme) {
            "antigravity" -> host == "oauth" && (path == "/callback" || path.startsWith("/callback/"))
            "http", "https" -> (host == "antigravity.google" && path.startsWith("/oauth-callback")) ||
                host == "127.0.0.1" ||
                host == "localhost"
            else -> false
        }
        if (!isTrustedCallback) return null

        val query = uri.rawQuery ?: return null
        for (pair in query.split('&')) {
            val eqIdx = pair.indexOf('=')
            if (eqIdx <= 0) continue
            val key = pair.substring(0, eqIdx)
            if (key == "code") {
                val rawValue = pair.substring(eqIdx + 1)
                val decoded = decodeUtf8(rawValue)?.trim() ?: return null
                if (decoded.isNotEmpty() && decoded.none { it.isWhitespace() }) {
                    return decoded
                }
            }
        }
        return null
    }

    private fun decodeUtf8(value: String): String? = try {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    } catch (_: Exception) {
        null
    }
}
