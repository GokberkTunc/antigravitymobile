package com.antigravity.mobile.feature.workspace.ui.chat

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.material3.InputChip
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
import com.antigravity.mobile.contracts.VoiceState
import com.antigravity.mobile.contracts.WorkspaceChatMessage
import com.antigravity.mobile.feature.workspace.model.AuthBannerState
import com.antigravity.mobile.feature.workspace.model.OverviewTab
import com.antigravity.mobile.feature.workspace.model.QuotaWarningState
import com.antigravity.mobile.feature.workspace.model.SlashCommand
import com.antigravity.mobile.feature.workspace.model.WorkspaceAction
import com.antigravity.mobile.feature.workspace.model.WorkspaceUiState
import com.antigravity.mobile.feature.workspace.theme.AntigravityPalette

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

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    val overviewBadgeCount = state.subagents.size +
        state.diffs.count { it.status == DiffApprovalStatus.PENDING }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Menü")
                    }
                },
                title = {
                    Column {
                        Text(
                            text = state.activeProject.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            SessionStatusDot(state.sessionState)
                            Text(
                                text = "${state.activeProfile?.displayName ?: "Hesap yok"} • ${state.selectedModel.displayName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onAction(WorkspaceAction.OpenOverviewSheet(OverviewTab.SUBAGENTS)) },
                    ) {
                        BadgedBox(
                            badge = {
                                if (overviewBadgeCount > 0) {
                                    Badge { Text(overviewBadgeCount.toString()) }
                                }
                            },
                        ) {
                            Icon(Icons.Default.Hub, contentDescription = "Alt ajanlar, Swarm ve Diffs")
                        }
                    }
                    if (state.sessionState == AcpSessionState.STREAMING ||
                        state.messages.any { it.isStreaming }
                    ) {
                        IconButton(onClick = { onAction(WorkspaceAction.TriggerEmergencyStop) }) {
                            Icon(
                                imageVector = Icons.Default.StopCircle,
                                contentDescription = "Acil Durdur",
                                tint = AntigravityPalette.CriticalRed,
                            )
                        }
                    }
                },
            )
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

            // Streaming Conversation Canvas
            if (state.messages.isEmpty()) {
                EmptyWorkspaceWelcome(
                    projectName = state.activeProject.name,
                    onQuickCommand = { cmd -> onAction(WorkspaceAction.SelectSlashCommand(cmd)) },
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
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.messages, key = { it.id }) { message ->
                        ChatMessageItem(message = message)
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

@Composable
private fun SessionStatusDot(state: AcpSessionState) {
    val color = when (state) {
        AcpSessionState.READY -> AntigravityPalette.SuccessGreen
        AcpSessionState.STREAMING, AcpSessionState.CONNECTING, AcpSessionState.INITIALIZING -> AntigravityPalette.NeonCyan
        AcpSessionState.AWAITING_PERMISSION, AcpSessionState.AWAITING_AUTH -> AntigravityPalette.WarningAmber
        AcpSessionState.ERROR -> AntigravityPalette.CriticalRed
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .size(8.dp)
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
        color = MaterialTheme.colorScheme.errorContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
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
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "Kimlik Doğrulama Gerekli (${banner.errorCode})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Kapat", modifier = Modifier.size(16.dp))
                }
            }
            Text(
                text = banner.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onLaunchOAuth(activeProfileId, banner.authUrl) },
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("Google ile Giriş Yap")
                }
                OutlinedButton(
                    onClick = { showCodeField = !showCodeField },
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("Manuel Kod")
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
                        placeholder = { Text("OAuth doğrulama kodu") },
                    )
                    Button(
                        onClick = {
                            onSubmitCode(activeProfileId, manualCode)
                            manualCode = ""
                        },
                        enabled = manualCode.isNotBlank(),
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
        color = Color(0xFF2B2212),
        border = BorderStroke(1.dp, AntigravityPalette.WarningAmber),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Kota Limite Yaklaşıyor (%.0f%% kaldı)".format(warning.remainingPercent),
                    style = MaterialTheme.typography.labelLarge,
                    color = AntigravityPalette.WarningAmber,
                    fontWeight = FontWeight.Bold,
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
                    Text("Geç", fontWeight = FontWeight.Bold, color = AntigravityPalette.WarningAmber)
                }
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Kapat", modifier = Modifier.size(16.dp))
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
        color = Color(0xFF1F1622),
        border = BorderStroke(1.dp, AntigravityPalette.WarningAmber),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.Default.Shield,
                    contentDescription = null,
                    tint = AntigravityPalette.WarningAmber,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "Araç İzni • ${request.toolName} (${request.riskLevel.name})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = AntigravityPalette.WarningAmber,
                )
            }
            Text(
                text = request.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (request.argumentsJson.isNotBlank() && request.argumentsJson != "{}") {
                Text(
                    text = request.argumentsJson,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onDeny,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, AntigravityPalette.CriticalRed),
                ) {
                    Text("Reddet", color = AntigravityPalette.CriticalRed)
                }
                Button(
                    onClick = { onApprove(false) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = AntigravityPalette.SuccessGreen),
                ) {
                    Text("İzin Ver", color = Color(0xFF052E16), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyWorkspaceWelcome(
    projectName: String,
    onQuickCommand: (SlashCommand) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(44.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Antigravity Hazır",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "$projectName çalışma alanında root & erişilebilirlik destekli otonom ajan.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(18.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                SlashCommand.PLAN,
                SlashCommand.SWARM,
                SlashCommand.AGENTS,
                SlashCommand.USAGE,
                SlashCommand.SCHEDULE,
            ).forEach { cmd ->
                AssistChip(
                    onClick = { onQuickCommand(cmd) },
                    label = { Text("${cmd.command} • ${cmd.title}") },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatMessageItem(
    message: WorkspaceChatMessage,
) {
    when (message.role) {
        ChatMessageRole.USER -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth(0.86f),
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (message.attachments.isNotEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                message.attachments.forEach { att ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                    ) {
                                        Text(
                                            text = att,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }

        ChatMessageRole.SYSTEM -> {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    MarkdownContent(text = message.content)
                }
            }
        }

        ChatMessageRole.ASSISTANT, ChatMessageRole.TOOL -> {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Thought block accordion
                    if (!message.thinkingContent.isNullOrBlank()) {
                        ThoughtBlockAccordion(
                            thinkingText = message.thinkingContent.orEmpty(),
                            isStreaming = message.isStreaming,
                        )
                    }

                    // Collapsible Tool Execution Pills
                    if (message.toolCalls.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            message.toolCalls.forEach { call ->
                                ToolExecutionPill(call = call)
                            }
                        }
                    }

                    // Streaming Markdown / Code block body
                    if (message.content.isNotBlank()) {
                        MarkdownContent(text = message.content)
                    } else if (message.isStreaming) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
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
}

@Composable
fun ThoughtBlockAccordion(
    thinkingText: String,
    isStreaming: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp),
                    )
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
                Text(
                    text = thinkingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
fun ToolExecutionPill(
    call: ToolCallSummary,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    val statusColor = when (call.status) {
        ToolCallStatus.COMPLETED -> AntigravityPalette.SuccessGreen
        ToolCallStatus.FAILED, ToolCallStatus.CANCELLED -> AntigravityPalette.CriticalRed
        ToolCallStatus.AWAITING_APPROVAL -> AntigravityPalette.WarningAmber
        ToolCallStatus.RUNNING, ToolCallStatus.PENDING -> AntigravityPalette.NeonCyan
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = AntigravityPalette.SurfaceElevated,
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.55f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
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
                                modifier = Modifier.size(16.dp),
                            )
                        }

                        ToolCallStatus.FAILED, ToolCallStatus.CANCELLED -> {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(16.dp),
                            )
                        }

                        ToolCallStatus.AWAITING_APPROVAL -> {
                            Icon(
                                Icons.Default.Shield,
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    Icon(
                        Icons.Default.Build,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = call.toolName,
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = call.status.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
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
                            text = "Argümanlar:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        CodeBlockCard(language = "json", code = call.argumentsPreview)
                    }
                    if (!call.outputPreview.isNullOrBlank()) {
                        Text(
                            text = "Çıktı:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        CodeBlockCard(language = "output", code = call.outputPreview.orEmpty())
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
                    )
                }

                is MarkdownBlock.Code -> {
                    CodeBlockCard(language = block.language, code = block.content)
                }

                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = renderInlineMarkdown(block.text),
                        style = MaterialTheme.typography.bodyMedium,
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
) {
    val clipboard = LocalClipboardManager.current
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = AntigravityPalette.CodeBlockBg,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF141B29))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Default.Code,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = language.ifBlank { "code" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = { clipboard.setText(AnnotatedString(code)) },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Kopyala",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Text(
                text = code,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                ),
                color = Color(0xFFD6E2F5),
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(10.dp),
            )
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
                            background = Color(0xFF1F2A3E),
                            color = AntigravityPalette.NeonCyan,
                        ),
                    ) {
                        append(text.substring(index + 1, end))
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

    val isListening = state.voiceState is VoiceState.Listening
    val isStreaming = state.sessionState == AcpSessionState.STREAMING || state.messages.any { it.isStreaming }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .imePadding(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            // Slash command picker popup
            AnimatedVisibility(visible = state.showSlashMenu && state.slashSuggestions.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = AntigravityPalette.SurfaceElevated,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                ) {
                    Column(modifier = Modifier.padding(6.dp)) {
                        state.slashSuggestions.forEach { cmd ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onAction(WorkspaceAction.SelectSlashCommand(cmd)) }
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = cmd.command,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
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
                    shape = RoundedCornerShape(14.dp),
                    color = AntigravityPalette.SurfaceElevated,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                ) {
                    Column(modifier = Modifier.padding(6.dp)) {
                        state.mentionSuggestions.forEach { suggestion ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onAction(WorkspaceAction.SelectMention(suggestion)) }
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = suggestion.token,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                                Text(
                                    text = "${suggestion.category} • ${suggestion.label}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            // Live Voice Dictation Banner
            if (state.voiceState !is VoiceState.Idle) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
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
                    TextButton(
                        onClick = {
                            val next = if (state.voiceLocaleTag == "tr-TR") "en-US" else "tr-TR"
                            onAction(WorkspaceAction.ChangeVoiceLocale(next))
                        },
                    ) {
                        Text(if (state.voiceLocaleTag == "tr-TR") "TR" else "EN")
                    }
                }
            }

            // Attachments strip
            if (state.attachments.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    state.attachments.forEach { att ->
                        InputChip(
                            selected = false,
                            onClick = { onAction(WorkspaceAction.RemoveAttachment(att)) },
                            label = { Text(att, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                            leadingIcon = {
                                Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(14.dp))
                            },
                            trailingIcon = {
                                Icon(Icons.Default.Close, contentDescription = "Kaldır", modifier = Modifier.size(14.dp))
                            },
                        )
                    }
                }
            }

            // Selector chips row: Model, Effort, Permission Mode
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box {
                    AssistChip(
                        onClick = { showModelMenu = true },
                        label = { Text(state.selectedModel.displayName, style = MaterialTheme.typography.labelSmall) },
                    )
                    DropdownMenu(expanded = showModelMenu, onDismissRequest = { showModelMenu = false }) {
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
                    AssistChip(
                        onClick = { showEffortMenu = true },
                        label = { Text("Efor: ${state.selectedEffort.name}", style = MaterialTheme.typography.labelSmall) },
                    )
                    DropdownMenu(expanded = showEffortMenu, onDismissRequest = { showEffortMenu = false }) {
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
                    AssistChip(
                        onClick = { showModeMenu = true },
                        label = { Text("İzin: ${state.permissionMode.name}", style = MaterialTheme.typography.labelSmall) },
                    )
                    DropdownMenu(expanded = showModeMenu, onDismissRequest = { showModeMenu = false }) {
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

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
            )

            // Main input bar: +, @, /, TextField, Mic, Send/Stop
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                IconButton(onClick = onOpenAttachmentDialog, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "Ek ekle", modifier = Modifier.size(20.dp))
                }
                IconButton(
                    onClick = { onAction(WorkspaceAction.OpenMentionMenu) },
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(Icons.Default.AlternateEmail, contentDescription = "@ bağlam ekle", modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = { onAction(WorkspaceAction.OpenSlashMenu) },
                    modifier = Modifier.size(34.dp),
                ) {
                    Text(
                        text = "/",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                OutlinedTextField(
                    value = state.composerText,
                    onValueChange = { onAction(WorkspaceAction.UpdateComposerText(it)) },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            text = "Görev verin, `/` komut veya `@` bağlam...",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    maxLines = 4,
                    shape = RoundedCornerShape(14.dp),
                )

                IconButton(
                    onClick = { onAction(WorkspaceAction.ToggleVoiceListening) },
                    modifier = Modifier.size(38.dp),
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Bas-Konuş sesli dikte",
                        tint = if (isListening) AntigravityPalette.CriticalRed else MaterialTheme.colorScheme.primary,
                    )
                }

                if (isStreaming && state.composerText.isBlank()) {
                    IconButton(
                        onClick = { onAction(WorkspaceAction.CancelActiveSession) },
                        modifier = Modifier.size(38.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.StopCircle,
                            contentDescription = "İptal et",
                            tint = AntigravityPalette.CriticalRed,
                        )
                    }
                } else {
                    IconButton(
                        onClick = { onAction(WorkspaceAction.SendMessage()) },
                        enabled = state.composerText.isNotBlank() || state.attachments.isNotEmpty(),
                        modifier = Modifier.size(38.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Gönder",
                            tint = if (state.composerText.isNotBlank() || state.attachments.isNotEmpty()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
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
        title = { Text("Dosya / Bağlam Ekle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text("Dosya veya klasör yolu") },
                    singleLine = true,
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
            ) {
                Text("Ekle")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal") }
        },
    )
}
