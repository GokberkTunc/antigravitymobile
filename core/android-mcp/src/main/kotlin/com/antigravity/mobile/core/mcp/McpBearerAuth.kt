package com.antigravity.mobile.core.mcp

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Cryptographically strong 256-bit Bearer token generator and constant-time verifier
 * protecting the loopback-only (`127.0.0.1`) Android System MCP server.
 */
object McpBearerAuth {

    private const val TOKEN_BYTE_LENGTH = 32 // 256 bits -> 64 hex characters
    private const val BEARER_PREFIX = "Bearer "
    private val secureRandom = SecureRandom()
    private val hexChars = "0123456789abcdef".toCharArray()

    fun generateToken(): String {
        val bytes = ByteArray(TOKEN_BYTE_LENGTH)
        secureRandom.nextBytes(bytes)
        val out = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            out[i * 2] = hexChars[v ushr 4]
            out[i * 2 + 1] = hexChars[v and 0x0F]
        }
        return String(out)
    }

    /**
     * Verifies `Authorization: Bearer <token>` using constant-time byte comparison
     * (`MessageDigest.isEqual`) to prevent timing side-channels.
     */
    fun verifyAuthorizationHeader(
        authorizationHeader: String?,
        expectedToken: String,
    ): Boolean {
        if (authorizationHeader.isNullOrBlank() || expectedToken.isEmpty()) return false
        if (!authorizationHeader.startsWith(BEARER_PREFIX, ignoreCase = false)) return false
        val providedToken = authorizationHeader.substring(BEARER_PREFIX.length)
        if (providedToken.isEmpty()) return false
        return constantTimeEquals(providedToken, expectedToken)
    }

    fun constantTimeEquals(a: String, b: String): Boolean {
        return MessageDigest.isEqual(
            a.toByteArray(Charsets.UTF_8),
            b.toByteArray(Charsets.UTF_8),
        )
    }
}
