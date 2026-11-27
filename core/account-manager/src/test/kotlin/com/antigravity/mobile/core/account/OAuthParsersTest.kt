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
    fun `extractCodeFromUserInput accepts raw codes, loopback URLs, and official antigravity oauth-callback URLs`() {
        val rawCode = OAuthCallbackParser.extractCodeFromUserInput("  4/0A_manual_code_123  ")
        assertEquals("4/0A_manual_code_123", rawCode.getOrThrow())

        val pastedUrl = OAuthCallbackParser.extractCodeFromUserInput(
            "http://127.0.0.1:51234/oauth2callback?code=4%2F0A_pasted_url_code&state=s1",
        )
        assertEquals("4/0A_pasted_url_code", pastedUrl.getOrThrow())

        val officialLandingUrl = OAuthCallbackParser.extractCodeFromUserInput(
            "https://antigravity.google/oauth-callback?code=4%2F0A_official_pkce_code&state=Z5nRf",
        )
        assertEquals("4/0A_official_pkce_code", officialLandingUrl.getOrThrow())

        val deepLinkUrl = OAuthCallbackParser.extractCodeFromUserInput(
            "antigravity://oauth/callback?code=4%2F0A_deeplink_code_99&state=s2",
        )
        assertEquals("4/0A_deeplink_code_99", deepLinkUrl.getOrThrow())

        assertTrue(
            "Strict loopback parse() must still reject non-loopback hosts",
            OAuthCallbackParser.parse("https://antigravity.google/oauth-callback?code=123") is OAuthCallbackResult.Invalid,
        )
        assertTrue(
            OAuthCallbackParser.extractCodeFromUserInput("https://antigravity.google/oauth-callback?error=access_denied").isFailure,
        )
        assertTrue(OAuthCallbackParser.extractCodeFromUserInput("   ").isFailure)
        assertTrue(OAuthCallbackParser.extractCodeFromUserInput("https://evil.com/cb?code=123").isFailure)
    }

    @Test
    fun `extractClipboardAuthCandidate accepts strictly verified OAuth codes and callback URLs`() {
        val validCode = "4/0AeaYSHAbcdefghijklmnopqrstuvwxyz1234"
        assertEquals(validCode, OAuthCallbackParser.extractClipboardAuthCandidate(validCode))
        assertEquals(validCode, OAuthCallbackParser.extractClipboardAuthCandidate("  $validCode \n"))

        assertEquals(
            "4/0A_official_clip_code_1234567890",
            OAuthCallbackParser.extractClipboardAuthCandidate(
                "https://antigravity.google/oauth-callback?code=4%2F0A_official_clip_code_1234567890&state=abc",
            ),
        )
        assertEquals(
            "4/0A_loopback_clip_code_1234567890",
            OAuthCallbackParser.extractClipboardAuthCandidate(
                "http://127.0.0.1:12345/oauth2callback?code=4%2F0A_loopback_clip_code_1234567890&scope=email",
            ),
        )
        assertEquals(
            "4/0A_localhost_clip_code_1234567890",
            OAuthCallbackParser.extractClipboardAuthCandidate(
                "http://localhost:8080/oauth2callback?code=4%2F0A_localhost_clip_code_1234567890",
            ),
        )
        assertEquals(
            "4/0A_deeplink_clip_code_1234567890",
            OAuthCallbackParser.extractClipboardAuthCandidate(
                "antigravity://oauth/callback?code=4%2F0A_deeplink_clip_code_1234567890&state=xyz",
            ),
        )
    }

    @Test
    fun `extractClipboardAuthCandidate rejects ordinary URLs, passwords, blank text, short codes, and prose`() {
        val rejected = listOf(
            "",
            "   \n\t  ",
            "4/short",
            "4/0A_only_19_chars12",
            "4/0A_invalid!chars_12345678901234567890",
            "MyS3cur3P@ssw0rd!2026LongPassword",
            "random-token-without-google-prefix-12345678901234567890",
            "https://www.google.com/search?q=antigravity",
            "https://evil.example.com/oauth-callback?code=4%2F0A_stolen_12345678901234567890",
            "https://antigravity.google.evil.com/oauth-callback?code=4%2F0A_spoofed_12345678901234567890",
            "http://antigravity.google/oauth-callback?code=4%2F0A_insecure_http_1234567890",
            "https://antigravity.google/docs?code=4%2F0A_wrong_path_1234567890",
            "https://antigravity.google/oauth-callback?state=no_code_here",
            "https://antigravity.google/oauth-callback?code=   ",
            "https://antigravity.google/oauth-callback?error=access_denied&code=4%2F0A_error_present",
            "http://127.0.0.1:12345/oauth2callback?state=missing_code",
            "antigravity://other/callback?code=4%2F0A_wrong_host_1234567890",
            "antigravity://oauth/other?code=4%2F0A_wrong_path_1234567890",
            "Here is your sign-in note. Please open Chrome and sign in with your account.",
            "Copy this code: 4/0AeaYSHAbcdefghijklmnopqrstuvwxyz1234",
            "4/0AeaYSHAbcdefghijklmnopqrstuvwxyz1234\nsecond line",
        )
        for (sample in rejected) {
            assertNull("Expected null for clipboard sample: '$sample'", OAuthCallbackParser.extractClipboardAuthCandidate(sample))
        }
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
