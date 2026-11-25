package com.antigravity.mobile.core.account

import com.antigravity.mobile.contracts.AccountPlanTier
import com.antigravity.mobile.contracts.UsageQuota
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.math.roundToInt

/**
 * Classification of a quota window emitted by `agy --print /usage`.
 */
enum class QuotaWindowKind {
    FIVE_HOUR,
    WEEKLY,
    OTHER,
}

/**
 * Single parsed quota bucket row from multi-bucket `agy --print /usage` output.
 */
data class QuotaBucketEntry(
    val modelGroup: String,
    val windowLabel: String,
    val windowKind: QuotaWindowKind,
    val remainingPercent: Double,
    val resetAtEpochMs: Long?,
)

/**
 * Parses `/usage` CLI output (TSV multi-bucket rows, whitespace tables, plain text, or JSON)
 * into a structured [UsageQuota] and optional per-model-group [QuotaBucketEntry] list.
 */
object UsageOutputParser {
    private const val MIN_VALID_RESET_EPOCH_MS = 86_400_000L
    private const val MAX_RAW_SUMMARY_LENGTH = 2048

    private val ANSI_REGEX = Regex("\\u001B\\[[0-9;]*[A-Za-z]")
    private val SENSITIVE_TOKEN_REGEX = Regex("""(?i)(Bearer\s+[A-Za-z0-9._-]+|ya29\.[A-Za-z0-9._-]+)""")

    private val TIER_ULTRA_REGEX = Regex("""(?i)\b(?:google\s+ai\s+|antigravity\s+|plan\s*[:=-]\s*|tier\s*[:=-]\s*)?ultra\b""")
    private val TIER_PRO_REGEX = Regex("""(?i)\b(?:google\s+ai\s+|antigravity\s+|plan\s*[:=-]\s*|tier\s*[:=-]\s*)?(?:pro|gemini\s+advanced)\b""")
    private val TIER_ENTERPRISE_REGEX = Regex("""(?i)\b(?:enterprise|workspace|business)\b""")
    private val TIER_FREE_REGEX = Regex("""(?i)\b(?:free|standard|basic)\b""")

    private val REMAINING_PCT_AFTER_REGEX = Regex("""(?i)(\d+(?:\.\d+)?)\s*%\s*(?:remaining|left|available)""")
    private val REMAINING_PCT_BEFORE_REGEX = Regex("""(?i)(?:remaining|quota\s+left|available)\s*[:=-]?\s*(\d+(?:\.\d+)?)\s*%""")
    private val USED_PCT_AFTER_REGEX = Regex("""(?i)(\d+(?:\.\d+)?)\s*%\s*(?:used|consumed)""")
    private val USED_PCT_BEFORE_REGEX = Regex("""(?i)(?:used|consumed)\s*[:=-]?\s*(\d+(?:\.\d+)?)\s*%""")

    private val REMAINING_RATIO_REGEX = Regex(
        """(?i)(?:remaining|left)\s*[:=-]?\s*([\d,]+)\s*(?:/|of)\s*([\d,]+)""",
    )
    private val GENERAL_RATIO_REGEX = Regex(
        """(?i)([\d,]+)\s*(?:/|of)\s*([\d,]+)\s*(?:requests?|credits?|messages?|prompts?|calls?|quota)?""",
    )

    private val ISO_TIMESTAMP_REGEX = Regex(
        """\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})""",
    )
    private val RELATIVE_RESET_REGEX = Regex(
        """(?i)resets?\s+in\s+(?:(\d+)\s*d(?:ays?)?\s*)?(?:(\d+)\s*h(?:ours?|rs?)?\s*)?(?:(\d+)\s*m(?:in(?:utes?)?)?\s*)?(?:(\d+)\s*s(?:ec(?:onds?)?)?)?""",
    )

    private val FIVE_HOUR_WINDOW_REGEX = Regex("""(?i)\b(?:five[\s-]*hour|5[\s-]*h(?:our|r)?s?)\b""")
    private val WEEKLY_WINDOW_REGEX = Regex("""(?i)\b(?:weekly|week|7[\s-]*d(?:ay)?s?)\b""")
    private val USED_WINDOW_LABEL_REGEX = Regex("""(?i)\b(?:used|consumed)\b""")
    private val REMAINING_WINDOW_LABEL_REGEX = Regex("""(?i)\b(?:remaining|left|available)\b""")
    private val BUCKET_PERCENT_REGEX = Regex("""^(\d+(?:\.\d+)?)\s*%$""")
    private val MULTI_SPACE_REGEX = Regex("""\s{2,}""")
    private val EMPTY_RESET_PLACEHOLDER_REGEX = Regex("""(?i)^(?:-|—|n/a|none|null)$""")
    private val SINGLE_SPACE_BUCKET_REGEX = Regex(
        """(?i)^(.+?)\s+((?:five[\s-]*hour|5[\s-]*h(?:our|r)?s?|weekly|7[\s-]*d(?:ay)?s?|daily|24[\s-]*h(?:our|r)?s?|monthly|hourly|session)\s+(?:limit|quota|window)(?:\s+(?:remaining|left|available|used|consumed))?|(?:five[\s-]*hour|5[\s-]*h(?:our|r)?s?|weekly)\s+(?:remaining|left|available|used|consumed))\s+(\d+(?:\.\d+)?)\s*%(?:\s+(\S+))?$""",
    )

