package com.antigravity.mobile.feature.workspace.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antigravity.mobile.contracts.AccountProfile
import com.antigravity.mobile.contracts.AccountRepository
import com.antigravity.mobile.contracts.AcpClient
import com.antigravity.mobile.contracts.AcpEvent
import com.antigravity.mobile.contracts.AcpModel
import com.antigravity.mobile.contracts.AcpSessionConfig
import com.antigravity.mobile.contracts.AcpSessionState
import com.antigravity.mobile.contracts.AndroidMcpServer
import com.antigravity.mobile.contracts.AuthFlowState
import com.antigravity.mobile.contracts.AutomationScheduler
import com.antigravity.mobile.contracts.ChatMessageRole
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
import com.antigravity.mobile.feature.workspace.model.AuthBannerState
import com.antigravity.mobile.feature.workspace.model.OverviewTab
import com.antigravity.mobile.feature.workspace.model.QuotaWarningState
import com.antigravity.mobile.feature.workspace.model.RootfsSourceOption
import com.antigravity.mobile.feature.workspace.model.SlashCommand
import com.antigravity.mobile.feature.workspace.model.SubagentItem
import com.antigravity.mobile.feature.workspace.model.WorkspaceAction
import com.antigravity.mobile.feature.workspace.model.WorkspaceArtifact
import com.antigravity.mobile.feature.workspace.model.WorkspaceUiState
import com.antigravity.mobile.feature.workspace.model.defaultMentions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkspaceUiState())
    val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()

    init {
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
                    mentionSuggestions = buildMentionSuggestions(it.activeProject),
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
            is WorkspaceAction.SetShowOnboarding -> _uiState.update { it.copy(showOnboarding = action.show) }
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
            WorkspaceAction.CompleteOnboarding -> _uiState.update {
                it.copy(
                    showOnboarding = false,
                    onboarding = it.onboarding.copy(isCompleted = true),
                )
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
            }
        }
        viewModelScope.launch {
            accountRepository.authFlowState.collect { authState ->
                _uiState.update { state ->
                    val customTabUrl = (authState as? AuthFlowState.AwaitingBrowserAuth)
                        ?.challenge
                        ?.authUrl
                    val clearedBanner = if (authState is AuthFlowState.Authenticated) null else state.authBanner
                    state.copy(
                        authFlowState = authState,
                        authBanner = clearedBanner,
                        pendingChromeCustomTabUrl = customTabUrl ?: state.pendingChromeCustomTabUrl,
                    )
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
                    state.copy(messages = updatedMessages)
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
                    state.copy(messages = updatedMessages)
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
                    state.copy(messages = updatedMessages)
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
                _uiState.update { state ->
                    state.copy(pendingPermissionRequest = event)
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
                _uiState.update { state ->
                    val finalizeStreaming = event.state == AcpSessionState.READY ||
                        event.state == AcpSessionState.ERROR ||
                        event.state == AcpSessionState.CLOSED
                    val updatedMessages = if (finalizeStreaming) {
                        state.messages.map { msg ->
                            if (msg.isStreaming) msg.copy(isStreaming = false) else msg
                        }
                    } else {
                        state.messages
                    }
                    state.copy(
                        sessionState = event.state,
                        messages = updatedMessages,
                    )
                }
            }

            is AcpEvent.Error -> {
                _uiState.update { state ->
                    val nextMessages = if (!event.recoverable) {
                        state.messages + systemMessage(
                            sessionId = event.sessionId ?: currentSessionId(state),
                            content = "ACP Hatası (${event.code ?: "?"}): ${event.message}",
                        )
                    } else {
                        state.messages
                    }
                    state.copy(
                        messages = nextMessages,
                        errorMessage = event.message,
                    )
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
        val allMentions = buildMentionSuggestions(_uiState.value.activeProject)
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
            state.copy(messages = listOf(summaryMsg) + currentMessages.takeLast(keepCount))
        }
    }

    private fun sendPromptToAcp(prompt: String, attachments: List<String>) {
        val sessionIdBefore = currentSessionId(_uiState.value)
        val userMessage = WorkspaceChatMessage(
            id = "user-${UUID.randomUUID()}",
            sessionId = sessionIdBefore,
            role = ChatMessageRole.USER,
            content = prompt,
            attachments = attachments,
            timestampEpochMs = System.currentTimeMillis(),
            isStreaming = false,
        )
        _uiState.update { state ->
            state.copy(messages = state.messages + userMessage)
        }

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

    private suspend fun ensureActiveSession(): com.antigravity.mobile.contracts.AcpSessionInfo {
        val existing = acpClient.activeSession.value ?: _uiState.value.activeSession
        if (existing != null && _uiState.value.sessionState != AcpSessionState.DISCONNECTED &&
            _uiState.value.sessionState != AcpSessionState.CLOSED
        ) {
            return existing
        }

        val state = _uiState.value
        val profileId = state.activeProfile?.id ?: "default"
        val mcpPort = state.mcpHealth.endpointPort ?: 0
        val config = AcpSessionConfig(
            profileId = profileId,
            workspaceDir = state.activeProject.rootPath,
            additionalDirs = listOf("/mnt/sdcard", state.activeProject.rootPath).distinct(),
            model = state.selectedModel,
            thinkingEffort = state.selectedEffort,
            permissionMode = state.permissionMode,
            ideMcpPort = mcpPort,
            processPerSession = true,
        )
        return acpClient.startSession(config).getOrThrow()
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
                appendSystemMessage("Yeni profil oluşturuldu ve etkinleştirildi: ${created.displayName}")
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
                appendSystemMessage("Aktif Google hesabı değiştirildi: ${switched.displayName}")
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "Hesap değiştirilemedi") }
            }
        }
    }

    private fun handleStartOAuthLogin(profileId: String, capturedAuthUrl: String?) {
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    errorMessage = null,
                    onboarding = state.onboarding.copy(
                        isBusy = true,
                        statusMessage = "Google OAuth bağlantısı hazırlanıyor...",
                        errorMessage = null,
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
                val verifiedLabel = profile.email ?: profile.displayName
                _uiState.update { state ->
                    state.copy(
                        authBanner = null,
                        activeProfile = profile,
                        onboarding = state.onboarding.copy(
                            isBusy = false,
                            statusMessage = "Google hesabı doğrulandı: $verifiedLabel",
                            errorMessage = null,
                        ),
                    )
                }
                appendSystemMessage("Google hesabı doğrulandı: $verifiedLabel")
            }.onFailure { error ->
                val msg = error.message ?: "Doğrulama kodu reddedildi"
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
                appendSystemMessage("Masaüstü ~/.gemini profili içe aktarıldı: ${profile.displayName}")
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
                    val percentStr = quota.remainingPercent?.let { "%.1f%%".format(Locale.US, it) } ?: "Bilinmiyor"
                    val summary = buildString {
                        append("`/usage` — **${targetProfile.displayName}** (${quota.planTier.name})\n")
                        append("Kalan kota: $percentStr")
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
        if (profile == null || !profile.quota.isNearLimit) {
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
                    remainingPercent = profile.quota.remainingPercent ?: 0.0,
                    suggestedFallbackProfile = fallback,
                ),
            )
        }
    }

    private fun handleSelectProject(projectId: String) {
        val target = _uiState.value.projects.firstOrNull { it.id == projectId } ?: return
        _uiState.update { state ->
            state.copy(
                activeProject = target,
                mentionSuggestions = buildMentionSuggestions(target),
            )
        }
        handleSelectPermissionMode(target.defaultPermissionMode)
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
            lastOpenedAtEpochMs = System.currentTimeMillis(),
        )
        _uiState.update { state ->
            state.copy(
                projects = state.projects + newProject,
                activeProject = newProject,
                mentionSuggestions = buildMentionSuggestions(newProject),
            )
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
            projectId = _uiState.value.activeProject.id,
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

    private fun appendSystemMessage(content: String) {
        _uiState.update { state ->
            state.copy(
                messages = state.messages + systemMessage(
                    sessionId = currentSessionId(state),
                    content = content,
                ),
            )
        }
    }

    private fun systemMessage(sessionId: String, content: String) = WorkspaceChatMessage(
        id = "sys-${UUID.randomUUID()}",
        sessionId = sessionId,
        role = ChatMessageRole.SYSTEM,
        content = content,
        timestampEpochMs = System.currentTimeMillis(),
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
            timestampEpochMs = System.currentTimeMillis(),
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

    private fun buildMentionSuggestions(project: WorkspaceProject) =
        listOf(
            com.antigravity.mobile.feature.workspace.model.MentionSuggestion(
                token = "@project:${project.rootPath}",
                label = project.name,
                category = "Proje",
            ),
        ) + defaultMentions()

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
}
