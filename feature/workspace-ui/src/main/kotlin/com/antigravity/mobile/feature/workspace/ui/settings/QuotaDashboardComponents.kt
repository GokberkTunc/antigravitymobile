package com.antigravity.mobile.feature.workspace.ui.settings

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antigravity.mobile.contracts.AccountPlanTier
import com.antigravity.mobile.contracts.AccountProfile
import com.antigravity.mobile.contracts.UsageQuota
import com.antigravity.mobile.feature.workspace.model.SettingsCategory
import com.antigravity.mobile.feature.workspace.model.WorkspaceAction
import com.antigravity.mobile.feature.workspace.model.WorkspaceUiState
import com.antigravity.mobile.feature.workspace.theme.AntigravityPalette
import kotlin.math.roundToInt

/**
 * Pure Kotlin formatting and color helpers for visual quota dashboards and gauges.
 */
object QuotaFormatter {
    private const val MINUTE_MS = 60_000L
    private const val HOUR_MS = 60L * MINUTE_MS
    private const val DAY_MS = 24L * HOUR_MS

    fun formatRemainingPercent(quota: UsageQuota): String {
        val percent = quota.remainingPercent ?: return "Bilinmiyor"
        val clamped = percent.coerceIn(0.0, 100.0).roundToInt()
        return "%$clamped"
    }

    fun formatRequestsSummary(quota: UsageQuota): String? {
        val used = quota.usedRequests ?: return null
        val limit = quota.limitRequests
        return if (limit != null) {
            "$used / $limit istek"
        } else {
            "$used istek"
        }
    }

    fun formatResetCountdown(
        resetAtEpochMs: Long?,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): String? {
        if (resetAtEpochMs == null || resetAtEpochMs <= 0L) return null
        val remainingMs = resetAtEpochMs - nowEpochMs
        if (remainingMs < MINUTE_MS) {
            return "Yenilenmek üzere"
        }

        val totalMinutes = remainingMs / MINUTE_MS
        val days = totalMinutes / (24L * 60L)
        val hours = (totalMinutes % (24L * 60L)) / 60L
        val minutes = totalMinutes % 60L

        return when {
            days > 0L && hours > 0L -> "${days}g ${hours}s sonra yenilenir"
            days > 0L -> "${days}g sonra yenilenir"
            hours > 0L && minutes > 0L -> "${hours}s ${minutes}dk sonra yenilenir"
            hours > 0L -> "${hours}s sonra yenilenir"
            else -> "${minutes}dk sonra yenilenir"
        }
    }

    fun quotaColor(remainingPercent: Double?): Color = when {
        remainingPercent == null -> AntigravityPalette.TextSecondary
        remainingPercent > 40.0 -> AntigravityPalette.SuccessGreen
        remainingPercent > 15.0 -> AntigravityPalette.WarningAmber
        else -> AntigravityPalette.CriticalRed
    }

    fun formatLastUpdated(
        updatedAtEpochMs: Long,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): String {
        if (updatedAtEpochMs <= 0L) return "Henüz güncellenmedi"
        val elapsedMs = (nowEpochMs - updatedAtEpochMs).coerceAtLeast(0L)
        return when {
            elapsedMs < MINUTE_MS -> "Az önce güncellendi"
            elapsedMs < HOUR_MS -> "${elapsedMs / MINUTE_MS} dk önce güncellendi"
            elapsedMs < DAY_MS -> "${elapsedMs / HOUR_MS} sa önce güncellendi"
            else -> "${elapsedMs / DAY_MS} gün önce güncellendi"
        }
    }

    fun planTierLabel(planTier: AccountPlanTier): String = when (planTier) {
        AccountPlanTier.FREE -> "FREE"
        AccountPlanTier.PRO -> "PRO"
        AccountPlanTier.ULTRA -> "ULTRA"
        AccountPlanTier.ENTERPRISE -> "ENTERPRISE"
        AccountPlanTier.UNKNOWN -> "PLAN"
    }

