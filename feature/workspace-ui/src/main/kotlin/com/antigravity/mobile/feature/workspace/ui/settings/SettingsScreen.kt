package com.antigravity.mobile.feature.workspace.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.antigravity.mobile.contracts.AccountProfile
import com.antigravity.mobile.contracts.AcpModel
import com.antigravity.mobile.contracts.AuthFlowState
import com.antigravity.mobile.contracts.ChrootState
import com.antigravity.mobile.contracts.McpToolRisk
import com.antigravity.mobile.contracts.PermissionMode
import com.antigravity.mobile.contracts.RootProviderType
import com.antigravity.mobile.contracts.ThinkingEffort
import com.antigravity.mobile.contracts.WorkspaceProject
import com.antigravity.mobile.feature.workspace.model.AppDestination
import com.antigravity.mobile.feature.workspace.model.AppThemeMode
import com.antigravity.mobile.feature.workspace.model.CustomizationCategory
import com.antigravity.mobile.feature.workspace.model.InterfaceLayoutMode
import com.antigravity.mobile.feature.workspace.model.SettingsCategory
import com.antigravity.mobile.feature.workspace.model.WorkspaceAction
import com.antigravity.mobile.feature.workspace.model.WorkspaceUiState
import com.antigravity.mobile.feature.workspace.theme.LocalAntigravityColors
import kotlin.math.roundToInt

/**
 * Pure Kotlin title and dynamic 1-line summary helpers for the Google Settings master-detail UI.
 */
object SettingsSummaryFormatter {
    fun categoryTitle(category: SettingsCategory): String = when (category) {
        SettingsCategory.GENERAL -> "Genel"
        SettingsCategory.ACCOUNTS_QUOTA -> "Hesaplar & Kota"
        SettingsCategory.MODELS_AGENT -> "Modeller & Ajan"
        SettingsCategory.PROJECTS_PERMISSIONS -> "Projeler & İzinler"
        SettingsCategory.CUSTOMIZATIONS -> "Özelleştirmeler & MCP"
        SettingsCategory.MOBILE_ROOT -> "Mobil & Root Sistemi"
    }

    fun categorySummary(category: SettingsCategory, state: WorkspaceUiState): String = when (category) {
        SettingsCategory.GENERAL -> {
            val themeLabel = when (state.settings.themeMode) {
                AppThemeMode.DARK -> "Karanlık"
                AppThemeMode.LIGHT -> "Aydınlık"
                AppThemeMode.SYSTEM -> "Sistem"
            }
            val layoutLabel = when (state.settings.layoutMode) {
                InterfaceLayoutMode.SOFTWARE_ENGINEERING -> "Yazılım Mühendisliği"
                InterfaceLayoutMode.COMPACT_MOBILE -> "Kompakt Mobil"
            }
            "$themeLabel • $layoutLabel • ${state.voiceLocaleTag}"
        }

        SettingsCategory.ACCOUNTS_QUOTA -> {
            val activeProfile = state.activeProfile
            if (activeProfile == null) {
                "Hesap yok • 5s — / Hf —"
            } else {
                val identity = activeProfile.email?.takeIf { it.isNotBlank() } ?: activeProfile.displayName
                val quotaSummary = QuotaFormatter.formatCompactDualSummary(
                    quota = activeProfile.quota,
                    selectedModel = state.selectedModel,
                )
                "$identity • $quotaSummary"
            }
        }

        SettingsCategory.MODELS_AGENT -> {
            "${state.selectedModel.displayName} • ${state.selectedEffort.name} • ${state.permissionMode.name}"
        }

        SettingsCategory.PROJECTS_PERMISSIONS -> {
            val base = "${state.projects.size} proje • ${state.denylistPackages.size} korumalı uygulama"
            if (state.settings.fullAccessEnabled) "$base • Tam Yetki Açık" else base
        }

        SettingsCategory.CUSTOMIZATIONS -> {
            val health = state.mcpHealth
            val mcpStatus = if (health.isRunning) {
                health.endpointPort?.let { "Aktif (:$it)" } ?: "Aktif"
            } else {
                "Kapalı"
            }
            val toolCount = if (state.mcpTools.isNotEmpty()) state.mcpTools.size else health.registeredToolCount
            "MCP $mcpStatus • $toolCount araç"
        }

        SettingsCategory.MOBILE_ROOT -> {
            val rootProvider = when (state.onboarding.rootEnvironment?.provider) {
                RootProviderType.KERNEL_SU -> "KernelSU"
                RootProviderType.MAGISK -> "Magisk"
                RootProviderType.APATCH -> "APatch"
                RootProviderType.GENERIC_SU -> "Generic SU"
                RootProviderType.NONE -> "Root Yok"
                null -> "Root Denetlenmedi"
            }
            val agyVersion = state.onboarding.agyVersion
                ?: (state.onboarding.chrootState as? ChrootState.Mounted)?.agyVersion
                ?: (state.onboarding.chrootState as? ChrootState.Unmounted)?.agyVersion
            "$rootProvider • agy ${agyVersion ?: "—"} • Overlay & Kill-Switch"
        }
    }
}

/**
 * Shared confirmation dialog before enabling Tam Yetki (dangerously-skip-permissions) mode.
 */
@Composable
internal fun FullAccessConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val extendedColors = LocalAntigravityColors.current
    val colorScheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colorScheme.surface,
        icon = {
            Icon(
                imageVector = Icons.Default.AdminPanelSettings,
                contentDescription = null,
                tint = extendedColors.critical,
            )
        },
        title = {
            Text(
                text = "Tam Yetki Modu Etkinleştirilsin mi?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface,
            )
        },
        text = {
            Text(
                text = "Ajan; kabuk (shell), root, dosya ve sistem komutlarını size onay sormadan doğrudan çalıştıracaktır (dangerously-skip-permissions).\n\nSes+ / Ses- donanım Kill-Switch ve Acil Durdur butonu aktif kalmaya devam eder.",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = extendedColors.critical,
                    contentColor = Color.White,
                ),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("Etkinleştir", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Vazgeç")
            }
        },
    )
}

/**
 * Backward-compatible entry point delegating to [SettingsListScreen] or [SettingsDetailScreen]
 * based on [WorkspaceUiState.currentDestination].
 */
