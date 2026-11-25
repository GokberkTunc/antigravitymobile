package com.antigravity.mobile

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antigravity.mobile.feature.workspace.model.AppDestination
import com.antigravity.mobile.feature.workspace.model.AppThemeMode
import com.antigravity.mobile.feature.workspace.model.WorkspaceAction
import com.antigravity.mobile.feature.workspace.theme.AntigravityTheme
import com.antigravity.mobile.feature.workspace.ui.AntigravityWorkspaceRoute
import com.antigravity.mobile.feature.workspace.viewmodel.WorkspaceViewModel
import com.antigravity.mobile.system.AppBootstrapHelper
import com.antigravity.mobile.system.DeviceAdminHelper
import com.antigravity.mobile.system.OAuthCallbackIntentParser
import com.antigravity.mobile.system.OAuthIntentParseResult
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Edge-to-edge Jetpack Compose host for Antigravity Mobile.
 *
 * Wires:
 * - Dynamic Light/Dark system bar contrast via [enableEdgeToEdge] + [SystemBarStyle]
 * - [AntigravityTheme] & [AntigravityWorkspaceRoute] (Hub, Conversation, Settings, Onboarding flow)
 * - Chrome Custom Tabs OAuth callback intent handling (`onCreate` + `onNewIntent`)
 * - [android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN] activity result launcher
 * - Full privileged system-service registration (`AntigravityAccessibilityService` +
 *   `AntigravityNotificationListenerService`) during onboarding
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val workspaceViewModel: WorkspaceViewModel by viewModels()

    @Inject
    lateinit var deviceAdminHelper: DeviceAdminHelper

    @Inject
    lateinit var appBootstrapHelper: AppBootstrapHelper

    private var isDeviceAdminActive by mutableStateOf(false)
    private var deviceAdminStatusNote by mutableStateOf<String?>(null)

    private val deviceAdminLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val active = deviceAdminHelper.isDeviceAdminActive()
        isDeviceAdminActive = active
        deviceAdminStatusNote = if (active) {
            "Cihaz Yöneticisi (Device Admin) etkinleştirildi."
        } else {
            "Cihaz Yöneticisi etkinleştirilmedi (isteğe bağlı, device_control:lock için gerekir)."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        isDeviceAdminActive = deviceAdminHelper.isDeviceAdminActive()
        handleOAuthCallbackIntent(intent)

        setContent {
            val uiState by workspaceViewModel.uiState.collectAsStateWithLifecycle()
            val systemInDarkTheme = isSystemInDarkTheme()
            val useDarkTheme = when (uiState.settings.themeMode) {
                AppThemeMode.DARK -> true
                AppThemeMode.LIGHT -> false
                AppThemeMode.SYSTEM -> systemInDarkTheme
            }

            DisposableEffect(useDarkTheme) {
                val barStyle = if (useDarkTheme) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(
                    statusBarStyle = barStyle,
                    navigationBarStyle = barStyle,
                )
                onDispose {}
            }

            AntigravityTheme(
                themeMode = uiState.settings.themeMode,
                dynamicColor = uiState.settings.useDynamicColor,
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    AntigravityWorkspaceRoute(
                        modifier = Modifier.weight(1f),
                        viewModel = workspaceViewModel,
                        applyTheme = false,
                    )

                    if (uiState.currentDestination is AppDestination.Onboarding) {
                        OnboardingSystemControlsBar(
                            isDeviceAdminActive = isDeviceAdminActive,
                            statusNote = deviceAdminStatusNote,
                            isBusy = uiState.onboarding.isBusy,
                            onEnableDeviceAdmin = { launchDeviceAdminRequest() },
                            onRegisterSystemServices = {
                                workspaceViewModel.onAction(appBootstrapHelper.createRootBootstrapAction())
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::deviceAdminHelper.isInitialized) {
            isDeviceAdminActive = deviceAdminHelper.isDeviceAdminActive()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOAuthCallbackIntent(intent)
    }

    private fun launchDeviceAdminRequest() {
        val addAdminIntent = deviceAdminHelper.createAddDeviceAdminIntent()
        deviceAdminLauncher.launch(addAdminIntent)
    }

    private fun handleOAuthCallbackIntent(intent: Intent?) {
        val rawUri = intent?.dataString ?: return
        when (val parsed = OAuthCallbackIntentParser.parseUriString(rawUri)) {
            is OAuthIntentParseResult.AuthorizationCode -> {
                val targetProfileId = workspaceViewModel.uiState.value.activeProfile?.id
                    ?: workspaceViewModel.uiState.value.profiles.firstOrNull()?.id
                    ?: return
                workspaceViewModel.onAction(
                    WorkspaceAction.SubmitAuthCode(
                        profileId = targetProfileId,
                        code = parsed.code,
                    ),
                )
            }

            is OAuthIntentParseResult.Error -> {
                deviceAdminStatusNote = parsed.message
            }

            OAuthIntentParseResult.NotOAuthIntent -> Unit
        }
    }
}

@Composable
private fun OnboardingSystemControlsBar(
    isDeviceAdminActive: Boolean,
    statusNote: String?,
    isBusy: Boolean,
    onEnableDeviceAdmin: () -> Unit,
    onRegisterSystemServices: () -> Unit,
) {
    Surface(
        tonalElevation = 0.dp,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!statusNote.isNullOrBlank()) {
                    Text(
                        text = statusNote,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onRegisterSystemServices,
                        enabled = !isBusy,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = "Servisleri Kaydet",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Button(
                        onClick = onEnableDeviceAdmin,
                        enabled = !isDeviceAdminActive,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = if (isDeviceAdminActive) "Device Admin Etkin" else "Device Admin Aç",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}
