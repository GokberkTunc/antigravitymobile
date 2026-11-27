package com.antigravity.mobile.feature.security.policy

/**
 * Inspects privileged shell commands for destructive or explicitly forbidden patterns
 * so they can NEVER be auto-approved under any permission mode or override.
 */
object DestructiveCommandDetector {

    enum class Severity {
        /** Explicitly forbidden catastrophic operations that always result in `PolicyDecision.DENY`. */
        FORBIDDEN,

        /** High-risk destructive patterns that always require explicit user approval (`REQUIRE_APPROVAL`). */
        DESTRUCTIVE,
    }

    data class DetectionMatch(
        val severity: Severity,
        val patternName: String,
        val reason: String,
    )

    private data class CommandRule(
        val severity: Severity,
        val name: String,
        val regex: Regex,
        val reason: String,
    )

    private val forbiddenRules: List<CommandRule> = listOf(
        CommandRule(
            severity = Severity.FORBIDDEN,
            name = "rm_rf_root_filesystem",
            regex = Regex(
                pattern = """\brm\s+(?:-[a-zA-Z]*r[a-zA-Z]*f[a-zA-Z]*|-[a-zA-Z]*f[a-zA-Z]*r[a-zA-Z]*|-[a-zA-Z]*r[a-zA-Z]*\s+-[a-zA-Z]*f[a-zA-Z]*|-[a-zA-Z]*f[a-zA-Z]*\s+-[a-zA-Z]*r[a-zA-Z]*|--recursive\s+--force|--force\s+--recursive)(?:\s+--no-preserve-root)?\s+(?:/\s*$|/\*|/(?:system|data|vendor|boot)(?:/\*?)?(?:\s|$|[;&|]))""",
                option = RegexOption.IGNORE_CASE,
            ),
            reason = "Catastrophic recursive deletion of critical system or root partition is forbidden",
        ),
        CommandRule(
            severity = Severity.FORBIDDEN,
            name = "dd_raw_block_device",
            regex = Regex(
                pattern = """(?:^|[;&|]\s*|\s+)dd\s+[^;&|]*\bof=/dev/(?:block/|mmcblk|sd[a-z])""",
                option = RegexOption.IGNORE_CASE,
            ),
            reason = "Direct raw block-device overwrite via dd is forbidden",
        ),
        CommandRule(
            severity = Severity.FORBIDDEN,
            name = "mkfs_raw_block_device",
            regex = Regex(
                pattern = """\bmkfs(?:\.\w+)?\b[^;&|]*/dev/(?:block/|mmcblk|sd[a-z])""",
                option = RegexOption.IGNORE_CASE,
            ),
            reason = "Formatting raw device block partitions via mkfs is forbidden",
        ),
        CommandRule(
            severity = Severity.FORBIDDEN,
            name = "fastboot_critical_partition",
            regex = Regex(
                pattern = """\bfastboot\s+(?:erase|flash)\s+(?:bootloader|aboot|xbl|abl|efs|persist|modemst[12])\b""",
                option = RegexOption.IGNORE_CASE,
            ),
            reason = "Erasing or flashing critical bootloader/EFS partitions via fastboot is forbidden",
        ),
        CommandRule(
            severity = Severity.FORBIDDEN,
            name = "shell_fork_bomb",
            regex = Regex(
                pattern = """:\(\)\s*\{[^}]*:\s*\|\s*:[^}]*&\s*\}\s*;\s*:""",
            ),
            reason = "Fork bomb pattern is explicitly forbidden",
        ),
    )

