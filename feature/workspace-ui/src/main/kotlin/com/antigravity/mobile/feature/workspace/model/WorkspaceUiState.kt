package com.antigravity.mobile.feature.workspace.model

import com.antigravity.mobile.contracts.AccountProfile
import com.antigravity.mobile.contracts.AcpEvent
import com.antigravity.mobile.contracts.AcpModel
import com.antigravity.mobile.contracts.AcpSessionInfo
import com.antigravity.mobile.contracts.AcpSessionState
import com.antigravity.mobile.contracts.AuthFlowState
import com.antigravity.mobile.contracts.BootstrapReport
import com.antigravity.mobile.contracts.ChrootState
import com.antigravity.mobile.contracts.DiffItem
import com.antigravity.mobile.contracts.McpServerHealth
import com.antigravity.mobile.contracts.McpToolDefinition
import com.antigravity.mobile.contracts.PermissionMode
import com.antigravity.mobile.contracts.RootEnvironmentInfo
import com.antigravity.mobile.contracts.ScheduledTask
import com.antigravity.mobile.contracts.ScheduledTaskRun
import com.antigravity.mobile.contracts.SwarmBoardState
import com.antigravity.mobile.contracts.SwarmMemberStatus
import com.antigravity.mobile.contracts.ThinkingEffort
import com.antigravity.mobile.contracts.VoiceState
import com.antigravity.mobile.contracts.WorkspaceChatMessage
import com.antigravity.mobile.contracts.WorkspaceProject

enum class SlashCommand(
    val command: String,
    val title: String,
    val description: String,
) {
    PLAN("/plan", "Plan Modu", "Değişiklik yapmadan önce adım adım plan hazırla"),
    SWARM("/swarm", "Swarm Panosu", "Çoklu ajan görev dağılımı ve ortak pano"),
    GOAL("/goal", "Uzun Hedef", "Hedef tamamlanana kadar otonom çalış"),
    SCHEDULE("/schedule", "Görev Zamanla", "Tek seferlik veya periyodik otomasyon ekle"),
    DIFF("/diff", "Diff & Artifacts", "Üretilen planları ve dosya farklarını incele"),
    AGENTS("/agents", "Alt Ajanlar", "Aktif arka plan alt ajanlarını görüntüle"),
    USAGE("/usage", "Kota Durumu", "Aktif Google hesabının kalan kredi/kotasını kontrol et"),
    COMPACT("/compact", "Bağlamı Sıkıştır", "Uzun sohbet geçmişini özetleyip yer aç"),
}

data class MentionSuggestion(
    val token: String,
    val label: String,
    val category: String,
)

enum class OverviewTab {
    SUBAGENTS,
    SWARM,
    ARTIFACTS_DIFFS,
}

enum class SwarmSubTab {
    BOARD,
    MEMBERS,
    GRAPH,
}

enum class CustomizationCategory {
    SKILLS,
    RULES,
    HOOKS,
}

data class CustomizationItem(
    val id: String,
    val name: String,
    val description: String,
    val category: CustomizationCategory,
    val enabled: Boolean = true,
)

data class WorkspaceArtifact(
    val id: String,
    val title: String,
    val mimeType: String,
    val content: String,
    val filePath: String? = null,
    val updatedAtEpochMs: Long = 0L,
)

data class SubagentItem(
    val id: String,
    val name: String,
    val role: String,
    val model: String,
    val status: SwarmMemberStatus,
    val lastUpdate: String = "",
)

data class AuthBannerState(
    val errorCode: Int = -32000,
    val message: String,
    val authUrl: String? = null,
    val profileId: String? = null,
)

data class QuotaWarningState(
    val currentProfile: AccountProfile,
    val remainingPercent: Double,
    val suggestedFallbackProfile: AccountProfile? = null,
)

enum class RootfsSourceOption {
    BUNDLED_ASSET,
    REMOTE_DOWNLOAD,
    LOCAL_FILE,
}

data class OnboardingUiState(
    val rootEnvironment: RootEnvironmentInfo? = null,
    val bootstrapReport: BootstrapReport? = null,
    val chrootState: ChrootState = ChrootState.NotInstalled,
    val selectedRootfsSource: RootfsSourceOption = RootfsSourceOption.BUNDLED_ASSET,
    val remoteRootfsUrl: String = "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.1-base-arm64.tar.gz",
    val localRootfsPath: String = "/sdcard/Download/arm64-rootfs.tar.gz",
    val agyVersion: String? = null,
    val isBusy: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val isCompleted: Boolean = false,
)

