package com.antigravity.mobile.core.account

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URI

class OAuthParsersTest {

    @Test
    fun `OAuthUrlExtractor extracts Google OAuth URL with ANSI codes and parses loopback callback port`() {
        val rawOutput = "\u001B[33mAuthentication required (-32000).\u001B[0m\n" +
            "Please visit: https://accounts.google.com/o/oauth2/v2/auth?client_id=test-client" +
            "&redirect_uri=http%3A%2F%2F127.0.0.1%3A45678%2Foauth2callback" +
            "&response_type=code&scope=openid%20email.\n"

        val extracted = OAuthUrlExtractor.extract(rawOutput)
        assertNotNull(extracted)
        assertEquals(45678, extracted!!.callbackPort)
        assertEquals("http://127.0.0.1:45678/oauth2callback", extracted.redirectUri)
        assertFalse(extracted.url.endsWith("."))
        assertTrue(extracted.url.startsWith("https://accounts.google.com/o/oauth2/v2/auth?"))
    }

    @Test
    fun `OAuthUrlExtractor rejects non-Google and cleartext HTTP auth URLs`() {
        assertNull(OAuthUrlExtractor.extract("Open http://accounts.google.com/o/oauth2/v2/auth?client_id=1"))
        assertNull(OAuthUrlExtractor.extract("Open https://evil.example.com/accounts.google.com/o/oauth2/v2/auth?client_id=1"))
        assertNull(OAuthUrlExtractor.extract("No auth link in this output"))
    }

    @Test
    fun `OAuthCallbackParser parses valid loopback callback and rejects non-loopback hosts`() {
        val ok = OAuthCallbackParser.parse(
            "http://127.0.0.1:45678/oauth2callback?code=4%2F0AfJohTestCode&state=csrf-123&scope=email",
        )
        assertTrue(ok is OAuthCallbackResult.Success)
        ok as OAuthCallbackResult.Success
        assertEquals("4/0AfJohTestCode", ok.code)
        assertEquals("csrf-123", ok.state)
        assertEquals(45678, ok.port)

        val err = OAuthCallbackParser.parse(
            "http://localhost:8080/oauth2callback?error=access_denied&error_description=User+cancelled",
        )
        assertTrue(err is OAuthCallbackResult.OAuthError)
        err as OAuthCallbackResult.OAuthError
        assertEquals("access_denied", err.error)
        assertEquals("User cancelled", err.description)

        val remote = OAuthCallbackParser.parse("https://attacker.example/oauth2callback?code=stolen")
        assertTrue(remote is OAuthCallbackResult.Invalid)

        val missingCode = OAuthCallbackParser.parse("http://127.0.0.1:8080/oauth2callback?state=abc")
        assertTrue(missingCode is OAuthCallbackResult.Invalid)
    }

    @Test
    fun `extractCodeFromUserInput accepts both raw auth codes and pasted loopback callback URLs`() {
        val rawCode = OAuthCallbackParser.extractCodeFromUserInput("  4/0A_manual_code_123  ")
        assertEquals("4/0A_manual_code_123", rawCode.getOrThrow())

        val pastedUrl = OAuthCallbackParser.extractCodeFromUserInput(
            "http://127.0.0.1:51234/oauth2callback?code=4%2F0A_pasted_url_code&state=s1",
        )
        assertEquals("4/0A_pasted_url_code", pastedUrl.getOrThrow())

        assertTrue(OAuthCallbackParser.extractCodeFromUserInput("   ").isFailure)
        assertTrue(OAuthCallbackParser.extractCodeFromUserInput("https://evil.com/cb?code=123").isFailure)
    }

    @Test(timeout = 15_000L)
    fun `LoopbackOAuthServer captures callback strictly on 127_0_0_1 ephemeral port`() = runBlocking {
        val server = LoopbackOAuthServer()
        try {
            val port = server.start(requestedPort = 0)
            assertTrue("Expected bound ephemeral port > 0", port > 0)

            val callbackDeferred = async { server.awaitCallback(timeoutMs = 5_000L) }

            val url = URI("http://127.0.0.1:$port/oauth2callback?code=4%2F0ALoopbackTest&state=st42").toURL()
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Connection", "close")
                connectTimeout = 3_000
                readTimeout = 3_000
            }
            val responseCode = conn.responseCode
            conn.inputStream.use { it.readBytes() }
            conn.disconnect()
            assertEquals(200, responseCode)

            val capturedUri = callbackDeferred.await()
            assertNotNull(capturedUri)
            val parsed = OAuthCallbackParser.parse(capturedUri!!)
            assertTrue(parsed is OAuthCallbackResult.Success)
            assertEquals("4/0ALoopbackTest", (parsed as OAuthCallbackResult.Success).code)
            assertEquals("st42", parsed.state)
        } finally {
            server.stop()
        }
    }
}