    private val destructiveRules: List<CommandRule> = listOf(
        CommandRule(
            severity = Severity.DESTRUCTIVE,
            name = "rm_rf",
            regex = Regex(
                pattern = """\brm\s+(?:-[a-zA-Z]*r[a-zA-Z]*f[a-zA-Z]*|-[a-zA-Z]*f[a-zA-Z]*r[a-zA-Z]*|-[a-zA-Z]*r[a-zA-Z]*\s+-[a-zA-Z]*f[a-zA-Z]*|-[a-zA-Z]*f[a-zA-Z]*\s+-[a-zA-Z]*r[a-zA-Z]*|--recursive\s+--force|--force\s+--recursive)\b""",
                option = RegexOption.IGNORE_CASE,
            ),
            reason = "Destructive recursive forced deletion (rm -rf) requires explicit approval",
        ),
        CommandRule(
            severity = Severity.DESTRUCTIVE,
            name = "dd_disk_write",
            regex = Regex(
                pattern = """(?:^|[;&|]\s*|\s+)dd\s+""",
                option = RegexOption.IGNORE_CASE,
            ),
            reason = "Low-level disk/stream operation (dd) requires explicit approval",
        ),
        CommandRule(
            severity = Severity.DESTRUCTIVE,
            name = "mkfs_format",
            regex = Regex(
                pattern = """\bmkfs(?:\.\w+)?\b""",
                option = RegexOption.IGNORE_CASE,
            ),
            reason = "Filesystem creation/formatting (mkfs) requires explicit approval",
        ),
        CommandRule(
            severity = Severity.DESTRUCTIVE,
            name = "system_partition_write",
            regex = Regex(
                pattern = """(?:mount\s+[^;&|]*(?:-o\s*[^;&|]*\brw\b|remount)[^;&|]*/system\b|>>?\s*/system(?:/|\s|$)|\b(?:cp|mv|rm|chmod|chown|touch|mkdir|rmdir|ln|tee|install)\b[^;&|]*/system(?:/|\s|$)|\bsed\b[^;&|]*-i[^;&|]*/system(?:/|\s|$))""",
                option = RegexOption.IGNORE_CASE,
            ),
            reason = "Write or remount operation targeting /system requires explicit approval",
        ),
        CommandRule(
            severity = Severity.DESTRUCTIVE,
            name = "reboot_bootloader_fastboot_edl",
            regex = Regex(
                pattern = """\b(?:reboot|svc\s+power\s+reboot)\s+(?:bootloader|fastboot|edl|download)\b""",
                option = RegexOption.IGNORE_CASE,
            ),
            reason = "Rebooting into bootloader, fastboot, or EDL mode requires explicit approval",
        ),
        CommandRule(
            severity = Severity.DESTRUCTIVE,
            name = "fastboot_flash_erase",
            regex = Regex(
                pattern = """\bfastboot\s+(?:flash|erase|format|oem\s+unlock|flashing\s+unlock)\b""",
                option = RegexOption.IGNORE_CASE,
            ),
            reason = "Fastboot flash/erase/unlock operation requires explicit approval",
        ),
        CommandRule(
            severity = Severity.DESTRUCTIVE,
            name = "block_wipe_utility",
            regex = Regex(
                pattern = """\b(?:blkdiscard|wipefs)\b""",
                option = RegexOption.IGNORE_CASE,
            ),
            reason = "Low-level block wipe utility requires explicit approval",
        ),
    )

    fun detect(command: String): DetectionMatch? {
        val raw = command.trim()
        if (raw.isEmpty()) return null
        val normalized = raw
            .replace("\"", "")
            .replace("'", "")
            .replace(Regex("/+"), "/")
        val candidates = if (normalized == raw) listOf(raw) else listOf(raw, normalized)

        for (candidate in candidates) {
            for (rule in forbiddenRules) {
                if (rule.regex.containsMatchIn(candidate)) {
                    return DetectionMatch(
                        severity = rule.severity,
                        patternName = rule.name,
                        reason = rule.reason,
                    )
                }
            }
        }

        for (candidate in candidates) {
            for (rule in destructiveRules) {
                if (rule.regex.containsMatchIn(candidate)) {
                    return DetectionMatch(
                        severity = rule.severity,
                        patternName = rule.name,
                        reason = rule.reason,
                    )
                }
            }
        }

        return null
    }
}
