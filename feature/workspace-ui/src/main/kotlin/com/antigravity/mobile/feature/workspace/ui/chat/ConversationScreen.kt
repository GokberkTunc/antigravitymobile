package com.antigravity.mobile.feature.workspace.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.mobile.contracts.AccountPlanTier
import com.antigravity.mobile.contracts.AcpEvent
import com.antigravity.mobile.contracts.AcpModel
import com.antigravity.mobile.contracts.AcpSessionState
import com.antigravity.mobile.contracts.ChatMessageRole
import com.antigravity.mobile.contracts.DiffApprovalStatus
import com.antigravity.mobile.contracts.PermissionMode
import com.antigravity.mobile.contracts.ThinkingEffort
import com.antigravity.mobile.contracts.ToolCallStatus
import com.antigravity.mobile.contracts.ToolCallSummary
import com.antigravity.mobile.contracts.VoiceState
import com.antigravity.mobile.contracts.WorkspaceChatMessage
import com.antigravity.mobile.feature.workspace.model.AuthBannerState
import com.antigravity.mobile.feature.workspace.model.InterfaceLayoutMode
import com.antigravity.mobile.feature.workspace.model.OverviewTab
import com.antigravity.mobile.feature.workspace.model.QuotaWarningState
import com.antigravity.mobile.feature.workspace.model.WorkspaceAction
import com.antigravity.mobile.feature.workspace.model.WorkspaceUiState
import com.antigravity.mobile.feature.workspace.theme.AntigravityPalette
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val TimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    state: WorkspaceUiState,
    onAction: (WorkspaceAction) -> Unit,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    var showAttachmentDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.errorMessage) {
        val message = state.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onAction(WorkspaceAction.DismissError)
    }

    val lastMessage = state.messages.lastOrNull()
    LaunchedEffect(
        state.messages.size,
        lastMessage?.content?.length,
        lastMessage?.toolCalls?.size,
        state.settings.autoScrollChat,
    ) {
        if (state.settings.autoScrollChat && state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    val overviewBadgeCount = state.subagents.size +
        state.diffs.count { it.status == DiffApprovalStatus.PENDING }
    val isStreaming = state.sessionState == AcpSessionState.STREAMING ||
        state.messages.any { it.isStreaming }
    val activeTitle = state.activeConversation?.title?.takeIf { it.isNotBlank() }
        ?: state.activeProject.name
    val isCompact = state.settings.layoutMode == InterfaceLayoutMode.COMPACT_MOBILE

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    navigationIcon = {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Menü",
                            )
                        }
                    },
                    title = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = activeTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                SessionStatusDot(state.sessionState)
                                Text(
                                    text = "${state.activeProject.name} • ${state.selectedModel.displayName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    },
                    actions = {
                        TopBarQuotaPill(
                            remainingPercent = state.activeProfile?.quota?.remainingPercent,
                            isRefreshing = state.isRefreshingQuota,
                            onClick = { onAction(WorkspaceAction.OpenQuotaSheet) },
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        IconButton(
                            onClick = {
                                onAction(WorkspaceAction.CreateConversation(state.activeProject.id))
                            },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Yeni Sohbet",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        IconButton(
                            onClick = { onAction(WorkspaceAction.OpenOverviewSheet(OverviewTab.SUBAGENTS)) },
                            modifier = Modifier.size(36.dp),
                        ) {
                            BadgedBox(
                                badge = {
                                    if (overviewBadgeCount > 0) {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary,
                                        ) {
                                            Text(
                                                text = overviewBadgeCount.toString(),
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            )
                                        }
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Hub,
                                    contentDescription = "Alt ajanlar, Swarm ve Diffs",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        if (isStreaming) {
                            Spacer(modifier = Modifier.width(2.dp))
                            EmergencyStopButton(
                                onClick = { onAction(WorkspaceAction.TriggerEmergencyStop) },
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    },
                )
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                )
            }
        },
        bottomBar = {
            WorkspaceComposer(
                state = state,
                onAction = onAction,
                onOpenAttachmentDialog = { showAttachmentDialog = true },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // -32000 AuthRequired Banner
            state.authBanner?.let { banner ->
                AuthRequiredBanner(
                    banner = banner,
                    activeProfileId = state.activeProfile?.id ?: banner.profileId ?: "default",
                    onLaunchOAuth = { profileId, url ->
                        onAction(WorkspaceAction.StartOAuthLogin(profileId, url))
                    },
                    onSubmitCode = { profileId, code ->
                        onAction(WorkspaceAction.SubmitAuthCode(profileId, code))
                    },
                    onDismiss = { onAction(WorkspaceAction.DismissAuthBanner) },
                )
            }

            // Quota Near-Limit Banner
            state.quotaWarning?.let { warning ->
                QuotaNearLimitBanner(
                    warning = warning,
                    onSwitchFallback = { fallbackId ->
                        onAction(WorkspaceAction.SwitchProfile(fallbackId))
                    },
                    onDismiss = { onAction(WorkspaceAction.DismissQuotaWarning) },
                )
            }

            // Inline ACP Permission Request Card
            state.pendingPermissionRequest?.let { req ->
                InlinePermissionCard(
                    request = req,
                    onApprove = { remember ->
                        onAction(
                            WorkspaceAction.RespondToPermission(
                                requestId = req.requestId,
                                approved = true,
                                rememberForSession = remember,
                            ),
                        )
                    },
                    onDeny = {
                        onAction(
                            WorkspaceAction.RespondToPermission(
                                requestId = req.requestId,
                                approved = false,
                            ),
                        )
                    },
                )
            }

            // Streaming Conversation Canvas or Empty Hero
            if (state.messages.isEmpty()) {
                EmptyWorkspaceWelcome(
                    state = state,
                    onAction = onAction,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(
                        horizontal = if (isCompact) 12.dp else 16.dp,
                        vertical = if (isCompact) 10.dp else 14.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(if (isCompact) 12.dp else 16.dp),
                ) {
                    items(state.messages, key = { it.id }) { message ->
                        ChatMessageItem(
                            message = message,
                            showCodeLineNumbers = state.settings.showCodeLineNumbers,
                            showThoughtBlocksExpanded = state.settings.showThoughtBlocksExpanded,
                        )
                    }
                }
            }
        }
    }

    if (showAttachmentDialog) {
        QuickAttachmentDialog(
            onDismiss = { showAttachmentDialog = false },
            onAdd = { path ->
                onAction(WorkspaceAction.AddAttachment(path))
                showAttachmentDialog = false
            },
        )
    }
}

private fun quotaStatusColor(remainingPercent: Double?): Color = when {
    remainingPercent == null -> AntigravityPalette.NeonCyan
    remainingPercent > 40.0 -> AntigravityPalette.SuccessGreen
    remainingPercent > 15.0 -> AntigravityPalette.WarningAmber
    else -> AntigravityPalette.CriticalRed
}

@Composable
private fun TopBarQuotaPill(
    remainingPercent: Double?,
    isRefreshing: Boolean,
    onClick: () -> Unit,
) {
    val accentColor = quotaStatusColor(remainingPercent)
    val label = remainingPercent?.let { "%${it.roundToInt().coerceIn(0, 100)}" } ?: "Kota"

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.42f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(10.dp),
                    strokeWidth = 1.5.dp,
                    color = accentColor,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(accentColor),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun EmergencyStopButton(
    onClick: () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "emergency-stop-glow")
    val glowAlpha by transition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.42f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow-alpha",
    )

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = AntigravityPalette.CriticalRed.copy(alpha = glowAlpha),
        border = BorderStroke(1.dp, AntigravityPalette.CriticalRed.copy(alpha = 0.75f)),
        modifier = Modifier.size(34.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.StopCircle,
                contentDescription = "Acil Durdur",
                tint = AntigravityPalette.CriticalRed,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SessionStatusDot(state: AcpSessionState) {
    val color = when (state) {
        AcpSessionState.READY -> AntigravityPalette.SuccessGreen
        AcpSessionState.STREAMING,
        AcpSessionState.CONNECTING,
        AcpSessionState.INITIALIZING,
        -> AntigravityPalette.NeonCyan

        AcpSessionState.AWAITING_PERMISSION,
        AcpSessionState.AWAITING_AUTH,
        -> AntigravityPalette.WarningAmber

        AcpSessionState.ERROR -> AntigravityPalette.CriticalRed
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val isAnimated = state == AcpSessionState.STREAMING ||
        state == AcpSessionState.CONNECTING ||
        state == AcpSessionState.INITIALIZING

    val dotAlpha = if (isAnimated) {
        val transition = rememberInfiniteTransition(label = "status-dot-pulse")
        val animated by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 750, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "dot-alpha",
        )
        animated
    } else {
        1f
    }

    Box(
        modifier = Modifier
            .size(7.dp)
            .alpha(dotAlpha)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun AuthRequiredBanner(
    banner: AuthBannerState,
    activeProfileId: String,
    onLaunchOAuth: (profileId: String, authUrl: String?) -> Unit,
    onSubmitCode: (profileId: String, code: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var manualCode by remember { mutableStateOf("") }
    var showCodeField by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, AntigravityPalette.CriticalRed.copy(alpha = 0.55f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = AntigravityPalette.WarningAmber,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Kimlik Doğrulama Gerekli (${banner.errorCode})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Kapat",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Text(
                text = banner.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onLaunchOAuth(activeProfileId, banner.authUrl) },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text("Google ile Giriş Yap", fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = { showCodeField = !showCodeField },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.4f)),
                ) {
                    Text("Manuel Kod", color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            AnimatedVisibility(visible = showCodeField) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    OutlinedTextField(
                        value = manualCode,
                        onValueChange = { manualCode = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        placeholder = { Text("OAuth doğrulama kodu") },
                    )
                    Button(
                        onClick = {
                            onSubmitCode(activeProfileId, manualCode)
                            manualCode = ""
                        },
                        enabled = manualCode.isNotBlank(),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("Gönder")
                    }
                }
            }
        }
    }
}

@Composable
private fun QuotaNearLimitBanner(
    warning: QuotaWarningState,
    onSwitchFallback: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        color = Color(0xFF231C0F),
        border = BorderStroke(1.dp, AntigravityPalette.WarningAmber.copy(alpha = 0.65f)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Kota Limite Yaklaşıyor (%.0f%% kaldı)".format(warning.remainingPercent),
                    style = MaterialTheme.typography.labelLarge,
                    color = AntigravityPalette.WarningAmber,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = warning.suggestedFallbackProfile?.let {
                        "Tek dokunuşla yedek hesaba geçin: ${it.displayName}"
                    } ?: "Yeni Google profili ekleyerek kesintisiz devam edebilirsiniz.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AntigravityPalette.TextSecondary,
                )
            }
            warning.suggestedFallbackProfile?.let { fallback ->
                TextButton(onClick = { onSwitchFallback(fallback.id) }) {
                    Text("Geç", fontWeight = FontWeight.Bold, color = AntigravityPalette.WarningAmber)
                }
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Kapat",
                    tint = AntigravityPalette.TextSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun InlinePermissionCard(
    request: AcpEvent.PermissionRequest,
    onApprove: (remember: Boolean) -> Unit,
    onDeny: () -> Unit,
) {
    Surface(
        color = AntigravityPalette.SurfaceElevated,
        border = BorderStroke(1.dp, AntigravityPalette.WarningAmber.copy(alpha = 0.7f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = AntigravityPalette.WarningAmber.copy(alpha = 0.16f),
                    modifier = Modifier.size(28.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = null,
                            tint = AntigravityPalette.WarningAmber,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Araç İzni • ${request.toolName}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = AntigravityPalette.TextPrimary,
                    )
                    Text(
                        text = "Risk: ${request.riskLevel.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = AntigravityPalette.WarningAmber,
                    )
                }
            }
            Text(
                text = request.description,
                style = MaterialTheme.typography.bodySmall,
                color = AntigravityPalette.TextSecondary,
            )
            if (request.argumentsJson.isNotBlank() && request.argumentsJson != "{}") {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AntigravityPalette.CodeBlockBg,
                    border = BorderStroke(1.dp, AntigravityPalette.BorderHairline),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = request.argumentsJson,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = AntigravityPalette.TextSecondary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onDeny,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, AntigravityPalette.CriticalRed.copy(alpha = 0.7f)),
                ) {
                    Text("Reddet", color = AntigravityPalette.CriticalRed, fontWeight = FontWeight.Medium)
                }
                Button(
                    onClick = { onApprove(false) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AntigravityPalette.SuccessGreen),
                ) {
                    Text("İzin Ver", color = Color(0xFF042214), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private data class QuickSuggestionItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accentColor: Color,
    val onClick: () -> Unit,
)

@Composable
private fun EmptyWorkspaceWelcome(
    state: WorkspaceUiState,
    onAction: (WorkspaceAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val suggestions = remember(onAction) {
        listOf(
            QuickSuggestionItem(
                title = "Cihaz & Sistem Özeti",
                subtitle = "Pil, depolama, aktif servisler ve sistem durumunu analiz et",
                icon = Icons.Default.Memory,
                accentColor = AntigravityPalette.NeonCyan,
                onClick = {
                    onAction(
                        WorkspaceAction.SendMessage(
                            "Pil, depolama, aktif servisler ve sistem durumunu analiz et",
                        ),
                    )
                },
            ),
            QuickSuggestionItem(
                title = "Proje Dosyalarını Tara",
                subtitle = "Aktif proje dizinindeki dosyaları listele ve özetle",
                icon = Icons.Default.FolderOpen,
                accentColor = AntigravityPalette.CarbonTeal,
                onClick = {
                    onAction(
                        WorkspaceAction.SendMessage(
                            "Aktif proje dizinindeki dosyaları listele ve özetle",
                        ),
                    )
                },
            ),
            QuickSuggestionItem(
                title = "Otomasyon Oluştur",
                subtitle = "/schedule ile zamanlı veya periyodik görev tanımla",
                icon = Icons.Default.Schedule,
                accentColor = AntigravityPalette.HydrogenViolet,
                onClick = { onAction(WorkspaceAction.UpdateComposerText("/schedule ")) },
            ),
            QuickSuggestionItem(
                title = "Swarm Görevi Başlat",
                subtitle = "/swarm ile çoklu ajan görev dağılımı başlat",
                icon = Icons.Default.Hub,
                accentColor = AntigravityPalette.SuccessGreen,
                onClick = { onAction(WorkspaceAction.UpdateComposerText("/swarm ")) },
            ),
        )
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Glowing cyan/indigo subtle gradient orb badge
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(84.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                AntigravityPalette.NeonCyan.copy(alpha = 0.28f),
                                AntigravityPalette.HydrogenViolet.copy(alpha = 0.14f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            AntigravityPalette.NeonCyan.copy(alpha = 0.7f),
                            AntigravityPalette.HydrogenViolet.copy(alpha = 0.65f),
                        ),
                    ),
                ),
                modifier = Modifier.size(56.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.background(
                        Brush.linearGradient(
                            colors = listOf(
                                AntigravityPalette.NeonCyan.copy(alpha = 0.18f),
                                AntigravityPalette.HydrogenViolet.copy(alpha = 0.16f),
                            ),
                        ),
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = AntigravityPalette.NeonCyan,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Antigravity Mobile",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = state.activeProject.rootPath,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Active Model & Effort Pill
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = AntigravityPalette.HydrogenViolet,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = "${state.selectedModel.displayName} • ${state.selectedEffort.name}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Compact Visual Quota Status Strip
        HeroQuotaStatusStrip(
            profileName = state.activeProfile?.displayName ?: "Google Hesabı",
            planTier = state.activeProfile?.quota?.planTier ?: AccountPlanTier.UNKNOWN,
            remainingPercent = state.activeProfile?.quota?.remainingPercent,
            isRefreshing = state.isRefreshingQuota,
            onClick = { onAction(WorkspaceAction.OpenQuotaSheet) },
        )

        Spacer(modifier = Modifier.height(22.dp))

        // 2x2 Quick-Action Suggestion Cards
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            suggestions.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rowItems.forEach { item ->
                        QuickSuggestionCard(
                            item = item,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroQuotaStatusStrip(
    profileName: String,
    planTier: AccountPlanTier,
    remainingPercent: Double?,
    isRefreshing: Boolean,
    onClick: () -> Unit,
) {
    val accentColor = quotaStatusColor(remainingPercent)
    val fraction = ((remainingPercent ?: 100.0) / 100.0).toFloat().coerceIn(0f, 1f)
    val percentText = remainingPercent?.let { "%${it.roundToInt().coerceIn(0, 100)} kalan" } ?: "Kotayı Görüntüle"
    val tierSuffix = if (planTier != AccountPlanTier.UNKNOWN) " • ${planTier.name}" else ""

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 380.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(11.dp),
                            strokeWidth = 1.6.dp,
                            color = accentColor,
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(accentColor),
                        )
                    }
                    Text(
                        text = "$profileName$tierSuffix",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = percentText,
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (remainingPercent != null) fraction else 0.18f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(accentColor),
                )
            }
        }
    }
}

@Composable
private fun QuickSuggestionCard(
    item: QuickSuggestionItem,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = item.onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = item.accentColor.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, item.accentColor.copy(alpha = 0.35f)),
                modifier = Modifier.size(32.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = item.accentColor,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatMessageItem(
    message: WorkspaceChatMessage,
    showCodeLineNumbers: Boolean,
    showThoughtBlocksExpanded: Boolean,
) {
    val formattedTime = remember(message.timestampEpochMs) {
        formatMessageTimestamp(message.timestampEpochMs)
    }

    when (message.role) {
        ChatMessageRole.USER -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)),
                    modifier = Modifier.widthIn(max = 320.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (message.attachments.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                message.attachments.forEach { att ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                                        border = BorderStroke(
                                            1.dp,
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                        ),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Icon(
                                                Icons.Default.AttachFile,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(12.dp),
                                            )
                                            Text(
                                                text = att,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        if (formattedTime != null) {
                            Text(
                                text = formattedTime,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f),
                                modifier = Modifier.align(Alignment.End),
                            )
                        }
                    }
                }
            }
        }

        ChatMessageRole.SYSTEM -> {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.7f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                    MarkdownContent(
                        text = message.content,
                        showCodeLineNumbers = showCodeLineNumbers,
                    )
                }
            }
        }

        ChatMessageRole.ASSISTANT, ChatMessageRole.TOOL -> {
            // Full-width editorial assistant layout
            Column(
                modifier = Modifier.fillMaxWidth(),
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
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(
                                1.dp,
                                Brush.linearGradient(
                                    colors = listOf(
                                        AntigravityPalette.NeonCyan.copy(alpha = 0.65f),
                                        AntigravityPalette.HydrogenViolet.copy(alpha = 0.6f),
                                    ),
                                ),
                            ),
                            modifier = Modifier.size(24.dp),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            AntigravityPalette.NeonCyan.copy(alpha = 0.16f),
                                            AntigravityPalette.HydrogenViolet.copy(alpha = 0.14f),
                                        ),
                                    ),
                                ),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = AntigravityPalette.NeonCyan,
                                    modifier = Modifier.size(13.dp),
                                )
                            }
                        }
                        Text(
                            text = if (message.role == ChatMessageRole.TOOL) "Araç Çıktısı" else "Antigravity",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (message.isStreaming) {
                            Surface(
                                shape = CircleShape,
                                color = AntigravityPalette.NeonCyan.copy(alpha = 0.14f),
                            ) {
                                Text(
                                    text = "Yazıyor",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = AntigravityPalette.NeonCyan,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                )
                            }
                        }
                    }
                    if (formattedTime != null) {
                        Text(
                            text = formattedTime,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        )
                    }
                }

                // Thought block accordion (respects settings.showThoughtBlocksExpanded)
                if (!message.thinkingContent.isNullOrBlank()) {
                    ThoughtBlockAccordion(
                        thinkingText = message.thinkingContent.orEmpty(),
                        isStreaming = message.isStreaming,
                        defaultExpanded = showThoughtBlocksExpanded,
                    )
                }

                // Collapsible Tool Execution Pills
                if (message.toolCalls.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        message.toolCalls.forEach { call ->
                            ToolExecutionPill(
                                call = call,
                                showCodeLineNumbers = showCodeLineNumbers,
                            )
                        }
                    }
                }

                // Editorial Markdown / Code block body
                if (message.content.isNotBlank()) {
                    MarkdownContent(
                        text = message.content,
                        showCodeLineNumbers = showCodeLineNumbers,
                    )
                } else if (message.isStreaming) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 2.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = AntigravityPalette.NeonCyan,
                        )
                        Text(
                            text = "Yanıt akışı bekleniyor...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun formatMessageTimestamp(epochMs: Long): String? {
    if (epochMs <= 0L) return null
    return runCatching {
        Instant.ofEpochMilli(epochMs)
            .atZone(ZoneId.systemDefault())
            .format(TimeFormatter)
    }.getOrNull()
}

