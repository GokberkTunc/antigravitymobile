package com.antigravity.mobile.feature.workspace.model

/**
 * Explicit, deterministic navigation destinations for Antigravity Mobile.
 *
 * [Hub] is the true root screen so pressing Back from an open [Conversation]
 * always returns cleanly to the Hub instead of exiting the app.
 */
sealed interface AppDestination {
    /** Antigravity Home / Projects & Conversations Hub — true root screen. */
    data object Hub : AppDestination

    /** Active conversation thread, or a fresh draft thread when [conversationId] is null. */
    data class Conversation(val conversationId: String? = null) : AppDestination

    /** Google-style Settings overview list. */
    data object SettingsList : AppDestination

    /** Focused Settings category detail screen. */
    data class SettingsDetail(val category: SettingsCategory) : AppDestination

    /** Root / Chroot / agy CLI Setup Wizard. */
    data object Onboarding : AppDestination
}
