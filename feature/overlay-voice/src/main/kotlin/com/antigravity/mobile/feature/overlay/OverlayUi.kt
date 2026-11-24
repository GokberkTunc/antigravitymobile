package com.antigravity.mobile.feature.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.mobile.contracts.ApprovalRequest
import com.antigravity.mobile.contracts.McpToolRisk
import com.antigravity.mobile.contracts.OverlayState
import com.antigravity.mobile.contracts.VoiceState

private val OverlayDarkScheme = darkColorScheme(
    primary = Color(0xFF63D2FF),
    onPrimary = Color(0xFF001F2A),
    secondary = Color(0xFFA78BFA),
    background = Color(0xFF0B0E14),
    surface = Color(0xF0111622),
    surfaceVariant = Color(0xFF1A2233),
    onSurface = Color(0xFFE6EDF6),
    onSurfaceVariant = Color(0xFF9AA8BE),
    error = Color(0xFFFF5A5F),
    onError = Color(0xFFFFFFFF),
)

private val OrbRunningColor = Color(0xFF38BDF8)
private val OrbApprovalColor = Color(0xFFF59E0B)
private val OrbKilledColor = Color(0xFFEF4444)
private val OrbIdleColor = Color(0xFF818CF8)
private val ApproveGreen = Color(0xFF22C55E)

@Composable
fun OverlayRootContent(
    state: OverlayState,
    voiceLocaleTag: String,
    isTextInputOpen: Boolean,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onToggleExpanded: () -> Unit,
    onHide: () -> Unit,
    onToggleVoice: () -> Unit,
    onToggleLocale: () -> Unit,
    onApprove: (String) -> Unit,
    onDeny: (String) -> Unit,
    onEmergencyStop: () -> Unit,
    onToggleTextInput: (Boolean) -> Unit,
    onSubmitTextPrompt: (String) -> Unit,
) {
    MaterialTheme(colorScheme = OverlayDarkScheme) {
        if (!state.isVisible) return@MaterialTheme

        if (!state.isExpanded) {
            FloatingStatusOrb(
                state = state,
                onDrag = onDrag,
                onClick = onToggleExpanded,
            )
        } else {
            ExpandedOverlayPanel(
                state = state,
                voiceLocaleTag = voiceLocaleTag,
                isTextInputOpen = isTextInputOpen,
                onDrag = onDrag,
                onCollapse = onToggleExpanded,
                onHide = onHide,
                onToggleVoice = onToggleVoice,
                onToggleLocale = onToggleLocale,
                onApprove = onApprove,
                onDeny = onDeny,
                onEmergencyStop = onEmergencyStop,
                onToggleTextInput = onToggleTextInput,
                onSubmitTextPrompt = onSubmitTextPrompt,
            )
        }
    }
}

@Composable
fun FloatingStatusOrb(
    state: OverlayState,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accentColor = when {
        state.isKilled -> OrbKilledColor
        state.pendingApproval != null -> OrbApprovalColor
        state.isAgentRunning -> OrbRunningColor
        else -> OrbIdleColor
    }

    Box(
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(Color(0xEE101624))
            .border(width = 2.dp, color = accentColor, shape = CircleShape)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            state.pendingApproval != null -> {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Onay bekliyor",
                    tint = accentColor,
                    modifier = Modifier.size(26.dp),
                )
            }

            state.isAgentRunning -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(26.dp),
                    color = accentColor,
                    strokeWidth = 2.5.dp,
                )
            }

            else -> {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(accentColor),
                )
            }
        }
    }
}

