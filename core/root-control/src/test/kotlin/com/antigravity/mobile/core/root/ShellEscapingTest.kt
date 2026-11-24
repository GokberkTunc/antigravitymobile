package com.antigravity.mobile.core.root

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ShellEscapingTest {

    @Test
    fun `quote safely wraps shell metacharacters and embedded single quotes`() {
        assertEquals("''", ShellEscaping.quote(""))
        assertEquals("'hello'", ShellEscaping.quote("hello"))
        assertEquals("'a b; rm -rf /'", ShellEscaping.quote("a b; rm -rf /"))
        assertEquals("'it'\\''s \$HOME `id`'", ShellEscaping.quote("it's \$HOME `id`"))
    }

    @Test
    fun `joinArgv escapes every argument and rejects empty list`() {
        assertEquals(
            "'pm' 'grant' 'com.antigravity.mobile' 'android.permission.CAMERA'",
            ShellEscaping.joinArgv(
                listOf("pm", "grant", "com.antigravity.mobile", "android.permission.CAMERA"),
            ),
        )
        assertThrows(IllegalArgumentException::class.java) {
            ShellEscaping.joinArgv(emptyList())
        }
    }

    @Test
    fun `package and component validators reject injection attempts`() {
        assertEquals("com.antigravity.mobile", ShellEscaping.validatePackageName("com.antigravity.mobile"))
        assertEquals(
            "com.antigravity.mobile/.service.AntigravityAccessibilityService",
            ShellEscaping.validateComponentName(
                "com.antigravity.mobile/.service.AntigravityAccessibilityService",
            ),
        )

        assertThrows(IllegalArgumentException::class.java) {
            ShellEscaping.validatePackageName("com.antigravity.mobile; id")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ShellEscaping.validatePackageName("singleword")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ShellEscaping.validateComponentName("com.foo/bar && reboot")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ShellEscaping.validateIdentifier("CAMERA;rm -rf /")
        }
    }
}
