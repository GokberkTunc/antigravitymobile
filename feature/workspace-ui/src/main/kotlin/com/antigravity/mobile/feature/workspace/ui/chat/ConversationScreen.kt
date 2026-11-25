package com.antigravity.mobile.feature.workspace.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountTree
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
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
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
import com.antigravity.mobile.contracts.AcpEvent
import com.antigravity.mobile.contracts.AcpModel
import com.antigravity.mobile.contracts.AcpSessionState
import com.antigravity.mobile.contracts.ChatMessageRole
import com.antigravity.mobile.contracts.DiffApprovalStatus
import com.antigravity.mobile.contracts.PermissionMode
import com.antigravity.mobile.contracts.ThinkingEffort
import com.antigravity.mobile.contracts.ToolCallStatus
import com.antigravity.mobile.contracts.ToolCallSummary
import com.antigravity.mobile.contracts.UsageQuota
import com.antigravity.mobile.contracts.VoiceState
import com.antigravity.mobile.contracts.WorkspaceChatMessage
import com.antigravity.mobile.feature.workspace.model.AuthBannerState
import com.antigravity.mobile.feature.workspace.model.InterfaceLayoutMode
import com.antigravity.mobile.feature.workspace.model.OverviewTab
import com.antigravity.mobile.feature.workspace.model.QuotaBucketParser
import com.antigravity.mobile.feature.workspace.model.QuotaWarningState
import com.antigravity.mobile.feature.workspace.model.SettingsCategory
import com.antigravity.mobile.feature.workspace.model.SlashCommand
import com.antigravity.mobile.feature.workspace.model.WorkspaceAction
import com.antigravity.mobile.feature.workspace.model.WorkspaceUiState
import com.antigravity.mobile.feature.workspace.model.defaultMentions
import com.antigravity.mobile.feature.workspace.theme.AntigravityExtendedColors
import com.antigravity.mobile.feature.workspace.theme.LocalAntigravityColors
import com.antigravity.mobile.feature.workspace.ui.settings.FullAccessConfirmationDialog
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
    val ext = LocalAntigravityColors.current
    val listState = rememberLazyListState()
    var showAttachmentDialog by remember { mutableStateOf(false) }
    var showFullAccessDialog by remember { mutableStateOf(false) }
    val permissionCardMaxHeight = (LocalConfiguration.current.screenHeightDp * 0.5f).dp

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
    val activeTitle = state.activeConversation?.title?.takeIf { it.isNotBlank() } ?: "Yeni Sohbet"
    val activeSubtitle = state.selectedProject?.name ?: "Cihaz Geneli"
    val isCompact = state.settings.layoutMode == InterfaceLayoutMode.COMPACT_MOBILE

    val activeQuota = state.activeProfile?.quota
    val fiveHourPercent = remember(activeQuota, state.selectedModel) {
        activeQuota?.let { QuotaBucketParser.primaryHeadlinePercent(it, state.selectedModel) }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
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
                        IconButton(onClick = { onAction(WorkspaceAction.NavigateBack) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Geri",
                            )
                        }
                    },
                    title = {
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(onClick = onOpenDrawer)
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
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
                                    text = activeSubtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    },
                    actions = {
                        if (state.settings.fullAccessEnabled) {
                            Surface(
                                onClick = {
                                    onAction(
                                        WorkspaceAction.OpenSettingsDetail(SettingsCategory.PROJECTS_PERMISSIONS),
                                    )
                                },
                                shape = CircleShape,
                                color = ext.critical.copy(alpha = if (ext.isDark) 0.18f else 0.12f),
                                border = BorderStroke(1.dp, ext.critical.copy(alpha = 0.45f)),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Shield,
                                        contentDescription = null,
                                        tint = ext.critical,
                                        modifier = Modifier.size(12.dp),
                                    )
                                    Text(
                                        text = "Tam Yetki",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = ext.critical,
                                        maxLines = 1,
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        TopBarQuotaPill(
                            fiveHourPercent = fiveHourPercent,
                            isRefreshing = state.isRefreshingQuota,
                            onClick = { onAction(WorkspaceAction.OpenQuotaSheet) },
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = { onAction(WorkspaceAction.OpenOverviewSheet(OverviewTab.SUBAGENTS)) },
                            modifier = Modifier.size(38.dp),
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
                                    contentDescription = "Yardımcı panel",
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
                        Spacer(modifier = Modifier.width(6.dp))
                    },
                )
                HorizontalDivider(
                    thickness = 1.dp,
                    color = ext.cardBorder.copy(alpha = 0.75f),
                )
            }
        },
        bottomBar = {
            WorkspaceComposer(
                state = state,
                onAction = onAction,
                onRequestEnableFullAccess = { showFullAccessDialog = true },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Sleek Dismissible Inline Error Card (replaces floating black Snackbar)
            AnimatedVisibility(
                visible = state.errorMessage != null,
                enter = fadeIn(tween(180)) + expandVertically(tween(200)),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(180)),
            ) {
                state.errorMessage?.let { errorText ->
                    InlineErrorCard(
                        message = errorText,
                        onDismiss = { onAction(WorkspaceAction.DismissError) },
                    )
                }
            }

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

            // Editorial Empty Draft Canvas or Streaming Conversation Thread
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
                        horizontal = if (isCompact) 14.dp else 18.dp,
                        vertical = if (isCompact) 12.dp else 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(if (isCompact) 14.dp else 20.dp),
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

            // Inline ACP Permission Request Card (placed below the scrollable thread, capped to 50% screen)
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
                    onEnableFullAccess = { showFullAccessDialog = true },
                    modifier = Modifier.heightIn(max = permissionCardMaxHeight),
                )
            }
        }
    }

    if (state.showComposerToolsSheet) {
        ComposerToolsBottomSheet(
            state = state,
            onAction = onAction,
            onOpenAttachmentDialog = {
                onAction(WorkspaceAction.CloseComposerToolsSheet)
                showAttachmentDialog = true
            },
        )
    }

    if (state.showModelPickerSheet) {
        ModelAndEffortPickerBottomSheet(
            state = state,
            onAction = onAction,
        )
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

    if (showFullAccessDialog) {
        FullAccessConfirmationDialog(
            onConfirm = {
                showFullAccessDialog = false
                onAction(WorkspaceAction.UpdateSettings { it.copy(fullAccessEnabled = true) })
            },
            onDismiss = { showFullAccessDialog = false },
        )
    }
}