@Composable
fun ExpandedOverlayPanel(
    state: OverlayState,
    voiceLocaleTag: String,
    isTextInputOpen: Boolean,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onCollapse: () -> Unit,
    onHide: () -> Unit,
    onToggleVoice: () -> Unit,
    onToggleLocale: () -> Unit,
    onApprove: (String) -> Unit,
    onDeny: (String) -> Unit,
    onEmergencyStop: () -> Unit,
    onToggleTextInput: (Boolean) -> Unit,
    onSubmitTextPrompt: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draftPrompt by remember { mutableStateOf("") }

    Surface(
        modifier = modifier
            .widthIn(min = 296.dp, max = 352.dp)
            .padding(4.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = when {
                state.isKilled -> OrbKilledColor
                state.pendingApproval != null -> OrbApprovalColor
                else -> Color(0xFF28354E)
            },
        ),
        shadowElevation = 12.dp,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Draggable Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.x, dragAmount.y)
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val dotColor = when {
                        state.isKilled -> OrbKilledColor
                        state.pendingApproval != null -> OrbApprovalColor
                        state.isAgentRunning -> OrbRunningColor
                        else -> OrbIdleColor
                    }
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(dotColor),
                    )
                    Text(
                        text = "Antigravity Canlı Katman",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onCollapse,
                        modifier = Modifier.size(30.dp),
                    ) {
                        Text(
                            text = "—",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    IconButton(
                        onClick = onHide,
                        modifier = Modifier.size(30.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Katmanı gizle",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Live Action Ticker
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.isAgentRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = OrbRunningColor,
                        )
                    }
                    Text(
                        text = state.tickerText.ifBlank {
                            if (state.isAgentRunning) "Ajan çalışıyor..." else "Beklemede"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.isKilled) OrbKilledColor else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Critical Tool Approval Card
            AnimatedVisibility(
                visible = state.pendingApproval != null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                state.pendingApproval?.let { approval ->
                    Column {
                        Spacer(modifier = Modifier.height(10.dp))
                        CriticalApprovalCard(
                            request = approval,
                            onApprove = { onApprove(approval.requestId) },
                            onDeny = { onDeny(approval.requestId) },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFF232D42))
            Spacer(modifier = Modifier.height(10.dp))

            // Push-to-Talk & Optional Text Prompt Controls
            VoiceControlSection(
                voiceState = state.voiceState,
                voiceLocaleTag = voiceLocaleTag,
                isTextInputOpen = isTextInputOpen,
                onToggleVoice = onToggleVoice,
                onToggleLocale = onToggleLocale,
                onToggleTextInput = { onToggleTextInput(!isTextInputOpen) },
            )

            AnimatedVisibility(visible = isTextInputOpen) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    OutlinedTextField(
                        value = draftPrompt,
                        onValueChange = { draftPrompt = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("Hızlı komut...", fontSize = 12.sp) },
                    )
                    IconButton(
                        onClick = {
                            if (draftPrompt.isNotBlank()) {
                                onSubmitTextPrompt(draftPrompt)
                                draftPrompt = ""
                                onToggleTextInput(false)
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Gönder",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // One-Tap Emergency Kill / Stop Button
            Button(
                onClick = onEmergencyStop,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OrbKilledColor,
                    contentColor = Color.White,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.StopCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Acil Durdur (Stop)",
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun CriticalApprovalCard(
    request: ApprovalRequest,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
) {
    val riskColor = when (request.riskLevel) {
        McpToolRisk.CRITICAL -> OrbKilledColor
        McpToolRisk.HIGH -> OrbApprovalColor
        McpToolRisk.MEDIUM -> OrbRunningColor
        McpToolRisk.LOW -> ApproveGreen
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF1F1620),
        border = BorderStroke(1.dp, riskColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = riskColor,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "${request.riskLevel.name} • ${request.toolName}",
                    style = MaterialTheme.typography.labelMedium,
                    color = riskColor,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = request.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (request.summary.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = request.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!request.foregroundPackage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Korumalı ön plan paketi: ${request.foregroundPackage}",
                    style = MaterialTheme.typography.labelSmall,
                    color = OrbApprovalColor,
                )
            }
            if (request.argumentsPreview.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0E1118),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = request.argumentsPreview,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        ),
                        color = Color(0xFFCBD5E1),
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onDeny,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, OrbKilledColor),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = OrbKilledColor),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reddet", fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = onApprove,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ApproveGreen,
                        contentColor = Color(0xFF052E16),
                    ),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("İzin Ver", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun VoiceControlSection(
    voiceState: VoiceState,
    voiceLocaleTag: String,
    isTextInputOpen: Boolean,
    onToggleVoice: () -> Unit,
    onToggleLocale: () -> Unit,
    onToggleTextInput: () -> Unit,
) {
    val isListening = voiceState is VoiceState.Listening
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onToggleVoice,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isListening) OrbRunningColor else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (isListening) Color(0xFF001F2A) else MaterialTheme.colorScheme.onSurface,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = "Sesli komut",
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isListening) "Dinlemeyi Bitir" else "Bas-Konuş (Ses)",
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            OutlinedButton(
                onClick = onToggleLocale,
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(
                    text = if (voiceLocaleTag.startsWith("tr", ignoreCase = true)) "TR" else "EN",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                )
            }

            IconButton(onClick = onToggleTextInput) {
                Icon(
                    imageVector = Icons.Default.Keyboard,
                    contentDescription = "Klavye ile yaz",
                    tint = if (isTextInputOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val voiceBannerText = when (voiceState) {
            is VoiceState.Idle -> null
            is VoiceState.Listening -> voiceState.partialText.ifBlank { "Dinleniyor ($voiceLocaleTag)..." }
            is VoiceState.Processing -> voiceState.partialText.ifBlank { "İşleniyor..." }
            is VoiceState.Result -> "Algılandı: ${voiceState.transcript}"
            is VoiceState.Error -> voiceState.message
        }

        if (voiceBannerText != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = voiceBannerText,
                style = MaterialTheme.typography.bodySmall,
                color = if (voiceState is VoiceState.Error) OrbKilledColor else MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
