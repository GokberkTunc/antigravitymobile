package com.antigravity.mobile.feature.workspace.ui.drawer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.antigravity.mobile.contracts.AccountPlanTier
import com.antigravity.mobile.contracts.AccountProfile
import com.antigravity.mobile.contracts.PermissionMode
import com.antigravity.mobile.contracts.ScheduledTask
import com.antigravity.mobile.contracts.ScheduledTaskRun
import com.antigravity.mobile.contracts.WorkspaceProject
import com.antigravity.mobile.feature.workspace.model.SettingsCategory
import com.antigravity.mobile.feature.workspace.model.WorkspaceAction
import com.antigravity.mobile.feature.workspace.model.WorkspaceConversation
import com.antigravity.mobile.feature.workspace.model.WorkspaceUiState
import com.antigravity.mobile.feature.workspace.theme.AntigravityPalette
import java.util.Locale

private data class FilteredProjectNode(
    val project: WorkspaceProject,
    val visibleConversations: List<WorkspaceConversation>,
    val totalConversationCount: Int,
    val forceExpandedBySearch: Boolean,
)

@Composable
fun WorkspaceDrawerSheet(
    state: WorkspaceUiState,
    onAction: (WorkspaceAction) -> Unit,
    onCloseDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAddProjectDialog by remember { mutableStateOf(false) }
    var projectToRename by remember { mutableStateOf<WorkspaceProject?>(null) }
    var projectToDelete by remember { mutableStateOf<Pair<WorkspaceProject, Int>?>(null) }
    var conversationToRename by remember { mutableStateOf<WorkspaceConversation?>(null) }
    var conversationToDelete by remember { mutableStateOf<WorkspaceConversation?>(null) }
    var showAddScheduleDialog by remember { mutableStateOf(false) }
    var automationsExpanded by remember { mutableStateOf(false) }
    var showAccountSwitcherDialog by remember { mutableStateOf(false) }
    var showAddAccountDialog by remember { mutableStateOf(false) }
    var showImportDesktopDialog by remember { mutableStateOf(false) }

    val trimmedQuery = state.sidebarSearchQuery.trim()
    val filteredProjectNodes = remember(state.projects, state.conversations, trimmedQuery) {
        val convsByProject = state.conversations.groupBy { it.projectId }
        state.projects.mapNotNull { project ->
            val sortedConvs = convsByProject[project.id].orEmpty()
                .sortedWith(
                    compareByDescending<WorkspaceConversation> { it.isPinned }
                        .thenByDescending { it.updatedAtEpochMs },
                )
            if (trimmedQuery.isEmpty()) {
                FilteredProjectNode(
                    project = project,
                    visibleConversations = sortedConvs,
                    totalConversationCount = sortedConvs.size,
                    forceExpandedBySearch = false,
                )
            } else {
                val projectMatches = project.name.contains(trimmedQuery, ignoreCase = true) ||
                    project.rootPath.contains(trimmedQuery, ignoreCase = true)
                val matchingConvs = sortedConvs.filter { conv ->
                    conv.title.contains(trimmedQuery, ignoreCase = true)
                }
                if (projectMatches || matchingConvs.isNotEmpty()) {
                    FilteredProjectNode(
                        project = project,
                        visibleConversations = if (matchingConvs.isNotEmpty()) matchingConvs else sortedConvs,
                        totalConversationCount = sortedConvs.size,
                        forceExpandedBySearch = matchingConvs.isNotEmpty(),
                    )
                } else {
                    null
                }
            }
        }
    }

    ModalDrawerSheet(
        modifier = modifier
            .fillMaxHeight()
            .width(332.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            // 1. Modern Sleek Obsidian Header
            DrawerBrandHeader(
                mcpRunning = state.mcpHealth.isRunning,
                onOpenSettings = {
                    onAction(WorkspaceAction.OpenSettings(SettingsCategory.GENERAL))
                    onCloseDrawer()
                },
            )

            // 2. Primary "+ Yeni Sohbet" Action + Compact Pill Search Filter
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = {
                        onAction(WorkspaceAction.CreateConversation(state.activeProject.id))
                        onCloseDrawer()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Yeni Sohbet",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                SidebarSearchBar(
                    query = state.sidebarSearchQuery,
                    onQueryChange = { onAction(WorkspaceAction.UpdateSidebarSearch(it)) },
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

            // 3 & 4. Scrollable Projects -> Nested Conversations Tree + Automations & Shortcuts
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Projects section header
                item(key = "projects-header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "Projeler",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            ) {
                                Text(
                                    text = state.projects.size.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                )
                            }
                        }
                        IconButton(
                            onClick = { showAddProjectDialog = true },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Yeni Proje Ekle",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                if (filteredProjectNodes.isEmpty() && trimmedQuery.isNotEmpty()) {
                    item(key = "search-empty") {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = "\"$trimmedQuery\" için eşleşen proje veya sohbet yok.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                TextButton(
                                    onClick = { onAction(WorkspaceAction.UpdateSidebarSearch("")) },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                ) {
                                    Text("Aramayı Temizle", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                } else {
                    items(
                        items = filteredProjectNodes,
                        key = { "project-${it.project.id}" },
                    ) { node ->
                        val isActiveProject = node.project.id == state.activeProject.id
                        val isExpanded = node.forceExpandedBySearch ||
                            node.project.id in state.expandedProjectIds

                        ProjectTreeSection(
                            project = node.project,
                            conversations = node.visibleConversations,
                            totalConversationCount = node.totalConversationCount,
                            isActiveProject = isActiveProject,
                            activeConversationId = state.activeConversationId,
                            isExpanded = isExpanded,
                            onToggleExpand = {
                                onAction(WorkspaceAction.ToggleProjectExpanded(node.project.id))
                            },
                            onCreateConversation = {
                                onAction(WorkspaceAction.CreateConversation(node.project.id))
                                onCloseDrawer()
                            },
                            onRenameProject = { projectToRename = node.project },
                            onDeleteProject = {
                                projectToDelete = node.project to node.totalConversationCount
                            },
                            onSelectConversation = { convId ->
                                onAction(WorkspaceAction.SelectConversation(convId))
                                onCloseDrawer()
                            },
                            onTogglePinConversation = { convId ->
                                onAction(WorkspaceAction.TogglePinConversation(convId))
                            },
                            onRenameConversation = { conv -> conversationToRename = conv },
                            onDeleteConversation = { conv -> conversationToDelete = conv },
                        )
                    }
                }

                // Divider before Automations & Shortcuts
                item(key = "automations-divider") {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 6.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    )
                }

                // 4. Compact Automations (/schedule) collapsible section
                item(key = "automations-section") {
                    CompactAutomationsSection(
                        tasks = state.scheduledTasks,
                        recentRuns = state.recentTaskRuns,
                        expanded = automationsExpanded,
                        onToggleExpanded = { automationsExpanded = !automationsExpanded },
                        onOpenAddSchedule = { showAddScheduleDialog = true },
                        onToggleTask = { id, enabled ->
                            onAction(WorkspaceAction.ToggleScheduledTask(id, enabled))
                        },
                        onTriggerTask = { id ->
                            onAction(WorkspaceAction.TriggerScheduledTask(id))
                        },
                        onCancelTask = { id ->
                            onAction(WorkspaceAction.CancelScheduledTask(id))
                        },
                    )
                }

                // Shortcut row: Customizations & MCP
                item(key = "customizations-shortcut") {
                    CustomizationsShortcutRow(
                        mcpRunning = state.mcpHealth.isRunning,
                        toolCount = state.mcpTools.size.coerceAtLeast(state.mcpHealth.registeredToolCount),
                        onClick = {
                            onAction(WorkspaceAction.OpenSettings(SettingsCategory.CUSTOMIZATIONS))
                            onCloseDrawer()
                        },
                    )
                }
            }

            // 5. Pinned Bottom Account & Visual Quota Footer (Desktop Style)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
            AccountQuotaFooter(
                activeProfile = state.activeProfile,
                profileCount = state.profiles.size,
                isRefreshingQuota = state.isRefreshingQuota,
                onOpenQuotaSheet = {
                    onAction(WorkspaceAction.OpenQuotaSheet)
                    onCloseDrawer()
                },
                onRefreshQuotas = { onAction(WorkspaceAction.RefreshAllQuotas) },
                onOpenAccountSwitcher = { showAccountSwitcherDialog = true },
                onOpenSettings = {
                    onAction(WorkspaceAction.OpenSettings(SettingsCategory.GENERAL))
                    onCloseDrawer()
                },
            )
        }
    }

    // Dialogs
    if (showAddProjectDialog) {
        AddProjectDialog(
            onDismiss = { showAddProjectDialog = false },
            onCreate = { name, path, mode ->
                onAction(WorkspaceAction.CreateProject(name, path, mode))
                showAddProjectDialog = false
            },
        )
    }

    projectToRename?.let { project ->
        RenameItemDialog(
            title = "Projeyi Yeniden Adlandır",
            label = "Proje Adı",
            initialValue = project.name,
            onDismiss = { projectToRename = null },
            onConfirm = { newName ->
                onAction(WorkspaceAction.RenameProject(project.id, newName))
                projectToRename = null
            },
        )
    }

    projectToDelete?.let { (project, convCount) ->
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            title = { Text("Projeyi Sil") },
            text = {
                Text(
                    text = "\"${project.name}\" projesi ve içindeki $convCount sohbet kalıcı olarak silinecek. Devam etmek istiyor musunuz?",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAction(WorkspaceAction.DeleteProject(project.id))
                        projectToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text("Sil")
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToDelete = null }) {
                    Text("İptal")
                }
            },
        )
    }

    conversationToRename?.let { conv ->
        RenameItemDialog(
            title = "Sohbeti Yeniden Adlandır",
            label = "Sohbet Başlığı",
            initialValue = conv.title,
            onDismiss = { conversationToRename = null },
            onConfirm = { newTitle ->
                onAction(WorkspaceAction.RenameConversation(conv.id, newTitle))
                conversationToRename = null
            },
        )
    }

    conversationToDelete?.let { conv ->
        AlertDialog(
            onDismissRequest = { conversationToDelete = null },
            title = { Text("Sohbeti Sil") },
            text = {
                Text(
                    text = "\"${conv.title}\" sohbeti ve tüm mesaj geçmişi silinecek. Emin misiniz?",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAction(WorkspaceAction.DeleteConversation(conv.id))
                        conversationToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text("Sil")
                }
            },
            dismissButton = {
                TextButton(onClick = { conversationToDelete = null }) {
                    Text("İptal")
                }
            },
        )
    }

    if (showAddScheduleDialog) {
        AddScheduleTaskDialog(
            onDismiss = { showAddScheduleDialog = false },
            onSchedule = { name, prompt, cron ->
                onAction(
                    WorkspaceAction.ScheduleTask(
                        name = name,
                        prompt = prompt,
                        cronExpression = cron.ifBlank { null },
                        exactTiming = true,
                    ),
                )
                showAddScheduleDialog = false
            },
        )
    }

    if (showAccountSwitcherDialog) {
        AccountSwitcherDialog(
            profiles = state.profiles,
            activeProfile = state.activeProfile,
            onDismiss = { showAccountSwitcherDialog = false },
            onSwitchProfile = { profileId ->
                onAction(WorkspaceAction.SwitchProfile(profileId))
                showAccountSwitcherDialog = false
            },
            onOpenAddAccount = {
                showAccountSwitcherDialog = false
                showAddAccountDialog = true
            },
            onOpenDesktopImport = {
                showAccountSwitcherDialog = false
                showImportDesktopDialog = true
            },
            onOpenAccountSettings = {
                showAccountSwitcherDialog = false
                onAction(WorkspaceAction.OpenSettings(SettingsCategory.ACCOUNTS_QUOTA))
                onCloseDrawer()
            },
        )
    }

    if (showAddAccountDialog) {
        AddAccountDialog(
            onDismiss = { showAddAccountDialog = false },
            onCreate = { name, email ->
                onAction(WorkspaceAction.CreateProfile(name, email))
                showAddAccountDialog = false
            },
        )
    }

    if (showImportDesktopDialog) {
        ImportDesktopProfileDialog(
            activeProfileId = state.activeProfile?.id ?: "default",
            onDismiss = { showImportDesktopDialog = false },
            onImport = { profileId, path ->
                onAction(WorkspaceAction.ImportDesktopProfile(profileId, path))
                showImportDesktopDialog = false
            },
        )
    }
}

