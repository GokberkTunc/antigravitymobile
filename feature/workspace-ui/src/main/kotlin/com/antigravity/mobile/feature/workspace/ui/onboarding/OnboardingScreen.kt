package com.antigravity.mobile.feature.workspace.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antigravity.mobile.contracts.AuthFlowState
import com.antigravity.mobile.contracts.ChrootState
import com.antigravity.mobile.contracts.RootProviderType
import com.antigravity.mobile.feature.workspace.model.RootfsSourceOption
import com.antigravity.mobile.feature.workspace.model.WorkspaceAction
import com.antigravity.mobile.feature.workspace.model.WorkspaceUiState
import com.antigravity.mobile.feature.workspace.theme.AntigravityPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    state: WorkspaceUiState,
    onAction: (WorkspaceAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val onboarding = state.onboarding

    var profileName by remember { mutableStateOf("Ana Google Hesabım") }
    var manualAuthCode by remember { mutableStateOf("") }
    var desktopImportPath by remember { mutableStateOf("/sdcard/Download/gemini-profile.tar.gz") }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                title = {
                    Column {
                        Text(
                            text = "Antigravity Kurulum Sihirbazı",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "KernelSU / Magisk • ARM64 Glibc Chroot • Google OAuth",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (onboarding.statusMessage != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = onboarding.statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            val visibleError = onboarding.errorMessage ?: state.errorMessage
            if (visibleError != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = visibleError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            // Step 1: Root Detection (KernelSU / Magisk / APatch)
            WizardStepCard(
                stepNumber = 1,
                title = "Root Ortamı (KernelSU / Magisk)",
                icon = Icons.Default.AdminPanelSettings,
                isDone = onboarding.rootEnvironment?.isRootGranted == true,
            ) {
                val env = onboarding.rootEnvironment
                if (env == null) {
                    Text(
                        text = "Cihazın root sağlayıcısı (KernelSU / Magisk / APatch) ve FLAG_MOUNT_MASTER desteği denetlenir.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = "Sağlayıcı: ${env.provider.name} • Durum: ${if (env.isRootGranted) "Yetkili" else "Yetki Bekleniyor"}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (env.isRootGranted) AntigravityPalette.SuccessGreen else AntigravityPalette.WarningAmber,
                    )
                    if (!env.isRootGranted || env.provider == RootProviderType.KERNEL_SU) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF282012),
                            border = BorderStroke(1.dp, AntigravityPalette.WarningAmber.copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = AntigravityPalette.WarningAmber,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = env.guidanceMessage.ifBlank {
                                        "KernelSU kullanıyorsanız KernelSU Manager uygulamasını açın → Süper Kullanıcı sekmesinden Antigravity Mobile için root yetkisini etkinleştirin."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
                Button(
                    onClick = { onAction(WorkspaceAction.CheckRootStatus) },
                    enabled = !onboarding.isBusy,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("Root Durumunu Denetle")
                }
            }

            // Step 2: One-Tap Root Permission Bootstrap
            WizardStepCard(
                stepNumber = 2,
                title = "Tek Dokunuşla İzin Otomasyonu",
                icon = Icons.Default.Security,
                isDone = onboarding.bootstrapReport?.allCriticalReady == true,
            ) {
                Text(
                    text = "Runtime izinleri, ACCESS_RESTRICTED_SETTINGS (Android 13-17), SYSTEM_ALERT_WINDOW, AccessibilityService ve Doze muafiyetini otomatik tanımlar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                onboarding.bootstrapReport?.let { report ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        ChecklistLine("Restricted Settings (AppOps)", report.restrictedSettingsGranted)
                        ChecklistLine("AccessibilityService Etkin", report.accessibilityEnabled)
                        ChecklistLine("NotificationListener Etkin", report.notificationListenerEnabled)
                        ChecklistLine("Pil / Doze Muafiyeti", report.batteryOptimizationExempted)
                    }
                }
                Button(
                    onClick = {
                        onAction(
                            WorkspaceAction.RunRootBootstrap(
                                packageName = context.packageName,
                            ),
                        )
                    },
                    enabled = !onboarding.isBusy,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("İzinleri Otomatik Yapılandır")
                }
            }

            // Step 3: ARM64 Glibc Chroot & agy CLI
            WizardStepCard(
                stepNumber = 3,
                title = "ARM64 Glibc Rootfs & agy CLI",
                icon = Icons.Default.Memory,
                isDone = onboarding.chrootState is ChrootState.Mounted && !onboarding.agyVersion.isNullOrBlank(),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = onboarding.selectedRootfsSource == RootfsSourceOption.BUNDLED_ASSET,
                        onClick = { onAction(WorkspaceAction.SelectRootfsSource(RootfsSourceOption.BUNDLED_ASSET)) },
                        label = { Text("APK Asset") },
                    )
                    FilterChip(
                        selected = onboarding.selectedRootfsSource == RootfsSourceOption.REMOTE_DOWNLOAD,
                        onClick = { onAction(WorkspaceAction.SelectRootfsSource(RootfsSourceOption.REMOTE_DOWNLOAD)) },
                        label = { Text("İndir") },
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
                            text = "Çevrimdışı APK içi assets/rootfs/arm64-rootfs.tar.gz arşivini `/data/local/ag_rootfs` dizinine çıkarır.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    RootfsSourceOption.REMOTE_DOWNLOAD -> {
                        OutlinedTextField(
                            value = onboarding.remoteRootfsUrl,
                            onValueChange = { onAction(WorkspaceAction.UpdateRemoteRootfsUrl(it)) },
                            label = { Text("ARM64 Rootfs URL") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                    }

                    RootfsSourceOption.LOCAL_FILE -> {
                        OutlinedTextField(
                            value = onboarding.localRootfsPath,
                            onValueChange = { onAction(WorkspaceAction.UpdateLocalRootfsPath(it)) },
                            label = { Text("Yerel .tar.gz yolu") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                    }
                }

                when (val cs = onboarding.chrootState) {
                    is ChrootState.Installing -> {
                        Text(
                            text = cs.stage,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        val pct = cs.progressPercent
                        if (pct != null) {
                            LinearProgressIndicator(
                                progress = { (pct / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }

                    is ChrootState.Mounted -> {
                        Text(
                            text = "Chroot bağlı (${cs.rootfsPath}) • Çekirdek mount: ${if (cs.mountStatus.allCoreMounted) "Tamam" else "Kısmi"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = AntigravityPalette.SuccessGreen,
                        )
                    }

                    is ChrootState.Error -> {
                        Text(
                            text = "Chroot hatası: ${cs.message}",
                            style = MaterialTheme.typography.bodySmall,
                            color = AntigravityPalette.CriticalRed,
                        )
                    }

                    else -> Unit
                }

                if (!onboarding.agyVersion.isNullOrBlank()) {
                    Text(
                        text = "agy sürümü doğrulandı: ${onboarding.agyVersion}",
                        style = MaterialTheme.typography.labelMedium,
                        color = AntigravityPalette.SuccessGreen,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onAction(WorkspaceAction.SetupChroot) },
                        enabled = !onboarding.isBusy,
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("Rootfs Kur & Bağla")
                    }
                    OutlinedButton(
                        onClick = { onAction(WorkspaceAction.CheckOrInstallAgy) },
                        enabled = !onboarding.isBusy,
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("agy Kur / Denetle")
                    }
                }
            }

            // Step 4: First Google Account Sign-In
            WizardStepCard(
                stepNumber = 4,
                title = "İlk Google Hesabı Girişi",
                icon = Icons.Default.AccountCircle,
                isDone = state.activeProfile?.isAuthenticated == true,
            ) {
                if (state.activeProfile == null) {
                    OutlinedTextField(
                        value = profileName,
                        onValueChange = { profileName = it },
                        label = { Text("Profil Adı") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Button(
                        onClick = { onAction(WorkspaceAction.CreateProfile(profileName, null)) },
                        enabled = profileName.isNotBlank(),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("İzole Profil Oluştur")
                    }
                } else {
                    val active = state.activeProfile
                    val authState = state.authFlowState
                    Text(
                        text = "Aktif Profil: ${active.displayName} (${if (active.isAuthenticated) "Doğrulandı" else "Giriş Bekleniyor"})",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Button(
                        onClick = { onAction(WorkspaceAction.StartOAuthLogin(active.id, null)) },
                        enabled = authState !is AuthFlowState.Starting && !onboarding.isBusy,
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        if (authState is AuthFlowState.Starting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Chrome Custom Tabs ile Google Girişi")
                    }

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
                                    text = "Google OAuth URL hazırlanıyor...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        is AuthFlowState.AwaitingBrowserAuth -> {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = authState.challenge.instructions.ifBlank {
                                            "Chrome Custom Tabs üzerinden Google hesabınızla giriş yapın; yönlendirilen sayfadaki kodu veya tam URL'yi aşağıya yapıştırıp Onayla'ya basın."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            onAction(
                                                WorkspaceAction.StartOAuthLogin(
                                                    active.id,
                                                    authState.challenge.authUrl,
                                                ),
                                            )
                                        },
                                        enabled = !onboarding.isBusy,
                                        shape = RoundedCornerShape(8.dp),
                                    ) {
                                        Text("Tarayıcıda Tekrar Aç")
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
                                    text = "Doğrulama kodu onaylanıyor...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        is AuthFlowState.Authenticated -> {
                            Text(
                                text = "Doğrulandı: ${authState.email ?: active.displayName}",
                                style = MaterialTheme.typography.labelMedium,
                                color = AntigravityPalette.SuccessGreen,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }

                        is AuthFlowState.Failed -> {
                            Text(
                                text = authState.reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = AntigravityPalette.CriticalRed,
                            )
                        }

                        AuthFlowState.Idle -> Unit
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        OutlinedTextField(
                            value = manualAuthCode,
                            onValueChange = { manualAuthCode = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("Manuel doğrulama kodu") },
                        )
                        OutlinedButton(
                            onClick = {
                                onAction(WorkspaceAction.SubmitAuthCode(active.id, manualAuthCode))
                                manualAuthCode = ""
                            },
                            enabled = manualAuthCode.isNotBlank(),
                        ) {
                            Text("Onayla")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        OutlinedTextField(
                            value = desktopImportPath,
                            onValueChange = { desktopImportPath = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("Masaüstü ~/.gemini arşivi") },
                        )
                        OutlinedButton(
                            onClick = {
                                onAction(WorkspaceAction.ImportDesktopProfile(active.id, desktopImportPath))
                            },
                            enabled = desktopImportPath.isNotBlank(),
                        ) {
                            Text("İçe Aktar")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Button(
                onClick = { onAction(WorkspaceAction.CompleteOnboarding) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                if (onboarding.isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = "Çalışma Alanına Geç",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun WizardStepCard(
    stepNumber: Int,
    title: String,
    icon: ImageVector,
    isDone: Boolean,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (isDone) AntigravityPalette.SuccessGreen.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outline,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isDone) AntigravityPalette.SuccessGreen else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "$stepNumber. $title",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (isDone) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Tamamlandı",
                        tint = AntigravityPalette.SuccessGreen,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun ChecklistLine(label: String, passed: Boolean) {
    Text(
        text = "${if (passed) "✓" else "○"} $label",
        style = MaterialTheme.typography.labelMedium,
        color = if (passed) AntigravityPalette.SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
