package com.antigravity.mobile.core.account

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Result of extracting a verified Google OAuth URL from ACP/CLI output.
 */
data class ExtractedOAuthUrl(
    val url: String,
    val callbackPort: Int? = null,
    val redirectUri: String? = null,
)

/**
 * Extracts Google OAuth authorization URLs from `agy` / `agy acp` stdout, stderr,
 * or `-32000 Authentication required` challenge messages.
 *
 * Only accepts HTTPS URLs hosted on `accounts.google.com` to guard against open-redirect/phishing injection.
 */
object OAuthUrlExtractor {
    private val ANSI_ESCAPE_REGEX = Regex("\\u001B\\[[0-9;]*[A-Za-z]")
    private val GOOGLE_OAUTH_URL_REGEX = Regex(
        """https://accounts\.google\.com/(?:o/oauth2/(?:v2/)?auth|signin/oauth|ServiceLogin)[^\s"'<>)\]}]+""",
        RegexOption.IGNORE_CASE,
    )
    private val TRAILING_PUNCTUATION = setOf('.', ',', ';', ':', ')', ']', '}', '"', '\'')

    fun extract(rawOutput: String): ExtractedOAuthUrl? {
        if (rawOutput.isBlank()) return null
        val cleaned = ANSI_ESCAPE_REGEX.replace(rawOutput, "")
        val match = GOOGLE_OAUTH_URL_REGEX.find(cleaned) ?: return null
        val candidate = trimTrailingPunctuation(match.value)

        val uri = parseValidGoogleAuthUri(candidate) ?: return null
        val queryParams = parseQueryParams(uri.rawQuery)
        val redirectUri = queryParams["redirect_uri"]
        val callbackPort = redirectUri?.let { extractLoopbackPort(it) }

        return ExtractedOAuthUrl(
            url = candidate,
            callbackPort = callbackPort,
            redirectUri = redirectUri,
        )
    }

    fun isValidGoogleAuthUrl(url: String): Boolean = parseValidGoogleAuthUri(url) != null

    private fun parseValidGoogleAuthUri(url: String): URI? {
        val uri = try {
            URI(url)
        } catch (_: Exception) {
            return null
        }
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (scheme != "https" || host != "accounts.google.com") return null
        return uri
    }

    private fun trimTrailingPunctuation(raw: String): String {
        var end = raw.length
        while (end > 0 && raw[end - 1] in TRAILING_PUNCTUATION) {
            end--
        }
        return raw.substring(0, end)
    }

    private fun extractLoopbackPort(redirectUri: String): Int? {
        val uri = try {
            URI(redirectUri)
        } catch (_: Exception) {
            return null
        }
        val host = uri.host?.lowercase() ?: return null
        val isLoopback = host == "127.0.0.1" || host == "localhost" || host == "::1" || host == "[::1]"
        if (!isLoopback) return null
        return uri.port.takeIf { it in 1..65535 }
    }

    internal fun parseQueryParams(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        val result = LinkedHashMap<String, String>()
        for (pair in rawQuery.split('&')) {
            if (pair.isEmpty()) continue
            val idx = pair.indexOf('=')
            val key = if (idx >= 0) decode(pair.substring(0, idx)) else decode(pair)
            val value = if (idx >= 0) decode(pair.substring(idx + 1)) else ""
            if (key.isNotEmpty()) {
                result[key] = value
            }
        }
        return result
    }

    private fun decode(value: String): String = try {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    } catch (_: Exception) {
        value
    }
}

sealed interface OAuthCallbackResult {
    data class Success(
        val code: String,
        val state: String? = null,
        val port: Int? = null,
    ) : OAuthCallbackResult

    data class OAuthError(
        val error: String,
        val description: String? = null,
    ) : OAuthCallbackResult

    data class Invalid(
        val reason: String,
    ) : OAuthCallbackResult
}

/**
 * Parses and strictly validates `127.0.0.1` / `localhost` OAuth loopback callback URIs.
 */
object OAuthCallbackParser {
    private val ALLOWED_HOSTS = setOf("127.0.0.1", "localhost", "::1", "[::1]")

    fun parse(callbackUri: String): OAuthCallbackResult {
        val trimmed = callbackUri.trim()
        if (trimmed.isEmpty()) {
            return OAuthCallbackResult.Invalid("Callback URI must not be blank")
        }

        val uri = try {
            URI(trimmed)
        } catch (e: Exception) {
            return OAuthCallbackResult.Invalid("Malformed callback URI: ${e.message}")
        }

        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            return OAuthCallbackResult.Invalid("Unsupported callback URI scheme: '$scheme'")
        }

        val host = uri.host?.lowercase()
        if (host !in ALLOWED_HOSTS) {
            return OAuthCallbackResult.Invalid("Callback URI host must be loopback (got '$host')")
        }

        val params = OAuthUrlExtractor.parseQueryParams(uri.rawQuery)
        val error = params["error"]
        if (!error.isNullOrBlank()) {
            return OAuthCallbackResult.OAuthError(
                error = error,
                description = params["error_description"]?.takeIf { it.isNotBlank() },
            )
        }

        val code = params["code"]?.trim()
        if (code.isNullOrEmpty()) {
            return OAuthCallbackResult.Invalid("Callback URI missing 'code' query parameter")
        }

        return OAuthCallbackResult.Success(
            code = code,
            state = params["state"]?.takeIf { it.isNotBlank() },
            port = uri.port.takeIf { it in 1..65535 },
        )
    }

    /**
     * Accepts a raw manual authorization code, a pasted loopback redirect URL, OR the official
     * `https://antigravity.google/oauth-callback?...` landing URL emitted by `agy 1.5.16+`,
     * and returns the extracted authorization code.
     */
    fun extractCodeFromUserInput(input: String): Result<String> {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Authorization code must not be blank"))
        }
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            val uri = try {
                URI(trimmed)
            } catch (e: Exception) {
                return Result.failure(IllegalArgumentException("Malformed callback URI: ${e.message}"))
            }
            val scheme = uri.scheme?.lowercase()
            val host = uri.host?.lowercase()
            val isOfficialAntigravityCallback = scheme == "https" &&
                host == "antigravity.google" &&
                uri.path?.startsWith("/oauth-callback") == true

            if (isOfficialAntigravityCallback) {
                val params = OAuthUrlExtractor.parseQueryParams(uri.rawQuery)
                val error = params["error"]
                if (!error.isNullOrBlank()) {
                    val desc = params["error_description"]?.takeIf { it.isNotBlank() }
                    return Result.failure(
                        IllegalStateException(
                            "Google OAuth returned error '$error'" + (desc?.let { ": $it" } ?: ""),
                        ),
                    )
                }
                val code = params["code"]?.trim()
                if (code.isNullOrEmpty()) {
                    return Result.failure(IllegalArgumentException("Callback URI missing 'code' query parameter"))
                }
                return Result.success(code)
            }

            return when (val parsed = parse(trimmed)) {
                is OAuthCallbackResult.Success -> Result.success(parsed.code)
                is OAuthCallbackResult.OAuthError -> Result.failure(
                    IllegalStateException(
                        "Google OAuth returned error '${parsed.error}'" +
                            (parsed.description?.let { ": $it" } ?: ""),
                    ),
                )
                is OAuthCallbackResult.Invalid -> Result.failure(IllegalArgumentException(parsed.reason))
            }
        }
        if ('\n' in trimmed || '\r' in trimmed || '\u0000' in trimmed) {
            return Result.failure(IllegalArgumentException("Authorization code contains illegal control characters"))
        }
        return Result.success(trimmed)
    }
}
