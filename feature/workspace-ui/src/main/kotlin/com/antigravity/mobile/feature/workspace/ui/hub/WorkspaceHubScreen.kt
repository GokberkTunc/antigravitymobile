package com.antigravity.mobile.feature.workspace.ui.hub

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.antigravity.mobile.contracts.AccountPlanTier
import com.antigravity.mobile.contracts.AccountProfile
import com.antigravity.mobile.contracts.PermissionMode
import com.antigravity.mobile.contracts.WorkspaceProject
import com.antigravity.mobile.feature.workspace.model.QuotaBucketParser
import com.antigravity.mobile.feature.workspace.model.QuotaBucketUi
import com.antigravity.mobile.feature.workspace.model.QuotaWindowKind
import com.antigravity.mobile.feature.workspace.model.SettingsCategory
import com.antigravity.mobile.feature.workspace.model.WorkspaceAction
import com.antigravity.mobile.feature.workspace.model.WorkspaceConversation
import com.antigravity.mobile.feature.workspace.model.WorkspaceUiState
import com.antigravity.mobile.feature.workspace.theme.LocalAntigravityColors
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val EXIT_CONFIRM_AUTO_DISMISS_MS = 2_000L

private data class HubFilteredProjectNode(
    val project: WorkspaceProject,
    val visibleConversations: List<WorkspaceConversation>,
    val totalConversationCount: Int,
    val forceExpandedBySearch: Boolean,
)

