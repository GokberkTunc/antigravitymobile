package com.antigravity.mobile.core.account

internal object AccountShellQuoting {
    private val SAFE_BARE_REGEX = Regex("^[A-Za-z0-9_@%+=:,./-]+$")

    fun quote(value: String): String {
        require('\u0000' !in value) { "Shell argument must not contain NUL bytes" }
        if (value.isEmpty()) return "''"
        if (SAFE_BARE_REGEX.matches(value)) return value
        return "'" + value.replace("'", "'\"'\"'") + "'"
    }

    fun validateAbsolutePath(path: String, paramName: String = "path"): String {
        require(path.isNotBlank()) { "$paramName must not be blank" }
        require(path.startsWith("/")) { "$paramName must be an absolute path: '$path'" }
        require('\u0000' !in path && '\n' !in path && '\r' !in path) {
            "$paramName contains illegal control characters"
        }
        require(path.split('/').none { it == ".." }) {
            "$paramName must not contain parent traversal ('..'): '$path'"
        }
        return path
    }
}
