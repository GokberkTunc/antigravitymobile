package com.antigravity.mobile.system

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import com.antigravity.mobile.R
import com.antigravity.mobile.core.account.OAuthCallbackParser
import com.antigravity.mobile.core.account.OAuthCallbackResult
import com.antigravity.mobile.core.account.OAuthUrlExtractor
import com.antigravity.mobile.di.AppComponentMetadata
import com.antigravity.mobile.feature.overlay.FloatingOverlayService
import com.antigravity.mobile.feature.security.service.AgentForegroundService
import com.antigravity.mobile.feature.workspace.model.WorkspaceAction
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper managing [DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN] state & activation intents.
 */
@Singleton
class DeviceAdminHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val devicePolicyManager: DevicePolicyManager,
    private val metadata: AppComponentMetadata,
) {
    fun isDeviceAdminActive(): Boolean =
        devicePolicyManager.isAdminActive(metadata.deviceAdminComponent)

    fun createAddDeviceAdminIntent(
        explanation: String = context.getString(R.string.device_admin_description),
    ): Intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, metadata.deviceAdminComponent)
        putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, explanation)
    }
}

/**
 * Launches validated Google OAuth URLs in Chrome Custom Tabs.
 */
@Singleton
class CustomTabsHelper @Inject constructor() {
    fun launchAuthUrl(context: Context, authUrl: String): Boolean {
        if (!OAuthUrlExtractor.isValidGoogleAuthUrl(authUrl)) {
            return false
        }
        return runCatching {
            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
            customTabsIntent.launchUrl(context, Uri.parse(authUrl))
            true
        }.getOrDefault(false)
    }
}

/**
 * Coordinates `:app`-specific component names for one-tap root permission bootstrapping
 * and foreground/overlay service startup.
 */
@Singleton
class AppBootstrapHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val metadata: AppComponentMetadata,
) {
    fun createRootBootstrapAction(): WorkspaceAction.RunRootBootstrap =
        WorkspaceAction.RunRootBootstrap(
            packageName = metadata.packageName,
            accessibilityServiceComponent = metadata.accessibilityServiceComponent,
            notificationListenerComponent = metadata.notificationListenerComponent,
        )

    fun startAgentForegroundService(statusText: String = AgentForegroundService.DEFAULT_STATUS_TEXT) {
        val intent = AgentForegroundService.createStartIntent(context, statusText = statusText)
        ContextCompat.startForegroundService(context, intent)
    }

    fun startFloatingOverlayService() {
        FloatingOverlayService.startOverlay(context)
    }
}

/**
 * Parses OAuth callback URIs delivered to [com.antigravity.mobile.MainActivity] via
 * custom scheme (`antigravity://oauth/callback?code=...`) or loopback redirect URIs.
 */
sealed interface OAuthIntentParseResult {
    data object NotOAuthIntent : OAuthIntentParseResult
    data class AuthorizationCode(val code: String, val state: String? = null) : OAuthIntentParseResult
    data class Error(val message: String) : OAuthIntentParseResult
}

object OAuthCallbackIntentParser {
    const val CUSTOM_SCHEME = "antigravity"
    const val CUSTOM_HOST = "oauth"
    const val CUSTOM_PATH_PREFIX = "/callback"
    const val OFFICIAL_HTTPS_HOST = "antigravity.google"
    const val OFFICIAL_HTTPS_PATH_PREFIX = "/oauth-callback"

    fun parseUriString(rawUri: String?): OAuthIntentParseResult {
        val trimmed = rawUri?.trim().orEmpty()
        if (trimmed.isEmpty()) return OAuthIntentParseResult.NotOAuthIntent

        val uri = try {
            URI(trimmed)
        } catch (_: Exception) {
            return OAuthIntentParseResult.NotOAuthIntent
        }

        val scheme = uri.scheme?.lowercase() ?: return OAuthIntentParseResult.NotOAuthIntent
        val host = uri.host?.lowercase() ?: return OAuthIntentParseResult.NotOAuthIntent
        val path = uri.path ?: ""

        // 1. Custom app scheme (`antigravity://oauth/callback?code=...`) or official
        //    Antigravity HTTPS callback (`https://antigravity.google/oauth-callback?code=...`)
        val isCustomSchemeCallback = scheme == CUSTOM_SCHEME &&
            host == CUSTOM_HOST &&
            path.startsWith(CUSTOM_PATH_PREFIX)
        val isOfficialHttpsCallback = scheme == "https" &&
            host == OFFICIAL_HTTPS_HOST &&
            path.startsWith(OFFICIAL_HTTPS_PATH_PREFIX)

        if (isCustomSchemeCallback || isOfficialHttpsCallback) {
            val params = parseQueryParams(uri.rawQuery)
            val oauthError = params["error"]
            if (!oauthError.isNullOrBlank()) {
                val desc = params["error_description"]?.takeIf { it.isNotBlank() }
                return OAuthIntentParseResult.Error(
                    if (desc != null) "Google OAuth hatası ($oauthError): $desc" else "Google OAuth hatası: $oauthError",
                )
            }
            val code = params["code"]?.trim()
            return if (!code.isNullOrEmpty()) {
                OAuthIntentParseResult.AuthorizationCode(
                    code = code,
                    state = params["state"]?.takeIf { it.isNotBlank() },
                )
            } else {
                OAuthIntentParseResult.Error("OAuth callback URI 'code' parametresi içermiyor")
            }
        }

        // 2. Loopback HTTP/HTTPS redirect URI (127.0.0.1 / localhost)
        if (scheme == "http" || scheme == "https") {
            return when (val parsed = OAuthCallbackParser.parse(trimmed)) {
                is OAuthCallbackResult.Success -> OAuthIntentParseResult.AuthorizationCode(
                    code = parsed.code,
                    state = parsed.state,
                )
                is OAuthCallbackResult.OAuthError -> OAuthIntentParseResult.Error(
                    "Google OAuth hatası (${parsed.error})" +
                        (parsed.description?.let { ": $it" } ?: ""),
                )
                is OAuthCallbackResult.Invalid -> OAuthIntentParseResult.NotOAuthIntent
            }
        }

        return OAuthIntentParseResult.NotOAuthIntent
    }

    private fun parseQueryParams(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        val result = LinkedHashMap<String, String>()
        for (pair in rawQuery.split('&')) {
            if (pair.isEmpty()) continue
            val idx = pair.indexOf('=')
            val key = java.net.URLDecoder.decode(
                if (idx >= 0) pair.substring(0, idx) else pair,
                Charsets.UTF_8.name(),
            )
            val value = java.net.URLDecoder.decode(
                if (idx >= 0) pair.substring(idx + 1) else "",
                Charsets.UTF_8.name(),
            )
            if (key.isNotEmpty()) {
                result[key] = value
            }
        }
        return result
    }
}
