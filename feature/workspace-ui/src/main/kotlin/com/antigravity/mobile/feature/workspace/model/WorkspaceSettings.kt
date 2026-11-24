package com.antigravity.mobile.feature.workspace.model

import kotlinx.serialization.Serializable

@Serializable
enum class AppThemeMode {
    DARK,
    LIGHT,
    SYSTEM,
}

@Serializable
enum class InterfaceLayoutMode {
    SOFTWARE_ENGINEERING,
    COMPACT_MOBILE,
}

@Serializable
enum class SettingsCategory {
    GENERAL,
    ACCOUNTS_QUOTA,
    MODELS_AGENT,
    PROJECTS_PERMISSIONS,
    CUSTOMIZATIONS,
    MOBILE_ROOT,
}

@Serializable
data class AppSettings(
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val useDynamicColor: Boolean = false,
    val layoutMode: InterfaceLayoutMode = InterfaceLayoutMode.SOFTWARE_ENGINEERING,
    val autoScrollChat: Boolean = true,
    val showCodeLineNumbers: Boolean = true,
    val showThoughtBlocksExpanded: Boolean = false,
    val acpProcessPerSession: Boolean = true,
    val acpSandboxEnabled: Boolean = false,
    val overlayOrbEnabled: Boolean = true,
    val overlayOpacity: Float = 0.92f,
    val hardwareKillSwitchEnabled: Boolean = true,
    val notifyOnTaskComplete: Boolean = true,
    val autoRefreshQuotaOnLaunch: Boolean = true,
)