    private val lenientJson = Json { ignoreUnknownKeys = true }

    fun parse(
        rawOutput: String,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): UsageQuota {
        val stripped = normalizeRawOutput(rawOutput)
        val sanitizedSummary = SENSITIVE_TOKEN_REGEX.replace(stripped, "[REDACTED]")
            .take(MAX_RAW_SUMMARY_LENGTH)

        if (stripped.isEmpty()) {
            return UsageQuota(
                planTier = AccountPlanTier.UNKNOWN,
                rawSummary = "",
                updatedAtEpochMs = nowEpochMs,
            )
        }

        // First attempt structured JSON parsing if `/usage` returned a JSON payload.
        if (stripped.startsWith("{") && stripped.endsWith("}")) {
            parseJsonPayload(stripped, sanitizedSummary, nowEpochMs)?.let { return it }
        }

        val tier = parseTier(stripped)

        // Next check for multi-bucket TSV / table rows emitted by real `agy --print /usage`.
        val buckets = parseBuckets(stripped)
        if (buckets.isNotEmpty()) {
            val primary = selectPrimaryBucket(buckets)
            return UsageQuota(
                planTier = tier,
                remainingPercent = primary.remainingPercent,
                usedRequests = null,
                limitRequests = null,
                resetAtEpochMs = primary.resetAtEpochMs,
                rawSummary = sanitizedSummary,
                updatedAtEpochMs = nowEpochMs,
            )
        }

        val (used, limit, ratioRemainingPct) = parseRatios(stripped)
        val explicitRemainingPct = parseExplicitRemainingPercent(stripped)
        val remainingPct = (explicitRemainingPct ?: ratioRemainingPct)?.coerceIn(0.0, 100.0)
        val resetAt = parseResetEpochMs(stripped, nowEpochMs)

        return UsageQuota(
            planTier = tier,
            remainingPercent = remainingPct?.let(::roundToTenth),
            usedRequests = used,
            limitRequests = limit,
            resetAtEpochMs = resetAt,
            rawSummary = sanitizedSummary,
            updatedAtEpochMs = nowEpochMs,
        )
    }

    /**
     * Parses multi-bucket quota rows from `agy --print /usage` output.
     *
     * Supports tab-separated rows (`<Group>\t<Window>\t<N>%\t<ISO>`) as well as multi-space
     * aligned or single-space-separated variants. Sentinel timestamps `<= 86_400_000L` (such as
     * `1970-01-01T00:00:00Z`) are normalized to `resetAtEpochMs = null`.
     */
    fun parseBuckets(rawOutput: String): List<QuotaBucketEntry> {
        val stripped = normalizeRawOutput(rawOutput)
        if (stripped.isEmpty()) return emptyList()

        return stripped.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull(::parseBucketLine)
            .toList()
    }

    private fun parseBucketLine(line: String): QuotaBucketEntry? {
        if ('\t' in line) {
            val cols = line.split('\t')
                .map { it.trim() }
                .dropLastWhile { it.isEmpty() }
            if (cols.size >= 3) {
                parseColumnsToBucket(
                    modelGroup = cols[0],
                    windowLabel = cols[1],
                    percentToken = cols[2],
                    resetToken = cols.getOrNull(3),
                    strictResetToken = false,
                )?.let { return it }
            }
        }

        val multiSpaceCols = MULTI_SPACE_REGEX.split(line)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (multiSpaceCols.size in 3..4) {
            parseColumnsToBucket(
                modelGroup = multiSpaceCols[0],
                windowLabel = multiSpaceCols[1],
                percentToken = multiSpaceCols[2],
                resetToken = multiSpaceCols.getOrNull(3),
                strictResetToken = true,
            )?.let { return it }
        }

        val singleSpaceMatch = SINGLE_SPACE_BUCKET_REGEX.matchEntire(line)
        if (singleSpaceMatch != null) {
            val modelGroup = singleSpaceMatch.groupValues[1].trim()
            val windowLabel = singleSpaceMatch.groupValues[2].trim()
            val percentNumber = singleSpaceMatch.groupValues[3]
            val resetToken = singleSpaceMatch.groupValues[4].takeIf { it.isNotBlank() }
            if (resetToken != null && !isValidOptionalResetToken(resetToken)) {
                return null
            }
            return buildBucketEntry(
                modelGroup = modelGroup,
                windowLabel = windowLabel,
                rawPercent = percentNumber.toDoubleOrNull() ?: return null,
                resetToken = resetToken,
            )
        }

        return null
    }

