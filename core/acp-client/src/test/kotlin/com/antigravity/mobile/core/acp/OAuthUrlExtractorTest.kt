package com.antigravity.mobile.core.acp

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OAuthUrlExtractorTest {

    @Test
    fun `extractFromRpcError reads structured authUrl in error data`() {
        val error = JsonRpcError(
            code = JsonRpcErrorCodes.AUTH_REQUIRED,
            message = "Authentication required",
            data = buildJsonObject {
                put("authUrl", "https://accounts.google.com/o/oauth2/v2/auth?client_id=abc&scope=email")
            },
        )

        assertEquals(
            "https://accounts.google.com/o/oauth2/v2/auth?client_id=abc&scope=email",
            OAuthUrlExtractor.extractFromRpcError(error),
        )
    }

    @Test
    fun `extractFromRpcError falls back to scanning message and trims trailing punctuation`() {
        val error = JsonRpcError(
            code = JsonRpcErrorCodes.AUTH_REQUIRED,
            message = "Please sign in at (https://accounts.google.com/o/oauth2/auth?state=xyz).",
        )

        assertEquals(
            "https://accounts.google.com/o/oauth2/auth?state=xyz",
            OAuthUrlExtractor.extractFromRpcError(error),
        )
    }

    @Test
    fun `extractFromDiagnosticLine captures Google OAuth URL from stderr`() {
        val line = "Open this URL in your browser: https://accounts.google.com/o/oauth2/v2/auth?redirect_uri=http://127.0.0.1:44321"
        assertEquals(
            "https://accounts.google.com/o/oauth2/v2/auth?redirect_uri=http://127.0.0.1:44321",
            OAuthUrlExtractor.extractFromDiagnosticLine(line),
        )
    }

    @Test
    fun `extractFromDiagnosticLine ignores unrelated non-auth URLs in stderr`() {
        val line = "INFO: Loaded plugin docs from https://example.org/readme.md"
        assertNull(OAuthUrlExtractor.extractFromDiagnosticLine(line))
    }

    @Test
    fun `extractFromDiagnosticLine captures generic https URL when line has explicit auth context`() {
        val line = "Authentication required: visit https://auth.example.com/device?code=1234 to login."
        assertEquals(
            "https://auth.example.com/device?code=1234",
            OAuthUrlExtractor.extractFromDiagnosticLine(line),
        )
    }

    @Test
    fun `looksLikeAuthRequiredMessage detects auth challenge lines`() {
        assertTrue(OAuthUrlExtractor.looksLikeAuthRequiredMessage("Error -32000: Authentication required"))
        assertTrue(OAuthUrlExtractor.looksLikeAuthRequiredMessage("User is not authenticated"))
        assertFalse(OAuthUrlExtractor.looksLikeAuthRequiredMessage("Normal debug log line"))
    }
}
