package com.antigravity.mobile.core.chroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PosixShellQuotingTest {

    @Test
    fun `quote leaves safe bare strings unquoted and wraps empty string`() {
        assertEquals("''", PosixShellQuoting.quote(""))
        assertEquals("/data/local/ag_rootfs", PosixShellQuoting.quote("/data/local/ag_rootfs"))
        assertEquals("--ide-mcp-port=12345", PosixShellQuoting.quote("--ide-mcp-port=12345"))
    }

    @Test
    fun `quote neutralizes shell injection payloads`() {
        val injection = "\$(rm -rf /); `id` | cat /etc/passwd"
        val quoted = PosixShellQuoting.quote(injection)
        assertTrue(quoted.startsWith("'"))
        assertTrue(quoted.endsWith("'"))
        assertEquals("'${injection}'", quoted)
    }

    @Test
    fun `quote safely escapes embedded single quotes`() {
        val payload = "it's '; rm -rf / #"
        val quoted = PosixShellQuoting.quote(payload)
        assertEquals("'it'\"'\"'s '\"'\"'; rm -rf / #'", quoted)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `quote rejects NUL bytes`() {
        PosixShellQuoting.quote("abc\u0000def")
    }

    @Test
    fun `validateEnvKey accepts valid POSIX names and rejects injection`() {
        assertEquals("XDG_CONFIG_HOME", PosixShellQuoting.validateEnvKey("XDG_CONFIG_HOME"))
        assertEquals("_PRIVATE_1", PosixShellQuoting.validateEnvKey("_PRIVATE_1"))

        val invalidKeys = listOf("", "1ABC", "FOO=BAR", "FOO;rm", "FOO BAR", "FOO-BAR")
        for (key in invalidKeys) {
            val threw = try {
                PosixShellQuoting.validateEnvKey(key)
                false
            } catch (_: IllegalArgumentException) {
                true
            }
            assertTrue("Expected key '$key' to be rejected", threw)
        }
    }

    @Test
    fun `validateAbsolutePath enforces absolute clean paths`() {
        assertEquals("/data/local/ag_rootfs", PosixShellQuoting.validateAbsolutePath("/data/local/ag_rootfs"))

        val invalidPaths = listOf(
            "",
            "relative/path",
            "/data/local/../etc/passwd",
            "/data/local\nrm -rf /",
            "/data/local\u0000/bad",
        )
        for (path in invalidPaths) {
            val threw = try {
                PosixShellQuoting.validateAbsolutePath(path)
                false
            } catch (_: IllegalArgumentException) {
                true
            }
            assertTrue("Expected path '$path' to be rejected", threw)
        }
    }

    @Test
    fun `isValidDnsAddress accepts valid IPv4 and IPv6 and rejects injection`() {
        assertTrue(PosixShellQuoting.isValidDnsAddress("8.8.8.8"))
        assertTrue(PosixShellQuoting.isValidDnsAddress("1.1.1.1"))
        assertTrue(PosixShellQuoting.isValidDnsAddress("2001:4860:4860::8888"))

        assertFalse(PosixShellQuoting.isValidDnsAddress(""))
        assertFalse(PosixShellQuoting.isValidDnsAddress("999.999.999.999"))
        assertFalse(PosixShellQuoting.isValidDnsAddress("8.8.8.8; rm -rf /"))
        assertFalse(PosixShellQuoting.isValidDnsAddress("8.8.8.8\nnameserver 1.2.3.4"))
    }
}