    private fun parseColumnsToBucket(
        modelGroup: String,
        windowLabel: String,
        percentToken: String,
        resetToken: String?,
        strictResetToken: Boolean,
    ): QuotaBucketEntry? {
        if (modelGroup.isBlank() || windowLabel.isBlank()) return null
        val pctMatch = BUCKET_PERCENT_REGEX.matchEntire(percentToken.trim()) ?: return null
        val rawPercent = pctMatch.groupValues[1].toDoubleOrNull() ?: return null
        val cleanResetToken = resetToken?.trim()?.takeIf { it.isNotEmpty() }
        if (strictResetToken && cleanResetToken != null && !isValidOptionalResetToken(cleanResetToken)) {
            return null
        }
        return buildBucketEntry(
            modelGroup = modelGroup,
            windowLabel = windowLabel,
            rawPercent = rawPercent,
            resetToken = cleanResetToken,
        )
    }

    private fun buildBucketEntry(
        modelGroup: String,
        windowLabel: String,
        rawPercent: Double,
        resetToken: String?,
    ): QuotaBucketEntry {
        val isUsedPercent = USED_WINDOW_LABEL_REGEX.containsMatchIn(windowLabel) &&
            !REMAINING_WINDOW_LABEL_REGEX.containsMatchIn(windowLabel)
        val remaining = (if (isUsedPercent) 100.0 - rawPercent else rawPercent)
            .coerceIn(0.0, 100.0)
            .let(::roundToTenth)
        val resetAtEpochMs = resetToken
            ?.takeUnless { EMPTY_RESET_PLACEHOLDER_REGEX.matches(it) }
            ?.let(::parseIsoTimestamp)

        return QuotaBucketEntry(
            modelGroup = modelGroup,
            windowLabel = windowLabel,
            windowKind = classifyWindowKind(windowLabel),
            remainingPercent = remaining,
            resetAtEpochMs = resetAtEpochMs,
        )
    }

    private fun selectPrimaryBucket(buckets: List<QuotaBucketEntry>): QuotaBucketEntry =
        buckets.firstOrNull {
            it.windowKind == QuotaWindowKind.FIVE_HOUR &&
                it.modelGroup.contains("gemini", ignoreCase = true)
        } ?: buckets.firstOrNull {
            it.windowKind == QuotaWindowKind.FIVE_HOUR
        } ?: buckets.first()

    private fun classifyWindowKind(windowLabel: String): QuotaWindowKind = when {
        FIVE_HOUR_WINDOW_REGEX.containsMatchIn(windowLabel) -> QuotaWindowKind.FIVE_HOUR
        WEEKLY_WINDOW_REGEX.containsMatchIn(windowLabel) -> QuotaWindowKind.WEEKLY
        else -> QuotaWindowKind.OTHER
    }

    private fun isValidOptionalResetToken(token: String): Boolean =
        ISO_TIMESTAMP_REGEX.matches(token) || EMPTY_RESET_PLACEHOLDER_REGEX.matches(token)

    private fun normalizeRawOutput(rawOutput: String): String =
        ANSI_REGEX.replace(rawOutput, "")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim()

    private fun roundToTenth(value: Double): Double = (value * 10.0).roundToInt() / 10.0

    private fun normalizeResetEpochMs(epochMs: Long?): Long? =
        epochMs?.takeIf { it > MIN_VALID_RESET_EPOCH_MS }

