package com.antigravity.mobile.core.acp

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Extracts Google OAuth / authorization URLs from JSON-RPC `-32000` errors and `agy acp`
 * process stdout/stderr streams so Android can launch Chrome Custom Tabs for sign-in.
 */
object OAuthUrlExtractor {
    private val URL_REGEX = Regex("""https?://[^\s"'<>)\]}]+""")
    private val TRAILING_PUNCTUATION = charArrayOf('.', ',', ';', ':', '!', '?', ')', ']', '}')
    private val AUTH_FIELD_KEYS = listOf(
        "authUrl",
        "auth_url",
        "authorizationUrl",
        "authorization_url",
        "loginUrl",
        "login_url",
        "verificationUri",
        "verification_uri",
        "url",
    )
    private val AUTH_DOMAINS = listOf(
        "accounts.google.com",
        "oauth2.googleapis.com",
        "login.google.com",
        "antigravity.google",
    )
    private val AUTH_CONTEXT_KEYWORDS = listOf(
        "oauth",
        "auth",
        "login",
        "sign in",
        "signin",
        "authorize",
        "authenticate",
        "authentication required",
        "credentials",
        "-32000",
    )

    /**
     * Extracts an auth URL from a `-32000 Authentication required` [JsonRpcError].
     * Checks structured `error.data` fields first, then falls back to scanning `error.message`
     * and serialized `error.data`.
     */
    fun extractFromRpcError(error: JsonRpcError): String? {
        extractFromJsonElement(error.data)?.let { return it }
        extractAnyUrl(error.message)?.let { return it }
        return error.data?.toString()?.let { extractAnyUrl(it) }
    }

    /**
     * Extracts an OAuth URL from a raw `stderr` or non-JSON `stdout` line.
     * Only matches URLs that either belong to a known Google/auth domain or appear in a line
     * containing authentication context keywords, avoiding false positives on unrelated log URLs.
     */
    fun extractFromDiagnosticLine(line: String): String? {
        if (line.isBlank()) return null
        val urls = findUrls(line)
        if (urls.isEmpty()) return null

        // Prefer known Google / OAuth URLs unconditionally.
        urls.firstOrNull { isKnownAuthUrl(it) }?.let { return it }

        // Otherwise require explicit auth context in the diagnostic line.
        val lower = line.lowercase()
        val hasAuthContext = AUTH_CONTEXT_KEYWORDS.any { lower.contains(it) }
        return if (hasAuthContext) urls.firstOrNull() else null
    }

    /**
     * Returns true when a diagnostic stderr line indicates an authentication requirement even if
     * the OAuth URL appears on a subsequent line.
     */
    fun looksLikeAuthRequiredMessage(line: String): Boolean {
        val lower = line.lowercase()
        return lower.contains("authentication required") ||
            lower.contains("not authenticated") ||
            lower.contains("-32000") ||
            (lower.contains("oauth") && lower.contains("http"))
    }

    fun extractFromJsonElement(element: JsonElement?): String? {
        if (element == null) return null
        return when (element) {
            is JsonPrimitive -> element.contentOrNull?.let { extractAnyUrl(it) }
            is JsonObject -> {
                for (key in AUTH_FIELD_KEYS) {
                    val direct = (element[key] as? JsonPrimitive)?.contentOrNull
                    if (direct != null) {
                        extractAnyUrl(direct)?.let { return it }
                    }
                }
                element.values.firstNotNullOfOrNull { extractFromJsonElement(it) }
            }
            is JsonArray -> element.firstNotNullOfOrNull { extractFromJsonElement(it) }
        }
    }

    fun extractAnyUrl(text: String): String? {
        val urls = findUrls(text)
        if (urls.isEmpty()) return null
        return urls.firstOrNull { isKnownAuthUrl(it) } ?: urls.first()
    }

    private fun findUrls(text: String): List<String> =
        URL_REGEX.findAll(text)
            .map { match -> match.value.trimEnd(*TRAILING_PUNCTUATION) }
            .filter { it.startsWith("https://") || it.startsWith("http://") }
            .toList()

    private fun isKnownAuthUrl(url: String): Boolean {
        val lower = url.lowercase()
        if (AUTH_DOMAINS.any { lower.contains(it) }) return true
        return lower.contains("oauth") ||
            lower.contains("client_id=") ||
            lower.contains("redirect_uri=") ||
            lower.contains("/authorize")
    }
}
