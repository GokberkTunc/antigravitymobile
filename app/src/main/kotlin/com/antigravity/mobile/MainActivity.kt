package com.antigravity.mobile

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antigravity.mobile.contracts.AuthFlowState
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
 * - Live self-healing diagnostics on launch and Android permission sync on resume/focus
 * - Chrome Custom Tabs OAuth callback intent handling (`onCreate` + `onNewIntent`) and
 *   zero-friction clipboard OAuth code detection on returning focus
 * - [android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN] activity result launcher
 * - Full privileged system-service registration (`AntigravityAccessibilityService` +
 *   `AntigravityNotificationListenerService`) inside Step 2 of OnboardingScreen
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
        syncAndroidSetupStatus()
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

            LaunchedEffect(Unit) {
                workspaceViewModel.onAction(
                    WorkspaceAction.RunFullDiagnostics(
                        packageName = packageName,
                        isOverlayGranted = Settings.canDrawOverlays(this@MainActivity),
                        isDeviceAdminActive = isDeviceAdminActive,
                    ),
                )
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
                AntigravityWorkspaceRoute(
                    modifier = Modifier.fillMaxSize(),
                    viewModel = workspaceViewModel,
                    applyTheme = false,
                    isDeviceAdminActive = isDeviceAdminActive,
                    deviceAdminStatusNote = deviceAdminStatusNote,
                    onEnableDeviceAdmin = { launchDeviceAdminRequest() },
                    onRegisterSystemServices = {
                        workspaceViewModel.onAction(appBootstrapHelper.createRootBootstrapAction())
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        syncAndroidSetupStatus()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        syncAndroidSetupStatus()
        if (workspaceViewModel.uiState.value.authFlowState is AuthFlowState.AwaitingBrowserAuth) {
            val clipboardText = readPrimaryClipboardText()
            if (!clipboardText.isNullOrBlank()) {
                workspaceViewModel.onAction(
                    WorkspaceAction.HandleClipboardAuthText(
                        rawText = clipboardText,
                        autoSubmit = true,
                    ),
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOAuthCallbackIntent(intent)
    }

    private fun syncAndroidSetupStatus() {
        val adminActive = if (::deviceAdminHelper.isInitialized) {
            deviceAdminHelper.isDeviceAdminActive().also { isDeviceAdminActive = it }
        } else {
            isDeviceAdminActive
        }
        val overlayGranted = runCatching { Settings.canDrawOverlays(this) }.getOrDefault(false)
        workspaceViewModel.onAction(
            WorkspaceAction.SyncAndroidSetupStatus(
                isOverlayGranted = overlayGranted,
                isDeviceAdminActive = adminActive,
            ),
        )
    }

    private fun readPrimaryClipboardText(): String? = runCatching {
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return@runCatching null
        val clip = clipboardManager.primaryClip ?: return@runCatching null
        if (clip.itemCount <= 0) return@runCatching null
        clip.getItemAt(0).coerceToText(this)?.toString()
    }.getOrNull()

    private fun launchDeviceAdminRequest() {
        val addAdminIntent = deviceAdminHelper.createAddDeviceAdminIntent()
        deviceAdminLauncher.launch(addAdminIntent)
    }

    private fun handleOAuthCallbackIntent(intent: Intent?) {
        val rawUri = intent?.dataString ?: return
        val awaiting = workspaceViewModel.uiState.value.authFlowState
            as? com.antigravity.mobile.contracts.AuthFlowState.AwaitingBrowserAuth
            ?: return
        when (val parsed = OAuthCallbackIntentParser.parseUriString(rawUri)) {
            is OAuthIntentParseResult.AuthorizationCode -> {
                val expectedState = runCatching {
                    val query = java.net.URI(awaiting.challenge.authUrl).rawQuery.orEmpty()
                    query.split('&')
                        .firstOrNull { it.startsWith("state=") }
                        ?.substringAfter("state=")
                        ?.let { java.net.URLDecoder.decode(it, Charsets.UTF_8.name()) }
                        ?.takeIf { it.isNotBlank() }
                }.getOrNull()
                if (expectedState != null && parsed.state != expectedState) {
                    return
                }
                workspaceViewModel.onAction(
                    WorkspaceAction.SubmitAuthCode(
                        profileId = awaiting.challenge.profileId,
                        code = parsed.code,
                    ),
                )
            }

            is OAuthIntentParseResult.Error -> Unit
            OAuthIntentParseResult.NotOAuthIntent -> Unit
        }
    }
}
