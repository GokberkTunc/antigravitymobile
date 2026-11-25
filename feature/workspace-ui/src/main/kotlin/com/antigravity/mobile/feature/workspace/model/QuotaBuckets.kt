package com.antigravity.mobile.feature.workspace.model

import com.antigravity.mobile.contracts.AcpModel
import com.antigravity.mobile.contracts.UsageQuota
import java.time.Instant
import java.time.OffsetDateTime

/**
 * Classification of quota rate-limit windows reported by `agy --print /usage`.
 */
enum class QuotaWindowKind {
    FIVE_HOUR,
    WEEKLY,
    OTHER,
}

/**
 * UI-ready parsed representation of an individual quota bucket row
 * (e.g., Gemini 5-Hour, Gemini Weekly, Claude & GPT 5-Hour, Claude & GPT Weekly).
 */
data class QuotaBucketUi(
    val modelGroup: String,
    val displayGroup: String,
    val windowKind: QuotaWindowKind,
    val windowLabel: String,
    val remainingPercent: Double,
    val resetAtEpochMs: Long?,
) {
    /**
     * True when the bucket has no active countdown window (e.g. epoch `1970-01-01T00:00:00Z`)
     * and is effectively untouched / full.
     */
    val isFullUnused: Boolean
        get() = resetAtEpochMs == null && remainingPercent >= 99.5
}

/**
 * Parses real multi-bucket TSV output stored in [UsageQuota.rawSummary] into typed [QuotaBucketUi]
 * entries and resolves model-family-aware 5-hour & weekly headline percentages.
 *
 * Example input from `agy --print /usage`:
 * ```
 * Gemini Models	Weekly Limit Remaining	52%	2026-12-01T08:19:42Z
 * Gemini Models	Five Hour Limit Remaining	91%	2026-11-25T17:30:55Z
 * Claude and GPT models	Weekly Limit Remaining	50%	2026-12-01T11:11:27Z
 * Claude and GPT models	Five Hour Limit Remaining	100%	1970-01-01T00:00:00Z
 * ```
 */
object QuotaBucketParser {
    private const val UNUSED_EPOCH_THRESHOLD_MS = 86_400_000L
    private val ANSI_REGEX = Regex("\u001B\\[[0-9;]*[A-Za-z]")
    private val PERCENT_REGEX = Regex("""(-?\d+(?:\.\d+)?)\s*%""")
    private val MULTI_SPACE_REGEX = Regex("""\s{2,}""")

    const val UNUSED_RESET_LABEL = "Tam dolu / Kullanılmadı"

    fun parse(quota: UsageQuota): List<QuotaBucketUi> = parse(quota.rawSummary)

    fun parse(rawSummary: String): List<QuotaBucketUi> {
        if (rawSummary.isBlank()) return emptyList()
        val sanitized = ANSI_REGEX.replace(rawSummary, "")
        val buckets = mutableListOf<QuotaBucketUi>()

        for (rawLine in sanitized.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            val parsed = parseLine(line) ?: continue
            buckets += parsed
        }
        return buckets
    }

    /**
     * Returns the **5-hour remaining percentage** for the currently selected model's family
     * (Gemini vs Claude/GPT), falling back to any 5-hour bucket, then [UsageQuota.remainingPercent],
     * then the first parsed bucket.
     */
    fun primaryHeadlinePercent(
        quota: UsageQuota,
        selectedModel: AcpModel = AcpModel.GEMINI_4_NEON,
    ): Double? {
        val buckets = parse(quota.rawSummary)
        if (buckets.isNotEmpty()) {
            val matchingFiveHour = buckets.firstOrNull { bucket ->
                bucket.windowKind == QuotaWindowKind.FIVE_HOUR && matchesModelFamily(bucket, selectedModel)
            }
            if (matchingFiveHour != null) return matchingFiveHour.remainingPercent

            val anyFiveHour = buckets.firstOrNull { it.windowKind == QuotaWindowKind.FIVE_HOUR }
            if (anyFiveHour != null) return anyFiveHour.remainingPercent
        }
        return quota.remainingPercent ?: buckets.firstOrNull()?.remainingPercent
    }

    /**
     * Returns the **weekly remaining percentage** for the currently selected model's family
     * (Gemini vs Claude/GPT), falling back to any weekly bucket.
     */
    fun weeklyPercent(
        quota: UsageQuota,
        selectedModel: AcpModel = AcpModel.GEMINI_4_NEON,
    ): Double? {
        val buckets = parse(quota.rawSummary)
        if (buckets.isEmpty()) return null

        return buckets.firstOrNull { bucket ->
            bucket.windowKind == QuotaWindowKind.WEEKLY && matchesModelFamily(bucket, selectedModel)
        }?.remainingPercent
            ?: buckets.firstOrNull { it.windowKind == QuotaWindowKind.WEEKLY }?.remainingPercent
    }

