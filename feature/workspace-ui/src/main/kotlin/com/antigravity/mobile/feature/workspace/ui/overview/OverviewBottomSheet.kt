package com.antigravity.mobile.feature.workspace.ui.overview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.mobile.contracts.DiffApprovalStatus
import com.antigravity.mobile.contracts.DiffItem
import com.antigravity.mobile.contracts.SwarmBoardState
import com.antigravity.mobile.contracts.SwarmMemberStatus
import com.antigravity.mobile.feature.workspace.model.OverviewTab
import com.antigravity.mobile.feature.workspace.model.SubagentItem
import com.antigravity.mobile.feature.workspace.model.SwarmSubTab
import com.antigravity.mobile.feature.workspace.model.WorkspaceAction
import com.antigravity.mobile.feature.workspace.model.WorkspaceArtifact
import com.antigravity.mobile.feature.workspace.model.WorkspaceUiState
import com.antigravity.mobile.feature.workspace.theme.AntigravityPalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewBottomSheet(
    state: WorkspaceUiState,
    onAction: (WorkspaceAction) -> Unit,
) {
    if (!state.isOverviewSheetVisible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { onAction(WorkspaceAction.CloseOverviewSheet) },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 380.dp, max = 640.dp)
                .padding(bottom = 20.dp),
        ) {
            TabRow(
                selectedTabIndex = state.selectedOverviewTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                Tab(
                    selected = state.selectedOverviewTab == OverviewTab.SUBAGENTS,
                    onClick = { onAction(WorkspaceAction.SelectOverviewTab(OverviewTab.SUBAGENTS)) },
                    text = { Text("Alt Ajanlar (${state.subagents.size})", maxLines = 1) },
                )
                Tab(
                    selected = state.selectedOverviewTab == OverviewTab.SWARM,
                    onClick = { onAction(WorkspaceAction.SelectOverviewTab(OverviewTab.SWARM)) },
                    text = { Text("Swarm (${state.swarmBoardState.members.size})", maxLines = 1) },
                )
                Tab(
                    selected = state.selectedOverviewTab == OverviewTab.ARTIFACTS_DIFFS,
                    onClick = { onAction(WorkspaceAction.SelectOverviewTab(OverviewTab.ARTIFACTS_DIFFS)) },
                    text = { Text("Diff & Plan (${state.diffs.size + state.artifacts.size})", maxLines = 1) },
                )
            }

            when (state.selectedOverviewTab) {
                OverviewTab.SUBAGENTS -> SubagentsTabContent(
                    subagents = state.subagents,
                )

                OverviewTab.SWARM -> SwarmTabContent(
                    boardState = state.swarmBoardState,
                    selectedSubTab = state.selectedSwarmSubTab,
                    onSelectSubTab = { onAction(WorkspaceAction.SelectSwarmSubTab(it)) },
                )

                OverviewTab.ARTIFACTS_DIFFS -> ArtifactsAndDiffsTabContent(
                    artifacts = state.artifacts,
                    diffs = state.diffs,
                    onApproveDiff = { onAction(WorkspaceAction.ApproveDiff(it)) },
                    onRejectDiff = { onAction(WorkspaceAction.RejectDiff(it)) },
                )
            }
        }
    }
}