@Composable
fun SettingsScreen(
    state: WorkspaceUiState,
    onAction: (WorkspaceAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (val destination = state.currentDestination) {
        is AppDestination.SettingsDetail -> {
            SettingsDetailScreen(
                category = destination.category,
                state = state,
                onAction = onAction,
                modifier = modifier,
            )
        }

        else -> {
            SettingsListScreen(
                state = state,
                onAction = onAction,
                modifier = modifier,
            )
        }
    }
}

/**
 * Google Settings-style master overview screen listing all 6 settings categories with tinted
 * circular icon badges, live 1-line dynamic summaries, and full hardware Back navigation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsListScreen(
    state: WorkspaceUiState,
    onAction: (WorkspaceAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = true) {
        onAction(WorkspaceAction.NavigateBack)
    }

    val extendedColors = LocalAntigravityColors.current
    val colorScheme = MaterialTheme.colorScheme

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = "Ayarlar",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { onAction(WorkspaceAction.NavigateBack) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Geri",
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = colorScheme.surface,
                        titleContentColor = colorScheme.onSurface,
                        navigationIconContentColor = colorScheme.onSurface,
                    ),
                )
                HorizontalDivider(color = extendedColors.cardBorder)
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            SettingsSetupWizardRow(
                readyCount = state.setupReadyCount,
                totalSteps = state.setupTotalSteps,
                isFullyReady = state.isSetupFullyReady,
                onClick = { onAction(WorkspaceAction.OpenOnboardingFromSettings(fromSettings = true)) },
            )

            SettingsCategoryGroup(
                groupLabel = "Çalışma Alanı",
                categories = listOf(
                    SettingsCategory.GENERAL,
                    SettingsCategory.ACCOUNTS_QUOTA,
                    SettingsCategory.MODELS_AGENT,
                ),
                state = state,
                onSelectCategory = { onAction(WorkspaceAction.OpenSettingsDetail(it)) },
            )

            SettingsCategoryGroup(
                groupLabel = "Sistem & Güvenlik",
                categories = listOf(
                    SettingsCategory.PROJECTS_PERMISSIONS,
                    SettingsCategory.CUSTOMIZATIONS,
                    SettingsCategory.MOBILE_ROOT,
                ),
                state = state,
                onSelectCategory = { onAction(WorkspaceAction.OpenSettingsDetail(it)) },
            )

            Text(
                text = "Antigravity Mobile • Android Root Edition",
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsSetupWizardRow(
    readyCount: Int,
    totalSteps: Int,
    isFullyReady: Boolean,
    onClick: () -> Unit,
) {
    val extendedColors = LocalAntigravityColors.current
    val colorScheme = MaterialTheme.colorScheme
    val accent = if (isFullyReady) extendedColors.success else colorScheme.primary

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = settingsCardContainerColor(),
        border = BorderStroke(
            width = 1.dp,
            color = if (isFullyReady) {
                extendedColors.success.copy(alpha = if (extendedColors.isDark) 0.45f else 0.35f)
            } else {
                colorScheme.primary.copy(alpha = if (extendedColors.isDark) 0.45f else 0.30f)
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = if (extendedColors.isDark) 0.18f else 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(21.dp),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Kurulum & Sistem Tanılama Sihirbazı",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (isFullyReady) {
                        "Tüm sistem bileşenleri hazır • Yeniden denetle"
                    } else {
                        "Root, izinler, ARM64 chroot, agy CLI ve Google girişi"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = accent.copy(alpha = if (extendedColors.isDark) 0.16f else 0.11f),
                border = BorderStroke(0.75.dp, accent.copy(alpha = 0.45f)),
            ) {
                Text(
                    text = "$readyCount/$totalSteps hazır",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun SettingsCategoryGroup(
    groupLabel: String,
    categories: List<SettingsCategory>,
    state: WorkspaceUiState,
    onSelectCategory: (SettingsCategory) -> Unit,
) {
    val extendedColors = LocalAntigravityColors.current
    val colorScheme = MaterialTheme.colorScheme

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = groupLabel,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.primary,
            modifier = Modifier.padding(horizontal = 6.dp),
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = settingsCardContainerColor(),
            border = BorderStroke(1.dp, extendedColors.cardBorder),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                categories.forEachIndexed { index, category ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 68.dp, end = 16.dp),
                            color = extendedColors.cardBorder.copy(alpha = 0.8f),
                        )
                    }
                    SettingsCategoryListRow(
                        category = category,
                        summary = SettingsSummaryFormatter.categorySummary(category, state),
                        onClick = { onSelectCategory(category) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsCategoryListRow(
    category: SettingsCategory,
    summary: String,
    onClick: () -> Unit,
) {
    val extendedColors = LocalAntigravityColors.current
    val colorScheme = MaterialTheme.colorScheme
    val badgeColor = categoryBadgeColor(category)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(badgeColor.copy(alpha = if (extendedColors.isDark) 0.18f else 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = category.icon(),
                contentDescription = null,
                tint = badgeColor,
                modifier = Modifier.size(20.dp),
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = SettingsSummaryFormatter.categoryTitle(category),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * Focused Google Settings detail screen for a single [SettingsCategory] with hardware Back
 * returning smoothly to [SettingsListScreen].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDetailScreen(
    category: SettingsCategory,
    state: WorkspaceUiState,
    onAction: (WorkspaceAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = true) {
        onAction(WorkspaceAction.NavigateBack)
    }

    val extendedColors = LocalAntigravityColors.current
    val colorScheme = MaterialTheme.colorScheme

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = SettingsSummaryFormatter.categoryTitle(category),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { onAction(WorkspaceAction.NavigateBack) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Geri",
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = colorScheme.surface,
                        titleContentColor = colorScheme.onSurface,
                        navigationIconContentColor = colorScheme.onSurface,
                    ),
                )
                HorizontalDivider(color = extendedColors.cardBorder)
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (category) {
                SettingsCategory.GENERAL -> GeneralSettingsContent(state = state, onAction = onAction)
                SettingsCategory.ACCOUNTS_QUOTA -> AccountsQuotaSettingsContent(state = state, onAction = onAction)
                SettingsCategory.MODELS_AGENT -> ModelsAgentSettingsContent(state = state, onAction = onAction)
                SettingsCategory.PROJECTS_PERMISSIONS -> ProjectsPermissionsSettingsContent(state = state, onAction = onAction)
                SettingsCategory.CUSTOMIZATIONS -> CustomizationsSettingsContent(state = state, onAction = onAction)
                SettingsCategory.MOBILE_ROOT -> MobileRootSettingsContent(state = state, onAction = onAction)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private fun SettingsCategory.icon(): ImageVector = when (this) {
    SettingsCategory.GENERAL -> Icons.Default.Tune
    SettingsCategory.ACCOUNTS_QUOTA -> Icons.Default.AccountCircle
    SettingsCategory.MODELS_AGENT -> Icons.Default.AutoAwesome
    SettingsCategory.PROJECTS_PERMISSIONS -> Icons.Default.Folder
    SettingsCategory.CUSTOMIZATIONS -> Icons.Default.Extension
    SettingsCategory.MOBILE_ROOT -> Icons.Default.AdminPanelSettings
}

@Composable
private fun categoryBadgeColor(category: SettingsCategory): Color {
    val extendedColors = LocalAntigravityColors.current
    val colorScheme = MaterialTheme.colorScheme
    return when (category) {
        SettingsCategory.GENERAL -> colorScheme.primary
        SettingsCategory.ACCOUNTS_QUOTA -> extendedColors.success
        SettingsCategory.MODELS_AGENT -> colorScheme.secondary
        SettingsCategory.PROJECTS_PERMISSIONS -> extendedColors.warning
        SettingsCategory.CUSTOMIZATIONS -> extendedColors.accentCyan
        SettingsCategory.MOBILE_ROOT -> extendedColors.critical
    }
}

@Composable
private fun settingsCardContainerColor(): Color {
    val extendedColors = LocalAntigravityColors.current
    return if (extendedColors.isDark) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
}

@Composable
private fun settingsInnerTileColor(): Color {
    val extendedColors = LocalAntigravityColors.current
    return if (extendedColors.isDark) {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)
    }
}

// ============================================================================
// 1. GENERAL SETTINGS
// ============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GeneralSettingsContent(
    state: WorkspaceUiState,
    onAction: (WorkspaceAction) -> Unit,
) {
    val settings = state.settings
    val dividerColor = LocalAntigravityColors.current.cardBorder.copy(alpha = 0.75f)

    SettingsSectionCard(
        title = "Görünüm & Tema",
        subtitle = "Karanlık, Aydınlık veya Sistem temasını seçin; her iki modda da tam uyumlu Google Antigravity görünümü.",
    ) {
        Text(
            text = "Tema Modu",
            style = MaterialTheme.typography.titleSmall,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AppThemeMode.entries.forEach { mode ->
                val selected = settings.themeMode == mode
                FilterChip(
                    selected = selected,
                    onClick = {
                        onAction(WorkspaceAction.UpdateSettings { it.copy(themeMode = mode) })
                    },
                    label = {
                        Text(
                            when (mode) {
                                AppThemeMode.DARK -> "Karanlık"
                                AppThemeMode.LIGHT -> "Aydınlık"
                                AppThemeMode.SYSTEM -> "Sistem"
                            },
                        )
                    },
                    leadingIcon = if (selected) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp)) }
                    } else {
                        null
                    },
                )
            }
        }

        HorizontalDivider(color = dividerColor)

        SettingsSwitchRow(
            title = "Dinamik Renk (Material You)",
            subtitle = "Android 12+ duvar kağıdı paletini uygula. Kapalıyken Antigravity resmi renk paleti kullanılır.",
            checked = settings.useDynamicColor,
            onCheckedChange = { enabled ->
                onAction(WorkspaceAction.UpdateSettings { it.copy(useDynamicColor = enabled) })
            },
        )

        HorizontalDivider(color = dividerColor)

        Text(
            text = "Arayüz Düzeni",
            style = MaterialTheme.typography.titleSmall,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            InterfaceLayoutMode.entries.forEach { layoutMode ->
                val selected = settings.layoutMode == layoutMode
                FilterChip(
                    selected = selected,
                    onClick = {
                        onAction(WorkspaceAction.UpdateSettings { it.copy(layoutMode = layoutMode) })
                    },
                    label = {
                        Text(
                            when (layoutMode) {
                                InterfaceLayoutMode.SOFTWARE_ENGINEERING -> "Yazılım Mühendisliği"
                                InterfaceLayoutMode.COMPACT_MOBILE -> "Kompakt Mobil"
                            },
                        )
                    },
                    leadingIcon = if (selected) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp)) }
                    } else {
                        null
                    },
                )
            }
        }
    }

    SettingsSectionCard(
        title = "Sesli Dikte Dili",
        subtitle = "Mikrofonla sohbet girişi ve sesli komutlar için aktif konuşma tanıma dili.",
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(
                "tr-TR" to "Türkçe (tr-TR)",
                "en-US" to "English (en-US)",
            ).forEach { (tag, label) ->
                val selected = state.voiceLocaleTag == tag
                FilterChip(
                    selected = selected,
                    onClick = { onAction(WorkspaceAction.ChangeVoiceLocale(tag)) },
                    label = { Text(label) },
                    leadingIcon = if (selected) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp)) }
                    } else {
                        null
                    },
                )
            }
        }
    }

    SettingsSectionCard(
        title = "Sohbet & Kod Deneyimi",
        subtitle = "Akış davranışı, kod blokları ve görev bildirim tercihleri.",
    ) {
        SettingsSwitchRow(
            title = "Sohbeti Otomatik Kaydır",
            subtitle = "Ajan yanıtı akarken (streaming) ekranı otomatik en alta kaydır.",
            checked = settings.autoScrollChat,
            onCheckedChange = { enabled ->
                onAction(WorkspaceAction.UpdateSettings { it.copy(autoScrollChat = enabled) })
            },
        )
        HorizontalDivider(color = dividerColor)
        SettingsSwitchRow(
            title = "Kod Satır Numaralarını Göster",
            subtitle = "Markdown kod bloklarında sol kenar satır numaralarını etkinleştir.",
            checked = settings.showCodeLineNumbers,
            onCheckedChange = { enabled ->
                onAction(WorkspaceAction.UpdateSettings { it.copy(showCodeLineNumbers = enabled) })
            },
        )
        HorizontalDivider(color = dividerColor)
        SettingsSwitchRow(
            title = "Düşünce Bloklarını Varsayılan Açık Tut",
            subtitle = "Modelin iç muhakeme (Thought) akordeonlarını otomatik genişletilmiş göster.",
            checked = settings.showThoughtBlocksExpanded,
            onCheckedChange = { enabled ->
                onAction(WorkspaceAction.UpdateSettings { it.copy(showThoughtBlocksExpanded = enabled) })
            },
        )
        HorizontalDivider(color = dividerColor)
        SettingsSwitchRow(
            title = "Görev Tamamlanma Bildirimleri",
            subtitle = "Uzun süren ajan/swarm görevleri tamamlandığında sistem bildirimi gönder.",
            checked = settings.notifyOnTaskComplete,
            onCheckedChange = { enabled ->
                onAction(WorkspaceAction.UpdateSettings { it.copy(notifyOnTaskComplete = enabled) })
            },
        )
    }
}

// ============================================================================
// 2. ACCOUNTS & QUOTA SETTINGS
// ============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountsQuotaSettingsContent(
    state: WorkspaceUiState,
    onAction: (WorkspaceAction) -> Unit,
) {
    val extendedColors = LocalAntigravityColors.current
    val colorScheme = MaterialTheme.colorScheme
    var showImportDesktopDialog by remember { mutableStateOf(false) }
    var profilePendingDeletion by remember { mutableStateOf<AccountProfile?>(null) }
    var manualAuthCode by remember { mutableStateOf("") }

    SettingsSectionCard(
        title = "Çoklu Google Hesabı & Kota Yönetimi",
        subtitle = "Her hesap izole chroot HOME diziniyle çalışır; kota düştüğünde tek dokunuşla geçiş yapın.",
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { onAction(WorkspaceAction.RefreshAllQuotas) },
                enabled = !state.isRefreshingQuota,
            ) {
                if (state.isRefreshingQuota) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = colorScheme.onPrimary,
                    )
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text("Tüm Kotaları Yenile")
            }

            FilledTonalButton(onClick = { onAction(WorkspaceAction.AddGoogleAccount()) }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Google Hesabı Ekle")
            }

            OutlinedButton(onClick = { showImportDesktopDialog = true }) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Masaüstü ~/.gemini İçe Aktar")
            }
        }

        HorizontalDivider(color = extendedColors.cardBorder.copy(alpha = 0.75f))

        SettingsSwitchRow(
            title = "Açılışta Kotaları Otomatik Yenile",
            subtitle = "Uygulama açıldığında aktif ve kayıtlı Google hesaplarının 4 kademeli kotasını çek.",
            checked = state.settings.autoRefreshQuotaOnLaunch,
            onCheckedChange = { enabled ->
                onAction(WorkspaceAction.UpdateSettings { it.copy(autoRefreshQuotaOnLaunch = enabled) })
            },
        )
    }

    // Active OAuth manual code submission box if awaiting browser auth
    val awaitingChallenge = (state.authFlowState as? AuthFlowState.AwaitingBrowserAuth)?.challenge
    if (awaitingChallenge != null) {
        SettingsSectionCard(
            title = "Google OAuth Doğrulama Kodu",
            subtitle = awaitingChallenge.instructions.ifBlank {
                "Tarayıcıda giriş yaptıktan sonra aldığınız kodu veya yönlendirilen URL'yi aşağıya yapıştırın."
            },
        ) {
            OutlinedTextField(
                value = manualAuthCode,
                onValueChange = { manualAuthCode = it },
                label = { Text("Doğrulama Kodu / Callback URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    onAction(WorkspaceAction.SubmitAuthCode(awaitingChallenge.profileId, manualAuthCode))
                    manualAuthCode = ""
                },
                enabled = manualAuthCode.isNotBlank(),
            ) {
                Text("Kodu Doğrula")
            }
        }
    }

    if (state.profiles.isEmpty()) {
        SettingsSectionCard(
            title = "Kayıtlı Hesap Yok",
            subtitle = "Henüz bir Google hesabı profili eklenmedi. Başlamak için 'Yeni Google Hesabı' butonuna dokunun.",
        ) {}
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.profiles.forEach { profile ->
                val isActive = profile.id == state.activeProfile?.id
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    QuotaGaugeCard(
                        profile = profile,
                        isActive = isActive,
                        isRefreshing = state.isRefreshingQuota,
                        onRefresh = { onAction(WorkspaceAction.RefreshUsage(profile.id)) },
                        onSwitch = if (!isActive) {
                            { onAction(WorkspaceAction.SwitchProfile(profile.id)) }
                        } else {
                            null
                        },
                        selectedModel = state.selectedModel,
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!profile.isAuthenticated) {
                            TextButton(
                                onClick = { onAction(WorkspaceAction.StartOAuthLogin(profile.id)) },
                            ) {
                                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Google ile Giriş Yap")
                            }
                        } else {
                            Text(
                                text = profile.paths.chrootHome,
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        TextButton(
                            onClick = { profilePendingDeletion = profile },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = extendedColors.critical,
                            ),
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Profil Sil")
                        }
                    }
                }
            }
        }
    }

    if (showImportDesktopDialog) {
        ImportDesktopProfileDialog(
            profiles = state.profiles,
            activeProfileId = state.activeProfile?.id ?: state.profiles.firstOrNull()?.id ?: "default",
            onDismiss = { showImportDesktopDialog = false },
            onConfirm = { profileId, path ->
                onAction(WorkspaceAction.ImportDesktopProfile(profileId, path))
                showImportDesktopDialog = false
            },
        )
    }

    profilePendingDeletion?.let { target ->
        AlertDialog(
            onDismissRequest = { profilePendingDeletion = null },
            title = { Text("Profili Sil?") },
            text = {
                Text(
                    "\"${target.displayName}\" profili ve chroot içindeki izole oturum verileri kalıcı olarak silinecektir. Devam etmek istiyor musunuz?",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAction(WorkspaceAction.DeleteProfile(target.id))
                        profilePendingDeletion = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.error,
                        contentColor = colorScheme.onError,
                    ),
                ) {
                    Text("Profil Sil")
                }
            },
            dismissButton = {
                TextButton(onClick = { profilePendingDeletion = null }) {
                    Text("İptal")
                }
            },
        )
    }
}

@Composable
private fun ImportDesktopProfileDialog(
    profiles: List<AccountProfile>,
    activeProfileId: String,
    onDismiss: () -> Unit,
    onConfirm: (profileId: String, sourcePath: String) -> Unit,
) {
    var selectedProfileId by remember { mutableStateOf(activeProfileId) }
    var sourcePath by remember { mutableStateOf("/sdcard/Download/.gemini") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Masaüstü ~/.gemini İçe Aktar") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Bilgisayarınızdan kopyaladığınız `.gemini` klasör yolunu girerek OAuth oturumunu seçili profile aktarabilirsiniz.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (profiles.size > 1) {
                    Text(
                        text = "Hedef Profil",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(profiles, key = { it.id }) { profile ->
                            FilterChip(
                                selected = selectedProfileId == profile.id,
                                onClick = { selectedProfileId = profile.id },
                                label = { Text(profile.displayName) },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = sourcePath,
                    onValueChange = { sourcePath = it },
                    label = { Text("Kaynak Dizin Yolu") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedProfileId, sourcePath.trim()) },
                enabled = sourcePath.isNotBlank(),
            ) {
                Text("İçe Aktar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal")
            }
        },
    )
}

// ============================================================================
// 3. MODELS & AGENT SETTINGS
// ============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelsAgentSettingsContent(
    state: WorkspaceUiState,
    onAction: (WorkspaceAction) -> Unit,
) {
    val extendedColors = LocalAntigravityColors.current
    val colorScheme = MaterialTheme.colorScheme
    val tileBackground = settingsInnerTileColor()

    SettingsSectionCard(
        title = "Varsayılan Model",
        subtitle = "Yeni ACP oturumlarında kullanılacak Antigravity ajan modelini seçin.",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AcpModel.entries.forEach { model ->
                val selected = state.selectedModel == model
                val description = when (model) {
                    AcpModel.GEMINI_4_NEON -> "Hızlı, dengeli günlük kodlama ve sistem otomasyonu"
                    AcpModel.GEMINI_4_HYDROGEN -> "Derin muhakeme, karmaşık mimari ve hata ayıklama"
                    AcpModel.GEMINI_4_CARBON -> "Ultra hafif, düşük gecikmeli hızlı araç çağrıları"
                    AcpModel.ORCHESTRATOR -> "Çoklu alt ajan & /swarm koordinasyonu için optimize"
                }
                val accent = when (model) {
                    AcpModel.GEMINI_4_NEON -> colorScheme.primary
                    AcpModel.GEMINI_4_HYDROGEN -> colorScheme.secondary
                    AcpModel.GEMINI_4_CARBON -> extendedColors.accentCyan
                    AcpModel.ORCHESTRATOR -> extendedColors.success
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) {
                        accent.copy(alpha = if (extendedColors.isDark) 0.16f else 0.10f)
                    } else {
                        tileBackground
                    },
                    border = BorderStroke(
                        width = if (selected) 1.25.dp else 1.dp,
                        color = if (selected) accent else extendedColors.cardBorder,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAction(WorkspaceAction.SelectModel(model)) },
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(accent),
                                )
                                Text(
                                    text = model.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colorScheme.onSurface,
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = colorScheme.surface,
                                    border = BorderStroke(0.5.dp, extendedColors.cardBorder),
                                ) {
                                    Text(
                                        text = model.cliId,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                            )
                        }
                        if (selected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Seçili",
                                tint = accent,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    SettingsSectionCard(
        title = "Varsayılan Düşünme Çabası (Thinking Effort)",
        subtitle = "Modelin yanıt üretmeden önce harcayacağı muhakeme derinliğini belirleyin.",
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ThinkingEffort.entries.forEach { effort ->
                val selected = state.selectedEffort == effort
                FilterChip(
                    selected = selected,
                    onClick = { onAction(WorkspaceAction.SelectEffort(effort)) },
                    label = {
                        Text(
                            when (effort) {
                                ThinkingEffort.LOW -> "Düşük (LOW)"
                                ThinkingEffort.MEDIUM -> "Orta (MEDIUM)"
                                ThinkingEffort.HIGH -> "Yüksek (HIGH)"
                                ThinkingEffort.EXTRA -> "Ekstra (EXTRA)"
                            },
                        )
                    },
                    leadingIcon = if (selected) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp)) }
                    } else {
                        null
                    },
                )
            }
        }
    }

    SettingsSectionCard(
        title = "Varsayılan İzin Modu",
        subtitle = "Araç çağrıları ve sistem değişiklikleri için güvenlik politikası.",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PermissionMode.entries.forEach { mode ->
                val selected = state.permissionMode == mode
                val (label, desc, color) = when (mode) {
                    PermissionMode.AUTO -> Triple(
                        "Otonom (AUTO)",
                        "Düşük/orta riskli işlemleri otomatik uygular; CRITICAL güvenlik kapısı her zaman aktiftir.",
                        extendedColors.warning,
                    )

                    PermissionMode.ASK -> Triple(
                        "Onay Sor (ASK)",
                        "Dosya yazma, sistem araçları ve root komutlarından önce kullanıcı onayı ister.",
                        colorScheme.primary,
                    )

                    PermissionMode.PLAN -> Triple(
                        "Plan Modu (PLAN)",
                        "Salt okunur analiz ve adım adım plan oluşturma; sistemde değişiklik yapmaz.",
                        colorScheme.secondary,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) {
                        color.copy(alpha = if (extendedColors.isDark) 0.16f else 0.10f)
                    } else {
                        tileBackground
                    },
                    border = BorderStroke(
                        width = if (selected) 1.25.dp else 1.dp,
                        color = if (selected) color else extendedColors.cardBorder,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAction(WorkspaceAction.SelectPermissionMode(mode)) },
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onSurface,
                            )
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                            )
                        }
                        if (selected) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = color)
                        }
                    }
                }
            }
        }
    }

    SettingsSectionCard(
        title = "ACP Çalıştırma Bayrakları",
        subtitle = "Chroot içindeki `agy acp` süreç izolasyonu ve güvenlik seçenekleri.",
    ) {
        SettingsSwitchRow(
            title = "ACP --process-per-session",
            subtitle = "Her sohbet oturumu için izole bir `agy acp` alt süreci başlat.",
            checked = state.settings.acpProcessPerSession,
            onCheckedChange = { enabled ->
                onAction(WorkspaceAction.UpdateSettings { it.copy(acpProcessPerSession = enabled) })
            },
        )
        HorizontalDivider(color = extendedColors.cardBorder.copy(alpha = 0.75f))
        SettingsSwitchRow(
            title = "ACP --sandbox",
            subtitle = "Ajan komutlarını ek dosya sistemi kısıtlamalarıyla korumalı alanda çalıştır.",
            checked = state.settings.acpSandboxEnabled,
            onCheckedChange = { enabled ->
                onAction(WorkspaceAction.UpdateSettings { it.copy(acpSandboxEnabled = enabled) })
            },
        )
    }
}

// ============================================================================
// 4. PROJECTS & PERMISSIONS SETTINGS
// ============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProjectsPermissionsSettingsContent(
    state: WorkspaceUiState,
    onAction: (WorkspaceAction) -> Unit,
) {
    val extendedColors = LocalAntigravityColors.current
    val colorScheme = MaterialTheme.colorScheme
    val tileBackground = settingsInnerTileColor()

    var showAddProjectDialog by remember { mutableStateOf(false) }
    var projectToRename by remember { mutableStateOf<WorkspaceProject?>(null) }
    var projectToDelete by remember { mutableStateOf<WorkspaceProject?>(null) }
    var showFullAccessDialog by remember { mutableStateOf(false) }
    var newPackageName by remember { mutableStateOf("") }

    SettingsSectionCard(
        title = "Tam Yetki Modu (Dangerously Skip Permissions)",
        subtitle = "Açıldığında ajan kabuk, root, dosya ve sistem aracı çağrılarını onay penceresi göstermeden otomatik onaylar.",
    ) {
        val fullAccess = state.settings.fullAccessEnabled
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (fullAccess) {
                extendedColors.critical.copy(alpha = if (extendedColors.isDark) 0.16f else 0.10f)
            } else {
                tileBackground
            },
            border = BorderStroke(
                width = if (fullAccess) 1.25.dp else 1.dp,
                color = if (fullAccess) extendedColors.critical.copy(alpha = 0.65f) else extendedColors.cardBorder,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.AdminPanelSettings,
                    contentDescription = null,
                    tint = if (fullAccess) extendedColors.critical else colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = if (fullAccess) "Tam Yetki Etkin (Onay Sorma)" else "Tam Yetki Kapalı",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (fullAccess) extendedColors.critical else colorScheme.onSurface,
                    )
                    Text(
                        text = "Ses+ / Ses- donanım kill-switch ve sohbet Acil Durdur butonu her zaman aktif kalır.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = fullAccess,
                    onCheckedChange = { wantEnable ->
                        if (wantEnable) {
                            showFullAccessDialog = true
                        } else {
                            onAction(WorkspaceAction.UpdateSettings { it.copy(fullAccessEnabled = false) })
                        }
                    },
                )
            }
        }
    }

    if (showFullAccessDialog) {
        FullAccessConfirmationDialog(
            onConfirm = {
                showFullAccessDialog = false
                onAction(WorkspaceAction.UpdateSettings { it.copy(fullAccessEnabled = true) })
            },
            onDismiss = { showFullAccessDialog = false },
        )
    }

    SettingsSectionCard(
        title = "Projeler (Workspace)",
        subtitle = "Çalışma dizinlerini oluşturun, yeniden adlandırın veya silin.",
        headerTrailing = {
            FilledTonalButton(
                onClick = { showAddProjectDialog = true },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Proje Ekle")
            }
        },
    ) {
        if (state.projects.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = tileBackground,
                border = BorderStroke(1.dp, extendedColors.cardBorder),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOff,
                        contentDescription = null,
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp),
                    )
                    Text(
                        text = "Henüz proje yok",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface,
                    )
                    Text(
                        text = "İsterseniz Cihaz Geneli olarak devam edebilir veya yeni bir çalışma dizini ekleyebilirsiniz.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(onClick = { showAddProjectDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("İlk Projeyi Ekle")
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.projects.forEach { project ->
                    val isSelected = project.id == state.selectedProjectId
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) {
                            colorScheme.primaryContainer.copy(alpha = if (extendedColors.isDark) 0.45f else 0.55f)
                        } else {
                            tileBackground
                        },
                        border = BorderStroke(
                            width = if (isSelected) 1.25.dp else 1.dp,
                            color = if (isSelected) {
                                colorScheme.primary.copy(alpha = 0.7f)
                            } else {
                                extendedColors.cardBorder
                            },
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAction(WorkspaceAction.SelectProject(project.id)) },
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            text = project.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (isSelected) {
                                            Surface(
                                                shape = RoundedCornerShape(999.dp),
                                                color = colorScheme.primary.copy(alpha = 0.16f),
                                            ) {
                                                Text(
                                                    text = "Aktif",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = project.rootPath,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = colorScheme.surface,
                                    border = BorderStroke(0.5.dp, extendedColors.cardBorder),
                                ) {
                                    Text(
                                        text = project.defaultPermissionMode.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(onClick = { projectToRename = project }) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Yeniden Adlandır")
                                }
                                TextButton(
                                    onClick = { projectToDelete = project },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = extendedColors.critical,
                                    ),
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Proje Sil")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    SettingsSectionCard(
        title = "Korunan Uygulama Listesi (Denylist)",
        subtitle = "Bankacılık, e-Devlet ve 2FA uygulamaları bu listede olduğunda ajan etkileşimi politika kapısından geçmeden engellenir.",
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = newPackageName,
                onValueChange = { newPackageName = it },
                label = { Text("Paket Adı") },
                placeholder = { Text("tr.gov.turkiye.edevlet.kapisi") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    val pkg = newPackageName.trim()
                    if (pkg.isNotEmpty()) {
                        onAction(WorkspaceAction.AddProtectedPackage(pkg))
                        newPackageName = ""
                    }
                },
                enabled = newPackageName.isNotBlank(),
            ) {
                Text("Ekle")
            }
        }

        val quickSuggestions = listOf(
            "tr.gov.turkiye.edevlet.kapisi" to "e-Devlet",
            "com.google.android.apps.authenticator2" to "Authenticator",
            "com.garanti.cepsubesi" to "Garanti BBVA",
            "com.akbank.android.apps.akbank_direkt" to "Akbank",
        ).filterNot { (pkg, _) -> pkg in state.denylistPackages }

        if (quickSuggestions.isNotEmpty()) {
            Text(
                text = "Hızlı Ekle",
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                quickSuggestions.forEach { (pkg, label) ->
                    AssistChip(
                        onClick = { onAction(WorkspaceAction.AddProtectedPackage(pkg)) },
                        label = { Text("+ $label", style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
        }

        if (state.denylistPackages.isEmpty()) {
            Text(
                text = "Henüz korunan paket eklenmedi.",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.denylistPackages.sorted().forEach { pkg ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = tileBackground,
                        border = BorderStroke(0.75.dp, extendedColors.cardBorder),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = extendedColors.warning,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = pkg,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = FontFamily.Monospace,
                                    color = colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(
                                onClick = { onAction(WorkspaceAction.RemoveProtectedPackage(pkg)) },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Kaldır",
                                    modifier = Modifier.size(16.dp),
                                    tint = extendedColors.critical,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddProjectDialog) {
        AddProjectDialog(
            onDismiss = { showAddProjectDialog = false },
            onConfirm = { name, rootPath, mode ->
                onAction(WorkspaceAction.CreateProject(name, rootPath, mode))
                showAddProjectDialog = false
            },
        )
    }

    projectToRename?.let { target ->
        RenameProjectDialog(
            project = target,
            onDismiss = { projectToRename = null },
            onConfirm = { newName ->
                onAction(WorkspaceAction.RenameProject(target.id, newName))
                projectToRename = null
            },
        )
    }

    projectToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            title = { Text("Projeyi Sil?") },
            text = {
                Text(
                    "\"${target.name}\" projesi ve bu projeye bağlı sohbet kayıtları listeden silinecektir. Cihazdaki dosyalarınıza dokunulmaz. Devam etmek istiyor musunuz?",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAction(WorkspaceAction.DeleteProject(target.id))
                        projectToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.error,
                        contentColor = colorScheme.onError,
                    ),
                ) {
                    Text("Proje Sil")
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToDelete = null }) {
                    Text("İptal")
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddProjectDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, rootPath: String, mode: PermissionMode) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var rootPath by remember { mutableStateOf("/mnt/sdcard/Projects/") }
    var permissionMode by remember { mutableStateOf(PermissionMode.ASK) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yeni Proje Ekle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Proje Adı") },
                    placeholder = { Text("Örn: Backend API") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = rootPath,
                    onValueChange = { rootPath = it },
                    label = { Text("Chroot Kök Dizini") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Varsayılan İzin Modu", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PermissionMode.entries.forEach { mode ->
                        FilterChip(
                            selected = permissionMode == mode,
                            onClick = { permissionMode = mode },
                            label = { Text(mode.name) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name.trim(), rootPath.trim(), permissionMode) },
                enabled = name.isNotBlank() && rootPath.isNotBlank(),
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

@Composable
private fun RenameProjectDialog(
    project: WorkspaceProject,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var newName by remember(project.id) { mutableStateOf(project.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Projeyi Yeniden Adlandır") },
        text = {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("Yeni Proje Adı") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(newName.trim()) },
                enabled = newName.isNotBlank(),
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

// ============================================================================
// 5. CUSTOMIZATIONS & MCP SETTINGS (MCP + Skills / Rules / Hooks)
// ============================================================================

@Composable
private fun CustomizationsSettingsContent(
    state: WorkspaceUiState,
    onAction: (WorkspaceAction) -> Unit,
) {
    val extendedColors = LocalAntigravityColors.current
    val colorScheme = MaterialTheme.colorScheme
    val tileBackground = settingsInnerTileColor()

    var expandTools by remember { mutableStateOf(false) }
    val health = state.mcpHealth
    val toolCount = if (state.mcpTools.isNotEmpty()) state.mcpTools.size else health.registeredToolCount
    val endpointLabel = "${health.endpointHost}:${health.endpointPort ?: "—"}"

    SettingsSectionCard(
        title = "Gömülü Android System MCP Sunucusu",
        subtitle = "Yalnızca 127.0.0.1 loopback arayüzünde yüksek entropili Bearer Token ile çalışır.",
        headerTrailing = {
            IconButton(onClick = { onAction(WorkspaceAction.RefreshMcpTools) }) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "MCP araçlarını yenile",
                )
            }
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val statusColor = if (health.isRunning) {
                    extendedColors.success
                } else {
                    extendedColors.critical
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = statusColor.copy(alpha = if (extendedColors.isDark) 0.16f else 0.10f),
                    border = BorderStroke(0.75.dp, statusColor.copy(alpha = 0.45f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(statusColor),
                        )
                        Text(
                            text = if (health.isRunning) "Çalışıyor" else "Durdu",
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Text(
                    text = endpointLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = colorScheme.onSurface,
                )
            }

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = colorScheme.primaryContainer,
            ) {
                Text(
                    text = "$toolCount araç",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "İşlenen toplam çağrı: ${health.totalCallsHandled}",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { expandTools = !expandTools }) {
                Text(if (expandTools) "Araçları Gizle" else "$toolCount MCP Aracını Göster")
                Icon(
                    imageVector = if (expandTools) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        if (!health.lastError.isNullOrBlank()) {
            Text(
                text = "Son hata: ${health.lastError}",
                style = MaterialTheme.typography.bodySmall,
                color = extendedColors.critical,
            )
        }

        if (expandTools) {
            HorizontalDivider(color = extendedColors.cardBorder.copy(alpha = 0.75f))
            if (state.mcpTools.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.mcpTools.forEach { tool ->
                        val riskColor = mcpRiskColor(tool.riskLevel)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = tileBackground,
                            border = BorderStroke(0.75.dp, extendedColors.cardBorder),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = tool.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colorScheme.onSurface,
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = riskColor.copy(alpha = if (extendedColors.isDark) 0.16f else 0.10f),
                                        border = BorderStroke(0.75.dp, riskColor.copy(alpha = 0.45f)),
                                    ) {
                                        Text(
                                            text = tool.riskLevel.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = riskColor,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                                if (tool.description.isNotBlank()) {
                                    Text(
                                        text = tool.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (health.registeredTools.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    health.registeredTools.forEach { toolName ->
                        Text(
                            text = "• $toolName",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            color = colorScheme.onSurface,
                        )
                    }
                }
            } else {
                Text(
                    text = "Kayıtlı MCP aracı bulunamadı. Sunucu başlatıldığında araçlar burada listelenir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    // Grouped Skills, Rules, and Lifecycle Hooks
    CustomizationCategory.entries.forEach { category ->
        val itemsForCategory = state.customizations.filter { it.category == category }
        if (itemsForCategory.isNotEmpty()) {
            val (sectionTitle, sectionSubtitle) = when (category) {
                CustomizationCategory.SKILLS -> "Yetenekler (Skills)" to "Alan bazlı ajan talimatları ve teknik kılavuzlar."
                CustomizationCategory.RULES -> "Kurallar (Rules)" to "Güvenlik kapısı ve davranış standartları."
                CustomizationCategory.HOOKS -> "Yaşam Döngüsü Kancaları (Hooks)" to "Donanım ve sistem olaylarına bağlı tetikleyiciler."
            }
            SettingsSectionCard(
                title = sectionTitle,
                subtitle = sectionSubtitle,
            ) {
                itemsForCategory.forEachIndexed { index, item ->
                    if (index > 0) {
                        HorizontalDivider(color = extendedColors.cardBorder.copy(alpha = 0.75f))
                    }
                    SettingsSwitchRow(
                        title = item.name,
                        subtitle = item.description,
                        checked = item.enabled,
                        onCheckedChange = {
                            onAction(WorkspaceAction.ToggleCustomization(item.id))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun mcpRiskColor(risk: McpToolRisk): Color {
    val extendedColors = LocalAntigravityColors.current
    return when (risk) {
        McpToolRisk.LOW -> extendedColors.success
        McpToolRisk.MEDIUM -> extendedColors.accentCyan
        McpToolRisk.HIGH -> extendedColors.warning
        McpToolRisk.CRITICAL -> extendedColors.critical
    }
}

// ============================================================================
// 6. MOBILE & ROOT SETTINGS (Mobile-Exclusive)
// ============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MobileRootSettingsContent(
    state: WorkspaceUiState,
    onAction: (WorkspaceAction) -> Unit,
) {
    val extendedColors = LocalAntigravityColors.current
    val colorScheme = MaterialTheme.colorScheme
    val onboarding = state.onboarding
    val rootEnv = onboarding.rootEnvironment

    // Root Provider Card
    SettingsSectionCard(
        title = "Root Sağlayıcı Durumu",
        subtitle = "KernelSU, Magisk veya APatch üzerinden `libsu` mount-master yetkisi.",
        headerTrailing = {
            FilledTonalButton(
                onClick = { onAction(WorkspaceAction.CheckRootStatus) },
                enabled = !onboarding.isBusy,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Root Denetle")
            }
        },
    ) {
        val providerLabel = when (rootEnv?.provider) {
            RootProviderType.KERNEL_SU -> "KernelSU"
            RootProviderType.MAGISK -> "Magisk"
            RootProviderType.APATCH -> "APatch"
            RootProviderType.GENERIC_SU -> "Generic SU"
            RootProviderType.NONE -> "Root Bulunamadı"
            null -> "Henüz Denetlenmedi"
        }
        val granted = rootEnv?.isRootGranted == true
        val statusColor = when {
            rootEnv == null -> colorScheme.onSurfaceVariant
            granted -> extendedColors.success
            else -> extendedColors.critical
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = providerLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface,
                )
                if (!rootEnv?.suVersion.isNullOrBlank()) {
                    Text(
                        text = "Sürüm: ${rootEnv?.suVersion}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = statusColor.copy(alpha = if (extendedColors.isDark) 0.16f else 0.10f),
                border = BorderStroke(0.75.dp, statusColor.copy(alpha = 0.45f)),
            ) {
                Text(
                    text = when {
                        rootEnv == null -> "Bilinmiyor"
                        granted -> "Root Aktif"
                        else -> "Yetki Yok"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }

        if (rootEnv != null) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StatusPill(
                    label = if (rootEnv.mountMasterSupported) "Mount Master: Destekli" else "Mount Master: Yok",
                    color = if (rootEnv.mountMasterSupported) extendedColors.success else extendedColors.warning,
                )
                rootEnv.managerPackageName?.let { pkg ->
                    StatusPill(label = pkg, color = colorScheme.primary)
                }
            }
            if (rootEnv.guidanceMessage.isNotBlank()) {
                Text(
                    text = rootEnv.guidanceMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    // ARM64 glibc Chroot Status Card
    SettingsSectionCard(
        title = "ARM64 glibc Chroot & agy CLI",
        subtitle = "Yerel Linux ortamını bağlayın, güvenli ayırın ve `agy` sürümünü denetleyin.",
    ) {
        if (onboarding.isBusy) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(999.dp)),
            )
        }

        val (chrootStatusText, chrootStatusColor, chrootDetail) = when (val cs = onboarding.chrootState) {
            ChrootState.NotInstalled -> Triple(
                "Kurulu Değil",
                colorScheme.onSurfaceVariant,
                "ARM64 rootfs henüz kurulmadı.",
            )

            is ChrootState.Installing -> Triple(
                "Kuruluyor (${cs.progressPercent ?: 0}%)",
                extendedColors.accentCyan,
                cs.stage,
            )

            is ChrootState.Mounted -> Triple(
                "Bağlı (Mounted)",
                extendedColors.success,
                "Rootfs: ${cs.rootfsPath} • Core Mount: ${if (cs.mountStatus.allCoreMounted) "Hazır" else "Kısmi"}",
            )

            is ChrootState.Unmounted -> Triple(
                "Ayrılmış (Unmounted)",
                extendedColors.warning,
                "Rootfs: ${cs.rootfsPath}",
            )

            is ChrootState.Error -> Triple(
                "Hata",
                extendedColors.critical,
                cs.message,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    tint = chrootStatusColor,
                    modifier = Modifier.size(20.dp),
                )
                Column {
                    Text(
                        text = chrootStatusText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface,
                    )
                    Text(
                        text = chrootDetail,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        val resolvedAgyVersion = onboarding.agyVersion
            ?: (onboarding.chrootState as? ChrootState.Mounted)?.agyVersion
            ?: (onboarding.chrootState as? ChrootState.Unmounted)?.agyVersion
        if (!resolvedAgyVersion.isNullOrBlank()) {
            StatusPill(
                label = "agy CLI: $resolvedAgyVersion",
                color = extendedColors.accentCyan,
            )
        }

        if (!onboarding.statusMessage.isNullOrBlank()) {
            Text(
                text = onboarding.statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = extendedColors.success,
            )
        }
        if (!onboarding.errorMessage.isNullOrBlank()) {
            Text(
                text = onboarding.errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = extendedColors.critical,
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { onAction(WorkspaceAction.SetupChroot) },
                enabled = !onboarding.isBusy,
            ) {
                Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Bağla")
            }

            OutlinedButton(
                onClick = { onAction(WorkspaceAction.UnmountChroot) },
                enabled = !onboarding.isBusy,
            ) {
                Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Güvenli Ayır (Unmount)")
            }

            FilledTonalButton(
                onClick = { onAction(WorkspaceAction.CheckOrInstallAgy) },
                enabled = !onboarding.isBusy,
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("agy Sürüm Denetle")
            }
        }
    }

    // Floating Overlay Orb & Hardware Kill-Switch Card
    SettingsSectionCard(
        title = "Yüzer Katman & Donanım Güvenliği",
        subtitle = "Diğer uygulamaların üzerinde ajan kontrolü ve acil durdurma kısayolu.",
    ) {
        SettingsSwitchRow(
            title = "Yüzer Ajan Küresi (Floating Overlay Orb)",
            subtitle = "SYSTEM_ALERT_WINDOW ile tüm uygulamaların üzerinde hızlı erişim küresi göster.",
            checked = state.settings.overlayOrbEnabled,
            onCheckedChange = { enabled ->
                onAction(WorkspaceAction.UpdateSettings { it.copy(overlayOrbEnabled = enabled) })
            },
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Yüzer Küre Opaklığı",
                    style = MaterialTheme.typography.titleSmall,
                    color = colorScheme.onSurface,
                )
                Text(
                    text = "%${(state.settings.overlayOpacity.coerceIn(0.4f, 1.0f) * 100f).roundToInt()}",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = colorScheme.primary,
                )
            }
            Slider(
                value = state.settings.overlayOpacity.coerceIn(0.4f, 1.0f),
                onValueChange = { value ->
                    onAction(WorkspaceAction.UpdateSettings { it.copy(overlayOpacity = value.coerceIn(0.4f, 1.0f)) })
                },
                valueRange = 0.4f..1.0f,
                enabled = state.settings.overlayOrbEnabled,
            )
        }

        HorizontalDivider(color = extendedColors.cardBorder.copy(alpha = 0.75f))

        SettingsSwitchRow(
            title = "Donanım Kill-Switch (Ses+ + Ses- 500ms)",
            subtitle = "Ses açma ve kısma tuşlarına 500ms birlikte basıldığında tüm ajan ve root süreçlerini anında sonlandır.",
            checked = state.settings.hardwareKillSwitchEnabled,
            onCheckedChange = { enabled ->
                onAction(WorkspaceAction.UpdateSettings { it.copy(hardwareKillSwitchEnabled = enabled) })
            },
        )
    }

    // Setup Wizard Launcher
    SettingsSectionCard(
        title = "Kurulum Sihirbazı",
        subtitle = "Root izinleri, rootfs kurulumu ve ilk hesap yapılandırmasını adım adım yeniden açın.",
    ) {
        OutlinedButton(
            onClick = { onAction(WorkspaceAction.OpenOnboardingFromSettings(true)) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.AutoMirrored.Filled.Launch, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Kurulum Sihirbazını Aç")
        }
    }
}

// ============================================================================
// SHARED SETTINGS BUILDING BLOCKS
// ============================================================================

@Composable
private fun SettingsSectionCard(
    title: String,
    subtitle: String? = null,
    headerTrailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val extendedColors = LocalAntigravityColors.current
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = settingsCardContainerColor(),
        border = BorderStroke(1.dp, extendedColors.cardBorder),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (headerTrailing != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    headerTrailing()
                }
            }
            content()
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun StatusPill(
    label: String,
    color: Color,
) {
    val extendedColors = LocalAntigravityColors.current
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = if (extendedColors.isDark) 0.14f else 0.10f),
        border = BorderStroke(0.75.dp, color.copy(alpha = 0.45f)),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
        )
    }
}
