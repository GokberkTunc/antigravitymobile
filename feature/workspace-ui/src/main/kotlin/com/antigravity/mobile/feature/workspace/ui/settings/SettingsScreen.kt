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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.FilterChipDefaults
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
import com.antigravity.mobile.feature.workspace.model.AppThemeMode
import com.antigravity.mobile.feature.workspace.model.CustomizationCategory
import com.antigravity.mobile.feature.workspace.model.InterfaceLayoutMode
import com.antigravity.mobile.feature.workspace.model.SettingsCategory
import com.antigravity.mobile.feature.workspace.model.WorkspaceAction
import com.antigravity.mobile.feature.workspace.model.WorkspaceUiState
import com.antigravity.mobile.feature.workspace.theme.AntigravityPalette
import kotlin.math.roundToInt

/**
 * Full Antigravity Desktop-parity + Mobile-exclusive Settings screen with 6 categories
 * and proper hardware Back handling (`CloseSettings`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: WorkspaceUiState,
    onAction: (WorkspaceAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = true) {
        onAction(WorkspaceAction.CloseSettings)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Ayarlar",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "Antigravity Mobile • Masaüstü Eşleniği & Root",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { onAction(WorkspaceAction.CloseSettings) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Geri",
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )

                // Horizontal scrollable category chips
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(SettingsCategory.entries) { category ->
                            val selected = state.selectedSettingsCategory == category
                            FilterChip(
                                selected = selected,
                                onClick = { onAction(WorkspaceAction.SelectSettingsCategory(category)) },
                                label = {
                                    Text(
                                        text = category.displayLabel(),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = category.icon(),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selected,
                                    borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                ),
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (state.selectedSettingsCategory) {
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

private fun SettingsCategory.displayLabel(): String = when (this) {
    SettingsCategory.GENERAL -> "Genel"
    SettingsCategory.ACCOUNTS_QUOTA -> "Hesaplar & Kota"
    SettingsCategory.MODELS_AGENT -> "Modeller & Ajan"
    SettingsCategory.PROJECTS_PERMISSIONS -> "Projeler & İzinler"
    SettingsCategory.CUSTOMIZATIONS -> "Özelleştirmeler"
    SettingsCategory.MOBILE_ROOT -> "Mobil & Root"
}

private fun SettingsCategory.icon(): ImageVector = when (this) {
    SettingsCategory.GENERAL -> Icons.Default.Tune
    SettingsCategory.ACCOUNTS_QUOTA -> Icons.Default.AccountCircle
    SettingsCategory.MODELS_AGENT -> Icons.Default.AutoAwesome
    SettingsCategory.PROJECTS_PERMISSIONS -> Icons.Default.Folder
    SettingsCategory.CUSTOMIZATIONS -> Icons.Default.Extension
    SettingsCategory.MOBILE_ROOT -> Icons.Default.AdminPanelSettings
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

    SettingsSectionCard(
        title = "Görünüm & Tema",
        subtitle = "Antigravity Obsidian koyu tema önceliklidir; sistem veya aydınlık tema seçebilirsiniz.",
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

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

        SettingsSwitchRow(
            title = "Dinamik Renk (Material You)",
            subtitle = "Android 12+ duvar kağıdı paletini uygula. Kapalıyken Antigravity Obsidian/Cyan paleti kullanılır.",
            checked = settings.useDynamicColor,
            onCheckedChange = { enabled ->
                onAction(WorkspaceAction.UpdateSettings { it.copy(useDynamicColor = enabled) })
            },
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

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
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        SettingsSwitchRow(
            title = "Kod Satır Numaralarını Göster",
            subtitle = "Markdown kod bloklarında sol kenar satır numaralarını etkinleştir.",
            checked = settings.showCodeLineNumbers,
            onCheckedChange = { enabled ->
                onAction(WorkspaceAction.UpdateSettings { it.copy(showCodeLineNumbers = enabled) })
            },
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        SettingsSwitchRow(
            title = "Düşünce Bloklarını Varsayılan Açık Tut",
            subtitle = "Modelin iç muhakeme (Thought) akordeonlarını otomatik genişletilmiş göster.",
            checked = settings.showThoughtBlocksExpanded,
            onCheckedChange = { enabled ->
                onAction(WorkspaceAction.UpdateSettings { it.copy(showThoughtBlocksExpanded = enabled) })
            },
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
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
    var showAddAccountDialog by remember { mutableStateOf(false) }
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
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text("Tüm Kotaları Yenile")
            }

            FilledTonalButton(onClick = { showAddAccountDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Yeni Google Hesabı")
            }

            OutlinedButton(onClick = { showImportDesktopDialog = true }) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Masaüstü ~/.gemini İçe Aktar")
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

        SettingsSwitchRow(
            title = "Açılışta Kotaları Otomatik Yenile",
            subtitle = "Uygulama açıldığında aktif ve kayıtlı Google hesaplarının kalan kotasını çek.",
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
            subtitle = "Henüz bir Google hesabı profili eklenmedi. Başlamak için '+ Yeni Google Hesabı' butonuna dokunun.",
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        TextButton(
                            onClick = { profilePendingDeletion = profile },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = AntigravityPalette.CriticalRed,
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

    if (showAddAccountDialog) {
        AddGoogleAccountDialog(
            onDismiss = { showAddAccountDialog = false },
            onConfirm = { name, email ->
                onAction(WorkspaceAction.CreateProfile(name, email))
                showAddAccountDialog = false
            },
        )
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
                        containerColor = AntigravityPalette.CriticalRed,
                        contentColor = Color.White,
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
                    AcpModel.GEMINI_4_NEON -> AntigravityPalette.NeonCyan
                    AcpModel.GEMINI_4_HYDROGEN -> AntigravityPalette.HydrogenViolet
                    AcpModel.GEMINI_4_CARBON -> AntigravityPalette.CarbonTeal
                    AcpModel.ORCHESTRATOR -> AntigravityPalette.SuccessGreen
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) {
                        accent.copy(alpha = 0.14f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    },
                    border = BorderStroke(
                        width = if (selected) 1.25.dp else 1.dp,
                        color = if (selected) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
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
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                ) {
                                    Text(
                                        text = model.cliId,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        AntigravityPalette.WarningAmber,
                    )
                    PermissionMode.ASK -> Triple(
                        "Onay Sor (ASK)",
                        "Dosya yazma, sistem araçları ve root komutlarından önce kullanıcı onayı ister.",
                        AntigravityPalette.NeonCyan,
                    )
                    PermissionMode.PLAN -> Triple(
                        "Plan Modu (PLAN)",
                        "Salt okunur analiz ve adım adım plan oluşturma; sistemde değişiklik yapmaz.",
                        AntigravityPalette.HydrogenViolet,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) {
                        color.copy(alpha = 0.14f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    },
                    border = BorderStroke(
                        1.dp,
                        if (selected) color else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
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
                            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
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
    var showAddProjectDialog by remember { mutableStateOf(false) }
    var projectToRename by remember { mutableStateOf<WorkspaceProject?>(null) }
    var projectToDelete by remember { mutableStateOf<WorkspaceProject?>(null) }
    var newPackageName by remember { mutableStateOf("") }

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
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            state.projects.forEach { project ->
                val isSelected = project.id == state.activeProject.id
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    },
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
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
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (isSelected) {
                                        Surface(
                                            shape = RoundedCornerShape(999.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        ) {
                                            Text(
                                                text = "Aktif",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = project.rootPath,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                            ) {
                                Text(
                                    text = project.defaultPermissionMode.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                    contentColor = AntigravityPalette.CriticalRed,
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

        // Quick suggestions for common protected packages not yet added
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.denylistPackages.sorted().forEach { pkg ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
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
                                    tint = AntigravityPalette.WarningAmber,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = pkg,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = FontFamily.Monospace,
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
                                    tint = AntigravityPalette.CriticalRed,
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
                        containerColor = AntigravityPalette.CriticalRed,
                        contentColor = Color.White,
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
// 5. CUSTOMIZATIONS SETTINGS (MCP + Skills / Rules / Hooks)
// ============================================================================

@Composable
private fun CustomizationsSettingsContent(
    state: WorkspaceUiState,
    onAction: (WorkspaceAction) -> Unit,
) {
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
                    AntigravityPalette.SuccessGreen
                } else {
                    AntigravityPalette.CriticalRed
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = statusColor.copy(alpha = 0.16f),
                    border = BorderStroke(0.75.dp, statusColor.copy(alpha = 0.5f)),
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
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    text = "$toolCount araç",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { expandTools = !expandTools }) {
                Text(if (expandTools) "Araçları Gizle" else "15 MCP Aracını Göster")
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
                color = AntigravityPalette.CriticalRed,
            )
        }

        if (expandTools) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            if (state.mcpTools.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.mcpTools.forEach { tool ->
                        val riskColor = mcpRiskColor(tool.riskLevel)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
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
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = riskColor.copy(alpha = 0.16f),
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
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        )
                    }
                }
            } else {
                Text(
                    text = "Kayıtlı MCP aracı bulunamadı. Sunucu başlatıldığında araçlar burada listelenir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
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

private fun mcpRiskColor(risk: McpToolRisk): Color = when (risk) {
    McpToolRisk.LOW -> AntigravityPalette.SuccessGreen
    McpToolRisk.MEDIUM -> AntigravityPalette.NeonCyan
    McpToolRisk.HIGH -> AntigravityPalette.WarningAmber
    McpToolRisk.CRITICAL -> AntigravityPalette.CriticalRed
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
            rootEnv == null -> AntigravityPalette.TextSecondary
            granted -> AntigravityPalette.SuccessGreen
            else -> AntigravityPalette.CriticalRed
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
                )
                if (!rootEnv?.suVersion.isNullOrBlank()) {
                    Text(
                        text = "Sürüm: ${rootEnv?.suVersion}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = statusColor.copy(alpha = 0.16f),
                border = BorderStroke(0.75.dp, statusColor.copy(alpha = 0.5f)),
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
                    color = if (rootEnv.mountMasterSupported) AntigravityPalette.SuccessGreen else AntigravityPalette.WarningAmber,
                )
                rootEnv.managerPackageName?.let { pkg ->
                    StatusPill(label = pkg, color = AntigravityPalette.NeonCyan)
                }
            }
            if (rootEnv.guidanceMessage.isNotBlank()) {
                Text(
                    text = rootEnv.guidanceMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                AntigravityPalette.TextSecondary,
                "ARM64 rootfs henüz kurulmadı.",
            )
            is ChrootState.Installing -> Triple(
                "Kuruluyor (${cs.progressPercent ?: 0}%)",
                AntigravityPalette.NeonCyan,
                cs.stage,
            )
            is ChrootState.Mounted -> Triple(
                "Bağlı (Mounted)",
                AntigravityPalette.SuccessGreen,
                "Rootfs: ${cs.rootfsPath} • Core Mount: ${if (cs.mountStatus.allCoreMounted) "Hazır" else "Kısmi"}",
            )
            is ChrootState.Unmounted -> Triple(
                "Ayrılmış (Unmounted)",
                AntigravityPalette.WarningAmber,
                "Rootfs: ${cs.rootfsPath}",
            )
            is ChrootState.Error -> Triple(
                "Hata",
                AntigravityPalette.CriticalRed,
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
                    )
                    Text(
                        text = chrootDetail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                color = AntigravityPalette.CarbonTeal,
            )
        }

        if (!onboarding.statusMessage.isNullOrBlank()) {
            Text(
                text = onboarding.statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = AntigravityPalette.SuccessGreen,
            )
        }
        if (!onboarding.errorMessage.isNullOrBlank()) {
            Text(
                text = onboarding.errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = AntigravityPalette.CriticalRed,
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
                )
                Text(
                    text = "%${(state.settings.overlayOpacity.coerceIn(0.4f, 1.0f) * 100f).roundToInt()}",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
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

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
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
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.14f),
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