data class WorkspaceUiState(
    val profiles: List<AccountProfile> = emptyList(),
    val activeProfile: AccountProfile? = null,
    val authFlowState: AuthFlowState = AuthFlowState.Idle,
    val projects: List<WorkspaceProject> = defaultProjects(),
    val selectedProjectId: String? = defaultProjects().firstOrNull()?.id,
    val conversations: List<WorkspaceConversation> = emptyList(),
    val activeConversationId: String? = null,
    val expandedProjectIds: Set<String> = setOf("proj-sdcard"),
    val sidebarSearchQuery: String = "",
    val messages: List<WorkspaceChatMessage> = emptyList(),
    val activeSession: AcpSessionInfo? = null,
    val sessionState: AcpSessionState = AcpSessionState.DISCONNECTED,
    val selectedModel: AcpModel = AcpModel.GEMINI_4_NEON,
    val selectedEffort: ThinkingEffort = ThinkingEffort.MEDIUM,
    val permissionMode: PermissionMode = PermissionMode.ASK,
    val composerText: String = "",
    val attachments: List<String> = emptyList(),
    val showSlashMenu: Boolean = false,
    val slashSuggestions: List<SlashCommand> = SlashCommand.entries,
    val showMentionMenu: Boolean = false,
    val mentionSuggestions: List<MentionSuggestion> = defaultMentions(),
    val voiceState: VoiceState = VoiceState.Idle,
    val voiceLocaleTag: String = "tr-TR",
    val mcpHealth: McpServerHealth = McpServerHealth(),
    val mcpTools: List<McpToolDefinition> = emptyList(),
    val scheduledTasks: List<ScheduledTask> = emptyList(),
    val recentTaskRuns: List<ScheduledTaskRun> = emptyList(),
    val denylistPackages: Set<String> = emptySet(),
    val customizations: List<CustomizationItem> = defaultCustomizations(),
    val swarmBoardState: SwarmBoardState = SwarmBoardState(),
    val subagents: List<SubagentItem> = emptyList(),
    val artifacts: List<WorkspaceArtifact> = emptyList(),
    val diffs: List<DiffItem> = emptyList(),
    val pendingPermissionRequest: AcpEvent.PermissionRequest? = null,
    val authBanner: AuthBannerState? = null,
    val quotaWarning: QuotaWarningState? = null,
    val pendingChromeCustomTabUrl: String? = null,
    val isOverviewSheetVisible: Boolean = false,
    val selectedOverviewTab: OverviewTab = OverviewTab.SUBAGENTS,
    val selectedSwarmSubTab: SwarmSubTab = SwarmSubTab.BOARD,
    val backStack: List<AppDestination> = listOf(AppDestination.Hub),
    val showSettings: Boolean = false,
    val selectedSettingsCategory: SettingsCategory = SettingsCategory.GENERAL,
    val showQuotaSheet: Boolean = false,
    val showModelPickerSheet: Boolean = false,
    val showComposerToolsSheet: Boolean = false,
    val isRefreshingQuota: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val onboardingReturnToSettings: Boolean = false,
    val onboarding: OnboardingUiState = OnboardingUiState(),
    val showOnboarding: Boolean = false,
    val exitConfirmPending: Boolean = false,
    val exitConfirmMessage: String? = null,
    val shouldExitApp: Boolean = false,
    val errorMessage: String? = null,
) {
    val currentDestination: AppDestination
        get() = backStack.lastOrNull() ?: AppDestination.Hub

    val selectedProject: WorkspaceProject?
        get() = selectedProjectId?.let { id -> projects.firstOrNull { it.id == id } }

    /**
     * Compatibility bridge for screens/sessions needing a non-null workspace root even when the
     * user has genuinely deleted all projects ([projects] is empty and [selectedProject] is null).
     */
    val activeProject: WorkspaceProject
        get() = selectedProject ?: projects.firstOrNull() ?: DEVICE_FALLBACK_PROJECT

    val activeConversation: WorkspaceConversation?
        get() = activeConversationId?.let { id -> conversations.firstOrNull { it.id == id } }
}

val DEVICE_FALLBACK_PROJECT = WorkspaceProject(
    id = "device-fallback",
    name = "Cihaz Depolama (/sdcard)",
    rootPath = "/mnt/sdcard",
    profileId = "default",
    defaultPermissionMode = PermissionMode.ASK,
)

fun defaultProjects(): List<WorkspaceProject> = listOf(
    WorkspaceProject(
        id = "proj-sdcard",
        name = "Cihaz Depolama (/sdcard)",
        rootPath = "/mnt/sdcard",
        profileId = "default",
        defaultPermissionMode = PermissionMode.ASK,
    ),
)

fun defaultMentions(): List<MentionSuggestion> = listOf(
    MentionSuggestion("@sdcard", "/sdcard dizini", "Dosya"),
    MentionSuggestion("@screen", "Aktif ekran UI ağacı", "Sistem"),
    MentionSuggestion("@apps", "Yüklü Android uygulamaları", "Sistem"),
    MentionSuggestion("@notifications", "Aktif bildirimler", "Sistem"),
    MentionSuggestion("@clipboard", "Cihaz panosu", "Sistem"),
    MentionSuggestion("@contacts", "Rehber & Takvim", "Sistem"),
)

fun defaultCustomizations(): List<CustomizationItem> = listOf(
    CustomizationItem(
        id = "skill-android-root",
        name = "android-root-system",
        description = "KernelSU/Magisk, ARM64 chroot ve Android 14/15 derin sistem kontrolü",
        category = CustomizationCategory.SKILLS,
        enabled = true,
    ),
    CustomizationItem(
        id = "rule-security-gate",
        name = "mandatory-security-gate",
        description = "CRITICAL araçlar ve banka/2FA uygulamalarında zorunlu kullanıcı onayı",
        category = CustomizationCategory.RULES,
        enabled = true,
    ),
    CustomizationItem(
        id = "rule-hybrid-ui",
        name = "hybrid-ui-tree-first",
        description = "Önce ui_get_tree, yalnızca Canvas/WebView ekranlarında screen_capture",
        category = CustomizationCategory.RULES,
        enabled = true,
    ),
    CustomizationItem(
        id = "hook-kill-switch",
        name = "hardware-volume-kill-switch",
        description = "500ms Ses+ / Ses- basılı tutulduğunda tüm ajan süreçlerini durdur",
        category = CustomizationCategory.HOOKS,
        enabled = true,
    ),
)
