package com.antigravity.mobile.feature.workspace.ui.overview

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.mobile.contracts.DiffApprovalStatus
import com.antigravity.mobile.contracts.DiffChangeType
import com.antigravity.mobile.contracts.DiffItem
import com.antigravity.mobile.contracts.SwarmBoardState
import com.antigravity.mobile.contracts.SwarmMemberStatus
import com.antigravity.mobile.feature.workspace.model.OverviewTab
import com.antigravity.mobile.feature.workspace.model.SubagentItem
import com.antigravity.mobile.feature.workspace.model.SwarmSubTab
import com.antigravity.mobile.feature.workspace.model.WorkspaceAction
import com.antigravity.mobile.feature.workspace.model.WorkspaceArtifact
import com.antigravity.mobile.feature.workspace.model.WorkspaceUiState
import com.antigravity.mobile.feature.workspace.theme.AntigravityExtendedColors
import com.antigravity.mobile.feature.workspace.theme.LocalAntigravityColors
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

    val ext = LocalAntigravityColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val projectLabel = state.selectedProject?.name ?: "Cihaz Geneli"

    ModalBottomSheet(
        onDismissRequest = { onAction(WorkspaceAction.CloseOverviewSheet) },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 400.dp, max = 660.dp)
                .padding(bottom = 22.dp),
        ) {
            // Antigravity 2.0 Auxiliary Pane Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = if (ext.isDark) 0.35f else 0.2f),
                        ),
                        modifier = Modifier.size(34.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Hub,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Çalışma Alanı Paneli",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = projectLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(
                    onClick = { onAction(WorkspaceAction.CloseOverviewSheet) },
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Paneli kapat",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Refined Segmented Tab Pills
            OverviewSegmentedTabBar(
                selectedTab = state.selectedOverviewTab,
                subagentsCount = state.subagents.size,
                swarmCount = state.swarmBoardState.members.size,
                artifactsDiffsCount = state.diffs.size + state.artifacts.size,
                onSelectTab = { onAction(WorkspaceAction.SelectOverviewTab(it)) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(modifier = Modifier.height(12.dp))

            HorizontalDivider(color = ext.cardBorder)

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
private fun OverviewSegmentedTabBar(
    selectedTab: OverviewTab,
    subagentsCount: Int,
    swarmCount: Int,
    artifactsDiffsCount: Int,
    onSelectTab: (OverviewTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ext = LocalAntigravityColors.current

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, ext.cardBorder),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SegmentedTabPill(
                label = "Alt Ajanlar",
                count = subagentsCount,
                selected = selectedTab == OverviewTab.SUBAGENTS,
                onClick = { onSelectTab(OverviewTab.SUBAGENTS) },
                modifier = Modifier.weight(1f),
            )
            SegmentedTabPill(
                label = "Swarm",
                count = swarmCount,
                selected = selectedTab == OverviewTab.SWARM,
                onClick = { onSelectTab(OverviewTab.SWARM) },
                modifier = Modifier.weight(0.85f),
            )
            SegmentedTabPill(
                label = "Artifacts & Diffs",
                count = artifactsDiffsCount,
                selected = selectedTab == OverviewTab.ARTIFACTS_DIFFS,
                onClick = { onSelectTab(OverviewTab.ARTIFACTS_DIFFS) },
                modifier = Modifier.weight(1.22f),
            )
        }
    }
}

@Composable
private fun SegmentedTabPill(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ext = LocalAntigravityColors.current

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.surface
        } else {
            Color.Transparent
        },
        shadowElevation = if (selected && !ext.isDark) 1.dp else 0.dp,
        border = if (selected) {
            BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = if (ext.isDark) 0.45f else 0.25f),
            )
        } else {
            null
        },
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp),
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (count > 0) {
                Spacer(modifier = Modifier.width(5.dp))
                Surface(
                    shape = CircleShape,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                ) {
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
    val ext = LocalAntigravityColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 42.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, ext.cardBorder),
            modifier = Modifier.size(48.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SubagentsTabContent(
    subagents: List<SubagentItem>,
) {
    val ext = LocalAntigravityColors.current
    val secondaryColor = MaterialTheme.colorScheme.secondary

    if (subagents.isEmpty()) {
        OverviewEmptyState(
            icon = Icons.Default.SmartToy,
            title = "Aktif Alt Ajan Yok",
            subtitle = "Arka plan alt ajanlarını başlatmak veya görmek için `/agents` kullanın.",
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(subagents, key = { it.id }) { agent ->
            val statusColor = memberStatusColor(agent.status, ext, secondaryColor)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, ext.cardBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(statusColor),
                            )
                            Text(
                                text = agent.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = statusColor.copy(alpha = if (ext.isDark) 0.16f else 0.12f),
                            border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f)),
                        ) {
                            Text(
                                text = agent.status.name,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = statusColor,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
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
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SwarmTabContent(
    boardState: SwarmBoardState,
    selectedSubTab: SwarmSubTab,
    onSelectSubTab: (SwarmSubTab) -> Unit,
) {
    val ext = LocalAntigravityColors.current
    val secondaryColor = MaterialTheme.colorScheme.secondary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (boardState.swarmTitle.isNotBlank()) {
            Text(
                text = boardState.swarmTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        // Sub-tabs: Board, Members, Graph as sleek pills
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SwarmSubTabPill(
                label = "Board (${boardState.messages.size})",
                selected = selectedSubTab == SwarmSubTab.BOARD,
                onClick = { onSelectSubTab(SwarmSubTab.BOARD) },
            )
            SwarmSubTabPill(
                label = "Members (${boardState.members.size})",
                selected = selectedSubTab == SwarmSubTab.MEMBERS,
                onClick = { onSelectSubTab(SwarmSubTab.MEMBERS) },
            )
            SwarmSubTabPill(
                label = "Graph (${boardState.edges.size})",
                selected = selectedSubTab == SwarmSubTab.GRAPH,
                onClick = { onSelectSubTab(SwarmSubTab.GRAPH) },
            )
        }

        when (selectedSubTab) {
            SwarmSubTab.BOARD -> {
                if (boardState.messages.isEmpty()) {
                    OverviewEmptyState(
                        icon = Icons.Default.Hub,
                        title = "Swarm Panosu Boş",
                        subtitle = "Çoklu ajan görev dağılımı için `/swarm` komutunu kullanın.",
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(boardState.messages, key = { it.seq }) { msg ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                border = BorderStroke(1.dp, ext.cardBorder),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                            ) {
                                                Text(
                                                    text = "#${msg.seq}",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                )
                                            }
                                            Text(
                                                text = msg.sender,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        }
                                        if (msg.mentions.isNotEmpty()) {
                                            Text(
                                                text = msg.mentions.joinToString(" ") { "@$it" },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.secondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    Text(
                                        text = msg.text,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            SwarmSubTab.MEMBERS -> {
                if (boardState.members.isEmpty()) {
                    OverviewEmptyState(
                        icon = Icons.Default.SmartToy,
                        title = "Swarm Üyesi Yok",
                        subtitle = "Aktif Swarm çalıştırıldığında üyeler burada listelenir.",
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(boardState.members, key = { it.name }) { member ->
                            val color = memberStatusColor(member.status, ext, secondaryColor)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                border = BorderStroke(1.dp, ext.cardBorder),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(color),
                                            )
                                            Text(
                                                text = member.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = color.copy(alpha = if (ext.isDark) 0.16f else 0.12f),
                                            border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
                                        ) {
                                            Text(
                                                text = member.status.name,
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                color = color,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            )
                                        }
                                    }
                                    if (member.ownedModules.isNotEmpty()) {
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            member.ownedModules.forEach { mod ->
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.surface,
                                                    border = BorderStroke(1.dp, ext.cardBorder),
                                                ) {
                                                    Text(
                                                        text = mod,
                                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    if (member.lastUpdateText.isNotBlank()) {
                                        Text(
                                            text = member.lastUpdateText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun SwarmSubTabPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val ext = LocalAntigravityColors.current

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        border = BorderStroke(
            1.dp,
            if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = if (ext.isDark) 0.55f else 0.3f)
            } else {
                ext.cardBorder
            },
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SwarmInteractionGraph(
    boardState: SwarmBoardState,
) {
    val ext = LocalAntigravityColors.current
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val members = boardState.members
    if (members.isEmpty()) {
        OverviewEmptyState(
            icon = Icons.Default.AccountTree,
            title = "Etkileşim Grafiği Bekleniyor",
            subtitle = "Aktif Swarm üyeleri ve mesaj akışı oluştuğunda grafik çizilir.",
        )
        return
    }

    val edgeLineColor = ext.accentCyan.copy(alpha = if (ext.isDark) 0.6f else 0.5f)
    val guideRingColor = ext.cardBorder.copy(alpha = 0.7f)
    val nodeColors = members.associate { it.name to memberStatusColor(it.status, ext, secondaryColor) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = ext.codeBlockBackground,
            border = BorderStroke(1.dp, ext.cardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp),
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp),
            ) {
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                val radius = (minOf(size.width, size.height) * 0.34f).coerceAtLeast(48f)

                // Subtle orbit guide ring
                drawCircle(
                    color = guideRingColor,
                    radius = radius,
                    center = Offset(centerX, centerY),
                    style = Stroke(width = 1.dp.toPx()),
                )

                val positions = members.mapIndexed { index, member ->
                    val angle = (2.0 * PI * index / members.size) - (PI / 2.0)
                    val x = centerX + (radius * cos(angle)).toFloat()
                    val y = centerY + (radius * sin(angle)).toFloat()
                    member.name to Offset(x, y)
                }.toMap()

                // Draw interaction edges
                boardState.edges.forEach { edge ->
                    val start = positions[edge.fromMember]
                    val end = positions[edge.toMember]
                    if (start != null && end != null) {
                        drawLine(
                            color = edgeLineColor,
                            start = start,
                            end = end,
                            strokeWidth = (2f + edge.messageCount.coerceAtMost(6)).dp.toPx() * 0.45f,
                        )
                    }
                }

                // Draw member nodes
                members.forEach { member ->
                    val pos = positions[member.name] ?: return@forEach
                    val color = nodeColors[member.name] ?: ext.accentCyan
                    drawCircle(
                        color = color.copy(alpha = 0.2f),
                        radius = 22.dp.toPx(),
                        center = pos,
                    )
                    drawCircle(
                        color = color,
                        radius = 11.dp.toPx(),
                        center = pos,
                    )
                }
            }
        }

        // Member legend chips
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            members.forEach { member ->
                val color = nodeColors[member.name] ?: ext.accentCyan
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(color),
                        )
                        Text(
                            text = member.name,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        // Edge Summary
        if (boardState.edges.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, ext.cardBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
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
        OverviewEmptyState(
            icon = Icons.Default.Code,
            title = "Artifact veya Diff Yok",
            subtitle = "Üretilen kod farkları ve planlar burada listelenir (`/diff`).",
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (diffs.isNotEmpty()) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Kod Farkları (${diffs.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Artifacts & Planlar (${artifacts.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
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
    val ext = LocalAntigravityColors.current

    val statusColor = when (diff.status) {
        DiffApprovalStatus.APPROVED -> ext.success
        DiffApprovalStatus.REJECTED -> ext.critical
        DiffApprovalStatus.PENDING -> ext.warning
    }

    val changeTypeColor = when (diff.changeType) {
        DiffChangeType.ADDED -> ext.success
        DiffChangeType.DELETED -> ext.critical
        DiffChangeType.RENAMED -> MaterialTheme.colorScheme.secondary
        DiffChangeType.MODIFIED -> ext.accentCyan
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.42f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = diff.filePath,
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = ext.diffAddBackground,
                            border = BorderStroke(1.dp, ext.diffAddForeground.copy(alpha = 0.3f)),
                        ) {
                            Text(
                                text = "+${diff.additions}",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = ext.diffAddForeground,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = ext.diffRemoveBackground,
                            border = BorderStroke(1.dp, ext.diffRemoveForeground.copy(alpha = 0.3f)),
                        ) {
                            Text(
                                text = "-${diff.deletions}",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = ext.diffRemoveForeground,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = changeTypeColor.copy(alpha = if (ext.isDark) 0.16f else 0.12f),
                        ) {
                            Text(
                                text = diff.changeType.name,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = changeTypeColor,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = CircleShape,
                    color = statusColor.copy(alpha = if (ext.isDark) 0.16f else 0.12f),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f)),
                ) {
                    Text(
                        text = diff.status.name,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = statusColor,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }

            HorizontalDivider(color = ext.cardBorder)

            // Line-by-line Light & Dark Theme-Adaptive Unified Diff Viewer
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = ext.codeBlockBackground,
                border = BorderStroke(1.dp, ext.cardBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                ) {
                    diff.unifiedDiff.lines().forEach { line ->
                        val (bg, fg) = when {
                            line.startsWith("+") && !line.startsWith("+++") ->
                                ext.diffAddBackground to ext.diffAddForeground
                            line.startsWith("-") && !line.startsWith("---") ->
                                ext.diffRemoveBackground to ext.diffRemoveForeground
                            line.startsWith("@@") ->
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f) to
                                    MaterialTheme.colorScheme.onPrimaryContainer
                            else ->
                                Color.Transparent to MaterialTheme.colorScheme.onSurface
                        }
                        Text(
                            text = line.ifEmpty { " " },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.5.sp,
                                lineHeight = 17.sp,
                            ),
                            color = fg,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(bg)
                                .padding(horizontal = 8.dp, vertical = 1.dp),
                        )
                    }
                }
            }

            if (diff.status == DiffApprovalStatus.PENDING) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = onReject,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ext.critical.copy(alpha = 0.6f)),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = ext.critical,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reddet", color = ext.critical, fontWeight = FontWeight.Medium)
                    }
                    Button(
                        onClick = onApprove,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Onayla", fontWeight = FontWeight.SemiBold)
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
    val ext = LocalAntigravityColors.current
    var expanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, ext.cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
    ) {
        Column(modifier = Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.secondary.copy(alpha = if (ext.isDark) 0.35f else 0.2f),
                    ),
                    modifier = Modifier.size(34.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = artifact.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = artifact.filePath ?: artifact.mimeType,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Daralt" else "Genişlet",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            AnimatedVisibility(visible = expanded && artifact.content.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ext.codeBlockBackground,
                    border = BorderStroke(1.dp, ext.cardBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = artifact.content,
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(11.dp),
                    )
                }
            }
        }
    }
}

private fun memberStatusColor(
    status: SwarmMemberStatus,
    ext: AntigravityExtendedColors,
    secondaryColor: Color,
): Color = when (status) {
    SwarmMemberStatus.WORKING -> ext.accentCyan
    SwarmMemberStatus.DONE -> ext.success
    SwarmMemberStatus.WAITING -> secondaryColor
    SwarmMemberStatus.ERROR, SwarmMemberStatus.KILLED -> ext.critical
    SwarmMemberStatus.STOPPED -> ext.warning
}