@Composable
fun ThoughtBlockAccordion(
    thinkingText: String,
    isStreaming: Boolean,
    modifier: Modifier = Modifier,
    defaultExpanded: Boolean = false,
) {
    var expanded by remember(defaultExpanded) { mutableStateOf(defaultExpanded) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    if (isStreaming) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(13.dp),
                            strokeWidth = 1.8.dp,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Text(
                        text = if (isStreaming) "Düşünüyor..." else "Düşünce Süreci",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Daralt" else "Genişlet",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f),
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    Text(
                        text = thinkingText,
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun ToolExecutionPill(
    call: ToolCallSummary,
    modifier: Modifier = Modifier,
    showCodeLineNumbers: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }

    val statusColor = when (call.status) {
        ToolCallStatus.COMPLETED -> AntigravityPalette.SuccessGreen
        ToolCallStatus.FAILED, ToolCallStatus.CANCELLED -> AntigravityPalette.CriticalRed
        ToolCallStatus.AWAITING_APPROVAL -> AntigravityPalette.WarningAmber
        ToolCallStatus.RUNNING, ToolCallStatus.PENDING -> AntigravityPalette.NeonCyan
    }

    val inlinePreview = remember(call.argumentsPreview, call.outputPreview) {
        val raw = call.argumentsPreview.ifBlank { call.outputPreview.orEmpty() }
            .replace("\n", " ")
            .trim()
        if (raw == "{}") "" else raw
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.42f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when (call.status) {
                        ToolCallStatus.RUNNING, ToolCallStatus.PENDING -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = statusColor,
                            )
                        }

                        ToolCallStatus.COMPLETED -> {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(15.dp),
                            )
                        }

                        ToolCallStatus.FAILED, ToolCallStatus.CANCELLED -> {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(15.dp),
                            )
                        }

                        ToolCallStatus.AWAITING_APPROVAL -> {
                            Icon(
                                Icons.Default.Shield,
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                    Icon(
                        Icons.Default.Build,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = call.toolName,
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!expanded && inlinePreview.isNotBlank()) {
                            Text(
                                text = inlinePreview,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = statusColor.copy(alpha = 0.14f),
                    ) {
                        Text(
                            text = call.status.name,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = statusColor,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Detay",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (call.argumentsPreview.isNotBlank()) {
                        Text(
                            text = "Argümanlar",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        CodeBlockCard(
                            language = "json",
                            code = call.argumentsPreview,
                            showLineNumbers = showCodeLineNumbers,
                        )
                    }
                    if (!call.outputPreview.isNullOrBlank()) {
                        Text(
                            text = "Çıktı",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        CodeBlockCard(
                            language = "output",
                            code = call.outputPreview.orEmpty(),
                            showLineNumbers = showCodeLineNumbers,
                        )
                    }
                }
            }
        }
    }
}

private sealed interface MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Code(val language: String, val content: String) : MarkdownBlock
}

@Composable
fun MarkdownContent(
    text: String,
    modifier: Modifier = Modifier,
    showCodeLineNumbers: Boolean = false,
) {
    val blocks = remember(text) { parseMarkdownBlocks(text) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> {
                    Text(
                        text = renderInlineMarkdown(block.text),
                        style = when (block.level) {
                            1 -> MaterialTheme.typography.titleLarge
                            2 -> MaterialTheme.typography.titleMedium
                            else -> MaterialTheme.typography.titleSmall
                        },
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                is MarkdownBlock.Code -> {
                    CodeBlockCard(
                        language = block.language,
                        code = block.content,
                        showLineNumbers = showCodeLineNumbers,
                    )
                }

                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = renderInlineMarkdown(block.text),
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun CodeBlockCard(
    language: String,
    code: String,
    showLineNumbers: Boolean = false,
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1800)
            copied = false
        }
    }

    val codeLines = remember(code) { code.lines() }
    val gutterWidthDigits = remember(codeLines.size) {
        codeLines.size.coerceAtLeast(1).toString().length
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = AntigravityPalette.CodeBlockBg,
        border = BorderStroke(1.dp, AntigravityPalette.BorderHairline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            // Sleek header bar with language pill + copy button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AntigravityPalette.SurfaceDark)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AntigravityPalette.SurfaceElevated,
                    border = BorderStroke(1.dp, AntigravityPalette.BorderHairline),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            Icons.Default.Code,
                            contentDescription = null,
                            tint = AntigravityPalette.NeonCyan,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = language.ifBlank { "code" },
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                            color = AntigravityPalette.TextSecondary,
                        )
                    }
                }
                Surface(
                    onClick = {
                        clipboard.setText(AnnotatedString(code))
                        copied = true
                    },
                    shape = RoundedCornerShape(6.dp),
                    color = if (copied) {
                        AntigravityPalette.SuccessGreen.copy(alpha = 0.16f)
                    } else {
                        Color.Transparent
                    },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = "Kopyala",
                            tint = if (copied) {
                                AntigravityPalette.SuccessGreen
                            } else {
                                AntigravityPalette.TextSecondary
                            },
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = if (copied) "Kopyalandı" else "Kopyala",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                            color = if (copied) {
                                AntigravityPalette.SuccessGreen
                            } else {
                                AntigravityPalette.TextSecondary
                            },
                        )
                    }
                }
            }

            HorizontalDivider(color = AntigravityPalette.BorderHairline.copy(alpha = 0.8f))

            if (showLineNumbers) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 10.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .padding(start = 10.dp, end = 8.dp),
                        horizontalAlignment = Alignment.End,
                    ) {
                        codeLines.forEachIndexed { index, _ ->
                            Text(
                                text = (index + 1).toString().padStart(gutterWidthDigits, ' '),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.5.sp,
                                    lineHeight = 18.sp,
                                ),
                                color = AntigravityPalette.TextMuted,
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height((codeLines.size.coerceAtLeast(1) * 18).dp)
                            .background(AntigravityPalette.BorderHairline.copy(alpha = 0.7f)),
                    )
                    Column(
                        modifier = Modifier.padding(start = 10.dp, end = 12.dp),
                    ) {
                        codeLines.forEach { line ->
                            Text(
                                text = line.ifEmpty { " " },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                ),
                                color = Color(0xFFD6E2F5),
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = code,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    ),
                    color = Color(0xFFD6E2F5),
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp),
                )
            }
        }
    }
}

