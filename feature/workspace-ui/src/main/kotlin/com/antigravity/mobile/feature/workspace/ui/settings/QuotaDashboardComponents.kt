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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
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
import com.antigravity.mobile.contracts.AcpModel
import com.antigravity.mobile.contracts.UsageQuota
import com.antigravity.mobile.feature.workspace.model.QuotaBucketParser
import com.antigravity.mobile.feature.workspace.model.QuotaBucketUi
import com.antigravity.mobile.feature.workspace.model.QuotaWindowKind
import com.antigravity.mobile.feature.workspace.model.SettingsCategory
import com.antigravity.mobile.feature.workspace.model.WorkspaceAction
import com.antigravity.mobile.feature.workspace.model.WorkspaceUiState
import com.antigravity.mobile.feature.workspace.theme.AntigravityExtendedColors
import com.antigravity.mobile.feature.workspace.theme.AntigravityPalette
import com.antigravity.mobile.feature.workspace.theme.LocalAntigravityColors
import kotlin.math.roundToInt

/**
 * Pure Kotlin formatting and color helpers for 4-bucket and legacy visual quota dashboards.
 */
object QuotaFormatter {
    private const val MINUTE_MS = 60_000L
    private const val HOUR_MS = 60L * MINUTE_MS
    private const val DAY_MS = 24L * HOUR_MS
    private const val UNUSED_EPOCH_THRESHOLD_MS = 86_400_000L

    const val READY_UNUSED_LABEL = "Hazır • Kullanılmadı"

    fun formatPercent(percent: Double?): String {
        if (percent == null) return "Bilinmiyor"
        val clamped = percent.coerceIn(0.0, 100.0).roundToInt()
        return "%$clamped"
    }

    fun formatRemainingPercent(quota: UsageQuota): String = formatPercent(quota.remainingPercent)

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
        if (resetAtEpochMs == null || resetAtEpochMs <= UNUSED_EPOCH_THRESHOLD_MS) return null
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

    /**
     * Formats an individual quota bucket's reset timestamp in clean Turkish.
     *
     * Buckets with a null or epoch-zero / 1970-01-01 (`<= 86_400_000L`) reset timestamp
     * represent untouched windows and render as [READY_UNUSED_LABEL] rather than an expired date.
     */
    fun formatBucketReset(
        resetAtEpochMs: Long?,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): String {
        if (resetAtEpochMs == null || resetAtEpochMs <= UNUSED_EPOCH_THRESHOLD_MS) {
            return READY_UNUSED_LABEL
        }
        return formatResetCountdown(resetAtEpochMs, nowEpochMs) ?: READY_UNUSED_LABEL
    }

    /**
     * Normalizes localized family titles for display in the 4-bucket quota card.
     */
    fun familyDisplayTitle(displayGroup: String): String = when {
        displayGroup.equals("Claude & GPT", ignoreCase = true) -> "Claude & GPT Modelleri"
        else -> displayGroup
    }

    /**
     * Formats a compact 1-line dual-window summary (e.g. `"5s %91 / Hf %52"`) for settings
     * subtitles and compact status indicators.
     */
    fun formatCompactDualSummary(
        quota: UsageQuota,
        selectedModel: AcpModel = AcpModel.GEMINI_4_NEON,
    ): String {
        val fiveHour = QuotaBucketParser.primaryHeadlinePercent(quota, selectedModel)
        val weekly = QuotaBucketParser.weeklyPercent(quota, selectedModel)
        return when {
            fiveHour != null && weekly != null -> {
                "5s ${formatPercent(fiveHour)} / Hf ${formatPercent(weekly)}"
            }

            fiveHour != null -> "5s ${formatPercent(fiveHour)}"
            weekly != null -> "Hf ${formatPercent(weekly)}"
            quota.remainingPercent != null -> formatPercent(quota.remainingPercent)
            else -> "Kota bekleniyor"
        }
    }

