package com.antigravity.mobile.feature.workspace.ui

import com.antigravity.mobile.contracts.AccountPlanTier
import com.antigravity.mobile.contracts.UsageQuota
import com.antigravity.mobile.feature.workspace.theme.AntigravityPalette
import com.antigravity.mobile.feature.workspace.ui.settings.QuotaFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsAndQuotaTest {

    @Test
    fun `formatRemainingPercent formats known percentages and clamps bounds`() {
        assertEquals("Bilinmiyor", QuotaFormatter.formatRemainingPercent(UsageQuota(remainingPercent = null)))
        assertEquals("%82", QuotaFormatter.formatRemainingPercent(UsageQuota(remainingPercent = 82.0)))
        assertEquals("%83", QuotaFormatter.formatRemainingPercent(UsageQuota(remainingPercent = 82.6)))
        assertEquals("%0", QuotaFormatter.formatRemainingPercent(UsageQuota(remainingPercent = 0.0)))
        assertEquals("%100", QuotaFormatter.formatRemainingPercent(UsageQuota(remainingPercent = 100.0)))
        assertEquals("%0", QuotaFormatter.formatRemainingPercent(UsageQuota(remainingPercent = -12.5)))
        assertEquals("%100", QuotaFormatter.formatRemainingPercent(UsageQuota(remainingPercent = 145.0)))
    }

    @Test
    fun `formatRequestsSummary handles used and limit combinations`() {
        assertEquals(
            "18 / 100 istek",
            QuotaFormatter.formatRequestsSummary(
                UsageQuota(usedRequests = 18L, limitRequests = 100L),
            ),
        )
        assertEquals(
            "42 istek",
            QuotaFormatter.formatRequestsSummary(
                UsageQuota(usedRequests = 42L, limitRequests = null),
            ),
        )
        assertNull(
            QuotaFormatter.formatRequestsSummary(
                UsageQuota(usedRequests = null, limitRequests = 100L),
            ),
        )
        assertNull(
            QuotaFormatter.formatRequestsSummary(
                UsageQuota(usedRequests = null, limitRequests = null),
            ),
        )
    }

    @Test
    fun `formatResetCountdown formats Turkish countdown edge cases`() {
        val now = 1_700_000_000_000L
        val minuteMs = 60_000L
        val hourMs = 60L * minuteMs
        val dayMs = 24L * hourMs

        assertNull(QuotaFormatter.formatResetCountdown(null, now))
        assertNull(QuotaFormatter.formatResetCountdown(0L, now))
        assertNull(QuotaFormatter.formatResetCountdown(-500L, now))

        assertEquals("Yenilenmek üzere", QuotaFormatter.formatResetCountdown(now - 5_000L, now))
        assertEquals("Yenilenmek üzere", QuotaFormatter.formatResetCountdown(now, now))
        assertEquals("Yenilenmek üzere", QuotaFormatter.formatResetCountdown(now + 59_000L, now))

        assertEquals("1dk sonra yenilenir", QuotaFormatter.formatResetCountdown(now + minuteMs, now))
        assertEquals("45dk sonra yenilenir", QuotaFormatter.formatResetCountdown(now + 45 * minuteMs, now))
        assertEquals("3s sonra yenilenir", QuotaFormatter.formatResetCountdown(now + 3 * hourMs, now))
        assertEquals(
            "2s 14dk sonra yenilenir",
            QuotaFormatter.formatResetCountdown(now + 2 * hourMs + 14 * minuteMs, now),
        )
        assertEquals(
            "1g 5s sonra yenilenir",
            QuotaFormatter.formatResetCountdown(now + dayMs + 5 * hourMs + 20 * minuteMs, now),
        )
        assertEquals(
            "2g sonra yenilenir",
            QuotaFormatter.formatResetCountdown(now + 2 * dayMs, now),
        )
    }

    @Test
    fun `quotaColor respects emerald amber coral red and neutral thresholds`() {
        assertEquals(AntigravityPalette.TextSecondary, QuotaFormatter.quotaColor(null))

        // Emerald > 40%
        assertEquals(AntigravityPalette.SuccessGreen, QuotaFormatter.quotaColor(40.1))
        assertEquals(AntigravityPalette.SuccessGreen, QuotaFormatter.quotaColor(82.0))
        assertEquals(AntigravityPalette.SuccessGreen, QuotaFormatter.quotaColor(100.0))

        // Amber 15-40% (15.0 < pct <= 40.0)
        assertEquals(AntigravityPalette.WarningAmber, QuotaFormatter.quotaColor(40.0))
        assertEquals(AntigravityPalette.WarningAmber, QuotaFormatter.quotaColor(25.0))
        assertEquals(AntigravityPalette.WarningAmber, QuotaFormatter.quotaColor(15.1))

        // Coral Red <= 15%
        assertEquals(AntigravityPalette.CriticalRed, QuotaFormatter.quotaColor(15.0))
        assertEquals(AntigravityPalette.CriticalRed, QuotaFormatter.quotaColor(5.0))
        assertEquals(AntigravityPalette.CriticalRed, QuotaFormatter.quotaColor(0.0))
    }

    @Test
    fun `formatLastUpdated formats relative Turkish timestamps`() {
        val now = 1_700_000_000_000L
        val minuteMs = 60_000L
        val hourMs = 60L * minuteMs
        val dayMs = 24L * hourMs

        assertEquals("Henüz güncellenmedi", QuotaFormatter.formatLastUpdated(0L, now))
        assertEquals("Az önce güncellendi", QuotaFormatter.formatLastUpdated(now - 20_000L, now))
        assertEquals("7 dk önce güncellendi", QuotaFormatter.formatLastUpdated(now - 7 * minuteMs, now))
        assertEquals("4 sa önce güncellendi", QuotaFormatter.formatLastUpdated(now - 4 * hourMs, now))
        assertEquals("3 gün önce güncellendi", QuotaFormatter.formatLastUpdated(now - 3 * dayMs, now))
    }

    @Test
    fun `planTierLabel maps every AccountPlanTier`() {
        assertEquals("FREE", QuotaFormatter.planTierLabel(AccountPlanTier.FREE))
        assertEquals("PRO", QuotaFormatter.planTierLabel(AccountPlanTier.PRO))
        assertEquals("ULTRA", QuotaFormatter.planTierLabel(AccountPlanTier.ULTRA))
        assertEquals("ENTERPRISE", QuotaFormatter.planTierLabel(AccountPlanTier.ENTERPRISE))
        assertEquals("PLAN", QuotaFormatter.planTierLabel(AccountPlanTier.UNKNOWN))
    }
}