private fun parseMarkdownBlocks(raw: String): List<MarkdownBlock> {
    val result = mutableListOf<MarkdownBlock>()
    val lines = raw.lines()
    var i = 0
    val paragraphBuffer = StringBuilder()

    fun flushParagraph() {
        val content = paragraphBuffer.toString().trim()
        if (content.isNotEmpty()) {
            result.add(MarkdownBlock.Paragraph(content))
        }
        paragraphBuffer.clear()
    }

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trimStart()
        if (trimmed.startsWith("```")) {
            flushParagraph()
            val lang = trimmed.removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            i += 1
            while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                codeLines.add(lines[i])
                i += 1
            }
            result.add(MarkdownBlock.Code(lang, codeLines.joinToString("\n")))
        } else if (trimmed.startsWith("### ")) {
            flushParagraph()
            result.add(MarkdownBlock.Heading(3, trimmed.removePrefix("### ")))
        } else if (trimmed.startsWith("## ")) {
            flushParagraph()
            result.add(MarkdownBlock.Heading(2, trimmed.removePrefix("## ")))
        } else if (trimmed.startsWith("# ")) {
            flushParagraph()
            result.add(MarkdownBlock.Heading(1, trimmed.removePrefix("# ")))
        } else if (line.isBlank()) {
            flushParagraph()
        } else {
            if (paragraphBuffer.isNotEmpty()) paragraphBuffer.append('\n')
            paragraphBuffer.append(line)
        }
        i += 1
    }
    flushParagraph()
    return result
}