    fun quotaColor(remainingPercent: Double?): Color = when {
        remainingPercent == null -> AntigravityPalette.TextSecondary
        remainingPercent > 40.0 -> AntigravityPalette.SuccessGreen
        remainingPercent > 15.0 -> AntigravityPalette.WarningAmber
        else -> AntigravityPalette.CriticalRed
    }

    /**
     * Theme-adaptive quota color tuned for high legibility across both Light and Dark themes.
     */
    fun adaptiveQuotaColor(
        remainingPercent: Double?,
        colors: AntigravityExtendedColors,
    ): Color = when {
        remainingPercent == null -> colors.textMuted
        remainingPercent > 40.0 -> colors.success
        remainingPercent > 15.0 -> colors.warning
        else -> colors.critical
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

    fun adaptivePlanTierColor(
        planTier: AccountPlanTier,
        colorScheme: ColorScheme,
        extendedColors: AntigravityExtendedColors,
    ): Color = when (planTier) {
        AccountPlanTier.FREE -> colorScheme.onSurfaceVariant
        AccountPlanTier.PRO -> colorScheme.primary
        AccountPlanTier.ULTRA -> colorScheme.secondary
        AccountPlanTier.ENTERPRISE -> extendedColors.success
        AccountPlanTier.UNKNOWN -> extendedColors.textMuted
    }
}

/**
 * Visual quota card displaying either:
 * - The full **4-bucket `agy --print /usage` breakdown** (Gemini Modelleri & Claude & GPT Modelleri,
 *   each showing 5 Saatlik Limit and Haftalık Limit progress bars with Turkish reset countdowns)
 *   paired with a top radial gauge for the active model family's 5-hour quota, OR
 * - A graceful single radial gauge fallback when only legacy/JSON single-percent data exists.
 */
@Composable
fun QuotaGaugeCard(
    profile: AccountProfile,
    isActive: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onSwitch: (() -> Unit)? = null,
    selectedModel: AcpModel = AcpModel.GEMINI_4_NEON,
    containerColor: Color? = null,
    modifier: Modifier = Modifier,
) {
    val extendedColors = LocalAntigravityColors.current
    val colorScheme = MaterialTheme.colorScheme
    val quota = profile.quota

    val groupedBuckets = remember(quota.rawSummary) {
        val rawGrouped = QuotaBucketParser.groupedByFamily(quota)
        rawGrouped.entries
            .sortedBy { (group, _) ->
                val lower = group.lowercase()
                when {
                    lower.contains("gemini") -> 0
                    lower.contains("claude") || lower.contains("gpt") -> 1
                    else -> 2
                }
            }
            .map { (group, buckets) ->
                QuotaFormatter.familyDisplayTitle(group) to buckets
            }
    }
    val hasMultiBucketData = groupedBuckets.isNotEmpty()

    val primaryFiveHourBucket = remember(quota.rawSummary, selectedModel) {
        QuotaBucketParser.primaryFiveHourBucket(quota, selectedModel)
    }
    val headlinePercent = remember(quota, selectedModel) {
        if (hasMultiBucketData) {
            QuotaBucketParser.primaryHeadlinePercent(quota, selectedModel)
        } else {
            quota.remainingPercent
        }
    }

    val gaugeColor = QuotaFormatter.adaptiveQuotaColor(headlinePercent, extendedColors)
    val percentText = QuotaFormatter.formatPercent(headlinePercent)
    val requestsSummary = QuotaFormatter.formatRequestsSummary(quota)
    val legacyResetCountdown = QuotaFormatter.formatResetCountdown(quota.resetAtEpochMs)
    val lastUpdatedText = QuotaFormatter.formatLastUpdated(quota.updatedAtEpochMs)
    val tierColor = QuotaFormatter.adaptivePlanTierColor(quota.planTier, colorScheme, extendedColors)
    val tierLabel = QuotaFormatter.planTierLabel(quota.planTier)
    val isLowQuota = headlinePercent != null && headlinePercent <= 15.0

    val resolvedContainerColor = containerColor ?: if (extendedColors.isDark) {
        colorScheme.surfaceContainer
    } else {
        colorScheme.surface
    }
    val borderColor = if (isActive) {
        colorScheme.primary.copy(alpha = if (extendedColors.isDark) 0.65f else 0.55f)
    } else {
        extendedColors.cardBorder
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = resolvedContainerColor,
        border = BorderStroke(if (isActive) 1.25.dp else 1.dp, borderColor),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Top summary header with radial ring (5-hour % for active model family, or legacy %)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QuotaRadialGauge(
                    remainingPercent = headlinePercent,
                    gaugeColor = gaugeColor,
                    percentLabel = percentText,
                    subLabel = when {
                        headlinePercent == null -> "bilinmiyor"
                        hasMultiBucketData && primaryFiveHourBucket != null -> "5s kalan"
                        else -> "kalan"
                    },
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
                                color = colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (isActive) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = colorScheme.primaryContainer,
                                ) {
                                    Text(
                                        text = "Aktif",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = tierColor.copy(alpha = if (extendedColors.isDark) 0.16f else 0.10f),
                            border = BorderStroke(0.75.dp, tierColor.copy(alpha = 0.4f)),
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
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(
                                    if (profile.isAuthenticated) {
                                        extendedColors.success
                                    } else {
                                        extendedColors.warning
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
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    if (hasMultiBucketData) {
                        val activeBucket = primaryFiveHourBucket
                        if (activeBucket != null) {
                            val activeResetText = QuotaFormatter.formatBucketReset(activeBucket.resetAtEpochMs)
                            val isUnused = activeBucket.resetAtEpochMs == null
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Icon(
                                    imageVector = if (isUnused) Icons.Default.CheckCircle else Icons.Default.Schedule,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = if (isUnused) extendedColors.success else colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = activeResetText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isUnused) extendedColors.success else colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        if (isLowQuota) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = colorScheme.errorContainer,
                            ) {
                                Text(
                                    text = "Düşük 5s kota",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                    } else {
                        // Graceful legacy fallback when only single-percent or request counts exist
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
                                        color = colorScheme.onSurface,
                                    )
                                    if (quota.isNearLimit) {
                                        Text(
                                            text = "Düşük kota",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = extendedColors.critical,
                                        )
                                    }
                                }
                                LinearProgressIndicator(
                                    progress = { usedFraction },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(999.dp)),
                                    color = gaugeColor,
                                    trackColor = colorScheme.outlineVariant.copy(alpha = 0.6f),
                                )
                            }
                        } else if (quota.rawSummary.isNotBlank()) {
                            Text(
                                text = quota.rawSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        if (legacyResetCountdown != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = legacyResetCountdown,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            // Full 4-Bucket Visual Breakdown grouped by Model Family
            if (hasMultiBucketData) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    groupedBuckets.forEach { (familyTitle, familyBuckets) ->
                        val isSelectedFamily = isMatchingFamilyTitle(familyTitle, selectedModel)
                        QuotaModelFamilySection(
                            familyTitle = familyTitle,
                            buckets = familyBuckets,
                            isSelectedFamily = isSelectedFamily,
                        )
                    }
                }
            }

            HorizontalDivider(color = extendedColors.cardBorder.copy(alpha = 0.8f))

            // Footer: last updated timestamp + refresh + quick switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = lastUpdatedText,
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
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
                                color = colorScheme.primary,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Kotayı yenile",
                                modifier = Modifier.size(18.dp),
                                tint = colorScheme.onSurfaceVariant,
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
private fun QuotaModelFamilySection(
    familyTitle: String,
    buckets: List<QuotaBucketUi>,
    isSelectedFamily: Boolean,
) {
    val extendedColors = LocalAntigravityColors.current
    val colorScheme = MaterialTheme.colorScheme
    val isGemini = familyTitle.contains("Gemini", ignoreCase = true)
    val familyIcon = if (isGemini) Icons.Default.AutoAwesome else Icons.Default.Memory
    val familyTint = if (isGemini) colorScheme.primary else colorScheme.secondary

    val sectionBg = if (extendedColors.isDark) {
        colorScheme.surfaceContainerHigh.copy(alpha = 0.55f)
    } else {
        colorScheme.surfaceContainer.copy(alpha = 0.72f)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = sectionBg,
        border = BorderStroke(
            width = if (isSelectedFamily) 1.dp else 0.75.dp,
            color = if (isSelectedFamily) {
                familyTint.copy(alpha = if (extendedColors.isDark) 0.45f else 0.35f)
            } else {
                extendedColors.cardBorder
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(familyTint.copy(alpha = if (extendedColors.isDark) 0.18f else 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = familyIcon,
                            contentDescription = null,
                            tint = familyTint,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                    Text(
                        text = familyTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface,
                    )
                }

                if (isSelectedFamily) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = familyTint.copy(alpha = if (extendedColors.isDark) 0.18f else 0.12f),
                    ) {
                        Text(
                            text = "Aktif model",
                            style = MaterialTheme.typography.labelSmall,
                            color = familyTint,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            buckets.forEachIndexed { index, bucket ->
                if (index > 0) {
                    HorizontalDivider(color = extendedColors.cardBorder.copy(alpha = 0.55f))
                }
                QuotaBucketBarRow(bucket = bucket)
            }
        }
    }
}

@Composable
private fun QuotaBucketBarRow(
    bucket: QuotaBucketUi,
) {
    val extendedColors = LocalAntigravityColors.current
    val colorScheme = MaterialTheme.colorScheme
    val isPrimaryWindow = bucket.windowKind == QuotaWindowKind.FIVE_HOUR
    val bucketColor = QuotaFormatter.adaptiveQuotaColor(bucket.remainingPercent, extendedColors)
    val percentLabel = QuotaFormatter.formatPercent(bucket.remainingPercent)
    val resetLabel = QuotaFormatter.formatBucketReset(bucket.resetAtEpochMs)
    val isUnused = bucket.resetAtEpochMs == null

    val targetFraction = (bucket.remainingPercent / 100.0).toFloat().coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "bucketBarProgress",
    )

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = bucket.windowLabel,
                style = if (isPrimaryWindow) {
                    MaterialTheme.typography.labelLarge
                } else {
                    MaterialTheme.typography.labelMedium
                },
                fontWeight = if (isPrimaryWindow) FontWeight.SemiBold else FontWeight.Medium,
                color = if (isPrimaryWindow) colorScheme.onSurface else colorScheme.onSurfaceVariant,
            )
            Text(
                text = percentLabel,
                style = if (isPrimaryWindow) {
                    MaterialTheme.typography.titleSmall
                } else {
                    MaterialTheme.typography.labelLarge
                },
                fontWeight = FontWeight.Bold,
                color = bucketColor,
            )
        }

        LinearProgressIndicator(
            progress = { animatedFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isPrimaryWindow) 7.dp else 5.dp)
                .clip(RoundedCornerShape(999.dp)),
            color = if (isPrimaryWindow) bucketColor else bucketColor.copy(alpha = 0.82f),
            trackColor = colorScheme.outlineVariant.copy(alpha = if (extendedColors.isDark) 0.6f else 0.85f),
            strokeCap = StrokeCap.Round,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = if (isUnused) Icons.Default.CheckCircle else Icons.Default.Schedule,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = if (isUnused) extendedColors.success else colorScheme.onSurfaceVariant,
            )
            Text(
                text = resetLabel,
                style = MaterialTheme.typography.labelSmall,
                color = if (isUnused) extendedColors.success else colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun isMatchingFamilyTitle(familyTitle: String, selectedModel: AcpModel): Boolean {
    val modelText = "${selectedModel.cliId} ${selectedModel.displayName}".lowercase()
    val isClaudeOrGptModel = modelText.contains("claude") || modelText.contains("gpt")
    val familyLower = familyTitle.lowercase()
    return if (isClaudeOrGptModel) {
        familyLower.contains("claude") || familyLower.contains("gpt")
    } else {
        familyLower.contains("gemini")
    }
}

@Composable
private fun QuotaRadialGauge(
    remainingPercent: Double?,
    gaugeColor: Color,
    percentLabel: String,
    subLabel: String,
    isRefreshing: Boolean,
    size: Dp,
) {
    val extendedColors = LocalAntigravityColors.current
    val colorScheme = MaterialTheme.colorScheme
    val targetFraction = (remainingPercent?.div(100.0)?.toFloat() ?: 0f).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
        label = "quotaRadialSweep",
    )
    val trackColor = colorScheme.outlineVariant.copy(alpha = if (extendedColors.isDark) 0.7f else 0.85f)

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val strokePx = 7.dp.toPx()
            val inset = strokePx / 2f
            val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
            val topLeft = Offset(inset, inset)

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
                    color = gaugeColor.copy(alpha = if (extendedColors.isDark) 0.22f else 0.14f),
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
                        colorScheme.onSurface
                    } else {
                        colorScheme.onSurfaceVariant
                    },
                )
                Text(
                    text = subLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Interactive bottom sheet displaying the active account's full 4-bucket visual quota dashboard
 * alongside all other configured accounts with one-tap switching.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuotaDetailBottomSheet(
    state: WorkspaceUiState,
    onAction: (WorkspaceAction) -> Unit,
) {
    if (!state.showQuotaSheet) return

    val extendedColors = LocalAntigravityColors.current
    val colorScheme = MaterialTheme.colorScheme
    var showAddAccountDialog by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetCardColor = if (extendedColors.isDark) {
        colorScheme.surfaceContainer
    } else {
        colorScheme.surfaceContainerLow
    }

    ModalBottomSheet(
        onDismissRequest = { onAction(WorkspaceAction.CloseQuotaSheet) },
        sheetState = sheetState,
        containerColor = colorScheme.surface,
        contentColor = colorScheme.onSurface,
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
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "Kota & Hesap Durumu",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                    )
                    Text(
                        text = "Aktif model: ${state.selectedModel.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
                        text = "Kotaları Yenile",
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
                        tint = colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Aktif Hesap",
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                QuotaGaugeCard(
                    profile = activeProfile,
                    isActive = true,
                    isRefreshing = state.isRefreshingQuota,
                    onRefresh = { onAction(WorkspaceAction.RefreshUsage(activeProfile.id)) },
                    onSwitch = null,
                    selectedModel = state.selectedModel,
                    containerColor = sheetCardColor,
                )
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = sheetCardColor,
                    border = BorderStroke(1.dp, extendedColors.cardBorder),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = extendedColors.warning,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Aktif Google hesabı bulunamadı",
                                style = MaterialTheme.typography.titleSmall,
                                color = colorScheme.onSurface,
                            )
                            Text(
                                text = "4 kademeli kota bilgilerini görmek için yeni bir Google profili ekleyin.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Other Accounts Section — full-width vertical list so all 4 bucket bars stay readable
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
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "Diğer Hesaplar (${otherProfiles.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        text = "Tek dokunuşla geçiş",
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    otherProfiles.forEach { profile ->
                        QuotaGaugeCard(
                            profile = profile,
                            isActive = false,
                            isRefreshing = state.isRefreshingQuota,
                            onRefresh = { onAction(WorkspaceAction.RefreshUsage(profile.id)) },
                            onSwitch = { onAction(WorkspaceAction.SwitchProfile(profile.id)) },
                            selectedModel = state.selectedModel,
                            containerColor = sheetCardColor,
                        )
                    }
                }
            }

            HorizontalDivider(color = extendedColors.cardBorder)

            // Bottom Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = { showAddAccountDialog = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("+ Hesap Ekle")
                }

                Button(
                    onClick = {
                        onAction(WorkspaceAction.CloseQuotaSheet)
                        onAction(WorkspaceAction.OpenSettingsDetail(SettingsCategory.ACCOUNTS_QUOTA))
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Hesap Ayarları")
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
