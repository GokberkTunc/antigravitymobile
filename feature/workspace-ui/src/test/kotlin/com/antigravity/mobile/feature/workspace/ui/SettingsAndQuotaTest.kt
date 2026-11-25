package com.antigravity.mobile.feature.workspace.ui

import com.antigravity.mobile.contracts.AccountPlanTier
import com.antigravity.mobile.contracts.AccountProfile
import com.antigravity.mobile.contracts.AcpModel
import com.antigravity.mobile.contracts.McpServerHealth
import com.antigravity.mobile.contracts.PermissionMode
import com.antigravity.mobile.contracts.ProfilePaths
import com.antigravity.mobile.contracts.RootEnvironmentInfo
import com.antigravity.mobile.contracts.RootProviderType
import com.antigravity.mobile.contracts.ThinkingEffort
import com.antigravity.mobile.contracts.UsageQuota
import com.antigravity.mobile.feature.workspace.model.AppSettings
import com.antigravity.mobile.feature.workspace.model.AppThemeMode
import com.antigravity.mobile.feature.workspace.model.InterfaceLayoutMode
import com.antigravity.mobile.feature.workspace.model.OnboardingUiState
import com.antigravity.mobile.feature.workspace.model.QuotaBucketParser
import com.antigravity.mobile.feature.workspace.model.QuotaWindowKind
import com.antigravity.mobile.feature.workspace.model.SettingsCategory
import com.antigravity.mobile.feature.workspace.model.WorkspaceUiState
import com.antigravity.mobile.feature.workspace.theme.AntigravityPalette
import com.antigravity.mobile.feature.workspace.theme.DarkAntigravityExtendedColors
import com.antigravity.mobile.feature.workspace.theme.LightAntigravityExtendedColors
import com.antigravity.mobile.feature.workspace.ui.settings.QuotaFormatter
import com.antigravity.mobile.feature.workspace.ui.settings.SettingsSummaryFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

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
    fun `formatBucketReset returns ready unused label for null zero and 1970 epoch values`() {
        val now = 1_795_620_775_000L

        assertEquals("Hazır • Kullanılmadı", QuotaFormatter.formatBucketReset(null, now))
        assertEquals("Hazır • Kullanılmadı", QuotaFormatter.formatBucketReset(0L, now))
        assertEquals("Hazır • Kullanılmadı", QuotaFormatter.formatBucketReset(-10_000L, now))
        assertEquals("Hazır • Kullanılmadı", QuotaFormatter.formatBucketReset(3_600_000L, now))
        assertEquals("Hazır • Kullanılmadı", QuotaFormatter.formatBucketReset(86_400_000L, now))
    }

    @Test
    fun `formatBucketReset formats days hours minutes and expired resets cleanly in Turkish`() {
        val now = 1_795_620_775_000L
        val minuteMs = 60_000L
        val hourMs = 60L * minuteMs
        val dayMs = 24L * hourMs

        assertEquals(
            "5g 16s sonra yenilenir",
            QuotaFormatter.formatBucketReset(now + 5 * dayMs + 16 * hourMs + 15 * minuteMs, now),
        )
        assertEquals(
            "4g sonra yenilenir",
            QuotaFormatter.formatBucketReset(now + 4 * dayMs, now),
        )
        assertEquals(
            "1s 58dk sonra yenilenir",
            QuotaFormatter.formatBucketReset(now + hourMs + 58 * minuteMs, now),
        )
        assertEquals(
            "3s sonra yenilenir",
            QuotaFormatter.formatBucketReset(now + 3 * hourMs, now),
        )
        assertEquals(
            "27dk sonra yenilenir",
            QuotaFormatter.formatBucketReset(now + 27 * minuteMs, now),
        )
        assertEquals("Yenilenmek üzere", QuotaFormatter.formatBucketReset(now + 30_000L, now))
        assertEquals("Yenilenmek üzere", QuotaFormatter.formatBucketReset(now, now))
        assertEquals("Yenilenmek üzere", QuotaFormatter.formatBucketReset(now - 120_000L, now))
    }

    @Test
    fun `four bucket agy usage TSV integrates cleanly with parser and formatter`() {
        val rawUsageTsv = """
            Gemini Models	Weekly Limit Remaining	52%	2026-12-01T08:19:42Z
            Gemini Models	Five Hour Limit Remaining	91%	2026-11-25T17:30:55Z
            Claude and GPT models	Weekly Limit Remaining	50%	2026-12-01T11:11:27Z
            Claude and GPT models	Five Hour Limit Remaining	100%	1970-01-01T00:00:00Z
        """.trimIndent()
        val quota = UsageQuota(
            planTier = AccountPlanTier.PRO,
            remainingPercent = 91.0,
            rawSummary = rawUsageTsv,
        )
        val nowEpochMs = Instant.parse("2026-11-25T15:32:55Z").toEpochMilli()

        val parsed = QuotaBucketParser.parse(quota)
        assertEquals(4, parsed.size)

        val grouped = QuotaBucketParser.groupedByFamily(quota)
        assertEquals(2, grouped.size)

        val geminiBuckets = grouped.getValue("Gemini Modelleri")
        assertEquals(2, geminiBuckets.size)
        assertEquals(QuotaWindowKind.FIVE_HOUR, geminiBuckets[0].windowKind)
        assertEquals("%91", QuotaFormatter.formatPercent(geminiBuckets[0].remainingPercent))
        assertEquals(
            "1s 58dk sonra yenilenir",
            QuotaFormatter.formatBucketReset(geminiBuckets[0].resetAtEpochMs, nowEpochMs),
        )
        assertEquals(QuotaWindowKind.WEEKLY, geminiBuckets[1].windowKind)
        assertEquals("%52", QuotaFormatter.formatPercent(geminiBuckets[1].remainingPercent))
        assertEquals(
            "5g 16s sonra yenilenir",
            QuotaFormatter.formatBucketReset(geminiBuckets[1].resetAtEpochMs, nowEpochMs),
        )

        val claudeGptBuckets = grouped.getValue("Claude & GPT")
        assertEquals("Claude & GPT Modelleri", QuotaFormatter.familyDisplayTitle("Claude & GPT"))
        assertEquals(2, claudeGptBuckets.size)
        assertEquals(QuotaWindowKind.FIVE_HOUR, claudeGptBuckets[0].windowKind)
        assertEquals("%100", QuotaFormatter.formatPercent(claudeGptBuckets[0].remainingPercent))
        assertNull(claudeGptBuckets[0].resetAtEpochMs)
        assertEquals(
            "Hazır • Kullanılmadı",
            QuotaFormatter.formatBucketReset(claudeGptBuckets[0].resetAtEpochMs, nowEpochMs),
        )
        assertEquals(QuotaWindowKind.WEEKLY, claudeGptBuckets[1].windowKind)
        assertEquals("%50", QuotaFormatter.formatPercent(claudeGptBuckets[1].remainingPercent))
        assertEquals(
            "5g 19s sonra yenilenir",
            QuotaFormatter.formatBucketReset(claudeGptBuckets[1].resetAtEpochMs, nowEpochMs),
        )

        assertEquals(
            "5s %91 / Hf %52",
            QuotaFormatter.formatCompactDualSummary(quota, AcpModel.GEMINI_4_NEON),
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
    fun `adaptiveQuotaColor resolves light and dark extended palette tokens`() {
        assertEquals(
            LightAntigravityExtendedColors.textMuted,
            QuotaFormatter.adaptiveQuotaColor(null, LightAntigravityExtendedColors),
        )
        assertEquals(
            LightAntigravityExtendedColors.success,
            QuotaFormatter.adaptiveQuotaColor(75.0, LightAntigravityExtendedColors),
        )
        assertEquals(
            LightAntigravityExtendedColors.warning,
            QuotaFormatter.adaptiveQuotaColor(30.0, LightAntigravityExtendedColors),
        )
        assertEquals(
            LightAntigravityExtendedColors.critical,
            QuotaFormatter.adaptiveQuotaColor(10.0, LightAntigravityExtendedColors),
        )

        assertEquals(
            DarkAntigravityExtendedColors.success,
            QuotaFormatter.adaptiveQuotaColor(91.0, DarkAntigravityExtendedColors),
        )
        assertEquals(
            DarkAntigravityExtendedColors.critical,
            QuotaFormatter.adaptiveQuotaColor(5.0, DarkAntigravityExtendedColors),
        )
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

    @Test
    fun `settings summary formatter produces live dynamic 1-line summaries for all categories`() {
        val rawUsageTsv = """
            Gemini Models	Weekly Limit Remaining	52%	2026-12-01T08:19:42Z
            Gemini Models	Five Hour Limit Remaining	91%	2026-11-25T17:30:55Z
        """.trimIndent()
        val profile = AccountProfile(
            id = "p1",
            displayName = "Kişisel",
            email = "dev@gmail.com",
            isAuthenticated = true,
            isActive = true,
            paths = ProfilePaths.forProfileId("p1"),
            quota = UsageQuota(
                planTier = AccountPlanTier.PRO,
                remainingPercent = 91.0,
                rawSummary = rawUsageTsv,
            ),
        )
        val state = WorkspaceUiState(
            profiles = listOf(profile),
            activeProfile = profile,
            projects = emptyList(),
            selectedProjectId = null,
            selectedModel = AcpModel.GEMINI_4_NEON,
            selectedEffort = ThinkingEffort.HIGH,
            permissionMode = PermissionMode.ASK,
            voiceLocaleTag = "tr-TR",
            mcpHealth = McpServerHealth(
                isRunning = true,
                endpointPort = 47891,
                registeredToolCount = 15,
            ),
            denylistPackages = setOf("tr.gov.turkiye.edevlet.kapisi", "com.garanti.cepsubesi"),
            settings = AppSettings(
                themeMode = AppThemeMode.LIGHT,
                layoutMode = InterfaceLayoutMode.COMPACT_MOBILE,
            ),
            onboarding = OnboardingUiState(
                rootEnvironment = RootEnvironmentInfo(
                    provider = RootProviderType.KERNEL_SU,
                    isRootGranted = true,
                ),
                agyVersion = "2.1.4",
            ),
        )

        assertEquals(
            "Aydınlık • Kompakt Mobil • tr-TR",
            SettingsSummaryFormatter.categorySummary(SettingsCategory.GENERAL, state),
        )
        assertEquals(
            "dev@gmail.com • 5s %91 / Hf %52",
            SettingsSummaryFormatter.categorySummary(SettingsCategory.ACCOUNTS_QUOTA, state),
        )
        assertEquals(
            "Gemini 4 Neon • HIGH • ASK",
            SettingsSummaryFormatter.categorySummary(SettingsCategory.MODELS_AGENT, state),
        )
        assertEquals(
            "0 proje • 2 korumalı uygulama",
            SettingsSummaryFormatter.categorySummary(SettingsCategory.PROJECTS_PERMISSIONS, state),
        )
        assertEquals(
            "MCP Aktif (:47891) • 15 araç",
            SettingsSummaryFormatter.categorySummary(SettingsCategory.CUSTOMIZATIONS, state),
        )
        assertEquals(
            "KernelSU • agy 2.1.4 • Overlay & Kill-Switch",
            SettingsSummaryFormatter.categorySummary(SettingsCategory.MOBILE_ROOT, state),
        )
    }
}
