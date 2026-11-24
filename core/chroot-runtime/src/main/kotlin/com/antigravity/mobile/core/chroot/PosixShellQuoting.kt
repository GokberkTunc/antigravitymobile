package com.antigravity.mobile.core.chroot

/**
 * POSIX shell escaping and validation helpers to prevent command/argument/env injection
 * when constructing rootfs, bind-mount, and chroot shell invocations.
 */
object PosixShellQuoting {
    private val SAFE_BARE_REGEX = Regex("^[A-Za-z0-9_@%+=:,./-]+$")
    private val ENV_KEY_REGEX = Regex("^[A-Za-z_][A-Za-z0-9_]*$")
    private val IPV4_REGEX = Regex("^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$")
    private val IPV6_REGEX = Regex("^[0-9A-Fa-f:]+$")

    /**
     * Quotes [value] as a single safe POSIX shell token.
     * Throws [IllegalArgumentException] if [value] contains a NUL byte (`\u0000`).
     */
    fun quote(value: String): String {
        require('\u0000' !in value) { "Shell argument must not contain NUL bytes" }
        if (value.isEmpty()) return "''"
        if (SAFE_BARE_REGEX.matches(value)) return value
        return "'" + value.replace("'", "'\"'\"'") + "'"
    }

    /**
     * Joins [argv] into a single POSIX-safe command line string.
     */
    fun joinCommand(argv: List<String>): String {
        require(argv.isNotEmpty()) { "Command argv must not be empty" }
        return argv.joinToString(" ") { quote(it) }
    }

    /**
     * Validates a POSIX environment variable key (`[A-Za-z_][A-Za-z0-9_]*`).
     */
    fun validateEnvKey(key: String): String {
        require(ENV_KEY_REGEX.matches(key)) {
            "Invalid POSIX environment variable name: '$key'"
        }
        return key
    }

    /**
     * Validates an absolute Unix path without NUL, newlines, or `..` traversal segments.
     */
    fun validateAbsolutePath(path: String, paramName: String = "path"): String {
        require(path.isNotBlank()) { "$paramName must not be blank" }
        require(path.startsWith("/")) { "$paramName must be an absolute path: '$path'" }
        require('\u0000' !in path && '\n' !in path && '\r' !in path) {
            "$paramName contains illegal control characters"
        }
        val segments = path.split('/')
        require(segments.none { it == ".." }) {
            "$paramName must not contain parent directory traversal ('..'): '$path'"
        }
        return path
    }

    /**
     * Validates an IPv4 or IPv6 DNS server address.
     */
    fun isValidDnsAddress(address: String): Boolean {
        val trimmed = address.trim()
        if (trimmed.isEmpty() || trimmed.length > 64) return false
        return IPV4_REGEX.matches(trimmed) || (trimmed.contains(':') && IPV6_REGEX.matches(trimmed))
    }
}
