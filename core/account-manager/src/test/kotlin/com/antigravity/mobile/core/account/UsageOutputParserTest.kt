package com.antigravity.mobile.core.account

import com.antigravity.mobile.contracts.AccountPlanTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class UsageOutputParserTest {

    @Test
    fun `parses exact 4-line real agy print usage TSV output with 5h Gemini primary and null epoch-zero reset`() {
        val raw = listOf(
            "Gemini Models\tWeekly Limit Remaining\t52%\t2026-12-01T08:19:42Z",
            "Gemini Models\tFive Hour Limit Remaining\t91%\t2026-11-25T17:30:55Z",
            "Claude and GPT models\tWeekly Limit Remaining\t50%\t2026-12-01T11:11:27Z",
            "Claude and GPT models\tFive Hour Limit Remaining\t100%\t1970-01-01T00:00:00Z",
        ).joinToString("\n")

        val buckets = UsageOutputParser.parseBuckets(raw)
        assertEquals(4, buckets.size)

        assertEquals(
            QuotaBucketEntry(
                modelGroup = "Gemini Models",
                windowLabel = "Weekly Limit Remaining",
                windowKind = QuotaWindowKind.WEEKLY,
                remainingPercent = 52.0,
                resetAtEpochMs = Instant.parse("2026-12-01T08:19:42Z").toEpochMilli(),
            ),
            buckets[0],
        )
        assertEquals(
            QuotaBucketEntry(
                modelGroup = "Gemini Models",
                windowLabel = "Five Hour Limit Remaining",
                windowKind = QuotaWindowKind.FIVE_HOUR,
                remainingPercent = 91.0,
                resetAtEpochMs = Instant.parse("2026-11-25T17:30:55Z").toEpochMilli(),
            ),
            buckets[1],
        )
        assertEquals(
            QuotaBucketEntry(
                modelGroup = "Claude and GPT models",
                windowLabel = "Weekly Limit Remaining",
                windowKind = QuotaWindowKind.WEEKLY,
                remainingPercent = 50.0,
                resetAtEpochMs = Instant.parse("2026-12-01T11:11:27Z").toEpochMilli(),
            ),
            buckets[2],
        )
        assertEquals(
            QuotaBucketEntry(
                modelGroup = "Claude and GPT models",
                windowLabel = "Five Hour Limit Remaining",
                windowKind = QuotaWindowKind.FIVE_HOUR,
                remainingPercent = 100.0,
                resetAtEpochMs = null,
            ),
            buckets[3],
        )
        assertNull("1970-01-01T00:00:00Z sentinel must map to null", buckets[3].resetAtEpochMs)

        val quota = UsageOutputParser.parse(raw, nowEpochMs = 1_795_620_000_000L)
        assertEquals(91.0, quota.remainingPercent!!, 0.01)
        assertEquals(Instant.parse("2026-11-25T17:30:55Z").toEpochMilli(), quota.resetAtEpochMs)
        assertFalse(quota.isNearLimit)
        assertEquals(4, quota.rawSummary.lines().size)
        assertTrue(quota.rawSummary.contains("Gemini Models\tWeekly Limit Remaining\t52%\t2026-12-01T08:19:42Z"))
        assertTrue(quota.rawSummary.contains("Gemini Models\tFive Hour Limit Remaining\t91%\t2026-11-25T17:30:55Z"))
        assertTrue(quota.rawSummary.contains("Claude and GPT models\tWeekly Limit Remaining\t50%\t2026-12-01T11:11:27Z"))
        assertTrue(quota.rawSummary.contains("Claude and GPT models\tFive Hour Limit Remaining\t100%\t1970-01-01T00:00:00Z"))
    }

    @Test
    fun `falls back to first five-hour bucket then first bucket when Gemini 5h is absent`() {
        val noGemini5h = listOf(
            "Gemini Models\tWeekly Limit Remaining\t52%\t2026-12-01T08:19:42Z",
            "Claude and GPT models\tFive Hour Limit Remaining\t100%\t1970-01-01T00:00:00Z",
        ).joinToString("\n")
        val quota1 = UsageOutputParser.parse(noGemini5h, nowEpochMs = 1_795_620_000_000L)
        assertEquals(100.0, quota1.remainingPercent!!, 0.01)
        assertNull(quota1.resetAtEpochMs)

        val onlyWeekly = listOf(
            "Gemini Models\tWeekly Limit Remaining\t52%\t2026-12-01T08:19:42Z",
            "Claude and GPT models\tWeekly Limit Remaining\t50%\t2026-12-01T11:11:27Z",
        ).joinToString("\n")
        val quota2 = UsageOutputParser.parse(onlyWeekly, nowEpochMs = 1_795_620_000_000L)
        assertEquals(52.0, quota2.remainingPercent!!, 0.01)
        assertEquals(Instant.parse("2026-12-01T08:19:42Z").toEpochMilli(), quota2.resetAtEpochMs)
    }

    @Test
    fun `parses whitespace-separated multi-bucket variants with ANSI codes and CRLF`() {
        val raw = "\u001B[36mGemini Models\u001B[0m    Weekly Limit Remaining    52%    2026-12-01T08:19:42Z\r\n" +
            "Gemini Models Five Hour Limit Remaining 91% 2026-11-25T17:30:55Z\r\n" +
            "Claude and GPT models   Five Hour Limit Remaining   100%   1970-01-02T00:00:00Z\r\n"

        val buckets = UsageOutputParser.parseBuckets(raw)
        assertEquals(3, buckets.size)
        assertEquals(QuotaWindowKind.WEEKLY, buckets[0].windowKind)
        assertEquals(52.0, buckets[0].remainingPercent, 0.01)
        assertEquals(QuotaWindowKind.FIVE_HOUR, buckets[1].windowKind)
        assertEquals(91.0, buckets[1].remainingPercent, 0.01)
        assertEquals(Instant.parse("2026-11-25T17:30:55Z").toEpochMilli(), buckets[1].resetAtEpochMs)
        assertNull("Epoch <= 86_400_000L must map to null", buckets[2].resetAtEpochMs)

        val quota = UsageOutputParser.parse(raw, nowEpochMs = 1_795_620_000_000L)
        assertEquals(91.0, quota.remainingPercent!!, 0.01)
        assertEquals(Instant.parse("2026-11-25T17:30:55Z").toEpochMilli(), quota.resetAtEpochMs)
    }

    @Test
    fun `treats epoch sentinel reset timestamps in JSON as null`() {
        val json = """
            {
              "planTier": "ULTRA",
              "remainingPercent": 100.0,
              "resetAt": "1970-01-01T00:00:00Z"
            }
        """.trimIndent()
        val quota = UsageOutputParser.parse(json, nowEpochMs = 1_795_620_000_000L)
        assertEquals(AccountPlanTier.ULTRA, quota.planTier)
        assertEquals(100.0, quota.remainingPercent!!, 0.01)
        assertNull(quota.resetAtEpochMs)
    }

    @Test
    fun `preserves up to 2048 chars in rawSummary and redacts tokens alongside TSV buckets`() {
        val longNote = "Info: " + "a".repeat(1200)
        val raw = listOf(
            "Gemini Models\tWeekly Limit Remaining\t52%\t2026-12-01T08:19:42Z",
            "Gemini Models\tFive Hour Limit Remaining\t91%\t2026-11-25T17:30:55Z",
            "Claude and GPT models\tWeekly Limit Remaining\t50%\t2026-12-01T11:11:27Z",
            "Claude and GPT models\tFive Hour Limit Remaining\t100%\t1970-01-01T00:00:00Z",
            longNote,
            "Authorization: Bearer ya29.super_secret_token",
        ).joinToString("\n")

        val quota = UsageOutputParser.parse(raw, nowEpochMs = 1_795_620_000_000L)
        assertTrue("rawSummary should retain > 1024 chars up to 2048", quota.rawSummary.length > 1024)
        assertTrue(quota.rawSummary.length <= 2048)
        assertFalse(quota.rawSummary.contains("ya29.super_secret_token"))
        assertTrue(quota.rawSummary.contains("[REDACTED]"))
    }

    @Test
    fun `parses Google AI Ultra text output with remaining percent and ISO reset time`() {
        val raw = """
            Plan: Google AI Ultra
            Quota: 72.5% remaining (275 / 1,000 requests)
            Resets at 2026-11-25T00:00:00Z
        """.trimIndent()

        val quota = UsageOutputParser.parse(raw, nowEpochMs = 1_700_000_000_000L)
        assertEquals(AccountPlanTier.ULTRA, quota.planTier)
        assertEquals(72.5, quota.remainingPercent!!, 0.01)
        assertEquals(275L, quota.usedRequests)
        assertEquals(1000L, quota.limitRequests)
        assertEquals(Instant.parse("2026-11-25T00:00:00Z").toEpochMilli(), quota.resetAtEpochMs)
        assertFalse(quota.isNearLimit)
    }

    @Test
    fun `parses Pro plan with used percent near limit and relative reset duration`() {
        val now = 1_700_000_000_000L
        val raw = "Antigravity Pro — 91% used, resets in 2h 30m"

        val quota = UsageOutputParser.parse(raw, nowEpochMs = now)
        assertEquals(AccountPlanTier.PRO, quota.planTier)
        assertEquals(9.0, quota.remainingPercent!!, 0.01)
        assertTrue(quota.isNearLimit)
        assertEquals(now + ((2L * 60L + 30L) * 60L * 1000L), quota.resetAtEpochMs)
    }

    @Test
    fun `parses Free plan from request ratio when explicit percentage is absent`() {
        val raw = "Tier: Free\nDaily usage: 45 / 50 prompts"
        val quota = UsageOutputParser.parse(raw, nowEpochMs = 1000L)
        assertEquals(AccountPlanTier.FREE, quota.planTier)
        assertEquals(45L, quota.usedRequests)
        assertEquals(50L, quota.limitRequests)
        assertEquals(10.0, quota.remainingPercent!!, 0.01)
        assertTrue(quota.isNearLimit)
    }

    @Test
    fun `parses structured JSON usage payload`() {
        val json = """
            {
              "planTier": "PRO",
              "usedRequests": 120,
              "limitRequests": 400,
              "resetAt": "2026-12-01T12:00:00Z"
            }
        """.trimIndent()
        val quota = UsageOutputParser.parse(json, nowEpochMs = 5000L)
        assertEquals(AccountPlanTier.PRO, quota.planTier)
        assertEquals(120L, quota.usedRequests)
        assertEquals(400L, quota.limitRequests)
        assertEquals(70.0, quota.remainingPercent!!, 0.01)
        assertNotNull(quota.resetAtEpochMs)
    }

    @Test
    fun `redacts accidental OAuth tokens in rawSummary`() {
        val raw = "Plan: Free\n80% remaining\nDebug header: Authorization: Bearer ya29.secret_token_value"
        val quota = UsageOutputParser.parse(raw, nowEpochMs = 1000L)
        assertFalse(quota.rawSummary.contains("ya29.secret_token_value"))
        assertTrue(quota.rawSummary.contains("[REDACTED]"))
    }
}