private fun quotaStatusColor(
    remainingPercent: Double?,
    ext: AntigravityExtendedColors,
    neutralColor: Color,
): Color = when {
    remainingPercent == null -> neutralColor
    remainingPercent > 40.0 -> ext.success
    remainingPercent > 15.0 -> ext.warning
    else -> ext.critical
}

@Composable
private fun TopBarQuotaPill(
    fiveHourPercent: Double?,
    isRefreshing: Boolean,
    onClick: () -> Unit,
) {
    val ext = LocalAntigravityColors.current
    val accentColor = quotaStatusColor(
        remainingPercent = fiveHourPercent,
        ext = ext,
        neutralColor = MaterialTheme.colorScheme.primary,
    )
    val label = fiveHourPercent?.let { "5s %${it.roundToInt().coerceIn(0, 100)}" } ?: "Kota"

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, accentColor.copy(alpha = if (ext.isDark) 0.42f else 0.32f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(10.dp),
                    strokeWidth = 1.6.dp,
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
    val ext = LocalAntigravityColors.current
    val transition = rememberInfiniteTransition(label = "emergency-stop-glow")
    val glowAlpha by transition.animateFloat(
        initialValue = if (ext.isDark) 0.16f else 0.10f,
        targetValue = if (ext.isDark) 0.34f else 0.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow-alpha",
    )

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = ext.critical.copy(alpha = glowAlpha),
        border = BorderStroke(1.dp, ext.critical.copy(alpha = 0.65f)),
        modifier = Modifier.size(34.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.StopCircle,
                contentDescription = "Acil Durdur",
                tint = ext.critical,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SessionStatusDot(state: AcpSessionState) {
    val ext = LocalAntigravityColors.current
    val color = when (state) {
        AcpSessionState.READY -> ext.success
        AcpSessionState.STREAMING,
        AcpSessionState.CONNECTING,
        AcpSessionState.INITIALIZING,
        -> ext.accentCyan

        AcpSessionState.AWAITING_PERMISSION,
        AcpSessionState.AWAITING_AUTH,
        -> ext.warning

        AcpSessionState.ERROR -> ext.critical
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
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
private fun InlineErrorCard(
    message: String,
    onDismiss: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(30.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Hatayı kapat",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
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
        color = MaterialTheme.colorScheme.errorContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Kimlik Doğrulama Gerekli (${banner.errorCode})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(26.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
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
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text("Google ile Giriş Yap", fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = { showCodeField = !showCodeField },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.35f)),
                ) {
                    Text("Manuel Kod", color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            AnimatedVisibility(visible = showCodeField) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                        shape = RoundedCornerShape(12.dp),
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
    val ext = LocalAntigravityColors.current

    Surface(
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, ext.warning.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier
                .background(ext.warning.copy(alpha = if (ext.isDark) 0.12f else 0.08f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = ext.warning,
                modifier = Modifier.size(18.dp),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Kota Limite Yaklaşıyor (%.0f%% kaldı)".format(warning.remainingPercent),
                    style = MaterialTheme.typography.labelLarge,
                    color = ext.warning,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = warning.suggestedFallbackProfile?.let {
                        "Tek dokunuşla yedek hesaba geçin: ${it.displayName}"
                    } ?: "Yeni Google profili ekleyerek kesintisiz devam edebilirsiniz.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            warning.suggestedFallbackProfile?.let { fallback ->
                TextButton(onClick = { onSwitchFallback(fallback.id) }) {
                    Text("Geç", fontWeight = FontWeight.SemiBold, color = ext.warning)
                }
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(26.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Kapat",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InlinePermissionCard(
    request: AcpEvent.PermissionRequest,
    onApprove: (remember: Boolean) -> Unit,
    onDeny: () -> Unit,
    onEnableFullAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ext = LocalAntigravityColors.current
    var expanded by remember(request.requestId) { mutableStateOf(false) }

    val shortToolLabel = remember(request.toolName) {
        request.toolName.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            ?: "Araç"
    }
    val detailPayload = remember(request) {
        buildString {
            val trimmedTool = request.toolName.trim()
            val showFullTool = trimmedTool.contains('\n') || trimmedTool.length > 60
            if (showFullTool) {
                append(trimmedTool)
            }
            val desc = request.description.trim()
            if (desc.isNotEmpty() && desc != trimmedTool) {
                if (isNotEmpty()) append("\n\n")
                append(desc)
            }
            val args = request.argumentsJson.trim()
            if (args.isNotEmpty() && args != "{}") {
                if (isNotEmpty()) append("\n\n")
                append(args)
            }
        }
    }
    val isLongPayload = detailPayload.length > 220 || detailPayload.count { it == '\n' } > 5

    Surface(
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, ext.warning.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = if (ext.isDark) 0.dp else 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Fixed single-line header + short tool summary + Risk badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = ext.warning.copy(alpha = if (ext.isDark) 0.16f else 0.12f),
                    modifier = Modifier.size(32.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = ext.warning,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Araç izni gerekiyor",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = shortToolLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = ext.warning.copy(alpha = if (ext.isDark) 0.18f else 0.12f),
                    border = BorderStroke(1.dp, ext.warning.copy(alpha = 0.4f)),
                ) {
                    Text(
                        text = request.riskLevel.name,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = ext.warning,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }

            // Scrollable monospace details box (never clipped / never pushes buttons off-screen)
            if (detailPayload.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ext.codeBlockBackground,
                        border = BorderStroke(1.dp, ext.cardBorder),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = if (expanded) 220.dp else 140.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(10.dp),
                        ) {
                            Text(
                                text = detailPayload,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (isLongPayload) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            Text(
                                text = if (expanded) "Daralt" else "Tamamını Gör / Genişlet",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { expanded = !expanded }
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }

            // Always-visible action buttons
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onDeny,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, ext.critical.copy(alpha = 0.6f)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text("Reddet", color = ext.critical, fontWeight = FontWeight.Medium)
                }
                Button(
                    onClick = { onApprove(false) },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text("Bir kez izin ver", fontWeight = FontWeight.SemiBold)
                }
                FilledTonalButton(
                    onClick = { onApprove(true) },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text("Oturum boyunca izin ver", fontWeight = FontWeight.Medium)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = onEnableFullAccess,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = ext.critical,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Bir daha sorma (Tam Yetki)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = ext.critical,
                    )
                }
            }
        }
    }
}

private data class QuickPromptChipItem(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyWorkspaceWelcome(
    state: WorkspaceUiState,
    onAction: (WorkspaceAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ext = LocalAntigravityColors.current
    val quota = state.activeProfile?.quota
    val fiveHourPercent = remember(quota, state.selectedModel) {
        quota?.let { QuotaBucketParser.primaryHeadlinePercent(it, state.selectedModel) }
    }
    val weeklyPercent = remember(quota, state.selectedModel) {
        quota?.let { QuotaBucketParser.weeklyPercent(it, state.selectedModel) }
    }
    val contextSummary = "${state.selectedProject?.name ?: "Cihaz Geneli"} • ${state.selectedModel.displayName}"

    val quickPrompts = remember(onAction) {
        listOf(
            QuickPromptChipItem(
                label = "Cihaz durumunu analiz et",
                icon = Icons.Default.Memory,
                onClick = {
                    onAction(WorkspaceAction.SendMessage("Cihaz durumunu analiz et"))
                },
            ),
            QuickPromptChipItem(
                label = "Proje dosyalarını tara",
                icon = Icons.Default.FolderOpen,
                onClick = {
                    onAction(WorkspaceAction.SendMessage("Proje dosyalarını tara"))
                },
            ),
            QuickPromptChipItem(
                label = "/schedule Otomasyon kur",
                icon = Icons.Default.Schedule,
                onClick = { onAction(WorkspaceAction.UpdateComposerText("/schedule ")) },
            ),
            QuickPromptChipItem(
                label = "/swarm Çoklu ajan başlat",
                icon = Icons.Default.Hub,
                onClick = { onAction(WorkspaceAction.UpdateComposerText("/swarm ")) },
            ),
        )
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Subtle Official Antigravity Sparkle Mark
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = if (ext.isDark) 0.35f else 0.2f),
            ),
            modifier = Modifier.size(54.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "Neye odaklanalım?",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Tappable Context & Model Pill -> Opens Model Picker Sheet
        Surface(
            onClick = { onAction(WorkspaceAction.OpenModelPickerSheet) },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, ext.cardBorder),
        ) {
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 9.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = contextSummary,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Refined Dual-Bucket Mini Quota Strip (5-Hour & Weekly)
        DualBucketMiniQuotaStrip(
            fiveHourPercent = fiveHourPercent,
            weeklyPercent = weeklyPercent,
            isRefreshing = state.isRefreshingQuota,
            onClick = { onAction(WorkspaceAction.OpenQuotaSheet) },
        )

        Spacer(modifier = Modifier.height(26.dp))

        // Minimalist Quick-Prompt Chips
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            quickPrompts.forEach { chip ->
                Surface(
                    onClick = chip.onClick,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, ext.cardBorder),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Icon(
                            imageVector = chip.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            text = chip.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DualBucketMiniQuotaStrip(
    fiveHourPercent: Double?,
    weeklyPercent: Double?,
    isRefreshing: Boolean,
    onClick: () -> Unit,
) {
    val ext = LocalAntigravityColors.current
    val fiveHourColor = quotaStatusColor(fiveHourPercent, ext, MaterialTheme.colorScheme.primary)
    val weeklyColor = quotaStatusColor(weeklyPercent, ext, MaterialTheme.colorScheme.secondary)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, ext.cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 360.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 1.8.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            QuotaMiniBucketColumn(
                label = "5 Saatlik",
                percent = fiveHourPercent,
                accentColor = fiveHourColor,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(26.dp)
                    .background(ext.cardBorder),
            )
            QuotaMiniBucketColumn(
                label = "Haftalık",
                percent = weeklyPercent,
                accentColor = weeklyColor,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Kota detayları",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun QuotaMiniBucketColumn(
    label: String,
    percent: Double?,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val ext = LocalAntigravityColors.current
    val fraction = ((percent ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
    val valueText = percent?.let { "%${it.roundToInt().coerceIn(0, 100)}" } ?: "—"

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (percent != null) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.5.dp)
                .clip(CircleShape)
                .background(ext.cardBorder),
        ) {
            if (percent != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction.coerceAtLeast(0.04f))
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(accentColor),
                )
            }
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
    val ext = LocalAntigravityColors.current
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
                    shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = if (ext.isDark) 0.26f else 0.14f),
                    ),
                    modifier = Modifier.widthIn(max = 316.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 15.dp, vertical = 10.dp),
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
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                                        border = BorderStroke(
                                            1.dp,
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                                        ),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AttachFile,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(12.dp),
                                            )
                                            Text(
                                                text = att,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurface,
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
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, ext.cardBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp)) {
                    MarkdownContent(
                        text = message.content,
                        showCodeLineNumbers = showCodeLineNumbers,
                    )
                }
            }
        }

        ChatMessageRole.ASSISTANT, ChatMessageRole.TOOL -> {
            // Full-width clean editorial prose (Antigravity Desktop 2.0 style)
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
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(22.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(12.dp),
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
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Text(
                                    text = "Yazıyor",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    if (formattedTime != null) {
                        Text(
                            text = formattedTime,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = ext.textMuted,
                        )
                    }
                }

                // Collapsible Thought Block Accordion
                if (!message.thinkingContent.isNullOrBlank()) {
                    ThoughtBlockAccordion(
                        thinkingText = message.thinkingContent.orEmpty(),
                        isStreaming = message.isStreaming,
                        defaultExpanded = showThoughtBlocksExpanded,
                    )
                }

                // Minimal Timeline-Style Tool Execution Pills
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

                // Editorial Markdown / Code Body
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
                            color = MaterialTheme.colorScheme.primary,
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
    val ext = LocalAntigravityColors.current
    var expanded by remember(defaultExpanded) { mutableStateOf(defaultExpanded) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (ext.isDark) 0.28f else 0.48f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.secondary.copy(alpha = if (ext.isDark) 0.32f else 0.22f),
        ),
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
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
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    Text(
                        text = thinkingText,
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
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
    val ext = LocalAntigravityColors.current
    var expanded by remember { mutableStateOf(false) }

    val statusColor = when (call.status) {
        ToolCallStatus.COMPLETED -> ext.success
        ToolCallStatus.FAILED, ToolCallStatus.CANCELLED -> ext.critical
        ToolCallStatus.AWAITING_APPROVAL -> ext.warning
        ToolCallStatus.RUNNING, ToolCallStatus.PENDING -> ext.accentCyan
    }

    val inlinePreview = remember(call.argumentsPreview, call.outputPreview) {
        val raw = call.argumentsPreview.ifBlank { call.outputPreview.orEmpty() }
            .replace("\n", " ")
            .trim()
        if (raw == "{}") "" else raw
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Minimal timeline accent indicator
        Box(
            modifier = Modifier
                .padding(top = 12.dp)
                .width(3.dp)
                .height(if (expanded) 44.dp else 18.dp)
                .clip(CircleShape)
                .background(statusColor.copy(alpha = 0.7f)),
        )

        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, ext.cardBorder),
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
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
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
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = statusColor,
                                    modifier = Modifier.size(15.dp),
                                )
                            }

                            ToolCallStatus.FAILED, ToolCallStatus.CANCELLED -> {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = null,
                                    tint = statusColor,
                                    modifier = Modifier.size(15.dp),
                                )
                            }

                            ToolCallStatus.AWAITING_APPROVAL -> {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = statusColor,
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.Build,
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
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            color = statusColor.copy(alpha = if (ext.isDark) 0.16f else 0.12f),
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
    val ext = LocalAntigravityColors.current
    val inlineCodeBg = ext.codeBlockBackground
    val inlineCodeFg = MaterialTheme.colorScheme.primary
    val blocks = remember(text) { parseMarkdownBlocks(text) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> {
                    Text(
                        text = renderInlineMarkdown(block.text, inlineCodeBg, inlineCodeFg),
                        style = when (block.level) {
                            1 -> MaterialTheme.typography.titleLarge
                            2 -> MaterialTheme.typography.titleMedium
                            else -> MaterialTheme.typography.titleSmall
                        },
                        fontWeight = FontWeight.SemiBold,
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
                        text = renderInlineMarkdown(block.text, inlineCodeBg, inlineCodeFg),
                        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 23.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun CodeBlockCard(
    language: String,
    code: String,
    showLineNumbers: Boolean = false,
) {
    val ext = LocalAntigravityColors.current
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
        color = ext.codeBlockBackground,
        border = BorderStroke(1.dp, ext.cardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            // Crisp Header Bar with Language + Copy Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (ext.isDark) 0.55f else 0.72f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = language.ifBlank { "code" },
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Surface(
                    onClick = {
                        clipboard.setText(AnnotatedString(code))
                        copied = true
                    },
                    shape = RoundedCornerShape(6.dp),
                    color = if (copied) {
                        ext.success.copy(alpha = if (ext.isDark) 0.18f else 0.12f)
                    } else {
                        Color.Transparent
                    },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = "Kopyala",
                            tint = if (copied) ext.success else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = if (copied) "Kopyalandı" else "Kopyala",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                            color = if (copied) ext.success else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            HorizontalDivider(color = ext.cardBorder)

            if (showLineNumbers) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 10.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(start = 10.dp, end = 8.dp),
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
                                color = ext.textMuted,
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height((codeLines.size.coerceAtLeast(1) * 18).dp)
                            .background(ext.cardBorder),
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
                                color = MaterialTheme.colorScheme.onSurface,
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
                    color = MaterialTheme.colorScheme.onSurface,
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

private fun renderInlineMarkdown(
    text: String,
    inlineCodeBg: Color,
    inlineCodeFg: Color,
): AnnotatedString = buildAnnotatedString {
    var index = 0
    while (index < text.length) {
        when {
            text.startsWith("**", index) -> {
                val end = text.indexOf("**", index + 2)
                if (end > index + 2) {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
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
                            background = inlineCodeBg,
                            color = inlineCodeFg,
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

private fun permissionModeLabel(mode: PermissionMode): String = when (mode) {
    PermissionMode.AUTO -> "Auto"
    PermissionMode.ASK -> "Ask"
    PermissionMode.PLAN -> "Plan"
}

private fun permissionModeSubtitle(mode: PermissionMode): String = when (mode) {
    PermissionMode.AUTO -> "Araç çağrılarını otomatik onayla"
    PermissionMode.ASK -> "Kritik işlemlerde onay iste"
    PermissionMode.PLAN -> "Salt okunur analiz ve planlama"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkspaceComposer(
    state: WorkspaceUiState,
    onAction: (WorkspaceAction) -> Unit,
    onRequestEnableFullAccess: () -> Unit,
) {
    val ext = LocalAntigravityColors.current
    var showModeMenu by remember { mutableStateOf(false) }
    var isInputFocused by remember { mutableStateOf(false) }

    val isListening = state.voiceState is VoiceState.Listening
    val isStreaming = state.sessionState == AcpSessionState.STREAMING || state.messages.any { it.isStreaming }
    val hasSendableContent = state.composerText.isNotBlank() || state.attachments.isNotEmpty()

    val capsuleBorderColor by animateColorAsState(
        targetValue = if (isInputFocused) {
            MaterialTheme.colorScheme.outline.copy(alpha = if (ext.isDark) 0.9f else 0.75f)
        } else {
            ext.cardBorder
        },
        animationSpec = tween(durationMillis = 160),
        label = "composer-border-color",
    )

    val micPulseAlpha = if (isListening) {
        val transition = rememberInfiniteTransition(label = "voice-mic-pulse")
        val animated by transition.animateFloat(
            initialValue = 0.16f,
            targetValue = 0.36f,
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
        // Inline Slash Command Autocomplete Popup (when typing `/`)
        AnimatedVisibility(visible = state.showSlashMenu && state.slashSuggestions.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, ext.cardBorder),
                shadowElevation = if (ext.isDark) 6.dp else 3.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            ) {
                Column(modifier = Modifier.padding(6.dp)) {
                    state.slashSuggestions.forEach { cmd ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onAction(WorkspaceAction.SelectSlashCommand(cmd)) }
                                .padding(horizontal = 10.dp, vertical = 9.dp),
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

        // Inline @ Mention Autocomplete Popup (when typing `@`)
        AnimatedVisibility(visible = state.showMentionMenu && state.mentionSuggestions.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, ext.cardBorder),
                shadowElevation = if (ext.isDark) 6.dp else 3.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            ) {
                Column(modifier = Modifier.padding(6.dp)) {
                    state.mentionSuggestions.forEach { suggestion ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onAction(WorkspaceAction.SelectMention(suggestion)) }
                                .padding(horizontal = 10.dp, vertical = 9.dp),
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
            val isVoiceError = state.voiceState is VoiceState.Error
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    1.dp,
                    if (isVoiceError) {
                        ext.critical.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
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
                        color = if (isVoiceError) ext.critical else MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        onClick = {
                            val next = if (state.voiceLocaleTag == "tr-TR") "en-US" else "tr-TR"
                            onAction(WorkspaceAction.ChangeVoiceLocale(next))
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, ext.cardBorder),
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

        // Elevated Soft Tonal Two-Tier Capsule (Material 3 Expressive)
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = if (ext.isDark) ext.cardBackground else MaterialTheme.colorScheme.surface,
            tonalElevation = if (ext.isDark) 2.dp else 1.dp,
            shadowElevation = if (ext.isDark) 4.dp else 2.dp,
            border = BorderStroke(1.dp, capsuleBorderColor),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
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
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                border = BorderStroke(1.dp, ext.cardBorder),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AttachFile,
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
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Kaldır",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(13.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                // Tier 1: Borderless Multi-Line Input + Circular Send / Stop Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (state.composerText.isEmpty()) {
                            Text(
                                text = "Antigravity'ye sor veya görev ver...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        BasicTextField(
                            value = state.composerText,
                            onValueChange = { onAction(WorkspaceAction.UpdateComposerText(it)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { isInputFocused = it.isFocused },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            maxLines = 5,
                        )
                    }

                    if (isStreaming && state.composerText.isBlank()) {
                        Surface(
                            onClick = { onAction(WorkspaceAction.CancelActiveSession) },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(38.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "İptal et",
                                    tint = MaterialTheme.colorScheme.onError,
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
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            modifier = Modifier.size(38.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Gönder",
                                    tint = if (hasSendableContent) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    },
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                        }
                    }
                }

                // Tier 2: Clean, Spacious 4-Control Utility Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // 1. `+` Composer Tools Sheet trigger
                    Surface(
                        onClick = { onAction(WorkspaceAction.OpenComposerToolsSheet) },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, ext.cardBorder),
                        modifier = Modifier.size(34.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Araçlar, ekler ve komutlar",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }

                    // 2. Combined Model & Effort Pill -> Opens Model Picker Bottom Sheet
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Surface(
                            onClick = { onAction(WorkspaceAction.OpenModelPickerSheet) },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.dp, ext.cardBorder),
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 10.dp, end = 7.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp),
                                )
                                Text(
                                    text = "${state.selectedModel.displayName} · ${state.selectedEffort.name}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }

                    // 3. Permission Mode Pill (Auto / Ask / Plan / Tam Yetki)
                    val fullAccess = state.settings.fullAccessEnabled
                    Box {
                        Surface(
                            onClick = { showModeMenu = true },
                            shape = CircleShape,
                            color = if (fullAccess) {
                                ext.critical.copy(alpha = if (ext.isDark) 0.18f else 0.11f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            },
                            border = BorderStroke(
                                1.dp,
                                if (fullAccess) ext.critical.copy(alpha = 0.55f) else ext.cardBorder,
                            ),
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                if (fullAccess) {
                                    Icon(
                                        imageVector = Icons.Default.Shield,
                                        contentDescription = null,
                                        tint = ext.critical,
                                        modifier = Modifier.size(12.dp),
                                    )
                                }
                                Text(
                                    text = if (fullAccess) "Tam Yetki" else permissionModeLabel(state.permissionMode),
                                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp),
                                    fontWeight = if (fullAccess) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (fullAccess) ext.critical else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                )
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "İzin modu",
                                    tint = if (fullAccess) ext.critical else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = showModeMenu,
                            onDismissRequest = { showModeMenu = false },
                            containerColor = MaterialTheme.colorScheme.surface,
                        ) {
                            PermissionMode.entries.forEach { mode ->
                                val isSelected = !fullAccess && mode == state.permissionMode
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = permissionModeLabel(mode),
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                                color = if (isSelected) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                },
                                            )
                                            Text(
                                                text = permissionModeSubtitle(mode),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    },
                                    trailingIcon = if (isSelected) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                    onClick = {
                                        if (fullAccess) {
                                            onAction(WorkspaceAction.UpdateSettings { it.copy(fullAccessEnabled = false) })
                                        }
                                        onAction(WorkspaceAction.SelectPermissionMode(mode))
                                        showModeMenu = false
                                    },
                                )
                            }
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = ext.cardBorder.copy(alpha = 0.7f),
                            )
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = "Tam Yetki (Onay Sorma)",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = if (fullAccess) FontWeight.Bold else FontWeight.SemiBold,
                                            color = ext.critical,
                                        )
                                        Text(
                                            text = if (fullAccess) {
                                                "Etkin • Kapatmak için dokunun"
                                            } else {
                                                "dangerously-skip-permissions • Hiçbir aracı sorma"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                                trailingIcon = if (fullAccess) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = ext.critical,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                } else {
                                    null
                                },
                                onClick = {
                                    showModeMenu = false
                                    if (fullAccess) {
                                        onAction(WorkspaceAction.UpdateSettings { it.copy(fullAccessEnabled = false) })
                                    } else {
                                        onRequestEnableFullAccess()
                                    }
                                },
                            )
                        }
                    }

                    // 4. Voice Mic Button (red highlight when listening)
                    Surface(
                        onClick = { onAction(WorkspaceAction.ToggleVoiceListening) },
                        shape = CircleShape,
                        color = if (isListening) {
                            ext.critical.copy(alpha = micPulseAlpha)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        },
                        border = BorderStroke(
                            1.dp,
                            if (isListening) ext.critical.copy(alpha = 0.75f) else ext.cardBorder,
                        ),
                        modifier = Modifier.size(34.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = "Sesli dikte",
                                tint = if (isListening) {
                                    ext.critical
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(17.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ComposerToolsBottomSheet(
    state: WorkspaceUiState,
    onAction: (WorkspaceAction) -> Unit,
    onOpenAttachmentDialog: () -> Unit,
) {
    val ext = LocalAntigravityColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val mentions = state.mentionSuggestions.ifEmpty { defaultMentions() }

    ModalBottomSheet(
        onDismissRequest = { onAction(WorkspaceAction.CloseComposerToolsSheet) },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Araçlar & Bağlam",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Dosya ekle, slash komutu çalıştır veya @ bağlamı ekle",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Section 1: Attachments
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Ekler",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ComposerToolChip(
                        icon = Icons.Default.AttachFile,
                        label = "+ Dosya/Yol Ekle",
                        accent = MaterialTheme.colorScheme.primary,
                        onClick = onOpenAttachmentDialog,
                    )
                    ComposerToolChip(
                        icon = Icons.Default.Image,
                        label = "Ekran Görüntüsü",
                        accent = MaterialTheme.colorScheme.secondary,
                        onClick = {
                            onAction(WorkspaceAction.AddAttachment("screencap://current"))
                            onAction(WorkspaceAction.CloseComposerToolsSheet)
                        },
                    )
                    ComposerToolChip(
                        icon = Icons.Default.AccountTree,
                        label = "UI Ağacı",
                        accent = ext.success,
                        onClick = {
                            onAction(WorkspaceAction.AddAttachment("uitree://foreground"))
                            onAction(WorkspaceAction.CloseComposerToolsSheet)
                        },
                    )
                }
            }

            HorizontalDivider(color = ext.cardBorder)

            // Section 2: Slash Commands
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Slash Komutları",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, ext.cardBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        SlashCommand.entries.forEachIndexed { index, cmd ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onAction(WorkspaceAction.CloseComposerToolsSheet)
                                        onAction(WorkspaceAction.SelectSlashCommand(cmd))
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Text(
                                        text = cmd.command,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = cmd.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = cmd.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            if (index < SlashCommand.entries.lastIndex) {
                                HorizontalDivider(
                                    color = ext.cardBorder.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = ext.cardBorder)

            // Section 3: @ Context Mentions
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.AlternateEmail,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "@ Bağlam Etiketleri",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    mentions.forEach { mention ->
                        Surface(
                            onClick = {
                                onAction(WorkspaceAction.CloseComposerToolsSheet)
                                onAction(WorkspaceAction.SelectMention(mention))
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.dp, ext.cardBorder),
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                                verticalArrangement = Arrangement.spacedBy(1.dp),
                            ) {
                                Text(
                                    text = mention.token,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                                Text(
                                    text = mention.label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Default,
                                        fontSize = 10.5.sp,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
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
private fun ComposerToolChip(
    icon: ImageVector,
    label: String,
    accent: Color,
    onClick: () -> Unit,
) {
    val ext = LocalAntigravityColors.current
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, ext.cardBorder),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelAndEffortPickerBottomSheet(
    state: WorkspaceUiState,
    onAction: (WorkspaceAction) -> Unit,
) {
    val ext = LocalAntigravityColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val activeQuota = state.activeProfile?.quota

    ModalBottomSheet(
        onDismissRequest = { onAction(WorkspaceAction.CloseModelPickerSheet) },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Model & Düşünme Düzeyi",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Aktif oturum için model ve akıl yürütme derinliği seçin",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = {
                        onAction(WorkspaceAction.CloseModelPickerSheet)
                        onAction(WorkspaceAction.OpenQuotaSheet)
                    },
                ) {
                    Text("Kota Detayı", fontWeight = FontWeight.SemiBold)
                }
            }

            // Models List with Live 5-Hour & Weekly Quota Badges
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Modeller",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                AcpModel.entries.forEach { model ->
                    ModelPickerRow(
                        model = model,
                        selected = model == state.selectedModel,
                        quota = activeQuota,
                        onClick = { onAction(WorkspaceAction.SelectModel(model)) },
                    )
                }
            }

            HorizontalDivider(color = ext.cardBorder)

            // Thinking Effort Segmented Pills
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Düşünme Düzeyi",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = effortHintLabel(state.selectedEffort),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, ext.cardBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        ThinkingEffort.entries.forEach { effort ->
                            val isSelected = effort == state.selectedEffort
                            Surface(
                                onClick = { onAction(WorkspaceAction.SelectEffort(effort)) },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color.Transparent
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 9.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = effort.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = { onAction(WorkspaceAction.CloseModelPickerSheet) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Tamam", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ModelPickerRow(
    model: AcpModel,
    selected: Boolean,
    quota: UsageQuota?,
    onClick: () -> Unit,
) {
    val ext = LocalAntigravityColors.current
    val fiveHourPercent = remember(quota, model) {
        quota?.let { QuotaBucketParser.primaryHeadlinePercent(it, model) }
    }
    val weeklyPercent = remember(quota, model) {
        quota?.let { QuotaBucketParser.weeklyPercent(it, model) }
    }

    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (ext.isDark) 0.55f else 0.7f)
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        ext.cardBorder
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = BorderStroke(if (selected) 1.4.dp else 1.dp, borderColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surface
                },
                border = if (selected) null else BorderStroke(1.dp, ext.cardBorder),
                modifier = Modifier.size(22.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (selected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Seçili",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = model.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = modelDescription(model),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Live 5-Hour & Weekly Quota % Badges
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                if (fiveHourPercent != null) {
                    ModelQuotaMiniBadge(
                        prefix = "5s",
                        percent = fiveHourPercent,
                    )
                }
                if (weeklyPercent != null) {
                    ModelQuotaMiniBadge(
                        prefix = "Hft",
                        percent = weeklyPercent,
                    )
                }
                if (fiveHourPercent == null && weeklyPercent == null) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, ext.cardBorder),
                    ) {
                        Text(
                            text = "—",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelQuotaMiniBadge(
    prefix: String,
    percent: Double,
) {
    val ext = LocalAntigravityColors.current
    val accent = quotaStatusColor(percent, ext, MaterialTheme.colorScheme.primary)
    val clamped = percent.roundToInt().coerceIn(0, 100)

    Surface(
        shape = CircleShape,
        color = accent.copy(alpha = if (ext.isDark) 0.16f else 0.11f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
    ) {
        Text(
            text = "$prefix %$clamped",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            fontWeight = FontWeight.SemiBold,
            color = accent,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}

private fun modelDescription(model: AcpModel): String = when (model) {
    AcpModel.GEMINI_4_NEON -> "Hızlı, dengeli günlük mühendislik"
    AcpModel.GEMINI_4_HYDROGEN -> "Derin akıl yürütme ve karmaşık mimari"
    AcpModel.GEMINI_4_CARBON -> "Hafif, düşük gecikmeli hızlı yanıt"
    AcpModel.ORCHESTRATOR -> "Çoklu ajan koordinasyonu ve planlama"
}

private fun effortHintLabel(effort: ThinkingEffort): String = when (effort) {
    ThinkingEffort.LOW -> "En hızlı yanıt"
    ThinkingEffort.MEDIUM -> "Dengeli derinlik"
    ThinkingEffort.HIGH -> "Kapsamlı akıl yürütme"
    ThinkingEffort.EXTRA -> "Maksimum analiz"
}

@Composable
private fun QuickAttachmentDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
) {
    var path by remember { mutableStateOf("/mnt/sdcard/") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = "Dosya / Bağlam Ekle",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
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
                    modifier = Modifier.fillMaxWidth(),
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
