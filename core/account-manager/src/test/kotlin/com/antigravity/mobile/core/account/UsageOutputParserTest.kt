package com.antigravity.mobile.core.account

import com.antigravity.mobile.contracts.AccountPlanTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class UsageOutputParserTest {

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
