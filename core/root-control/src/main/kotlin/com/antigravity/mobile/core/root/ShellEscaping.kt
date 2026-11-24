package com.antigravity.mobile.core.root

/**
 * Strict POSIX shell escaping and input validation helpers to prevent command injection
 * when constructing privileged shell commands.
 */
object ShellEscaping {

    private val PACKAGE_NAME_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")
    private val COMPONENT_NAME_REGEX =
        Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+/[a-zA-Z0-9_.$]+$")
    private val IDENTIFIER_REGEX = Regex("^[A-Za-z0-9_.:-]+$")

    /**
     * Quotes [arg] using POSIX single-quoting so no shell metacharacters
     * (`$`, `` ` ``, `"`, `\`, `;`, `|`, `&`, newlines, spaces) are interpreted.
     */
    fun quote(arg: String): String {
        if (arg.isEmpty()) return "''"
        return "'" + arg.replace("'", "'\\''") + "'"
    }

    /**
     * Escapes every element of [argv] and joins them with single spaces.
     */
    fun joinArgv(argv: List<String>): String {
        require(argv.isNotEmpty()) { "argv must not be empty" }
        return argv.joinToString(separator = " ") { quote(it) }
    }

    /**
     * Validates an Android package name (e.g. `com.antigravity.mobile`).
     */
    fun validatePackageName(packageName: String): String {
        require(PACKAGE_NAME_REGEX.matches(packageName)) {
            "Invalid Android package name: $packageName"
        }
        return packageName
    }

    /**
     * Validates a flattened Android component name (`pkg/ClassName` or `pkg/.ClassName`).
     */
    fun validateComponentName(componentName: String): String {
        require(COMPONENT_NAME_REGEX.matches(componentName)) {
            "Invalid Android component name: $componentName"
        }
        return componentName
    }

    /**
     * Validates an Android permission name or AppOp identifier.
     */
    fun validateIdentifier(identifier: String): String {
        require(IDENTIFIER_REGEX.matches(identifier)) {
            "Invalid shell identifier: $identifier"
        }
        return identifier
    }
}