    /**
     * Returns the primary 5-hour [QuotaBucketUi] matching [selectedModel], if available.
     */
    fun primaryFiveHourBucket(
        quota: UsageQuota,
        selectedModel: AcpModel = AcpModel.GEMINI_4_NEON,
    ): QuotaBucketUi? {
        val buckets = parse(quota.rawSummary)
        return buckets.firstOrNull {
            it.windowKind == QuotaWindowKind.FIVE_HOUR && matchesModelFamily(it, selectedModel)
        } ?: buckets.firstOrNull { it.windowKind == QuotaWindowKind.FIVE_HOUR }
    }

    /**
     * Returns the primary weekly [QuotaBucketUi] matching [selectedModel], if available.
     */
    fun primaryWeeklyBucket(
        quota: UsageQuota,
        selectedModel: AcpModel = AcpModel.GEMINI_4_NEON,
    ): QuotaBucketUi? {
        val buckets = parse(quota.rawSummary)
        return buckets.firstOrNull {
            it.windowKind == QuotaWindowKind.WEEKLY && matchesModelFamily(it, selectedModel)
        } ?: buckets.firstOrNull { it.windowKind == QuotaWindowKind.WEEKLY }
    }

    /**
     * Groups parsed buckets by localized model family display name, preserving
     * 5-Hour before Weekly ordering inside each family.
     */
    fun groupedByFamily(quota: UsageQuota): Map<String, List<QuotaBucketUi>> {
        val buckets = parse(quota.rawSummary)
        return buckets
            .groupBy { it.displayGroup }
            .mapValues { (_, items) ->
                items.sortedBy { bucket ->
                    when (bucket.windowKind) {
                        QuotaWindowKind.FIVE_HOUR -> 0
                        QuotaWindowKind.WEEKLY -> 1
                        QuotaWindowKind.OTHER -> 2
                    }
                }
            }
    }

    private fun parseLine(line: String): QuotaBucketUi? {
        val columns = when {
            line.contains('\t') -> line.split('\t').map { it.trim() }.filter { it.isNotEmpty() }
            MULTI_SPACE_REGEX.containsMatchIn(line) -> line.split(MULTI_SPACE_REGEX).map { it.trim() }.filter { it.isNotEmpty() }
            else -> return null
        }
        if (columns.size < 3) return null

        val rawGroup = columns[0]
        val rawWindow = columns[1]
        val rawPercent = columns[2]
        val rawReset = columns.getOrNull(3)

        val percentMatch = PERCENT_REGEX.find(rawPercent) ?: return null
        val percent = percentMatch.groupValues[1].toDoubleOrNull()?.coerceIn(0.0, 100.0) ?: return null

        val (windowKind, windowLabel) = classifyWindow(rawWindow)
        val displayGroup = mapDisplayGroup(rawGroup)
        val resetEpochMs = parseResetEpochMs(rawReset)

        return QuotaBucketUi(
            modelGroup = rawGroup,
            displayGroup = displayGroup,
            windowKind = windowKind,
            windowLabel = windowLabel,
            remainingPercent = percent,
            resetAtEpochMs = resetEpochMs,
        )
    }

    private fun mapDisplayGroup(rawGroup: String): String {
        val lower = rawGroup.lowercase()
        return when {
            lower.contains("gemini") -> "Gemini Modelleri"
            lower.contains("claude") || lower.contains("gpt") -> "Claude & GPT"
            else -> rawGroup.trim()
        }
    }

    private fun classifyWindow(rawWindow: String): Pair<QuotaWindowKind, String> {
        val lower = rawWindow.lowercase()
        return when {
            lower.contains("five hour") || lower.contains("5 hour") || lower.contains("5h") || lower.contains("5 saat") -> {
                QuotaWindowKind.FIVE_HOUR to "5 Saatlik Limit"
            }

            lower.contains("weekly") || lower.contains("week") || lower.contains("hafta") -> {
                QuotaWindowKind.WEEKLY to "Haftalık Limit"
            }

            else -> QuotaWindowKind.OTHER to rawWindow.trim()
        }
    }

    private fun parseResetEpochMs(rawTimestamp: String?): Long? {
        val clean = rawTimestamp?.trim()?.ifEmpty { null } ?: return null
        val epochMs = runCatching { Instant.parse(clean).toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(clean).toInstant().toEpochMilli() }.getOrNull()
            ?: return null
        return if (epochMs <= UNUSED_EPOCH_THRESHOLD_MS) null else epochMs
    }

    private fun matchesModelFamily(bucket: QuotaBucketUi, selectedModel: AcpModel): Boolean {
        val modelText = "${selectedModel.cliId} ${selectedModel.displayName}".lowercase()
        val bucketText = bucket.modelGroup.lowercase()
        val isClaudeOrGptModel = modelText.contains("claude") || modelText.contains("gpt")
        return if (isClaudeOrGptModel) {
            bucketText.contains("claude") || bucketText.contains("gpt")
        } else {
            bucketText.contains("gemini")
        }
    }
}