private fun renderInlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    var index = 0
    while (index < text.length) {
        when {
            text.startsWith("**", index) -> {
                val end = text.indexOf("**", index + 2)
                if (end > index + 2) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(index + 2, end))
                    }
                    index = end + 2
                } else {
                    append(text[index])
                    index += 1
                }
            }

            text[index] == '`' -> {
                val end = text.indexOf('`', index + 1)
                if (end > index + 1) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color(0xFF1A2538),
                            color = AntigravityPalette.NeonCyan,
                        ),
                    ) {
                        append(" ${text.substring(index + 1, end)} ")
                    }
                    index = end + 1
                } else {
                    append(text[index])
                    index += 1
                }
            }

            else -> {
                append(text[index])
                index += 1
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkspaceComposer(
    state: WorkspaceUiState,
    onAction: (WorkspaceAction) -> Unit,
    onOpenAttachmentDialog: () -> Unit,
) {
    var showModelMenu by remember { mutableStateOf(false) }
    var showEffortMenu by remember { mutableStateOf(false) }
    var showModeMenu by remember { mutableStateOf(false) }
    var isInputFocused by remember { mutableStateOf(false) }

    val isListening = state.voiceState is VoiceState.Listening
    val isStreaming = state.sessionState == AcpSessionState.STREAMING || state.messages.any { it.isStreaming }
    val hasSendableContent = state.composerText.isNotBlank() || state.attachments.isNotEmpty()

    val capsuleBorderColor by animateColorAsState(
        targetValue = if (isInputFocused || hasSendableContent) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
        } else {
            MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
        },
        animationSpec = tween(durationMillis = 180),
        label = "composer-border-color",
    )

    val micPulseAlpha = if (isListening) {
        val transition = rememberInfiniteTransition(label = "voice-mic-pulse")
        val animated by transition.animateFloat(
            initialValue = 0.2f,
            targetValue = 0.45f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 650, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "mic-alpha",
        )
        animated
    } else {
        0f
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        // Slash command picker popup
        AnimatedVisibility(visible = state.showSlashMenu && state.slashSuggestions.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            ) {
                Column(modifier = Modifier.padding(6.dp)) {
                    state.slashSuggestions.forEach { cmd ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onAction(WorkspaceAction.SelectSlashCommand(cmd)) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = cmd.command,
                                style = MaterialTheme.typography.labelLarge,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = cmd.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 10.dp),
                            )
                        }
                    }
                }
            }
        }

        // @ Mention picker popup
        AnimatedVisibility(visible = state.showMentionMenu && state.mentionSuggestions.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            ) {
                Column(modifier = Modifier.padding(6.dp)) {
                    state.mentionSuggestions.forEach { suggestion ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onAction(WorkspaceAction.SelectMention(suggestion)) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = suggestion.token,
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "${suggestion.category} • ${suggestion.label}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 10.dp),
                            )
                        }
                    }
                }
            }
        }

        // Live Voice Dictation Banner
        AnimatedVisibility(visible = state.voiceState !is VoiceState.Idle) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(
                    1.dp,
                    if (state.voiceState is VoiceState.Error) {
                        AntigravityPalette.CriticalRed.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                    },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val voiceText = when (val vs = state.voiceState) {
                        is VoiceState.Listening -> vs.partialText.ifBlank { "Dinleniyor (${state.voiceLocaleTag})..." }
                        is VoiceState.Processing -> vs.partialText.ifBlank { "Ses çözümleniyor..." }
                        is VoiceState.Result -> "Ses metni eklendi"
                        is VoiceState.Error -> vs.message
                        VoiceState.Idle -> ""
                    }
                    Text(
                        text = voiceText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.voiceState is VoiceState.Error) {
                            AntigravityPalette.CriticalRed
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Surface(
                        onClick = {
                            val next = if (state.voiceLocaleTag == "tr-TR") "en-US" else "tr-TR"
                            onAction(WorkspaceAction.ChangeVoiceLocale(next))
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            text = if (state.voiceLocaleTag == "tr-TR") "TR" else "EN",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }

        // Modern Floating Capsule Container
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 2.dp,
            shadowElevation = 6.dp,
            border = BorderStroke(1.dp, capsuleBorderColor),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Attachments chips row (when non-empty)
                if (state.attachments.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        state.attachments.forEach { att ->
                            Surface(
                                onClick = { onAction(WorkspaceAction.RemoveAttachment(att)) },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Icon(
                                        Icons.Default.AttachFile,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(13.dp),
                                    )
                                    Text(
                                        text = att,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Kaldır",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(13.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                // Clean borderless Text Input Field
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                ) {
                    if (state.composerText.isEmpty()) {
                        Text(
                            text = "Antigravity'ye bir görev ver...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        )
                    }
                    BasicTextField(
                        value = state.composerText,
                        onValueChange = { onAction(WorkspaceAction.UpdateComposerText(it)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { isInputFocused = it.isFocused },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        maxLines = 5,
                    )
                }

                // Bottom Toolbar Row: +, @, /, Scrollable Model/Effort/Mode Pills, Voice Mic, Send/Stop
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ComposerIconPill(
                        onClick = onOpenAttachmentDialog,
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Ek ekle",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                    ComposerIconPill(
                        onClick = { onAction(WorkspaceAction.OpenMentionMenu) },
                    ) {
                        Icon(
                            Icons.Default.AlternateEmail,
                            contentDescription = "@ bağlam ekle",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    ComposerIconPill(
                        onClick = { onAction(WorkspaceAction.OpenSlashMenu) },
                    ) {
                        Text(
                            text = "/",
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    // Scrollable selector pills for Model / Effort / Permission Mode
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Box {
                            ComposerSelectorPill(
                                label = state.selectedModel.displayName,
                                onClick = { showModelMenu = true },
                            )
                            DropdownMenu(
                                expanded = showModelMenu,
                                onDismissRequest = { showModelMenu = false },
                            ) {
                                AcpModel.entries.forEach { model ->
                                    DropdownMenuItem(
                                        text = { Text(model.displayName) },
                                        onClick = {
                                            onAction(WorkspaceAction.SelectModel(model))
                                            showModelMenu = false
                                        },
                                    )
                                }
                            }
                        }

                        Box {
                            ComposerSelectorPill(
                                label = state.selectedEffort.name,
                                onClick = { showEffortMenu = true },
                            )
                            DropdownMenu(
                                expanded = showEffortMenu,
                                onDismissRequest = { showEffortMenu = false },
                            ) {
                                ThinkingEffort.entries.forEach { effort ->
                                    DropdownMenuItem(
                                        text = { Text(effort.name) },
                                        onClick = {
                                            onAction(WorkspaceAction.SelectEffort(effort))
                                            showEffortMenu = false
                                        },
                                    )
                                }
                            }
                        }

                        Box {
                            ComposerSelectorPill(
                                label = state.permissionMode.name,
                                onClick = { showModeMenu = true },
                            )
                            DropdownMenu(
                                expanded = showModeMenu,
                                onDismissRequest = { showModeMenu = false },
                            ) {
                                PermissionMode.entries.forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text(mode.name) },
                                        onClick = {
                                            onAction(WorkspaceAction.SelectPermissionMode(mode))
                                            showModeMenu = false
                                        },
                                    )
                                }
                            }
                        }
                    }

                    // Voice Mic Pill (pulsing red when listening)
                    Surface(
                        onClick = { onAction(WorkspaceAction.ToggleVoiceListening) },
                        shape = CircleShape,
                        color = if (isListening) {
                            AntigravityPalette.CriticalRed.copy(alpha = micPulseAlpha)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (isListening) {
                                AntigravityPalette.CriticalRed.copy(alpha = 0.85f)
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                            },
                        ),
                        modifier = Modifier.size(34.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = "Bas-Konuş sesli dikte",
                                tint = if (isListening) {
                                    AntigravityPalette.CriticalRed
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(17.dp),
                            )
                        }
                    }

                    // Crisp Circular Send / Stop Button
                    if (isStreaming && state.composerText.isBlank()) {
                        Surface(
                            onClick = { onAction(WorkspaceAction.CancelActiveSession) },
                            shape = CircleShape,
                            color = AntigravityPalette.CriticalRed,
                            modifier = Modifier.size(34.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "İptal et",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    } else {
                        Surface(
                            onClick = { onAction(WorkspaceAction.SendMessage()) },
                            enabled = hasSendableContent,
                            shape = CircleShape,
                            color = if (hasSendableContent) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                            },
                            modifier = Modifier.size(34.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Gönder",
                                    tint = if (hasSendableContent) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                    },
                                    modifier = Modifier.size(16.dp),
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
private fun ComposerIconPill(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        modifier = Modifier.size(30.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
private fun ComposerSelectorPill(
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 5.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(13.dp),
            )
        }
    }
}

@Composable
private fun QuickAttachmentDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
) {
    var path by remember { mutableStateOf("/mnt/sdcard/") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = {
            Text(
                text = "Dosya / Bağlam Ekle",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text("Dosya veya klasör yolu") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { onAdd("screencap://current") },
                        label = { Text("Ekran Görüntüsü") },
                    )
                    AssistChip(
                        onClick = { onAdd("uitree://foreground") },
                        label = { Text("UI Ağacı") },
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(path) },
                enabled = path.isNotBlank(),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("Ekle")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal") }
        },
    )
}