@Composable
private fun SubagentsTabContent(
    subagents: List<SubagentItem>,
) {
    if (subagents.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Aktif arka plan alt ajanı bulunmuyor (`/agents`).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(subagents, key = { it.id }) { agent ->
            val statusColor = memberStatusColor(agent.status)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(statusColor),
                            )
                            Text(
                                text = agent.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            text = agent.status.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        text = "${agent.role} • ${agent.model}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (agent.lastUpdate.isNotBlank()) {
                        Text(
                            text = agent.lastUpdate,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SwarmTabContent(
    boardState: SwarmBoardState,
    selectedSubTab: SwarmSubTab,
    onSelectSubTab: (SwarmSubTab) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (boardState.swarmTitle.isNotBlank()) {
            Text(
                text = boardState.swarmTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        // Sub-tabs: Board, Members, Graph
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selectedSubTab == SwarmSubTab.BOARD,
                onClick = { onSelectSubTab(SwarmSubTab.BOARD) },
                label = { Text("Board (${boardState.messages.size})") },
            )
            FilterChip(
                selected = selectedSubTab == SwarmSubTab.MEMBERS,
                onClick = { onSelectSubTab(SwarmSubTab.MEMBERS) },
                label = { Text("Members (${boardState.members.size})") },
            )
            FilterChip(
                selected = selectedSubTab == SwarmSubTab.GRAPH,
                onClick = { onSelectSubTab(SwarmSubTab.GRAPH) },
                label = { Text("Graph (${boardState.edges.size})") },
            )
        }

        when (selectedSubTab) {
            SwarmSubTab.BOARD -> {
                if (boardState.messages.isEmpty()) {
                    Text(
                        text = "Swarm ortak panosunda henüz mesaj yok.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(boardState.messages, key = { it.seq }) { msg ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = "#${msg.seq} • ${msg.sender}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        if (msg.mentions.isNotEmpty()) {
                                            Text(
                                                text = msg.mentions.joinToString(" ") { "@$it" },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.secondary,
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = msg.text,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            SwarmSubTab.MEMBERS -> {
                if (boardState.members.isEmpty()) {
                    Text(
                        text = "Swarm üyesi bulunmuyor.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(boardState.members, key = { it.name }) { member ->
                            val color = memberStatusColor(member.status)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = member.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            text = member.status.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = color,
                                        )
                                    }
                                    if (member.ownedModules.isNotEmpty()) {
                                        Text(
                                            text = "Modüller: ${member.ownedModules.joinToString(", ")}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (member.lastUpdateText.isNotBlank()) {
                                        Text(
                                            text = member.lastUpdateText,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            SwarmSubTab.GRAPH -> {
                SwarmInteractionGraph(boardState = boardState)
            }
        }
    }
}

@Composable
private fun SwarmInteractionGraph(
    boardState: SwarmBoardState,
) {
    val members = boardState.members
    if (members.isEmpty()) {
        Text(
            text = "Etkileşim grafiği için aktif Swarm üyeleri bekleniyor.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = AntigravityPalette.CodeBlockBg,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp),
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(210.dp)) {
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                val radius = (minOf(size.width, size.height) * 0.34f).coerceAtLeast(48f)

                val positions = members.mapIndexed { index, member ->
                    val angle = (2.0 * PI * index / members.size) - (PI / 2.0)
                    val x = centerX + (radius * cos(angle)).toFloat()
                    val y = centerY + (radius * sin(angle)).toFloat()
                    member.name to Offset(x, y)
                }.toMap()

                // Draw edges
                boardState.edges.forEach { edge ->
                    val start = positions[edge.fromMember]
                    val end = positions[edge.toMember]
                    if (start != null && end != null) {
                        drawLine(
                            color = AntigravityPalette.NeonCyan.copy(alpha = 0.65f),
                            start = start,
                            end = end,
                            strokeWidth = (2f + edge.messageCount.coerceAtMost(6)).dp.toPx() * 0.45f,
                        )
                    }
                }

                // Draw member nodes
                members.forEach { member ->
                    val pos = positions[member.name] ?: return@forEach
                    val color = memberStatusColor(member.status)
                    drawCircle(
                        color = color.copy(alpha = 0.25f),
                        radius = 22.dp.toPx(),
                        center = pos,
                    )
                    drawCircle(
                        color = color,
                        radius = 12.dp.toPx(),
                        center = pos,
                    )
                }
            }
        }

        // Legend & Edge Summary
        boardState.edges.forEach { edge ->
            Text(
                text = "${edge.fromMember} → ${edge.toMember} (${edge.messageCount} mesaj)",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ArtifactsAndDiffsTabContent(
    artifacts: List<WorkspaceArtifact>,
    diffs: List<DiffItem>,
    onApproveDiff: (String) -> Unit,
    onRejectDiff: (String) -> Unit,
) {
    if (artifacts.isEmpty() && diffs.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Henüz Artifact veya Diff üretilmedi (`/diff`).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (diffs.isNotEmpty()) {
            item {
                Text(
                    text = "Kod Farkları (Diff Viewer)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            items(diffs, key = { it.id }) { diff ->
                DiffReviewCard(
                    diff = diff,
                    onApprove = { onApproveDiff(diff.id) },
                    onReject = { onRejectDiff(diff.id) },
                )
            }
        }

        if (artifacts.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Artifacts & Planlar",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            items(artifacts, key = { it.id }) { artifact ->
                ArtifactCard(artifact = artifact)
            }
        }
    }
}

@Composable
fun DiffReviewCard(
    diff: DiffItem,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    val statusColor = when (diff.status) {
        DiffApprovalStatus.APPROVED -> AntigravityPalette.SuccessGreen
        DiffApprovalStatus.REJECTED -> AntigravityPalette.CriticalRed
        DiffApprovalStatus.PENDING -> AntigravityPalette.WarningAmber
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = AntigravityPalette.CodeBlockBg,
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = diff.filePath,
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "+${diff.additions}",
                            style = MaterialTheme.typography.labelSmall,
                            color = AntigravityPalette.DiffAddFg,
                        )
                        Text(
                            text = "-${diff.deletions}",
                            style = MaterialTheme.typography.labelSmall,
                            color = AntigravityPalette.DiffRemoveFg,
                        )
                        Text(
                            text = diff.changeType.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = statusColor.copy(alpha = 0.2f),
                ) {
                    Text(
                        text = diff.status.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

            // Line-by-line mobile-friendly unified diff
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            ) {
                diff.unifiedDiff.lines().forEach { line ->
                    val (bg, fg) = when {
                        line.startsWith("+") && !line.startsWith("+++") ->
                            AntigravityPalette.DiffAddBg to AntigravityPalette.DiffAddFg
                        line.startsWith("-") && !line.startsWith("---") ->
                            AntigravityPalette.DiffRemoveBg to AntigravityPalette.DiffRemoveFg
                        line.startsWith("@@") ->
                            Color(0xFF172542) to AntigravityPalette.NeonCyan
                        else ->
                            Color.Transparent to Color(0xFFCBD5E1)
                    }
                    Text(
                        text = line.ifEmpty { " " },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp,
                        ),
                        color = fg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bg)
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }

            if (diff.status == DiffApprovalStatus.PENDING) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onReject,
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, AntigravityPalette.CriticalRed),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp), tint = AntigravityPalette.CriticalRed)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reddet", color = AntigravityPalette.CriticalRed)
                    }
                    Button(
                        onClick = onApprove,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = AntigravityPalette.SuccessGreen),
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF052E16))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Onayla", color = Color(0xFF052E16), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtifactCard(
    artifact: WorkspaceArtifact,
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Default.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = artifact.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = artifact.filePath ?: artifact.mimeType,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (expanded && artifact.content.isNotBlank()) {
                Text(
                    text = artifact.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private fun memberStatusColor(status: SwarmMemberStatus): Color = when (status) {
    SwarmMemberStatus.WORKING -> AntigravityPalette.NeonCyan
    SwarmMemberStatus.DONE -> AntigravityPalette.SuccessGreen
    SwarmMemberStatus.WAITING -> AntigravityPalette.HydrogenViolet
    SwarmMemberStatus.ERROR, SwarmMemberStatus.KILLED -> AntigravityPalette.CriticalRed
    SwarmMemberStatus.STOPPED -> AntigravityPalette.WarningAmber
}