@Composable
private fun DrawerBrandHeader(
    mcpRunning: Boolean,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(11.dp),
                color = Color.Transparent,
                border = BorderStroke(
                    1.dp,
                    Brush.linearGradient(
                        colors = listOf(
                            AntigravityPalette.NeonCyan.copy(alpha = 0.85f),
                            AntigravityPalette.HydrogenViolet.copy(alpha = 0.7f),
                        ),
                    ),
                ),
                modifier = Modifier.size(36.dp),
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    AntigravityPalette.NeonCyan.copy(alpha = 0.24f),
                                    AntigravityPalette.HydrogenViolet.copy(alpha = 0.26f),
                                ),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = AntigravityPalette.NeonCyan,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Column {
                Text(
                    text = "Antigravity",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (mcpRunning) AntigravityPalette.SuccessGreen
                                else AntigravityPalette.NeonCyan,
                            ),
                    )
                    Text(
                        text = "Mobile • Root",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = "Ayarlar",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SidebarSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = "Proje veya sohbet ara...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(22.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Aramayı temizle",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectTreeSection(
    project: WorkspaceProject,
    conversations: List<WorkspaceConversation>,
    totalConversationCount: Int,
    isActiveProject: Boolean,
    activeConversationId: String?,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onCreateConversation: () -> Unit,
    onRenameProject: () -> Unit,
    onDeleteProject: () -> Unit,
    onSelectConversation: (String) -> Unit,
    onTogglePinConversation: (String) -> Unit,
    onRenameConversation: (WorkspaceConversation) -> Unit,
    onDeleteConversation: (WorkspaceConversation) -> Unit,
) {
    var showProjectMenu by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        label = "projectChevronRotation",
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Expandable project header card
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isActiveProject) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
            },
            border = BorderStroke(
                width = 1.dp,
                color = if (isActiveProject) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onToggleExpand),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = if (isExpanded) "Daralt" else "Genişlet",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(chevronRotation),
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Rounded.FolderOpen else Icons.Rounded.Folder,
                    contentDescription = null,
                    tint = if (isActiveProject) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(17.dp),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                ) {
                    Text(
                        text = project.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isActiveProject) FontWeight.SemiBold else FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = project.rootPath,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                PermissionModePill(mode = project.defaultPermissionMode)

                IconButton(
                    onClick = onCreateConversation,
                    modifier = Modifier.size(26.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Bu projede yeni sohbet",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }

                Box {
                    IconButton(
                        onClick = { showProjectMenu = true },
                        modifier = Modifier.size(26.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Proje seçenekleri",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = showProjectMenu,
                        onDismissRequest = { showProjectMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Bu Projede Yeni Sohbet") },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            onClick = {
                                showProjectMenu = false
                                onCreateConversation()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Projeyi Yeniden Adlandır") },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            onClick = {
                                showProjectMenu = false
                                onRenameProject()
                            },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Projeyi Sil",
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            onClick = {
                                showProjectMenu = false
                                onDeleteProject()
                            },
                        )
                    }
                }
            }
        }

        // Nested conversations list
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(start = 14.dp, top = 2.dp, bottom = 2.dp),
            ) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .padding(vertical = 4.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    if (conversations.isEmpty() && totalConversationCount == 0) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(onClick = onCreateConversation),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ChatBubbleOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = "+ İlk sohbeti başlat",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        conversations.forEach { conversation ->
                            ConversationTreeItem(
                                conversation = conversation,
                                isActive = conversation.id == activeConversationId,
                                onSelect = { onSelectConversation(conversation.id) },
                                onTogglePin = { onTogglePinConversation(conversation.id) },
                                onRename = { onRenameConversation(conversation) },
                                onDelete = { onDeleteConversation(conversation) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionModePill(mode: PermissionMode) {
    val accent = when (mode) {
        PermissionMode.ASK -> AntigravityPalette.NeonCyan
        PermissionMode.AUTO -> AntigravityPalette.WarningAmber
        PermissionMode.PLAN -> AntigravityPalette.HydrogenViolet
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = accent.copy(alpha = 0.14f),
        border = BorderStroke(0.5.dp, accent.copy(alpha = 0.35f)),
    ) {
        Text(
            text = mode.name,
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
        )
    }
}

@Composable
private fun ConversationTreeItem(
    conversation: WorkspaceConversation,
    isActive: Boolean,
    onSelect: () -> Unit,
    onTogglePin: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    val relativeTime = remember(conversation.updatedAtEpochMs) {
        formatShortRelativeTime(conversation.updatedAtEpochMs)
    }
    val metaText = remember(conversation.messages.size, relativeTime) {
        val countPart = "${conversation.messages.size} mesaj"
        if (relativeTime != null) "$countPart • $relativeTime" else countPart
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isActive) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
        } else {
            Color.Transparent
        },
        border = if (isActive) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
        } else {
            null
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onSelect),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            // Cyan left accent bar for active conversation
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(26.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (isActive) AntigravityPalette.NeonCyan else Color.Transparent,
                    ),
            )

            Icon(
                imageVector = if (conversation.isPinned) {
                    Icons.Rounded.PushPin
                } else {
                    Icons.Rounded.ChatBubbleOutline
                },
                contentDescription = null,
                tint = when {
                    conversation.isPinned -> AntigravityPalette.WarningAmber
                    isActive -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                },
                modifier = Modifier.size(15.dp),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = conversation.title.ifBlank { WorkspaceConversation.DEFAULT_TITLE },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isActive) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = metaText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(26.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Sohbet seçenekleri",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp),
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(if (conversation.isPinned) "Sabitlemeyi Kaldır" else "Sabitle")
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.PushPin,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp),
                            )
                        },
                        onClick = {
                            showMenu = false
                            onTogglePin()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Yeniden Adlandır") },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp),
                            )
                        },
                        onClick = {
                            showMenu = false
                            onRename()
                        },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Sohbeti Sil",
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(17.dp),
                            )
                        },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactAutomationsSection(
    tasks: List<ScheduledTask>,
    recentRuns: List<ScheduledTaskRun>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onOpenAddSchedule: () -> Unit,
    onToggleTask: (String, Boolean) -> Unit,
    onTriggerTask: (String) -> Unit,
    onCancelTask: (String) -> Unit,
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        label = "automationsChevronRotation",
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onToggleExpanded),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = if (expanded) "Daralt" else "Genişlet",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(17.dp)
                            .rotate(chevronRotation),
                    )
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(17.dp),
                    )
                    Text(
                        text = "Otomasyonlar (/schedule)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Surface(
                        shape = CircleShape,
                        color = if (tasks.isNotEmpty()) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    ) {
                        Text(
                            text = tasks.size.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (tasks.isNotEmpty()) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                }
                IconButton(
                    onClick = onOpenAddSchedule,
                    modifier = Modifier.size(26.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Görev zamanla",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (tasks.isEmpty()) {
                    Text(
                        text = "Henüz zamanlanmış görev yok. '+' ile veya /schedule komutuyla ekleyin.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                } else {
                    tasks.forEach { task ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(
                                0.5.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = task.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = task.cronExpression ?: "Tek seferlik",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { onTriggerTask(task.id) },
                                            modifier = Modifier.size(28.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.PlayArrow,
                                                contentDescription = "Şimdi çalıştır",
                                                tint = AntigravityPalette.SuccessGreen,
                                                modifier = Modifier.size(17.dp),
                                            )
                                        }
                                        IconButton(
                                            onClick = { onCancelTask(task.id) },
                                            modifier = Modifier.size(28.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.DeleteOutline,
                                                contentDescription = "Görevi sil",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                        Switch(
                                            checked = task.enabled,
                                            onCheckedChange = { enabled -> onToggleTask(task.id, enabled) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (recentRuns.isNotEmpty()) {
                    Column(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = "Son Çalışmalar (${recentRuns.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        recentRuns.take(2).forEach { run ->
                            Text(
                                text = "• ${run.status.name}: ${run.summary.take(36)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomizationsShortcutRow(
    mcpRunning: Boolean,
    toolCount: Int,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = null,
                    tint = AntigravityPalette.HydrogenViolet,
                    modifier = Modifier.size(17.dp),
                )
                Text(
                    text = "Özelleştirmeler & MCP",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(
                            if (mcpRunning) AntigravityPalette.SuccessGreen
                            else AntigravityPalette.WarningAmber,
                        ),
                )
                Text(
                    text = "$toolCount araç",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun AccountQuotaFooter(
    activeProfile: AccountProfile?,
    profileCount: Int,
    isRefreshingQuota: Boolean,
    onOpenQuotaSheet: () -> Unit,
    onRefreshQuotas: () -> Unit,
    onOpenAccountSwitcher: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val remainingPercent = activeProfile?.quota?.remainingPercent
    val planTier = activeProfile?.quota?.planTier ?: AccountPlanTier.UNKNOWN

    val quotaBarColor = when {
        remainingPercent == null -> MaterialTheme.colorScheme.primary
        remainingPercent <= 15.0 -> AntigravityPalette.CriticalRed
        remainingPercent <= 35.0 -> AntigravityPalette.WarningAmber
        else -> AntigravityPalette.SuccessGreen
    }

    val tierColors = when (planTier) {
        AccountPlanTier.ULTRA -> AntigravityPalette.HydrogenViolet.copy(alpha = 0.22f) to AntigravityPalette.HydrogenViolet
        AccountPlanTier.PRO -> AntigravityPalette.NeonCyan.copy(alpha = 0.2f) to AntigravityPalette.NeonCyan
        AccountPlanTier.ENTERPRISE -> AntigravityPalette.CarbonTeal.copy(alpha = 0.2f) to AntigravityPalette.CarbonTeal
        AccountPlanTier.FREE, AccountPlanTier.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Account identity row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        AccountAvatarBadge(profile = activeProfile)

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = activeProfile?.displayName ?: "Hesap Bağlı Değil",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                if (activeProfile != null && planTier != AccountPlanTier.UNKNOWN) {
                                    Surface(
                                        shape = RoundedCornerShape(5.dp),
                                        color = tierColors.first,
                                    ) {
                                        Text(
                                            text = planTier.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = tierColors.second,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                        )
                                    }
                                }
                            }
                            Text(
                                text = activeProfile?.email ?: "Google hesabı ekle veya seç",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        IconButton(
                            onClick = onOpenAccountSwitcher,
                            modifier = Modifier.size(30.dp),
                        ) {
                            Icon(
                                imageVector = if (profileCount > 1) {
                                    Icons.Rounded.SwapHoriz
                                } else {
                                    Icons.Rounded.PersonAdd
                                },
                                contentDescription = "Hesap değiştir veya ekle",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }

                    // Visual Quota Progress Bar (Tapping opens Quota Sheet directly)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                        border = BorderStroke(
                            0.5.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onOpenQuotaSheet),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "Kalan Kota",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = if (remainingPercent != null) {
                                            "%.0f%%".format(Locale.US, remainingPercent)
                                        } else {
                                            "Detaylar"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = quotaBarColor,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .clickable(
                                                enabled = !isRefreshingQuota,
                                                onClick = onRefreshQuotas,
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (isRefreshingQuota) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(13.dp),
                                                strokeWidth = 1.6.dp,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Rounded.Refresh,
                                                contentDescription = "Kotaları yenile",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(14.dp),
                                            )
                                        }
                                    }
                                }
                            }
                            val fraction = ((remainingPercent ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
                            LinearProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = quotaBarColor,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                        }
                    }
                }
            }

            // Bottom quick action row: Account Switcher + Ayarlar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onOpenAccountSwitcher,
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SwapHoriz,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (profileCount > 0) "Hesaplar ($profileCount)" else "Hesap Ekle",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                OutlinedButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Ayarlar",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountAvatarBadge(profile: AccountProfile?) {
    val initial = profile?.displayName?.trim()?.firstOrNull()?.uppercaseChar()
        ?: profile?.email?.trim()?.firstOrNull()?.uppercaseChar()

    Box(modifier = Modifier.size(34.dp)) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            AntigravityPalette.NeonCyan.copy(alpha = 0.3f),
                            AntigravityPalette.HydrogenViolet.copy(alpha = 0.35f),
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (initial != null) {
                Text(
                    text = initial.toString(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (profile?.isAuthenticated == true) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(1.5.dp)
                    .clip(CircleShape)
                    .background(AntigravityPalette.SuccessGreen),
            )
        }
    }
}

private fun formatShortRelativeTime(epochMs: Long): String? {
    if (epochMs <= 0L) return null
    val diffMs = (System.currentTimeMillis() - epochMs).coerceAtLeast(0L)
    val minutes = diffMs / 60_000L
    val hours = minutes / 60L
    val days = hours / 24L
    return when {
        minutes < 1L -> "şimdi"
        minutes < 60L -> "${minutes} dk"
        hours < 24L -> "${hours} sa"
        days < 30L -> "${days} gün"
        else -> "${days / 30L} ay"
    }
}

@Composable
private fun RenameItemDialog(
    title: String,
    label: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember(initialValue) { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(text.trim()) },
                enabled = text.isNotBlank(),
            ) {
                Text("Kaydet")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal")
            }
        },
    )
}

@Composable
private fun AccountSwitcherDialog(
    profiles: List<AccountProfile>,
    activeProfile: AccountProfile?,
    onDismiss: () -> Unit,
    onSwitchProfile: (String) -> Unit,
    onOpenAddAccount: () -> Unit,
    onOpenDesktopImport: () -> Unit,
    onOpenAccountSettings: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Google Hesapları") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (profiles.isEmpty()) {
                    Text(
                        text = "Henüz kayıtlı Google profili yok. Yeni profil oluşturun veya masaüstü ~/.gemini profilini içe aktarın.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    profiles.forEach { profile ->
                        val isSelected = profile.id == activeProfile?.id
                        val pct = profile.quota.remainingPercent
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            },
                            border = if (isSelected) {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            } else {
                                null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onSwitchProfile(profile.id) },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                AccountAvatarBadge(profile = profile)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = profile.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = buildString {
                                            append(profile.email ?: "OAuth bekleniyor")
                                            if (pct != null) {
                                                append(" • %.0f%%".format(Locale.US, pct))
                                            }
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "Aktif hesap",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onOpenAddAccount,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Hesap Ekle", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = onOpenDesktopImport,
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Download,
                            contentDescription = "Masaüstü ~/.gemini içe aktar",
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenAccountSettings) {
                Text("Hesap Ayarları")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Kapat")
            }
        },
    )
}

@Composable
private fun AddAccountDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, email: String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yeni Google Profili Ekle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Profil Adı (ör. Kişisel / İş)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Google E-posta (opsiyonel)") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, email.ifBlank { null }) },
                enabled = name.isNotBlank(),
            ) {
                Text("Oluştur")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal") }
        },
    )
}

