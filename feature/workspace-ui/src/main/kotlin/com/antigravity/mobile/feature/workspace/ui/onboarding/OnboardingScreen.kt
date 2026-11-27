package com.antigravity.mobile.feature.workspace.ui.onboarding

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.antigravity.mobile.contracts.AuthFlowState
import com.antigravity.mobile.contracts.ChrootState
import com.antigravity.mobile.contracts.RootProviderType
import com.antigravity.mobile.feature.workspace.model.RootfsSourceOption
import com.antigravity.mobile.feature.workspace.model.WorkspaceAction
import com.antigravity.mobile.feature.workspace.model.WorkspaceUiState
import com.antigravity.mobile.feature.workspace.theme.LocalAntigravityColors

private enum class StepStatusKind {
    READY,
    MISSING,
    IN_PROGRESS,
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    state: WorkspaceUiState,
    onAction: (WorkspaceAction) -> Unit,
    modifier: Modifier = Modifier,
    isDeviceAdminActive: Boolean = state.onboarding.isDeviceAdminActive,
    deviceAdminStatusNote: String? = null,
    onEnableDeviceAdmin: (() -> Unit)? = null,
    onRegisterSystemServices: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val extColors = LocalAntigravityColors.current
    val colorScheme = MaterialTheme.colorScheme
    val onboarding = state.onboarding

    val effectiveDeviceAdminActive = isDeviceAdminActive || onboarding.isDeviceAdminActive
    val effectiveOverlayGranted = onboarding.isOverlayPermissionGranted || canDrawOverlaysSafe(context)