    fun planTierColor(planTier: AccountPlanTier): Color = when (planTier) {
        AccountPlanTier.FREE -> AntigravityPalette.TextSecondary
        AccountPlanTier.PRO -> AntigravityPalette.NeonCyan
        AccountPlanTier.ULTRA -> AntigravityPalette.HydrogenViolet
        AccountPlanTier.ENTERPRISE -> AntigravityPalette.CarbonTeal
        AccountPlanTier.UNKNOWN -> AntigravityPalette.TextMuted
    }
}

/**
 * Modern radial-gauge quota card displaying an account's remaining quota, plan tier,
 * request usage bar, reset countdown, last-updated timestamp, and quick actions.
 */
@Composable
fun QuotaGaugeCard(
    profile: AccountProfile,
    isActive: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onSwitch: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val quota = profile.quota
    val gaugeColor = QuotaFormatter.quotaColor(quota.remainingPercent)
    val percentText = QuotaFormatter.formatRemainingPercent(quota)
    val requestsSummary = QuotaFormatter.formatRequestsSummary(quota)
    val resetCountdown = QuotaFormatter.formatResetCountdown(quota.resetAtEpochMs)
    val lastUpdatedText = QuotaFormatter.formatLastUpdated(quota.updatedAtEpochMs)
    val tierColor = QuotaFormatter.planTierColor(quota.planTier)
    val tierLabel = QuotaFormatter.planTierLabel(quota.planTier)

    val borderColor = if (isActive) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(if (isActive) 1.25.dp else 1.dp, borderColor),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QuotaRadialGauge(
                    remainingPercent = quota.remainingPercent,
                    gaugeColor = gaugeColor,
                    percentLabel = percentText,
                    isRefreshing = isRefreshing,
                    size = 78.dp,
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = profile.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (isActive) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Text(
                                        text = "Aktif",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = tierColor.copy(alpha = 0.16f),
                            border = BorderStroke(0.75.dp, tierColor.copy(alpha = 0.45f)),
                        ) {
                            Text(
                                text = tierLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = tierColor,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(
                                    if (profile.isAuthenticated) {
                                        AntigravityPalette.SuccessGreen
                                    } else {
                                        AntigravityPalette.WarningAmber
                                    },
                                ),
                        )
                        Text(
                            text = profile.email ?: if (profile.isAuthenticated) {
                                "Oturum açık"
                            } else {
                                "Google girişi bekleniyor"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    if (requestsSummary != null) {
                        val limit = quota.limitRequests
                        val used = quota.usedRequests
                        val usedFraction = if (limit != null && limit > 0L && used != null) {
                            (used.toFloat() / limit.toFloat()).coerceIn(0f, 1f)
                        } else {
                            quota.remainingPercent?.let { (1.0 - (it / 100.0)).toFloat().coerceIn(0f, 1f) } ?: 0f
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = requestsSummary,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                if (quota.isNearLimit) {
                                    Text(
                                        text = "Düşük kota",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AntigravityPalette.CriticalRed,
                                    )
                                }
                            }
                            LinearProgressIndicator(
                                progress = { usedFraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(999.dp)),
                                color = gaugeColor,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                        }
                    } else if (quota.rawSummary.isNotBlank()) {
                        Text(
                            text = quota.rawSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    if (resetCountdown != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = resetCountdown,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = lastUpdatedText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onRefresh,
                        enabled = !isRefreshing,
                        modifier = Modifier.size(34.dp),
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Kotayı yenile",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (!isActive && onSwitch != null) {
                        FilledTonalButton(
                            onClick = onSwitch,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Geç",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuotaRadialGauge(
    remainingPercent: Double?,
    gaugeColor: Color,
    percentLabel: String,
    isRefreshing: Boolean,
    size: Dp,
) {
    val targetFraction = (remainingPercent?.div(100.0)?.toFloat() ?: 0f).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
        label = "quotaRadialSweep",
    )
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val strokePx = 7.dp.toPx()
            val inset = strokePx / 2f
            val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
            val topLeft = Offset(inset, inset)

            // Subtle outer glow track
            drawArc(
                color = trackColor,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )

            if (remainingPercent != null) {
                val sweep = (270f * animatedFraction).coerceAtLeast(6f)
                drawArc(
                    color = gaugeColor.copy(alpha = 0.22f),
                    startAngle = 135f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx + 4.dp.toPx(), cap = StrokeCap.Round),
                )
                drawArc(
                    color = gaugeColor,
                    startAngle = 135f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = gaugeColor,
                )
            } else {
                Text(
                    text = if (remainingPercent != null) percentLabel else "—",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (remainingPercent != null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Text(
                    text = if (remainingPercent != null) "kalan" else "bilinmiyor",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Interactive bottom sheet displaying visual quota gauges for the active Google account and
 * all other accounts side by side with one-tap account switching — no slash command required.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuotaDetailBottomSheet(
    state: WorkspaceUiState,
    onAction: (WorkspaceAction) -> Unit,
) {
    if (!state.showQuotaSheet) return

    var showAddAccountDialog by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { onAction(WorkspaceAction.CloseQuotaSheet) },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Sheet Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Kota & Hesap Durumu",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Slash komutu olmadan anlık kota takibi ve tek dokunuşla hesap geçişi",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                FilledTonalButton(
                    onClick = { onAction(WorkspaceAction.RefreshAllQuotas) },
                    enabled = !state.isRefreshingQuota,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    if (state.isRefreshingQuota) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Tüm Kotaları Yenile",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            // Active Account Section
            val activeProfile = state.activeProfile
            if (activeProfile != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Aktif Hesap",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                QuotaGaugeCard(
                    profile = activeProfile,
                    isActive = true,
                    isRefreshing = state.isRefreshingQuota,
                    onRefresh = { onAction(WorkspaceAction.RefreshUsage(activeProfile.id)) },
                    onSwitch = null,
                )
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = AntigravityPalette.WarningAmber,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Aktif Google hesabı bulunamadı",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = "Kota bilgilerini görmek için yeni bir Google profili ekleyin.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Side-by-side list of all other accounts
            val otherProfiles = state.profiles.filter { it.id != activeProfile?.id }
            if (otherProfiles.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "Diğer Hesaplar (${otherProfiles.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        text = "Kaydır & Geç",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(items = otherProfiles, key = { it.id }) { profile ->
                        QuotaGaugeCard(
                            profile = profile,
                            isActive = false,
                            isRefreshing = state.isRefreshingQuota,
                            onRefresh = { onAction(WorkspaceAction.RefreshUsage(profile.id)) },
                            onSwitch = { onAction(WorkspaceAction.SwitchProfile(profile.id)) },
                            modifier = Modifier.width(if (otherProfiles.size == 1) 320.dp else 296.dp),
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

            // Footer Quick Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = { showAddAccountDialog = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Hesap Ekle")
                }

                Button(
                    onClick = {
                        onAction(WorkspaceAction.CloseQuotaSheet)
                        onAction(WorkspaceAction.OpenSettings(SettingsCategory.ACCOUNTS_QUOTA))
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ayarlara Git")
                }
            }
        }
    }

    if (showAddAccountDialog) {
        AddGoogleAccountDialog(
            onDismiss = { showAddAccountDialog = false },
            onConfirm = { name, email ->
                onAction(WorkspaceAction.CreateProfile(name, email))
                showAddAccountDialog = false
            },
        )
    }
}

@Composable
internal fun AddGoogleAccountDialog(
    onDismiss: () -> Unit,
    onConfirm: (displayName: String, email: String?) -> Unit,
) {
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yeni Google Hesabı Ekle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Her hesap chroot içinde tamamen izole HOME ve ~/.gemini diziniyle çalışır.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Profil Adı") },
                    placeholder = { Text("Örn: İş Hesabı") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("E-posta (İsteğe bağlı)") },
                    placeholder = { Text("kullanici@gmail.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(displayName.trim(), email.trim().ifBlank { null }) },
                enabled = displayName.isNotBlank(),
            ) {
                Text("Oluştur")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal")
            }
        },
    )
}