    private fun parseJsonPayload(
        jsonText: String,
        sanitizedSummary: String,
        nowEpochMs: Long,
    ): UsageQuota? {
        val obj = try {
            lenientJson.parseToJsonElement(jsonText) as? JsonObject
        } catch (_: Exception) {
            null
        } ?: return null

        val tierStr = obj["plan"]?.jsonPrimitive?.contentOrNull
            ?: obj["planTier"]?.jsonPrimitive?.contentOrNull
            ?: obj["tier"]?.jsonPrimitive?.contentOrNull
            ?: ""
        val tier = parseTier(tierStr)

        val used = obj["used"]?.jsonPrimitive?.longOrNull
            ?: obj["usedRequests"]?.jsonPrimitive?.longOrNull
        val limit = obj["limit"]?.jsonPrimitive?.longOrNull
            ?: obj["limitRequests"]?.jsonPrimitive?.longOrNull

        val explicitRem = obj["remainingPercent"]?.jsonPrimitive?.doubleOrNull
            ?: obj["remaining_percent"]?.jsonPrimitive?.doubleOrNull
            ?: obj["usedPercent"]?.jsonPrimitive?.doubleOrNull?.let { 100.0 - it }
            ?: obj["used_percent"]?.jsonPrimitive?.doubleOrNull?.let { 100.0 - it }

        val computedRem = explicitRem ?: if (used != null && limit != null && limit > 0L) {
            ((limit - used).toDouble() / limit.toDouble()) * 100.0
        } else {
            null
        }

        val resetEpoch = normalizeResetEpochMs(obj["resetAtEpochMs"]?.jsonPrimitive?.longOrNull)
            ?: obj["reset_at"]?.jsonPrimitive?.contentOrNull?.let { parseIsoTimestamp(it) }
            ?: obj["resetAt"]?.jsonPrimitive?.contentOrNull?.let { parseIsoTimestamp(it) }

        return UsageQuota(
            planTier = tier,
            remainingPercent = computedRem?.coerceIn(0.0, 100.0)?.let(::roundToTenth),
            usedRequests = used,
            limitRequests = limit,
            resetAtEpochMs = resetEpoch,
            rawSummary = sanitizedSummary,
            updatedAtEpochMs = nowEpochMs,
        )
    }

    internal fun parseTier(text: String): AccountPlanTier = when {
        TIER_ULTRA_REGEX.containsMatchIn(text) -> AccountPlanTier.ULTRA
        TIER_ENTERPRISE_REGEX.containsMatchIn(text) -> AccountPlanTier.ENTERPRISE
        TIER_PRO_REGEX.containsMatchIn(text) -> AccountPlanTier.PRO
        TIER_FREE_REGEX.containsMatchIn(text) -> AccountPlanTier.FREE
        else -> AccountPlanTier.UNKNOWN
    }

    private fun parseExplicitRemainingPercent(text: String): Double? {
        REMAINING_PCT_AFTER_REGEX.find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.let { return it }
        REMAINING_PCT_BEFORE_REGEX.find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.let { return it }
        USED_PCT_AFTER_REGEX.find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.let { return 100.0 - it }
        USED_PCT_BEFORE_REGEX.find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.let { return 100.0 - it }
        return null
    }

    private fun parseRatios(text: String): Triple<Long?, Long?, Double?> {
        REMAINING_RATIO_REGEX.find(text)?.let { match ->
            val remaining = match.groupValues[1].replace(",", "").toLongOrNull()
            val limit = match.groupValues[2].replace(",", "").toLongOrNull()
            if (remaining != null && limit != null && limit > 0L) {
                val used = (limit - remaining).coerceAtLeast(0L)
                val pct = (remaining.toDouble() / limit.toDouble()) * 100.0
                return Triple(used, limit, pct)
            }
        }

        GENERAL_RATIO_REGEX.find(text)?.let { match ->
            val used = match.groupValues[1].replace(",", "").toLongOrNull()
            val limit = match.groupValues[2].replace(",", "").toLongOrNull()
            if (used != null && limit != null && limit > 0L) {
                val remaining = (limit - used).coerceAtLeast(0L)
                val pct = (remaining.toDouble() / limit.toDouble()) * 100.0
                return Triple(used, limit, pct)
            }
        }

        return Triple(null, null, null)
    }

    private fun parseResetEpochMs(text: String, nowEpochMs: Long): Long? {
        ISO_TIMESTAMP_REGEX.findAll(text)
            .firstNotNullOfOrNull { parseIsoTimestamp(it.value) }
            ?.let { return it }

        RELATIVE_RESET_REGEX.find(text)?.let { match ->
            val days = match.groupValues[1].toLongOrNull() ?: 0L
            val hours = match.groupValues[2].toLongOrNull() ?: 0L
            val minutes = match.groupValues[3].toLongOrNull() ?: 0L
            val seconds = match.groupValues[4].toLongOrNull() ?: 0L
            val totalMs = (((days * 24L + hours) * 60L + minutes) * 60L + seconds) * 1000L
            if (totalMs > 0L) {
                return nowEpochMs + totalMs
            }
        }
        return null
    }

    private fun parseIsoTimestamp(iso: String): Long? {
        val epochMs = try {
            Instant.parse(iso).toEpochMilli()
        } catch (_: Exception) {
            try {
                OffsetDateTime.parse(iso).toInstant().toEpochMilli()
            } catch (_: Exception) {
                null
            }
        }
        return normalizeResetEpochMs(epochMs)
    }
}