    var showAddAnotherAccount by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }
    var showAdvancedAuth by remember { mutableStateOf(false) }
    var manualAuthCode by remember { mutableStateOf("") }
    var desktopImportPath by remember { mutableStateOf("/sdcard/Download/gemini-profile.tar.gz") }
    var localClipboardFeedback by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        onAction(
            WorkspaceAction.RunFullDiagnostics(
                packageName = context.packageName,
                isOverlayGranted = canDrawOverlaysSafe(context),
                isDeviceAdminActive = isDeviceAdminActive,
            ),
        )
    }

    LaunchedEffect(state.authFlowState) {
        if (state.authFlowState !is AuthFlowState.AwaitingBrowserAuth) {
            localClipboardFeedback = null
        }
    }

    BackHandler(enabled = true) {
        onAction(WorkspaceAction.NavigateBack)
    }

    val needsSystemAutoFix = !onboarding.isRootReady ||
        !onboarding.isPermissionsReady ||
        !onboarding.isChrootMounted ||
        !onboarding.isAgyReady

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = colorScheme.surface,
                        titleContentColor = colorScheme.onSurface,
                        navigationIconContentColor = colorScheme.onSurface,
                        actionIconContentColor = colorScheme.onSurfaceVariant,
                    ),
                    navigationIcon = {
                        IconButton(onClick = { onAction(WorkspaceAction.NavigateBack) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Geri",
                            )
                        }
                    },
                    title = {
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                text = "Kurulum & Sistem Tanılama",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${state.setupReadyCount} / ${state.setupTotalSteps} bileşen hazır",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (state.isSetupFullyReady) extColors.success else colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                onAction(
                                    WorkspaceAction.RunFullDiagnostics(
                                        packageName = context.packageName,
                                        isOverlayGranted = canDrawOverlaysSafe(context),
                                        isDeviceAdminActive = effectiveDeviceAdminActive,
                                    ),
                                )
                            },
                            enabled = !onboarding.isRunningDiagnostics && !onboarding.isAutoFixingAll,
                        ) {
                            if (onboarding.isRunningDiagnostics) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = colorScheme.primary,
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Tanılamayı Yenile",
                                )
                            }
                        }
                    },
                )
                HorizontalDivider(color = extColors.cardBorder)
            }
        },
        bottomBar = {
            Surface(
                color = colorScheme.surface,
                contentColor = colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = extColors.cardBorder)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        if (state.isSetupFullyReady) {
                            Button(
                                onClick = { onAction(WorkspaceAction.CompleteOnboarding) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Çalışma Alanına Geç",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        } else {
                            FilledTonalButton(
                                onClick = { onAction(WorkspaceAction.CompleteOnboarding) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Text(
                                    text = "Şimdilik Atla — Çalışma Alanına Geç",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Hero Readiness Card
            SetupHeroReadinessCard(
                readyCount = state.setupReadyCount,
                totalSteps = state.setupTotalSteps,
                isFullyReady = state.isSetupFullyReady,
                isDiagnosing = onboarding.isRunningDiagnostics,
                isAutoFixingAll = onboarding.isAutoFixingAll,
                autoFixStageLabel = onboarding.autoFixStageLabel,
                needsSystemAutoFix = needsSystemAutoFix,
                isBusy = onboarding.isBusy,
                onAutoFixAll = {
                    onAction(WorkspaceAction.AutoFixAllPrerequisites(context.packageName))
                },
            )

            // Live Status Banner
            if (!onboarding.statusMessage.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colorScheme.primaryContainer.copy(alpha = if (extColors.isDark) 0.50f else 0.80f),
                    border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = if (extColors.isDark) 0.35f else 0.25f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = onboarding.statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // Dismissible Error Banner
            val visibleError = onboarding.errorMessage ?: state.errorMessage
            if (!visibleError.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colorScheme.errorContainer,
                    border = BorderStroke(1.dp, colorScheme.error.copy(alpha = if (extColors.isDark) 0.45f else 0.30f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = colorScheme.onErrorContainer,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = visibleError,
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { onAction(WorkspaceAction.DismissError) },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Hatayı kapat",
                                tint = colorScheme.onErrorContainer,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            // =================================================================
            // STEP 1 — Root Environment (KernelSU / Magisk / APatch)
            // =================================================================
            val rootEnv = onboarding.rootEnvironment
            val step1Status = when {
                onboarding.isRunningDiagnostics && rootEnv == null -> StepStatusKind.IN_PROGRESS
                onboarding.isRootReady -> StepStatusKind.READY
                else -> StepStatusKind.MISSING
            }
            SetupStepCard(
                stepNumber = 1,
                title = "Root Ortamı (KernelSU / Magisk / APatch)",
                status = step1Status,
                whyNeededText = "Antigravity Mobile; izole ARM64 Linux (glibc) chroot konteynerini bağlamak, Restricted Settings kısıtını aşmak ve sistem servislerini tek dokunuşla yapılandırmak için Mount Master destekli root erişimi kullanır.",
            ) {
                val providerLabel = when (rootEnv?.provider) {
                    RootProviderType.KERNEL_SU -> "KernelSU"
                    RootProviderType.MAGISK -> "Magisk"
                    RootProviderType.APATCH -> "APatch"
                    RootProviderType.GENERIC_SU -> "Generic SU"
                    RootProviderType.NONE -> "Root Bulunamadı"
                    null -> if (onboarding.isRunningDiagnostics) "Taranıyor..." else "Henüz Denetlenmedi"
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    DiagnosticPill(
                        label = "Sağlayıcı: $providerLabel",
                        ok = rootEnv?.isRootGranted == true,
                        neutral = rootEnv == null,
                    )
                    if (rootEnv != null) {
                        DiagnosticPill(
                            label = if (rootEnv.mountMasterSupported) "Mount Master ✓" else "Mount Master Yok",
                            ok = rootEnv.mountMasterSupported,
                        )
                        if (!rootEnv.suVersion.isNullOrBlank()) {
                            DiagnosticPill(
                                label = rootEnv.suVersion.orEmpty(),
                                ok = true,
                                neutral = true,
                            )
                        }
                    }
                }

                if (rootEnv != null && !rootEnv.isRootGranted) {
                    val fixGuide = rootEnv.guidanceMessage.ifBlank {
                        when (rootEnv.provider) {
                            RootProviderType.KERNEL_SU ->
                                "KernelSU Manager uygulamasını açın → Süper Kullanıcı (Superuser) sekmesinden Antigravity Mobile için root yetkisini açın ve Global Mount Namespace seçeneğini etkinleştirin."
                            RootProviderType.MAGISK ->
                                "Magisk uygulamasını açın → Süper Kullanıcı sekmesinden Antigravity Mobile iznini onaylayın ve Mount Namespace Mode ayarını Global olarak belirleyin."
                            RootProviderType.APATCH ->
                                "APatch Manager → SuperUser bölümünden Antigravity Mobile için root yetkisi verin."
                            else ->
                                "Root yöneticinizde (KernelSU / Magisk / APatch) Antigravity Mobile için Süper Kullanıcı yetkisini onaylayıp yeniden denetleyin."
                        }
                    }
                    GuidanceWarningBox(text = fixGuide)
                } else if (rootEnv?.provider == RootProviderType.KERNEL_SU && !rootEnv.mountMasterSupported) {
                    GuidanceWarningBox(
                        text = rootEnv.guidanceMessage.ifBlank {
                            "KernelSU Manager → Antigravity Mobile profilinde Global Mount Namespace açık olduğunda chroot bağlama işlemleri sorunsuz çalışır."
                        },
                    )
                }

                FilledTonalButton(
                    onClick = { onAction(WorkspaceAction.CheckRootStatus) },
                    enabled = !onboarding.isBusy && !onboarding.isAutoFixingAll,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Root Durumunu Denetle")
                }
            }

            // =================================================================
            // STEP 2 — Android System Permissions & Services
            // =================================================================
            val bootstrapReport = onboarding.bootstrapReport
            val step2Status = when {
                onboarding.isRunningDiagnostics && bootstrapReport == null -> StepStatusKind.IN_PROGRESS
                onboarding.isPermissionsReady -> StepStatusKind.READY
                else -> StepStatusKind.MISSING
            }
            SetupStepCard(
                stepNumber = 2,
                title = "Sistem İzinleri & Android Servisleri",
                status = step2Status,
                whyNeededText = "Ajanın ekran UI ağacını okuyabilmesi (AccessibilityService), bildirimleri yönetebilmesi (NotificationListener), diğer uygulamalar üzerinde yüzer kontrol küresi gösterebilmesi ve arka planda kesilmemesi için bu Android izinleri gerekir.",
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DiagnosticPill(
                        label = "Restricted Settings (AppOps)",
                        ok = bootstrapReport?.restrictedSettingsGranted == true,
                        neutral = bootstrapReport == null,
                    )
                    DiagnosticPill(
                        label = "AccessibilityService",
                        ok = bootstrapReport?.accessibilityEnabled == true,
                        neutral = bootstrapReport == null,
                    )
                    DiagnosticPill(
                        label = "NotificationListener",
                        ok = bootstrapReport?.notificationListenerEnabled == true,
                        neutral = bootstrapReport == null,
                    )
                    DiagnosticPill(
                        label = "Yüzen Katman (Overlay)",
                        ok = effectiveOverlayGranted,
                    )
                    DiagnosticPill(
                        label = "Pil / Doze Muafiyeti",
                        ok = bootstrapReport?.batteryOptimizationExempted == true,
                        neutral = bootstrapReport == null,
                    )
                    DiagnosticPill(
                        label = "Device Admin (İsteğe bağlı)",
                        ok = effectiveDeviceAdminActive,
                        neutral = !effectiveDeviceAdminActive,
                    )
                }

                if (!deviceAdminStatusNote.isNullOrBlank()) {
                    Text(
                        text = deviceAdminStatusNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            if (onRegisterSystemServices != null) {
                                onRegisterSystemServices()
                            } else {
                                onAction(WorkspaceAction.RunRootBootstrap(packageName = context.packageName))
                            }
                        },
                        enabled = !onboarding.isBusy && !onboarding.isAutoFixingAll,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Eksik İzinleri Otomatik Onar")
                    }

                    if (onEnableDeviceAdmin != null) {
                        OutlinedButton(
                            onClick = onEnableDeviceAdmin,
                            enabled = !effectiveDeviceAdminActive && !onboarding.isBusy,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.AdminPanelSettings,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (effectiveDeviceAdminActive) "Device Admin Etkin" else "Device Admin Aç")
                        }
                    }
                }

                // Compact manual fallback shortcuts to Android Settings
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Manuel ayarlar:",
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                    AssistChip(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        },
                        label = { Text("Erişilebilirlik", style = MaterialTheme.typography.labelSmall) },
                    )
                    AssistChip(
                        onClick = {
                            runCatching {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}"),
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            }
                        },
                        label = { Text("Üstte Gösterme", style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }

            // =================================================================
            // STEP 3 — ARM64 Glibc Chroot (Ubuntu 24.04)
            // =================================================================
            val chrootState = onboarding.chrootState
            val step3Status = when {
                chrootState is ChrootState.Installing -> StepStatusKind.IN_PROGRESS
                onboarding.isChrootMounted -> StepStatusKind.READY
                else -> StepStatusKind.MISSING
            }
            SetupStepCard(
                stepNumber = 3,
                title = "ARM64 Glibc Chroot (Ubuntu 24.04)",
                status = step3Status,
                whyNeededText = "Google Antigravity CLI (`agy`) standart glibc kütüphanelerine ihtiyaç duyar. Android'in Bionic libc ortamı yerine cihaz içinde hafif bir Ubuntu 24.04 ARM64 chroot katmanı kurularak yerel hızda çalıştırılır.",
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterChip(
                        selected = onboarding.selectedRootfsSource == RootfsSourceOption.BUNDLED_ASSET,
                        onClick = { onAction(WorkspaceAction.SelectRootfsSource(RootfsSourceOption.BUNDLED_ASSET)) },
                        label = { Text("APK Asset (Önerilen)") },
                    )
                    FilterChip(
                        selected = onboarding.selectedRootfsSource == RootfsSourceOption.REMOTE_DOWNLOAD,
                        onClick = { onAction(WorkspaceAction.SelectRootfsSource(RootfsSourceOption.REMOTE_DOWNLOAD)) },
                        label = { Text("İndir (Ubuntu 24.04.5)") },
                    )
                    FilterChip(
                        selected = onboarding.selectedRootfsSource == RootfsSourceOption.LOCAL_FILE,
                        onClick = { onAction(WorkspaceAction.SelectRootfsSource(RootfsSourceOption.LOCAL_FILE)) },
                        label = { Text("Yerel .tar.gz") },
                    )
                }

                when (onboarding.selectedRootfsSource) {
                    RootfsSourceOption.BUNDLED_ASSET -> {
                        Text(
                            text = "Çevrimdışı APK içi `assets/rootfs/arm64-rootfs.tar.gz` görüntüsünü `/data/local/ag_rootfs` dizinine çıkarıp çekirdek mount noktalarını bağlar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }

                    RootfsSourceOption.REMOTE_DOWNLOAD -> {
                        OutlinedTextField(
                            value = onboarding.remoteRootfsUrl,
                            onValueChange = { onAction(WorkspaceAction.UpdateRemoteRootfsUrl(it)) },
                            label = { Text("Ubuntu 24.04.5 ARM64 URL") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                    }

                    RootfsSourceOption.LOCAL_FILE -> {
                        OutlinedTextField(
                            value = onboarding.localRootfsPath,
                            onValueChange = { onAction(WorkspaceAction.UpdateLocalRootfsPath(it)) },
                            label = { Text("Yerel .tar.gz dosya yolu") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                    }
                }

                when (chrootState) {
                    is ChrootState.Installing -> {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = chrootState.stage,
                                style = MaterialTheme.typography.labelMedium,
                                color = colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                            )
                            val pct = chrootState.progressPercent
                            if (pct != null) {
                                LinearProgressIndicator(
                                    progress = { (pct / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(999.dp)),
                                )
                            } else {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(999.dp)),
                                )
                            }
                        }
                    }

                    is ChrootState.Mounted -> {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            DiagnosticPill(
                                label = "Bağlı: ${chrootState.rootfsPath}",
                                ok = true,
                            )
                            DiagnosticPill(
                                label = if (chrootState.mountStatus.allCoreMounted) "Core Mount ✓" else "Kısmi Mount",
                                ok = chrootState.mountStatus.allCoreMounted,
                            )
                        }
                    }

                    is ChrootState.Unmounted -> {
                        DiagnosticPill(
                            label = "Kurulu ama bağlı değil (${chrootState.rootfsPath})",
                            ok = false,
                        )
                    }

                    is ChrootState.Error -> {
                        GuidanceWarningBox(
                            text = "Chroot hatası: ${chrootState.message}",
                            isCritical = true,
                        )
                    }

                    ChrootState.NotInstalled -> {
                        DiagnosticPill(
                            label = "Rootfs henüz kurulmadı",
                            ok = false,
                        )
                    }
                }

                Button(
                    onClick = { onAction(WorkspaceAction.SetupChroot) },
                    enabled = !onboarding.isBusy && !onboarding.isAutoFixingAll,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (onboarding.isChrootMounted) "Bağlantıyı Yenile" else "Rootfs Kur & Bağla")
                }
            }

            // =================================================================
            // STEP 4 — Chroot Packages & agy CLI Engine
            // =================================================================
            val step4Status = when {
                onboarding.isRunningDiagnostics && onboarding.agyVersion == null && !onboarding.chrootDependenciesReady ->
                    StepStatusKind.IN_PROGRESS
                onboarding.isAgyReady -> StepStatusKind.READY
                else -> StepStatusKind.MISSING
            }
            SetupStepCard(
                stepNumber = 4,
                title = "Chroot Paketleri & agy CLI Motoru",
                status = step4Status,
                whyNeededText = "Google sunucularıyla güvenli HTTPS/TLS iletişimi için `ca-certificates` + `curl`, OAuth geri dönüş dinleyicisi için `netcat` ve ajan/sohbet motoru için `agy` CLI ikilisi chroot içine kurulur.",
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DiagnosticPill(
                        label = "TLS Sertifikaları",
                        ok = onboarding.chrootCaCertsInstalled,
                        neutral = !onboarding.diagnosticsChecked,
                    )
                    DiagnosticPill(
                        label = "curl",
                        ok = onboarding.chrootCurlInstalled,
                        neutral = !onboarding.diagnosticsChecked,
                    )
                    DiagnosticPill(
                        label = "netcat",
                        ok = onboarding.chrootNetcatInstalled,
                        neutral = !onboarding.diagnosticsChecked,
                    )
                    DiagnosticPill(
                        label = "agy ${onboarding.agyVersion ?: "Kurulu Değil"}",
                        ok = !onboarding.agyVersion.isNullOrBlank(),
                        neutral = !onboarding.diagnosticsChecked,
                    )
                }

                Text(
                    text = "Tek dokunuşla eksik Ubuntu temel paketlerini (ca-certificates, curl, netcat) yükler ve en güncel Antigravity (`agy`) CLI sürümünü doğrular.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                )

                Button(
                    onClick = { onAction(WorkspaceAction.CheckOrInstallAgy) },
                    enabled = !onboarding.isBusy && !onboarding.isAutoFixingAll,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Paketleri & agy CLI'yi Kur / Güncelle")
                }
            }

            // =================================================================
            // STEP 5 — Effortless Google Account Sign-In
            // =================================================================
            val activeProfile = state.activeProfile
            val authState = state.authFlowState
            val isAuthenticated = activeProfile?.isAuthenticated == true
            val step5Status = when {
                authState is AuthFlowState.Starting || authState is AuthFlowState.SubmittingCode ->
                    StepStatusKind.IN_PROGRESS
                isAuthenticated -> StepStatusKind.READY
                else -> StepStatusKind.MISSING
            }

            SetupStepCard(
                stepNumber = 5,
                title = "Google Hesabı Girişi",
                status = step5Status,
                whyNeededText = "Gemini / Antigravity modellerine erişmek ve 5 saatlik/haftalık kota durumunuzu görmek için Google hesabınız izole bir chroot profilinde doğrulanır. OAuth token'ları yalnızca cihazınızda saklanır.",
            ) {
                if (activeProfile != null && activeProfile.isAuthenticated) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = extColors.success.copy(alpha = if (extColors.isDark) 0.14f else 0.10f),
                        border = BorderStroke(1.dp, extColors.success.copy(alpha = if (extColors.isDark) 0.45f else 0.35f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(extColors.success.copy(alpha = 0.20f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = extColors.success,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = "Google Hesabı Bağlandı ✓",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = extColors.success,
                                )
                                Text(
                                    text = activeProfile.email?.takeIf { it.isNotBlank() } ?: activeProfile.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    TextButton(
                        onClick = { showAddAnotherAccount = !showAddAnotherAccount },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    ) {
                        Text(
                            text = if (showAddAnotherAccount) "Farklı Hesap Ekleme Panelini Gizle" else "Farklı Hesap Ekle",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }

                    AnimatedVisibility(
                        visible = showAddAnotherAccount,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedTextField(
                                    value = newProfileName,
                                    onValueChange = { newProfileName = it },
                                    label = { Text("Yeni hesap etiketi (ör. İş)") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                )
                                FilledTonalButton(
                                    onClick = {
                                        val label = newProfileName.trim()
                                        if (label.isNotEmpty()) {
                                            onAction(WorkspaceAction.CreateProfile(label, null))
                                            newProfileName = ""
                                        }
                                    },
                                    enabled = newProfileName.isNotBlank() && !onboarding.isBusy,
                                    shape = RoundedCornerShape(10.dp),
                                ) {
                                    Text("Ekle")
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Tek dokunuşla profilinizi otomatik oluşturur ve Chrome Custom Tabs üzerinden resmi Google giriş ekranını açar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { onAction(WorkspaceAction.QuickGoogleSignIn) },
                        enabled = authState !is AuthFlowState.Starting &&
                            authState !is AuthFlowState.SubmittingCode &&
                            !onboarding.isAutoFixingAll,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        if (authState is AuthFlowState.Starting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = colorScheme.onPrimary,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Google Girişi Hazırlanıyor...")
                        } else {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Google ile Giriş Yap",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                // Awaiting Browser Auth 3-Step Micro-Guide & 1-Tap Clipboard Confirm
                when (authState) {
                    is AuthFlowState.Starting -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Text(
                                text = "Google OAuth bağlantısı oluşturuluyor...",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.primary,
                            )
                        }
                    }

                    is AuthFlowState.AwaitingBrowserAuth -> {
                        val targetProfileId = authState.challenge.profileId.ifBlank {
                            activeProfile?.id.orEmpty()
                        }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = colorScheme.primaryContainer.copy(alpha = if (extColors.isDark) 0.40f else 0.72f),
                            border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = if (extColors.isDark) 0.45f else 0.30f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    text = "Hızlı Doğrulama Adımları",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colorScheme.onPrimaryContainer,
                                )
                                MicroGuideStepRow(
                                    number = "1",
                                    text = "Tarayıcıda Google hesabınızla onaylayın",
                                )
                                MicroGuideStepRow(
                                    number = "2",
                                    text = "Açılan sayfadaki kodu kopyalayın",
                                )
                                MicroGuideStepRow(
                                    number = "3",
                                    text = "Uygulamaya dönün — kod otomatik algılanıp onaylanır",
                                )

                                if (!onboarding.clipboardAutoAuthHint.isNullOrBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = colorScheme.surface.copy(alpha = if (extColors.isDark) 0.85f else 0.95f),
                                        border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.35f)),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(14.dp),
                                                strokeWidth = 2.dp,
                                                color = colorScheme.primary,
                                            )
                                            Text(
                                                text = onboarding.clipboardAutoAuthHint,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = colorScheme.onSurface,
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                    }
                                }

                                if (!localClipboardFeedback.isNullOrBlank()) {
                                    Text(
                                        text = localClipboardFeedback.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = extColors.warning,
                                    )
                                }

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Button(
                                        onClick = {
                                            val clipText = readClipboardTextSafe(context)?.trim().orEmpty()
                                            if (clipText.isEmpty()) {
                                                localClipboardFeedback =
                                                    "Panoda kopyalanmış kod bulunamadı. Tarayıcıdaki kodu kopyalayıp tekrar dokunun."
                                            } else {
                                                localClipboardFeedback = null
                                                if (looksLikeStrictClipboardOAuth(clipText)) {
                                                    onAction(
                                                        WorkspaceAction.HandleClipboardAuthText(
                                                            rawText = clipText,
                                                            autoSubmit = true,
                                                        ),
                                                    )
                                                } else if (targetProfileId.isNotBlank()) {
                                                    onAction(
                                                        WorkspaceAction.SubmitAuthCode(
                                                            profileId = targetProfileId,
                                                            code = clipText,
                                                        ),
                                                    )
                                                }
                                            }
                                        },
                                        enabled = !onboarding.isBusy,
                                        shape = RoundedCornerShape(10.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentPaste,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Panodan Yapıştır & Onayla")
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            if (targetProfileId.isNotBlank()) {
                                                onAction(
                                                    WorkspaceAction.StartOAuthLogin(
                                                        profileId = targetProfileId,
                                                        capturedAuthUrl = authState.challenge.authUrl,
                                                    ),
                                                )
                                            } else {
                                                onAction(WorkspaceAction.QuickGoogleSignIn)
                                            }
                                        },
                                        enabled = !onboarding.isBusy,
                                        shape = RoundedCornerShape(10.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp),
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Tarayıcıda Tekrar Aç")
                                    }
                                }
                            }
                        }
                    }

                    is AuthFlowState.SubmittingCode -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Text(
                                text = "Google doğrulama kodu onaylanıyor...",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.primary,
                            )
                        }
                    }

                    is AuthFlowState.Failed -> {
                        GuidanceWarningBox(
                            text = authState.reason,
                            isCritical = true,
                        )
                    }

                    is AuthFlowState.Authenticated,
                    AuthFlowState.Idle,
                    -> Unit
                }

                // Collapsible Advanced Sign-In Options (Manual Code & Desktop ~/.gemini Import)
                HorizontalDivider(color = extColors.cardBorder.copy(alpha = 0.7f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showAdvancedAuth = !showAdvancedAuth }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Gelişmiş Giriş Seçenekleri (Manuel Kod / Masaüstü ~/.gemini)",
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = if (showAdvancedAuth) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }

                AnimatedVisibility(
                    visible = showAdvancedAuth,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val effectiveProfileId = activeProfile?.id
                        if (effectiveProfileId == null) {
                            Text(
                                text = "Manuel kod veya masaüstü profil aktarımı için önce yukarıdaki \"Google ile Giriş Yap\" butonuna basarak profil oluşturun.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedTextField(
                                    value = manualAuthCode,
                                    onValueChange = { manualAuthCode = it },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    label = { Text("Manuel doğrulama kodu / URL") },
                                )
                                FilledTonalButton(
                                    onClick = {
                                        onAction(WorkspaceAction.SubmitAuthCode(effectiveProfileId, manualAuthCode))
                                        manualAuthCode = ""
                                    },
                                    enabled = manualAuthCode.isNotBlank() && !onboarding.isBusy,
                                    shape = RoundedCornerShape(10.dp),
                                ) {
                                    Text("Onayla")
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedTextField(
                                    value = desktopImportPath,
                                    onValueChange = { desktopImportPath = it },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    label = { Text("Masaüstü ~/.gemini .tar.gz yolu") },
                                )
                                OutlinedButton(
                                    onClick = {
                                        onAction(
                                            WorkspaceAction.ImportDesktopProfile(
                                                effectiveProfileId,
                                                desktopImportPath,
                                            ),
                                        )
                                    },
                                    enabled = desktopImportPath.isNotBlank() && !onboarding.isBusy,
                                    shape = RoundedCornerShape(10.dp),
                                ) {
                                    Text("Aktar")
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SetupHeroReadinessCard(
    readyCount: Int,
    totalSteps: Int,
    isFullyReady: Boolean,
    isDiagnosing: Boolean,
    isAutoFixingAll: Boolean,
    autoFixStageLabel: String?,
    needsSystemAutoFix: Boolean,
    isBusy: Boolean,
    onAutoFixAll: () -> Unit,
) {
    val extColors = LocalAntigravityColors.current
    val colorScheme = MaterialTheme.colorScheme
    val targetFraction = if (totalSteps > 0) (readyCount.toFloat() / totalSteps.toFloat()).coerceIn(0f, 1f) else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = tween(durationMillis = 420),
        label = "setup_readiness_progress",
    )

    val ringColor = if (isFullyReady) extColors.success else colorScheme.primary
    val containerColor = if (isFullyReady) {
        extColors.success.copy(alpha = if (extColors.isDark) 0.12f else 0.08f)
    } else if (extColors.isDark) {
        extColors.cardBackground
    } else {
        colorScheme.surface
    }
    val borderColor = if (isFullyReady) {
        extColors.success.copy(alpha = if (extColors.isDark) 0.50f else 0.40f)
    } else {
        extColors.cardBorder
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier = Modifier.size(68.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 6.dp,
                        color = colorScheme.surfaceVariant,
                        trackColor = Color.Transparent,
                    )
                    CircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 6.dp,
                        color = ringColor,
                        trackColor = Color.Transparent,
                        strokeCap = StrokeCap.Round,
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$readyCount/$totalSteps",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = ringColor,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = if (isFullyReady) "Antigravity Kullanıma Hazır" else "Kurulumu Tamamlayın",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                    )
                    Text(
                        text = when {
                            isAutoFixingAll ->
                                autoFixStageLabel ?: "Eksik sistem bileşenleri otomatik olarak onarılıyor..."
                            isDiagnosing ->
                                "Cihazınızdaki root, izin, chroot ve agy durumu canlı olarak taranıyor..."
                            isFullyReady ->
                                "Root, sistem izinleri, ARM64 chroot, agy CLI ve Google hesabınız doğrulandı."
                            needsSystemAutoFix ->
                                "Eksik sistem ön koşullarını aşağıdaki tek dokunuşlu otomatik onarım ile tamamlayabilirsiniz."
                            else ->
                                "Sistem altyapısı hazır! Son adım olarak Google hesabınızla giriş yapın."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (isAutoFixingAll) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(999.dp)),
                    )
                    if (!autoFixStageLabel.isNullOrBlank()) {
                        Text(
                            text = autoFixStageLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            } else if (needsSystemAutoFix) {
                Button(
                    onClick = onAutoFixAll,
                    enabled = !isBusy && !isDiagnosing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoFixHigh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Tüm Eksikleri Otomatik Düzelt",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupStepCard(
    stepNumber: Int,
    title: String,
    status: StepStatusKind,
    whyNeededText: String,
    content: @Composable () -> Unit,
) {
    val extColors = LocalAntigravityColors.current
    val colorScheme = MaterialTheme.colorScheme
    var showWhyNeeded by remember { mutableStateOf(false) }

    val cardColor = if (extColors.isDark) extColors.cardBackground else colorScheme.surface
    val borderColor = when (status) {
        StepStatusKind.READY -> extColors.success.copy(alpha = if (extColors.isDark) 0.55f else 0.45f)
        StepStatusKind.IN_PROGRESS -> colorScheme.primary.copy(alpha = if (extColors.isDark) 0.55f else 0.45f)
        StepStatusKind.MISSING -> extColors.cardBorder
    }

    val badgeColor = when (status) {
        StepStatusKind.READY -> extColors.success
        StepStatusKind.IN_PROGRESS -> colorScheme.primary
        StepStatusKind.MISSING -> extColors.warning
    }
    val statusLabel = when (status) {
        StepStatusKind.READY -> "Hazır"
        StepStatusKind.IN_PROGRESS -> "Kuruluyor"
        StepStatusKind.MISSING -> "Eksik"
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = cardColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(badgeColor.copy(alpha = if (extColors.isDark) 0.20f else 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    when (status) {
                        StepStatusKind.READY -> {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Hazır",
                                tint = extColors.success,
                                modifier = Modifier.size(18.dp),
                            )
                        }

                        StepStatusKind.IN_PROGRESS -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = colorScheme.primary,
                            )
                        }

                        StepStatusKind.MISSING -> {
                            Text(
                                text = stepNumber.toString(),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = badgeColor,
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface,
                    )
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { showWhyNeeded = !showWhyNeeded }
                            .padding(vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = if (showWhyNeeded) "Açıklamayı gizle" else "Neden gerekli?",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.primary,
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = badgeColor.copy(alpha = if (extColors.isDark) 0.16f else 0.11f),
                    border = BorderStroke(0.75.dp, badgeColor.copy(alpha = 0.45f)),
                ) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            AnimatedVisibility(
                visible = showWhyNeeded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colorScheme.surfaceVariant.copy(alpha = if (extColors.isDark) 0.55f else 0.70f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = whyNeededText,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            content()
        }
    }
}

@Composable
private fun DiagnosticPill(
    label: String,
    ok: Boolean,
    neutral: Boolean = false,
) {
    val extColors = LocalAntigravityColors.current
    val colorScheme = MaterialTheme.colorScheme
    val tint = when {
        neutral -> colorScheme.onSurfaceVariant
        ok -> extColors.success
        else -> extColors.warning
    }
    val prefix = when {
        neutral -> "•"
        ok -> "✓"
        else -> "○"
    }

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = tint.copy(alpha = if (extColors.isDark) 0.14f else 0.10f),
        border = BorderStroke(0.75.dp, tint.copy(alpha = if (neutral) 0.30f else 0.45f)),
    ) {
        Text(
            text = "$prefix $label",
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = if (ok) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun GuidanceWarningBox(
    text: String,
    isCritical: Boolean = false,
) {
    val extColors = LocalAntigravityColors.current
    val colorScheme = MaterialTheme.colorScheme
    val accent = if (isCritical) extColors.critical else extColors.warning
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = accent.copy(alpha = if (extColors.isDark) 0.14f else 0.09f),
        border = BorderStroke(1.dp, accent.copy(alpha = if (extColors.isDark) 0.45f else 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = if (isCritical) Icons.Default.Warning else Icons.Default.Build,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun MicroGuideStepRow(
    number: String,
    text: String,
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(colorScheme.primary.copy(alpha = 0.20f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onPrimaryContainer,
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onPrimaryContainer,
        )
    }
}

private fun canDrawOverlaysSafe(context: Context): Boolean = runCatching {
    Settings.canDrawOverlays(context)
}.getOrDefault(false)

private fun readClipboardTextSafe(context: Context): String? = runCatching {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        ?: return@runCatching null
    val clip = manager.primaryClip ?: return@runCatching null
    if (clip.itemCount <= 0) return@runCatching null
    clip.getItemAt(0).coerceToText(context)?.toString()
}.getOrNull()

private fun looksLikeStrictClipboardOAuth(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return false
    if (trimmed.startsWith("4/") || trimmed.startsWith("4%2F", ignoreCase = true)) return true
    return trimmed.contains("antigravity.google/oauth-callback", ignoreCase = true) ||
        trimmed.startsWith("antigravity://oauth/callback", ignoreCase = true) ||
        trimmed.startsWith("http://127.0.0.1", ignoreCase = true) ||
        trimmed.startsWith("http://localhost", ignoreCase = true)
}
