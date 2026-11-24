package com.antigravity.mobile.feature.workspace.ui.drawer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.antigravity.mobile.contracts.AccountProfile
import com.antigravity.mobile.contracts.McpToolRisk
import com.antigravity.mobile.contracts.PermissionMode
import com.antigravity.mobile.feature.workspace.model.WorkspaceAction
import com.antigravity.mobile.feature.workspace.model.WorkspaceUiState
import com.antigravity.mobile.feature.workspace.theme.AntigravityPalette

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkspaceDrawerSheet(
    state: WorkspaceUiState,
    onAction: (WorkspaceAction) -> Unit,
    onCloseDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAddAccountDialog by remember { mutableStateOf(false) }
    var showImportDesktopDialog by remember { mutableStateOf(false) }
    var showAddProjectDialog by remember { mutableStateOf(false) }
    var showAddScheduleDialog by remember { mutableStateOf(false) }
    var newDenylistPkg by remember { mutableStateOf("") }
    var expandMcpTools by remember { mutableStateOf(false) }

    ModalDrawerSheet(
        modifier = modifier
            .fillMaxHeight()
            .width(328.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Branding header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column {
                        Text(
                            text = "Antigravity Mobile",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Root Edition • ACP + MCP",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(
                    onClick = {
                        onAction(WorkspaceAction.SetShowOnboarding(true))
                        onCloseDrawer()
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Kurulum Sihirbazı",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 1. Account Switcher Section
            DrawerSectionHeader(
                icon = Icons.Default.AccountCircle,
                title = "Hesap Seçici (Multi-Account)",
            )
            AccountSwitcherCard(
                activeProfile = state.activeProfile,
                profiles = state.profiles,
                onSwitchProfile = { onAction(WorkspaceAction.SwitchProfile(it)) },
                onRefreshUsage = { onAction(WorkspaceAction.RefreshUsage(state.activeProfile?.id)) },
                onOpenAddAccount = { showAddAccountDialog = true },
                onOpenDesktopImport = { showImportDesktopDialog = true },
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

            // 2. Projects Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                DrawerSectionHeader(
                    icon = Icons.Default.Folder,
                    title = "Projeler (Workspace)",
                )
                IconButton(
                    onClick = { showAddProjectDialog = true },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Proje ekle", modifier = Modifier.size(18.dp))
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.projects.forEach { project ->
                    val isSelected = project.id == state.activeProject.id
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        },
                        border = if (isSelected) {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        } else {
                            null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onAction(WorkspaceAction.SelectProject(project.id))
                                onCloseDrawer()
                            },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = project.name,
                                    style = MaterialTheme.typography.titleSmall,
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
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surface,
                            ) {
                                Text(
                                    text = project.defaultPermissionMode.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

            // 3. Automations (/schedule) Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                DrawerSectionHeader(
                    icon = Icons.Default.Schedule,
                    title = "Otomasyonlar (/schedule)",
                )
                IconButton(
                    onClick = { showAddScheduleDialog = true },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Görev zamanla", modifier = Modifier.size(18.dp))
                }
            }

            if (state.scheduledTasks.isEmpty()) {
                Text(
                    text = "Henüz zamanlanmış görev yok. '+' ile veya `/schedule` komutuyla ekleyin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.scheduledTasks.forEach { task ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = task.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = task.cronExpression ?: "Tek seferlik zamanlama",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    Switch(
                                        checked = task.enabled,
                                        onCheckedChange = { enabled ->
                                            onAction(WorkspaceAction.ToggleScheduledTask(task.id, enabled))
                                        },
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    IconButton(
                                        onClick = { onAction(WorkspaceAction.TriggerScheduledTask(task.id)) },
                                        modifier = Modifier.size(30.dp),
                                    ) {
                                        Icon(
                                            Icons.Default.PlayArrow,
                                            contentDescription = "Şimdi çalıştır",
                                            tint = AntigravityPalette.SuccessGreen,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    IconButton(
                                        onClick = { onAction(WorkspaceAction.CancelScheduledTask(task.id)) },
                                        modifier = Modifier.size(30.dp),
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Görevi sil",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (state.recentTaskRuns.isNotEmpty()) {
                Text(
                    text = "Son Çalışmalar (${state.recentTaskRuns.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.recentTaskRuns.take(3).forEach { run ->
                    Text(
                        text = "• ${run.taskId}: ${run.status.name} ${run.summary.take(36)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

            // 4. Customizations: MCP Health, Skills/Rules/Hooks, Protected App Denylist
            DrawerSectionHeader(
                icon = Icons.Default.Build,
                title = "Özelleştirmeler & MCP",
            )

            // Embedded Android System MCP Health Card
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                border = BorderStroke(
                    1.dp,
                    if (state.mcpHealth.isRunning) AntigravityPalette.SuccessGreen.copy(alpha = 0.6f)
                    else MaterialTheme.colorScheme.outline,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expandMcpTools = !expandMcpTools },
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (state.mcpHealth.isRunning) AntigravityPalette.SuccessGreen
                                        else AntigravityPalette.WarningAmber,
                                    ),
                            )
                            Text(
                                text = "Android System MCP",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Text(
                            text = "${state.mcpTools.size.coerceAtLeast(state.mcpHealth.registeredToolCount)} araç",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (state.mcpHealth.isRunning) {
                            "${state.mcpHealth.endpointHost}:${state.mcpHealth.endpointPort ?: "-"} • Bearer Korumalı"
                        } else {
                            "Loopback sunucu bekleniyor (127.0.0.1)"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (expandMcpTools && state.mcpTools.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        state.mcpTools.forEach { tool ->
                            val riskColor = when (tool.riskLevel) {
                                McpToolRisk.CRITICAL -> AntigravityPalette.CriticalRed
                                McpToolRisk.HIGH -> AntigravityPalette.WarningAmber
                                McpToolRisk.MEDIUM -> AntigravityPalette.NeonCyan
                                McpToolRisk.LOW -> AntigravityPalette.SuccessGreen
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = tool.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                )
                                Text(
                                    text = tool.riskLevel.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = riskColor,
                                )
                            }
                        }
                    }
                }
            }

            // Skills, Rules, Hooks toggles
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.customizations.forEach { item ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${item.category.name}: ${item.name}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = item.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Switch(
                                checked = item.enabled,
                                onCheckedChange = { onAction(WorkspaceAction.ToggleCustomization(item.id)) },
                            )
                        }
                    }
                }
            }

            // Protected App Denylist
            DrawerSectionHeader(
                icon = Icons.Default.Security,
                title = "Korumalı Uygulamalar (${state.denylistPackages.size})",
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedTextField(
                    value = newDenylistPkg,
                    onValueChange = { newDenylistPkg = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("com.bank.app", style = MaterialTheme.typography.bodySmall) },
                )
                IconButton(
                    onClick = {
                        if (newDenylistPkg.isNotBlank()) {
                            onAction(WorkspaceAction.AddProtectedPackage(newDenylistPkg))
                            newDenylistPkg = ""
                        }
                    },
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Paketi koru")
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                state.denylistPackages.take(12).forEach { pkg ->
                    AssistChip(
                        onClick = { onAction(WorkspaceAction.RemoveProtectedPackage(pkg)) },
                        label = {
                            Text(
                                text = pkg.substringAfterLast('.'),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                }
            }
        }
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

    if (showAddProjectDialog) {
        AddProjectDialog(
            onDismiss = { showAddProjectDialog = false },
            onCreate = { name, path, mode ->
                onAction(WorkspaceAction.CreateProject(name, path, mode))
                showAddProjectDialog = false
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
}

@Composable
private fun DrawerSectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountSwitcherCard(
    activeProfile: AccountProfile?,
    profiles: List<AccountProfile>,
    onSwitchProfile: (String) -> Unit,
    onRefreshUsage: () -> Unit,
    onOpenAddAccount: () -> Unit,
    onOpenDesktopImport: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = activeProfile?.displayName ?: "Hesap Bağlı Değil",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = activeProfile?.email ?: "Kişisel Google hesabı ekleyin",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (activeProfile != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = activeProfile.quota.planTier.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    IconButton(onClick = onRefreshUsage, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "/usage yenile",
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            // Quota /usage progress bar
            val remainingPercent = activeProfile?.quota?.remainingPercent
            if (remainingPercent != null) {
                val fraction = (remainingPercent / 100.0).toFloat().coerceIn(0f, 1f)
                val barColor = if (activeProfile.quota.isNearLimit) {
                    AntigravityPalette.WarningAmber
                } else {
                    AntigravityPalette.SuccessGreen
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "/usage Kalan Kota",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "%.0f%%".format(remainingPercent),
                            style = MaterialTheme.typography.labelSmall,
                            color = barColor,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = barColor,
                        trackColor = MaterialTheme.colorScheme.surface,
                    )
                }
            }

            if (profiles.size > 1) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    profiles.forEach { profile ->
                        FilterChip(
                            selected = profile.id == activeProfile?.id,
                            onClick = { onSwitchProfile(profile.id) },
                            label = {
                                Text(
                                    text = profile.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                )
                            },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onOpenAddAccount,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Hesap Ekle", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = onOpenDesktopImport,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(Icons.Default.Download, contentDescription = "Masaüstü ~/.gemini içe aktar", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
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
        title = { Text("Yeni Çalışma Alanı (Proje)") },
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
