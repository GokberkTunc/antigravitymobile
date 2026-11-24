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
 * Parses `/usage` CLI output (plain text, table, or JSON) into a structured [UsageQuota].
 */
object UsageOutputParser {
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

    private val lenientJson = Json { ignoreUnknownKeys = true }

    fun parse(
        rawOutput: String,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): UsageQuota {
        val stripped = ANSI_REGEX.replace(rawOutput, "").trim()
        val sanitizedSummary = SENSITIVE_TOKEN_REGEX.replace(stripped, "[REDACTED]")
            .take(1024)

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
        val (used, limit, ratioRemainingPct) = parseRatios(stripped)
        val explicitRemainingPct = parseExplicitRemainingPercent(stripped)
        val remainingPct = (explicitRemainingPct ?: ratioRemainingPct)?.coerceIn(0.0, 100.0)
        val resetAt = parseResetEpochMs(stripped, nowEpochMs)

        return UsageQuota(
            planTier = tier,
            remainingPercent = remainingPct?.let { (it * 10.0).roundToInt() / 10.0 },
            usedRequests = used,
            limitRequests = limit,
            resetAtEpochMs = resetAt,
            rawSummary = sanitizedSummary,
            updatedAtEpochMs = nowEpochMs,
        )
    }

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

        val resetEpoch = obj["resetAtEpochMs"]?.jsonPrimitive?.longOrNull
            ?: obj["reset_at"]?.jsonPrimitive?.contentOrNull?.let { parseIsoTimestamp(it) }
            ?: obj["resetAt"]?.jsonPrimitive?.contentOrNull?.let { parseIsoTimestamp(it) }

        return UsageQuota(
            planTier = tier,
            remainingPercent = computedRem?.coerceIn(0.0, 100.0)?.let { (it * 10.0).roundToInt() / 10.0 },
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
        ISO_TIMESTAMP_REGEX.find(text)?.value?.let { iso ->
            parseIsoTimestamp(iso)?.let { return it }
        }

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

    private fun parseIsoTimestamp(iso: String): Long? = try {
        Instant.parse(iso).toEpochMilli()
    } catch (_: Exception) {
        try {
            OffsetDateTime.parse(iso).toInstant().toEpochMilli()
        } catch (_: Exception) {
            null
        }
    }
}