/**
 * Official Google Antigravity Mobile root Home / Workspace Manager screen.
 *
 * Adapts Antigravity Desktop's Projects & Conversations workspace ergonomically for mobile
 * with live multi-bucket quota visibility, search filtering, full project/conversation CRUD
 * (including genuine empty states when all projects or conversations are deleted), and
 * full Light + Dark Material 3 theme support.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceHubScreen(
    state: WorkspaceUiState,
    onAction: (WorkspaceAction) -> Unit,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extColors = LocalAntigravityColors.current

    var showAddProjectDialog by remember { mutableStateOf(false) }
    var projectToRename by remember { mutableStateOf<WorkspaceProject?>(null) }
    var projectToDelete by remember { mutableStateOf<Pair<WorkspaceProject, Int>?>(null) }
    var conversationToRename by remember { mutableStateOf<WorkspaceConversation?>(null) }
    var conversationToDelete by remember { mutableStateOf<WorkspaceConversation?>(null) }
    var generalConversationsExpanded by remember { mutableStateOf(true) }

    LaunchedEffect(state.exitConfirmPending, state.exitConfirmMessage) {
        if (state.exitConfirmPending && state.exitConfirmMessage != null) {
            delay(EXIT_CONFIRM_AUTO_DISMISS_MS)
            onAction(WorkspaceAction.DismissExitConfirm)
        }
    }

    val trimmedQuery = state.sidebarSearchQuery.trim()
    val projectIds = remember(state.projects) { state.projects.map { it.id }.toSet() }

    val filteredProjectNodes = remember(state.projects, state.conversations, trimmedQuery) {
        val convsByProject = state.conversations.groupBy { it.projectId }
        state.projects.mapNotNull { project ->
            val sortedConvs = convsByProject[project.id].orEmpty()
                .sortedWith(
                    compareByDescending<WorkspaceConversation> { it.isPinned }
                        .thenByDescending { it.updatedAtEpochMs },
                )
            if (trimmedQuery.isEmpty()) {
                HubFilteredProjectNode(
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
                    HubFilteredProjectNode(
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

    val orphanConversations = remember(state.conversations, projectIds, trimmedQuery) {
        val sortedOrphans = state.conversations
            .filter { it.projectId !in projectIds }
            .sortedWith(
                compareByDescending<WorkspaceConversation> { it.isPinned }
                    .thenByDescending { it.updatedAtEpochMs },
            )
        if (trimmedQuery.isEmpty() || "Genel Sohbetler".contains(trimmedQuery, ignoreCase = true)) {
            sortedOrphans
        } else {
            sortedOrphans.filter { it.title.contains(trimmedQuery, ignoreCase = true) }
        }
    }

    val subtitleText = state.activeProfile?.email
        ?: state.activeProfile?.displayName
        ?: "Çalışma Alanı"

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    navigationIcon = {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(
                                imageVector = Icons.Rounded.Menu,
                                contentDescription = "Menüyü aç",
                            )
                        }
                    },
                    title = {
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                text = "Antigravity",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = subtitleText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    actions = {
                        if (state.settings.fullAccessEnabled) {
                            Surface(
                                onClick = {
                                    onAction(
                                        WorkspaceAction.OpenSettingsDetail(SettingsCategory.PROJECTS_PERMISSIONS),
                                    )
                                },
                                shape = CircleShape,
                                color = extColors.critical.copy(alpha = if (extColors.isDark) 0.18f else 0.12f),
                                border = BorderStroke(1.dp, extColors.critical.copy(alpha = 0.45f)),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Shield,
                                        contentDescription = null,
                                        tint = extColors.critical,
                                        modifier = Modifier.size(12.dp),
                                    )
                                    Text(
                                        text = "Tam Yetki",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = extColors.critical,
                                        maxLines = 1,
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        HubAccountActionButton(
                            activeProfile = state.activeProfile,
                            onClick = { onAction(WorkspaceAction.OpenQuotaSheet) },
                        )
                        IconButton(onClick = { onAction(WorkspaceAction.OpenSettingsList) }) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = "Ayarlar",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
                HorizontalDivider(color = extColors.cardBorder.copy(alpha = 0.7f))
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onAction(WorkspaceAction.CreateConversation(state.selectedProjectId)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                    )
                },
                text = {
                    Text(
                        text = "Yeni Sohbet",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 104.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Optional non-intrusive error banner
                state.errorMessage?.let { errorMsg ->
                    item(key = "hub-error-banner") {
                        HubErrorBanner(
                            message = errorMsg,
                            onDismiss = { onAction(WorkspaceAction.DismissError) },
                        )
                    }
                }

                // 1. Live Multi-Bucket Quota Summary Card
                item(key = "hub-quota-summary") {
                    HubMultiBucketQuotaCard(
                        activeProfile = state.activeProfile,
                        profileCount = state.profiles.size,
                        isRefreshing = state.isRefreshingQuota,
                        onOpenQuotaSheet = { onAction(WorkspaceAction.OpenQuotaSheet) },
                        onRefreshAllQuotas = { onAction(WorkspaceAction.RefreshAllQuotas) },
                    )
                }

                // 2. Clean Rounded Search Bar
                item(key = "hub-search-bar") {
                    HubSearchBar(
                        query = state.sidebarSearchQuery,
                        onQueryChange = { onAction(WorkspaceAction.UpdateSidebarSearch(it)) },
                    )
                }

                // 3. Projects Section Header
                item(key = "hub-projects-header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "Projeler",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                            ) {
                                Text(
                                    text = state.projects.size.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }

                        FilledTonalButton(
                            onClick = { showAddProjectDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.height(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Proje",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }

                // 4. Genuine Empty State / Search Empty State / Expandable Project Cards
                when {
                    trimmedQuery.isNotEmpty() && filteredProjectNodes.isEmpty() && orphanConversations.isEmpty() -> {
                        item(key = "hub-search-empty") {
                            HubSearchEmptyCard(
                                query = trimmedQuery,
                                onClear = { onAction(WorkspaceAction.UpdateSidebarSearch("")) },
                            )
                        }
                    }

                    state.projects.isEmpty() && trimmedQuery.isEmpty() -> {
                        item(key = "hub-projects-empty") {
                            HubEmptyProjectsCard(
                                onAddProject = { showAddProjectDialog = true },
                                onStartQuickChat = { onAction(WorkspaceAction.CreateConversation(null)) },
                            )
                        }
                    }

                    else -> {
                        items(
                            items = filteredProjectNodes,
                            key = { "hub-project-${it.project.id}" },
                        ) { node ->
                            val isSelectedProject = node.project.id == state.selectedProjectId
                            val isExpanded = node.forceExpandedBySearch ||
                                node.project.id in state.expandedProjectIds

                            HubProjectCard(
                                project = node.project,
                                conversations = node.visibleConversations,
                                totalConversationCount = node.totalConversationCount,
                                isSelected = isSelectedProject,
                                activeConversationId = state.activeConversationId,
                                isExpanded = isExpanded,
                                onToggleExpand = {
                                    onAction(WorkspaceAction.ToggleProjectExpanded(node.project.id))
                                },
                                onCreateConversation = {
                                    onAction(WorkspaceAction.CreateConversation(node.project.id))
                                },
                                onRenameProject = { projectToRename = node.project },
                                onDeleteProject = {
                                    projectToDelete = node.project to node.totalConversationCount
                                },
                                onSelectConversation = { convId ->
                                    onAction(WorkspaceAction.SelectConversation(convId))
                                },
                                onTogglePinConversation = { convId ->
                                    onAction(WorkspaceAction.TogglePinConversation(convId))
                                },
                                onRenameConversation = { conv -> conversationToRename = conv },
                                onDeleteConversation = { conv -> conversationToDelete = conv },
                            )
                        }
                    }
                }

                // 5. Orphan / Device-Wide Conversations ("Genel Sohbetler")
                if (orphanConversations.isNotEmpty()) {
                    item(key = "hub-general-conversations") {
                        HubGeneralConversationsCard(
                            conversations = orphanConversations,
                            activeConversationId = state.activeConversationId,
                            isExpanded = trimmedQuery.isNotEmpty() || generalConversationsExpanded,
                            onToggleExpand = {
                                generalConversationsExpanded = !generalConversationsExpanded
                            },
                            onCreateConversation = {
                                onAction(WorkspaceAction.CreateConversation(null))
                            },
                            onSelectConversation = { convId ->
                                onAction(WorkspaceAction.SelectConversation(convId))
                            },
                            onTogglePinConversation = { convId ->
                                onAction(WorkspaceAction.TogglePinConversation(convId))
                            },
                            onRenameConversation = { conv -> conversationToRename = conv },
                            onDeleteConversation = { conv -> conversationToDelete = conv },
                        )
                    }
                }
            }

            // 6. Sleek Non-Intrusive Double-Back Exit Confirmation Pill
            AnimatedVisibility(
                visible = state.exitConfirmPending && state.exitConfirmMessage != null,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp, vertical = 18.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    shadowElevation = 4.dp,
                ) {
                    Text(
                        text = state.exitConfirmMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }

    // Dialogs
    if (showAddProjectDialog) {
        HubAddProjectDialog(
            onDismiss = { showAddProjectDialog = false },
            onCreate = { name, path, mode ->
                onAction(WorkspaceAction.CreateProject(name, path, mode))
                showAddProjectDialog = false
            },
        )
    }

    projectToRename?.let { project ->
        HubRenameDialog(
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
                    text = if (convCount > 0) {
                        "\"${project.name}\" projesi ve içindeki $convCount sohbet kalıcı olarak silinecek. Devam etmek istiyor musunuz?"
                    } else {
                        "\"${project.name}\" projesi kalıcı olarak silinecek. Devam etmek istiyor musunuz?"
                    },
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
        HubRenameDialog(
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
                    text = "\"${conv.title.ifBlank { WorkspaceConversation.DEFAULT_TITLE }}\" sohbeti ve tüm mesaj geçmişi kalıcı olarak silinecek. Emin misiniz?",
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
}

@Composable
private fun hubCardSurfaceColor(): Color {
    val ext = LocalAntigravityColors.current
    return if (ext.isDark) ext.cardBackground else MaterialTheme.colorScheme.surface
}

@Composable
private fun HubAccountActionButton(
    activeProfile: AccountProfile?,
    onClick: () -> Unit,
) {
    val ext = LocalAntigravityColors.current
    val initial = activeProfile?.displayName?.trim()?.firstOrNull()?.uppercaseChar()
        ?: activeProfile?.email?.trim()?.firstOrNull()?.uppercaseChar()

    IconButton(onClick = onClick) {
        if (initial != null) {
            Box(modifier = Modifier.size(30.dp)) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = initial.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                if (activeProfile?.isAuthenticated == true) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .align(Alignment.BottomEnd)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.background)
                            .padding(1.5.dp)
                            .clip(CircleShape)
                            .background(ext.success),
                    )
                }
            }
        } else {
            Icon(
                imageVector = Icons.Rounded.AccountCircle,
                contentDescription = "Hesap ve Kota",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HubErrorBanner(
    message: String,
    onDismiss: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(26.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Hatayı kapat",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun HubMultiBucketQuotaCard(
    activeProfile: AccountProfile?,
    profileCount: Int,
    isRefreshing: Boolean,
    onOpenQuotaSheet: () -> Unit,
    onRefreshAllQuotas: () -> Unit,
) {
    val ext = LocalAntigravityColors.current
    val quota = activeProfile?.quota
    val groupedBuckets = remember(quota?.rawSummary) {
        quota?.let { QuotaBucketParser.groupedByFamily(it) }.orEmpty()
    }
    val hasAnyQuotaData = groupedBuckets.isNotEmpty() || quota?.remainingPercent != null

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = hubCardSurfaceColor(),
        border = BorderStroke(1.dp, ext.cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onOpenQuotaSheet),
    ) {
        when {
            activeProfile == null -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.AccountCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = "Google hesabı ekle",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Anlık 5 saatlik ve haftalık kota takibi için hesabınızı bağlayın",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            !hasAnyQuotaData -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(42.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = "Kota bilgisini yükle",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = activeProfile.email ?: activeProfile.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    HubQuotaRefreshButton(
                        isRefreshing = isRefreshing,
                        onClick = onRefreshAllQuotas,
                    )
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Header: Active Account + Plan Badge + Refresh Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = "Kota Özeti",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                if (quota != null && quota.planTier != AccountPlanTier.UNKNOWN) {
                                    HubPlanTierBadge(planTier = quota.planTier)
                                }
                            }
                            Text(
                                text = buildString {
                                    append(activeProfile.displayName)
                                    if (profileCount > 1) {
                                        append(" • $profileCount hesap")
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            HubQuotaRefreshButton(
                                isRefreshing = isRefreshing,
                                onClick = onRefreshAllQuotas,
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = "Kota detaylarını aç",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    HorizontalDivider(color = ext.cardBorder.copy(alpha = 0.7f))

                    val fallbackRemaining = quota?.remainingPercent
                    if (groupedBuckets.isNotEmpty()) {
                        val entries = groupedBuckets.entries.toList()
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            entries.forEachIndexed { index, (familyTitle, buckets) ->
                                if (index > 0) {
                                    HorizontalDivider(color = ext.cardBorder.copy(alpha = 0.5f))
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = familyTitle,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    buckets.forEach { bucket ->
                                        HubQuotaBucketRow(bucket = bucket)
                                    }
                                }
                            }
                        }
                    } else if (fallbackRemaining != null) {
                        HubQuotaMetricRow(
                            label = "Kalan Kota",
                            percent = fallbackRemaining,
                            resetLabel = formatHubResetCountdown(quota.resetAtEpochMs),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HubQuotaRefreshButton(
    isRefreshing: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = !isRefreshing,
        modifier = Modifier.size(34.dp),
    ) {
        if (isRefreshing) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = "Kotaları yenile",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun HubQuotaBucketRow(bucket: QuotaBucketUi) {
    val label = when (bucket.windowKind) {
        QuotaWindowKind.FIVE_HOUR -> "5 Saatlik"
        QuotaWindowKind.WEEKLY -> "Haftalık"
        QuotaWindowKind.OTHER -> bucket.windowLabel
    }
    val resetLabel = formatHubResetCountdown(bucket.resetAtEpochMs)
    HubQuotaMetricRow(
        label = label,
        percent = bucket.remainingPercent,
        resetLabel = resetLabel,
    )
}

@Composable
private fun HubQuotaMetricRow(
    label: String,
    percent: Double,
    resetLabel: String,
) {
    val barColor = hubQuotaSemanticColor(percent)
    val clamped = percent.coerceIn(0.0, 100.0)
    val percentText = "%${clamped.roundToInt()}"
    val fraction = (clamped / 100.0).toFloat()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = percentText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = barColor,
                )
            }
            Text(
                text = resetLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp)),
            color = barColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
private fun HubPlanTierBadge(planTier: AccountPlanTier) {
    val (bg, fg) = when (planTier) {
        AccountPlanTier.ULTRA -> {
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        }

        AccountPlanTier.PRO -> {
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        }

        AccountPlanTier.ENTERPRISE -> {
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        }

        AccountPlanTier.FREE, AccountPlanTier.UNKNOWN -> {
            MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        }
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bg,
    ) {
        Text(
            text = planTier.name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun HubSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    val ext = LocalAntigravityColors.current
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = hubCardSurfaceColor(),
        border = BorderStroke(1.dp, ext.cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = "Proje veya sohbet ara...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Aramayı temizle",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HubEmptyProjectsCard(
    onAddProject: () -> Unit,
    onStartQuickChat: () -> Unit,
) {
    val ext = LocalAntigravityColors.current
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = hubCardSurfaceColor(),
        border = BorderStroke(1.dp, ext.cardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(56.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.CreateNewFolder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Henüz proje yok",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Klasör ekleyerek çalışma alanı oluşturun veya doğrudan cihaz geneli sohbet başlatın",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onAddProject,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 11.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Proje Ekle",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }

                OutlinedButton(
                    onClick = onStartQuickChat,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 11.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ChatBubbleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Hızlı Sohbet Başlat",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun HubSearchEmptyCard(
    query: String,
    onClear: () -> Unit,
) {
    val ext = LocalAntigravityColors.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = hubCardSurfaceColor(),
        border = BorderStroke(1.dp, ext.cardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "\"$query\" için eşleşen proje veya sohbet bulunamadı.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = onClear) {
                Text("Aramayı Temizle")
            }
        }
    }
}

@Composable
private fun HubProjectCard(
    project: WorkspaceProject,
    conversations: List<WorkspaceConversation>,
    totalConversationCount: Int,
    isSelected: Boolean,
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
    val ext = LocalAntigravityColors.current
    var showProjectMenu by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        label = "hubProjectChevron",
    )

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = hubCardSurfaceColor(),
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            } else {
                ext.cardBorder
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(start = 10.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = if (isExpanded) "Daralt" else "Genişlet",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(chevronRotation),
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isExpanded) {
                                Icons.Rounded.FolderOpen
                            } else {
                                Icons.Rounded.Folder
                            },
                            contentDescription = null,
                            tint = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = project.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
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

                HubPermissionPill(mode = project.defaultPermissionMode)

                IconButton(
                    onClick = onCreateConversation,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Bu projede yeni sohbet",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(19.dp),
                    )
                }

                Box {
                    IconButton(
                        onClick = { showProjectMenu = true },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Proje seçenekleri",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
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
                                    imageVector = Icons.Rounded.Add,
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
                            text = { Text("Yeniden Adlandır") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
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
                                    imageVector = Icons.Rounded.DeleteOutline,
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

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = ext.cardBorder.copy(alpha = 0.7f))
                    if (conversations.isEmpty() && totalConversationCount == 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onCreateConversation)
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ChatBubbleOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = "+ İlk sohbeti başlat",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            conversations.forEach { conversation ->
                                HubConversationRow(
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
}

@Composable
private fun HubGeneralConversationsCard(
    conversations: List<WorkspaceConversation>,
    activeConversationId: String?,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onCreateConversation: () -> Unit,
    onSelectConversation: (String) -> Unit,
    onTogglePinConversation: (String) -> Unit,
    onRenameConversation: (WorkspaceConversation) -> Unit,
    onDeleteConversation: (WorkspaceConversation) -> Unit,
) {
    val ext = LocalAntigravityColors.current
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        label = "hubGeneralChevron",
    )

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = hubCardSurfaceColor(),
        border = BorderStroke(1.dp, ext.cardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(start = 10.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = if (isExpanded) "Daralt" else "Genişlet",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(chevronRotation),
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Forum,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "Genel Sohbetler",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Text(
                                text = conversations.size.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            )
                        }
                    }
                    Text(
                        text = "Cihaz geneli ve bağımsız oturumlar",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                IconButton(
                    onClick = onCreateConversation,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Yeni genel sohbet",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = ext.cardBorder.copy(alpha = 0.7f))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        conversations.forEach { conversation ->
                            HubConversationRow(
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
private fun HubConversationRow(
    conversation: WorkspaceConversation,
    isActive: Boolean,
    onSelect: () -> Unit,
    onTogglePin: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val ext = LocalAntigravityColors.current
    var showMenu by remember { mutableStateOf(false) }
    val relativeTime = remember(conversation.updatedAtEpochMs) {
        formatHubRelativeTime(conversation.updatedAtEpochMs)
    }
    val metaText = remember(conversation.messages.size, relativeTime) {
        val countPart = "${conversation.messages.size} mesaj"
        if (relativeTime != null) "$countPart • $relativeTime" else countPart
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isActive) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            Color.Transparent
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onSelect),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = if (conversation.isPinned) {
                    Icons.Rounded.PushPin
                } else {
                    Icons.Rounded.ChatBubbleOutline
                },
                contentDescription = null,
                tint = when {
                    conversation.isPinned -> ext.warning
                    isActive -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(16.dp),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = conversation.title.ifBlank { WorkspaceConversation.DEFAULT_TITLE },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = metaText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(30.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Sohbet seçenekleri",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
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
                                imageVector = Icons.Rounded.PushPin,
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
                                imageVector = Icons.Rounded.Edit,
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
                                imageVector = Icons.Rounded.DeleteOutline,
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
private fun HubPermissionPill(mode: PermissionMode) {
    val ext = LocalAntigravityColors.current
    val accent = when (mode) {
        PermissionMode.ASK -> MaterialTheme.colorScheme.primary
        PermissionMode.AUTO -> ext.warning
        PermissionMode.PLAN -> MaterialTheme.colorScheme.secondary
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = accent.copy(alpha = 0.12f),
        border = BorderStroke(0.5.dp, accent.copy(alpha = 0.35f)),
    ) {
        Text(
            text = mode.name,
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun hubQuotaSemanticColor(remainingPercent: Double?): Color {
    val ext = LocalAntigravityColors.current
    return when {
        remainingPercent == null -> MaterialTheme.colorScheme.onSurfaceVariant
        remainingPercent > 40.0 -> ext.success
        remainingPercent > 15.0 -> ext.warning
        else -> ext.critical
    }
}

private fun formatHubResetCountdown(
    resetAtEpochMs: Long?,
    nowEpochMs: Long = System.currentTimeMillis(),
): String {
    if (resetAtEpochMs == null || resetAtEpochMs <= 0L) return "Hazır"
    val remainingMs = resetAtEpochMs - nowEpochMs
    if (remainingMs < 60_000L) return "Yenilenmek üzere"

    val totalMinutes = remainingMs / 60_000L
    val days = totalMinutes / (24L * 60L)
    val hours = (totalMinutes % (24L * 60L)) / 60L
    val minutes = totalMinutes % 60L

    return when {
        days > 0L && hours > 0L -> "${days}g ${hours}s sonra yenilenir"
        days > 0L -> "${days}g sonra yenilenir"
        hours > 0L && minutes > 0L -> "${hours}s ${minutes}dk sonra yenilenir"
        hours > 0L -> "${hours}s sonra yenilenir"
        else -> "${minutes}dk sonra yenilenir"
    }
}

private fun formatHubRelativeTime(epochMs: Long): String? {
    if (epochMs <= 0L) return null
    val diffMs = (System.currentTimeMillis() - epochMs).coerceAtLeast(0L)
    val minutes = diffMs / 60_000L
    val hours = minutes / 60L
    val days = hours / 24L
    return when {
        minutes < 1L -> "şimdi"
        minutes < 60L -> "$minutes dk"
        hours < 24L -> "$hours sa"
        days < 30L -> "$days gün"
        else -> "${days / 30L} ay"
    }
}

@Composable
private fun HubRenameDialog(
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
private fun HubAddProjectDialog(
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
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Proje Adı") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text("Cihaz Dizini") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
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
                onClick = { onCreate(name.trim(), path.trim(), mode) },
                enabled = name.isNotBlank() && path.isNotBlank(),
            ) {
                Text("Ekle")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal")
            }
        },
    )
}
