package com.antigravity.mobile.feature.workspace.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antigravity.mobile.feature.workspace.model.AppDestination
import com.antigravity.mobile.feature.workspace.model.WorkspaceAction
import com.antigravity.mobile.feature.workspace.model.WorkspaceUiState
import com.antigravity.mobile.feature.workspace.theme.AntigravityTheme
import com.antigravity.mobile.feature.workspace.ui.chat.ConversationScreen
import com.antigravity.mobile.feature.workspace.ui.drawer.WorkspaceDrawerSheet
import com.antigravity.mobile.feature.workspace.ui.hub.WorkspaceHubScreen
import com.antigravity.mobile.feature.workspace.ui.onboarding.OnboardingScreen
import com.antigravity.mobile.feature.workspace.ui.overview.OverviewBottomSheet
import com.antigravity.mobile.feature.workspace.ui.settings.QuotaDetailBottomSheet
import com.antigravity.mobile.feature.workspace.ui.settings.SettingsDetailScreen
import com.antigravity.mobile.feature.workspace.ui.settings.SettingsListScreen
import com.antigravity.mobile.feature.workspace.viewmodel.WorkspaceViewModel
import kotlinx.coroutines.launch

/**
 * Main entry Composable wiring [WorkspaceViewModel] with strict UDF (`collectAsStateWithLifecycle`)
 * and Chrome Custom Tabs OAuth side-effect handling.
 *
 * Pass [applyTheme] = `false` when the hosting Activity (such as `MainActivity`) already wraps
 * the composition tree in [AntigravityTheme] to avoid double-wrapping.
 */
@Composable
fun AntigravityWorkspaceRoute(
    modifier: Modifier = Modifier,
    viewModel: WorkspaceViewModel = hiltViewModel(),
    applyTheme: Boolean = true,
    isDeviceAdminActive: Boolean = false,
    deviceAdminStatusNote: String? = null,
    onEnableDeviceAdmin: (() -> Unit)? = null,
    onRegisterSystemServices: (() -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.pendingChromeCustomTabUrl) {
        val authUrl = state.pendingChromeCustomTabUrl ?: return@LaunchedEffect
        val parsedUri = Uri.parse(authUrl)
        val launchedCustomTab = runCatching {
            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
            customTabsIntent.launchUrl(context, parsedUri)
        }.isSuccess
        if (!launchedCustomTab) {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, parsedUri))
            }
        }
        viewModel.onAction(WorkspaceAction.ConsumeChromeCustomTabUrl)
    }

    if (applyTheme) {
        AntigravityTheme(
            themeMode = state.settings.themeMode,
            dynamicColor = state.settings.useDynamicColor,
        ) {
            AntigravityWorkspaceScaffold(
                state = state,
                onAction = viewModel::onAction,
                modifier = modifier,
                isDeviceAdminActive = isDeviceAdminActive,
                deviceAdminStatusNote = deviceAdminStatusNote,
                onEnableDeviceAdmin = onEnableDeviceAdmin,
                onRegisterSystemServices = onRegisterSystemServices,
            )
        }
    } else {
        AntigravityWorkspaceScaffold(
            state = state,
            onAction = viewModel::onAction,
            modifier = modifier,
            isDeviceAdminActive = isDeviceAdminActive,
            deviceAdminStatusNote = deviceAdminStatusNote,
            onEnableDeviceAdmin = onEnableDeviceAdmin,
            onRegisterSystemServices = onRegisterSystemServices,
        )
    }
}

@Composable
fun AntigravityWorkspaceScaffold(
    state: WorkspaceUiState,
    onAction: (WorkspaceAction) -> Unit,
    modifier: Modifier = Modifier,
    isDeviceAdminActive: Boolean = false,
    deviceAdminStatusNote: String? = null,
    onEnableDeviceAdmin: (() -> Unit)? = null,
    onRegisterSystemServices: (() -> Unit)? = null,
) {
    val context = LocalContext.current

    LaunchedEffect(state.shouldExitApp) {
        if (state.shouldExitApp) {
            context.findActivity()?.finish()
            onAction(WorkspaceAction.ConsumeExitApp)
        }
    }

    // Unified deterministic hardware Back handler. Any inner BackHandler registered after this
    // (such as closing an open navigation drawer) intercepts Back first via LIFO ordering.
    BackHandler(enabled = !state.shouldExitApp) {
        onAction(WorkspaceAction.NavigateBack)
    }

    when (val dest = state.currentDestination) {
        AppDestination.Hub,
        is AppDestination.Conversation,
        -> {
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            val coroutineScope = rememberCoroutineScope()

            LaunchedEffect(state.currentDestination) {
                if (drawerState.isOpen || drawerState.targetValue == DrawerValue.Open) {
                    drawerState.close()
                }
            }

            BackHandler(enabled = drawerState.isOpen) {
                coroutineScope.launch { drawerState.close() }
            }

            ModalNavigationDrawer(
                modifier = modifier,
                drawerState = drawerState,
                drawerContent = {
                    WorkspaceDrawerSheet(
                        state = state,
                        onAction = onAction,
                        onCloseDrawer = {
                            coroutineScope.launch { drawerState.close() }
                        },
                    )
                },
            ) {
                AnimatedContent(
                    targetState = dest is AppDestination.Conversation,
                    transitionSpec = {
                        if (targetState) {
                            (fadeIn(tween(180)) + slideInHorizontally(tween(200)) { it / 10 }) togetherWith
                                (fadeOut(tween(140)) + slideOutHorizontally(tween(180)) { -it / 14 })
                        } else {
                            (fadeIn(tween(180)) + slideInHorizontally(tween(200)) { -it / 10 }) togetherWith
                                (fadeOut(tween(140)) + slideOutHorizontally(tween(180)) { it / 14 })
                        }
                    },
                    label = "workspace_hub_conversation_transition",
                ) { inConversation ->
                    if (inConversation) {
                        ConversationScreen(
                            state = state,
                            onAction = onAction,
                            onOpenDrawer = {
                                coroutineScope.launch { drawerState.open() }
                            },
                        )
                    } else {
                        WorkspaceHubScreen(
                            state = state,
                            onAction = onAction,
                            onOpenDrawer = {
                                coroutineScope.launch { drawerState.open() }
                            },
                        )
                    }
                }
            }

            OverviewBottomSheet(
                state = state,
                onAction = onAction,
            )

            QuotaDetailBottomSheet(
                state = state,
                onAction = onAction,
            )
        }

        AppDestination.SettingsList -> {
            SettingsListScreen(
                state = state,
                onAction = onAction,
                modifier = modifier,
            )
        }

        is AppDestination.SettingsDetail -> {
            SettingsDetailScreen(
                category = dest.category,
                state = state,
                onAction = onAction,
                modifier = modifier,
            )
        }

        AppDestination.Onboarding -> {
            OnboardingScreen(
                state = state,
                onAction = onAction,
                modifier = modifier,
                isDeviceAdminActive = isDeviceAdminActive,
                deviceAdminStatusNote = deviceAdminStatusNote,
                onEnableDeviceAdmin = onEnableDeviceAdmin,
                onRegisterSystemServices = onRegisterSystemServices,
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