@Composable
private fun ImportDesktopProfileDialog(
    activeProfileId: String,
    onDismiss: () -> Unit,
    onImport: (profileId: String, sourcePath: String) -> Unit,
) {
    var path by remember { mutableStateOf("/sdcard/Download/gemini-profile.tar.gz") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Masaüstü ~/.gemini Profilini İçe Aktar") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Bilgisayarınızdaki giriş yapılmış ~/.gemini veya antigravity-cli dizini/arşiv yolunu girin:",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text("Kaynak yol") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onImport(activeProfileId, path) },
                enabled = path.isNotBlank(),
            ) {
                Text("İçe Aktar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal") }
        },
    )
}

@Composable
private fun AddProjectDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, path: String, mode: PermissionMode) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var path by remember { mutableStateOf("/mnt/sdcard/Projects/") }
    var mode by remember { mutableStateOf(PermissionMode.ASK) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yeni Proje Ekle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Proje Adı") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text("Cihaz Dizini") },
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PermissionMode.entries.forEach { option ->
                        FilterChip(
                            selected = mode == option,
                            onClick = { mode = option },
                            label = { Text(option.name) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, path, mode) },
                enabled = name.isNotBlank() && path.isNotBlank(),
            ) {
                Text("Ekle")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal") }
        },
    )
}

@Composable
private fun AddScheduleTaskDialog(
    onDismiss: () -> Unit,
    onSchedule: (name: String, prompt: String, cron: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var cron by remember { mutableStateOf("0 9 * * *") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Otomasyon Zamanla (/schedule)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Görev Adı") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Ajan Talimatı") },
                )
                OutlinedTextField(
                    value = cron,
                    onValueChange = { cron = it },
                    label = { Text("Cron İfadesi (boş = 1 dk sonra)") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSchedule(name, prompt, cron) },
                enabled = prompt.isNotBlank(),
            ) {
                Text("Zamanla")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal") }
        },
    )
}
