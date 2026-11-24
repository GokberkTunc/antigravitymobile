package com.antigravity.mobile.feature.workspace.ui

import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
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
import com.antigravity.mobile.feature.workspace.model.WorkspaceAction
import com.antigravity.mobile.feature.workspace.model.WorkspaceUiState
import com.antigravity.mobile.feature.workspace.theme.AntigravityTheme
import com.antigravity.mobile.feature.workspace.ui.chat.ConversationScreen
import com.antigravity.mobile.feature.workspace.ui.drawer.WorkspaceDrawerSheet
import com.antigravity.mobile.feature.workspace.ui.onboarding.OnboardingScreen
import com.antigravity.mobile.feature.workspace.ui.overview.OverviewBottomSheet
import com.antigravity.mobile.feature.workspace.viewmodel.WorkspaceViewModel
import kotlinx.coroutines.launch

/**
 * Main entry Composable wiring [WorkspaceViewModel] with strict UDF (`collectAsStateWithLifecycle`)
 * and Chrome Custom Tabs OAuth side-effect handling.
 */
@Composable
fun AntigravityWorkspaceRoute(
    modifier: Modifier = Modifier,
    viewModel: WorkspaceViewModel = hiltViewModel(),
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

    AntigravityTheme {
        AntigravityWorkspaceScaffold(
            state = state,
            onAction = viewModel::onAction,
            modifier = modifier,
        )
    }
}

@Composable
fun AntigravityWorkspaceScaffold(
    state: WorkspaceUiState,
    onAction: (WorkspaceAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.showOnboarding) {
        OnboardingScreen(
            state = state,
            onAction = onAction,
            modifier = modifier,
        )
        return
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

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
        ConversationScreen(
            state = state,
            onAction = onAction,
            onOpenDrawer = {
                coroutineScope.launch { drawerState.open() }
            },
        )
    }

    OverviewBottomSheet(
        state = state,
        onAction = onAction,
    )
}
