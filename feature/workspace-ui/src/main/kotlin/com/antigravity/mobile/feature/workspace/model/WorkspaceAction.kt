package com.antigravity.mobile.feature.workspace.model

import com.antigravity.mobile.contracts.AcpModel
import com.antigravity.mobile.contracts.PermissionMode
import com.antigravity.mobile.contracts.ThinkingEffort

sealed interface WorkspaceAction {
    // Composer & Conversation
    data class UpdateComposerText(val text: String) : WorkspaceAction
    data class SendMessage(val overrideText: String? = null) : WorkspaceAction
    data class SelectSlashCommand(val command: SlashCommand) : WorkspaceAction
    data class SelectMention(val mention: MentionSuggestion) : WorkspaceAction
    data class AddAttachment(val pathOrLabel: String) : WorkspaceAction
    data class RemoveAttachment(val pathOrLabel: String) : WorkspaceAction
    data object DismissComposerMenus : WorkspaceAction
    data object OpenSlashMenu : WorkspaceAction
    data object OpenMentionMenu : WorkspaceAction

    // Session & Model Settings
    data class SelectModel(val model: AcpModel) : WorkspaceAction
    data class SelectEffort(val effort: ThinkingEffort) : WorkspaceAction
    data class SelectPermissionMode(val mode: PermissionMode) : WorkspaceAction
    data object CancelActiveSession : WorkspaceAction
    data object TriggerEmergencyStop : WorkspaceAction

    // Voice Input
    data object ToggleVoiceListening : WorkspaceAction
    data class ChangeVoiceLocale(val localeTag: String) : WorkspaceAction

    // Accounts & OAuth
    data class CreateProfile(val displayName: String, val email: String? = null) : WorkspaceAction
    data class SwitchProfile(val profileId: String) : WorkspaceAction
    data class StartOAuthLogin(val profileId: String, val capturedAuthUrl: String? = null) : WorkspaceAction
    data class SubmitAuthCode(val profileId: String, val code: String) : WorkspaceAction
    data class ImportDesktopProfile(val profileId: String, val sourcePath: String) : WorkspaceAction
    data class RefreshUsage(val profileId: String? = null) : WorkspaceAction
    data object DismissAuthBanner : WorkspaceAction
    data object ConsumeChromeCustomTabUrl : WorkspaceAction
    data object DismissQuotaWarning : WorkspaceAction

    // Projects & Conversations (Desktop-Parity CRUD)
    data class SelectProject(val projectId: String) : WorkspaceAction
    data class CreateProject(
        val name: String,
        val rootPath: String,
        val defaultPermissionMode: PermissionMode = PermissionMode.ASK,
    ) : WorkspaceAction
    data class DeleteProject(val projectId: String) : WorkspaceAction
    data class RenameProject(val projectId: String, val newName: String) : WorkspaceAction
    data class ToggleProjectExpanded(val projectId: String) : WorkspaceAction
    data class UpdateSidebarSearch(val query: String) : WorkspaceAction
    data class CreateConversation(val projectId: String? = null, val title: String? = null) : WorkspaceAction
    data class SelectConversation(val conversationId: String) : WorkspaceAction
    data class DeleteConversation(val conversationId: String) : WorkspaceAction
    data class RenameConversation(val conversationId: String, val newTitle: String) : WorkspaceAction
    data class TogglePinConversation(val conversationId: String) : WorkspaceAction

    // Navigation / Back-Stack / Sheets / Settings / Quota
    data object NavigateBack : WorkspaceAction
    data object NavigateToHub : WorkspaceAction
    data class NavigateToConversation(val conversationId: String? = null) : WorkspaceAction
    data object OpenSettingsList : WorkspaceAction
    data class OpenSettingsDetail(val category: SettingsCategory) : WorkspaceAction
    data object OpenModelPickerSheet : WorkspaceAction
    data object CloseModelPickerSheet : WorkspaceAction
    data object OpenComposerToolsSheet : WorkspaceAction
    data object CloseComposerToolsSheet : WorkspaceAction
    data object ConsumeExitApp : WorkspaceAction
    data object DismissExitConfirm : WorkspaceAction
    data class OpenSettings(val category: SettingsCategory = SettingsCategory.GENERAL) : WorkspaceAction
    data object CloseSettings : WorkspaceAction
    data class SelectSettingsCategory(val category: SettingsCategory) : WorkspaceAction
    data class UpdateSettings(val transform: (AppSettings) -> AppSettings) : WorkspaceAction
    data object OpenQuotaSheet : WorkspaceAction
    data object CloseQuotaSheet : WorkspaceAction
    data object RefreshAllQuotas : WorkspaceAction
    data object DismissOnboarding : WorkspaceAction
    data class OpenOnboardingFromSettings(val fromSettings: Boolean = true) : WorkspaceAction

    // Mobile Root / Chroot & Account Management from Settings
    data object UnmountChroot : WorkspaceAction
    data class DeleteProfile(val profileId: String) : WorkspaceAction

    // Permission & Diff Approvals
    data class RespondToPermission(
        val requestId: String,
        val approved: Boolean,
        val rememberForSession: Boolean = false,
    ) : WorkspaceAction

    data class ApproveDiff(val diffId: String) : WorkspaceAction
    data class RejectDiff(val diffId: String) : WorkspaceAction

    // Automations (/schedule)
    data class ScheduleTask(
        val name: String,
        val prompt: String,
        val cronExpression: String? = null,
        val triggerAtEpochMs: Long? = null,
        val exactTiming: Boolean = false,
    ) : WorkspaceAction

    data class CancelScheduledTask(val taskId: String) : WorkspaceAction
    data class ToggleScheduledTask(val taskId: String, val enabled: Boolean) : WorkspaceAction
    data class TriggerScheduledTask(val taskId: String) : WorkspaceAction

    // Customizations & Denylist
    data class AddProtectedPackage(val packageName: String) : WorkspaceAction
    data class RemoveProtectedPackage(val packageName: String) : WorkspaceAction
    data class ToggleCustomization(val itemId: String) : WorkspaceAction
    data object RefreshMcpTools : WorkspaceAction

    // Overview Modal Bottom Sheet
    data class OpenOverviewSheet(val tab: OverviewTab = OverviewTab.SUBAGENTS) : WorkspaceAction
    data object CloseOverviewSheet : WorkspaceAction
    data class SelectOverviewTab(val tab: OverviewTab) : WorkspaceAction
    data class SelectSwarmSubTab(val subTab: SwarmSubTab) : WorkspaceAction

    // Onboarding / Setup Wizard
    data class SetShowOnboarding(val show: Boolean) : WorkspaceAction
    data object CheckRootStatus : WorkspaceAction
    data class RunRootBootstrap(
        val packageName: String,
        val accessibilityServiceComponent: String? = null,
        val notificationListenerComponent: String? = null,
    ) : WorkspaceAction

    data class SelectRootfsSource(val option: RootfsSourceOption) : WorkspaceAction
    data class UpdateRemoteRootfsUrl(val url: String) : WorkspaceAction
    data class UpdateLocalRootfsPath(val path: String) : WorkspaceAction
    data object SetupChroot : WorkspaceAction
    data object CheckOrInstallAgy : WorkspaceAction
    data object CompleteOnboarding : WorkspaceAction

    // Misc
    data object DismissError : WorkspaceAction
}
