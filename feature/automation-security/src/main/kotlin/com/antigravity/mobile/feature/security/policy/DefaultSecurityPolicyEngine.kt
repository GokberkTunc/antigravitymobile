package com.antigravity.mobile.feature.security.policy

import com.antigravity.mobile.contracts.McpToolNames
import com.antigravity.mobile.contracts.McpToolRisk
import com.antigravity.mobile.contracts.PermissionMode
import com.antigravity.mobile.contracts.PolicyDecision
import com.antigravity.mobile.contracts.PolicyEvaluation
import com.antigravity.mobile.contracts.PolicyRuleOverride
import com.antigravity.mobile.contracts.SecurityPolicyEngine
import com.antigravity.mobile.feature.security.killswitch.KillSwitchCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Production implementation of [SecurityPolicyEngine] enforcing Confirmed Design Decision #5:
 *
 * 1. Explicitly forbidden catastrophic `root_exec` patterns ALWAYS return [PolicyDecision.DENY].
 * 2. Protected foreground packages (Turkish banks, e-Devlet, 2FA/authenticators, password managers,
 *    Google Wallet) ALWAYS force `ui_action`, `ui_input_raw`, and `screen_capture` to
 *    [PolicyDecision.REQUIRE_APPROVAL] (or [PolicyDecision.DENY] if tightened by a rule).
 * 3. `CRITICAL` tools (`phone_call_control`, `sms_manage` send, `root_exec` and destructive
 *    patterns, or any tool flagged [McpToolRisk.CRITICAL]) ALWAYS return
 *    [PolicyDecision.REQUIRE_APPROVAL] (or [PolicyDecision.DENY] in PLAN mode / explicit DENY rule)
 *    — NEVER bypassed even in [PermissionMode.AUTO] or by a per-project ALLOW override.
 * 4. `LOW` and `MEDIUM` tools on non-protected packages return [PolicyDecision.ALLOW] in
 *    [PermissionMode.AUTO], [PolicyDecision.REQUIRE_APPROVAL] in [PermissionMode.ASK], and
 *    [PolicyDecision.DENY] for mutating tools in [PermissionMode.PLAN].
 * 5. Per-project rule overrides customize non-critical behavior without ever bypassing the gate.
 */
class DefaultSecurityPolicyEngine(
    private val store: SecurityPolicyStore,
    private val killSwitchCoordinator: KillSwitchCoordinator,
) : SecurityPolicyEngine {

    private val stateMutex = Mutex()
    private var hydrated: Boolean = false

    private val _denylistPackages = MutableStateFlow(DefaultProtectedPackages.ALL)
    override val denylistPackages: StateFlow<Set<String>> = _denylistPackages.asStateFlow()

    private val _permissionMode = MutableStateFlow(PermissionMode.ASK)
    override val permissionMode: StateFlow<PermissionMode> = _permissionMode.asStateFlow()

    private val _ruleOverrides = MutableStateFlow<List<PolicyRuleOverride>>(emptyList())
    val ruleOverrides: StateFlow<List<PolicyRuleOverride>> = _ruleOverrides.asStateFlow()

    /**
     * Hydrates in-memory policy state from [SecurityPolicyStore] on IO if not yet loaded.
     */
    suspend fun initialize() {
        ensureHydrated()
    }

    override suspend fun evaluate(
        toolName: String,
        riskLevel: McpToolRisk,
        args: Map<String, String>,
        foregroundPackage: String?,
        projectId: String?,
    ): PolicyEvaluation {
        ensureHydrated()

        val normalizedTool = toolName.trim()
        val normalizedPackage = foregroundPackage?.trim()?.takeIf { it.isNotEmpty() }
        val currentDenylist = _denylistPackages.value
        val currentMode = _permissionMode.value
        val matchingOverride = findMatchingOverride(normalizedTool, projectId)
        val isMutating = isMutatingInvocation(normalizedTool, args)

        // 1. Inspect command / path for destructive or explicitly forbidden patterns
        val commandArg = extractCommandArgument(normalizedTool, args)
        val destructiveMatch = if (normalizedTool == McpToolNames.ROOT_EXEC || commandArg.isNotEmpty()) {
            DestructiveCommandDetector.detect(commandArg)
        } else {
            null
        }

        if (destructiveMatch?.severity == DestructiveCommandDetector.Severity.FORBIDDEN) {
            return PolicyEvaluation(
                toolName = normalizedTool,
                riskLevel = McpToolRisk.CRITICAL,
                decision = PolicyDecision.DENY,
                reason = destructiveMatch.reason,
                matchedProtectedPackage = normalizedPackage?.takeIf { it in currentDenylist },
                matchedDestructivePattern = destructiveMatch.patternName,
                canBeOverridden = false,
            )
        }

        val isSystemFsWrite = normalizedTool == McpToolNames.FS_WRITE &&
            isSystemPath(args["path"].orEmpty())

        // 2. Protected Package Denylist gate (never bypassed even in AUTO or via ALLOW override)
        if (
            normalizedPackage != null &&
            normalizedPackage in currentDenylist &&
            normalizedTool in PROTECTED_UI_TOOLS
        ) {
            val tightenedDecision = if (matchingOverride?.decision == PolicyDecision.DENY) {
                PolicyDecision.DENY
            } else {
                PolicyDecision.REQUIRE_APPROVAL
            }
            return PolicyEvaluation(
                toolName = normalizedTool,
                riskLevel = maxRisk(riskLevel, McpToolRisk.HIGH),
                decision = tightenedDecision,
                reason = "Foreground package '$normalizedPackage' is on the protected denylist; explicit approval is mandatory for '$normalizedTool'",
                matchedProtectedPackage = normalizedPackage,
                canBeOverridden = false,
            )
        }

        // 3. Mandatory CRITICAL tool gate (never bypassed even in AUTO or via ALLOW override)
        val effectiveRisk = computeEffectiveRisk(
            toolName = normalizedTool,
            declaredRisk = riskLevel,
            args = args,
            destructiveMatch = destructiveMatch,
            isSystemFsWrite = isSystemFsWrite,
        )

        if (effectiveRisk == McpToolRisk.CRITICAL) {
            val matchedPattern = destructiveMatch?.patternName
                ?: if (isSystemFsWrite) "system_partition_write" else null

            if (currentMode == PermissionMode.PLAN && isMutating) {
                return PolicyEvaluation(
                    toolName = normalizedTool,
                    riskLevel = McpToolRisk.CRITICAL,
                    decision = PolicyDecision.DENY,
                    reason = "PLAN mode denies mutating critical tool '$normalizedTool'",
                    matchedDestructivePattern = matchedPattern,
                    canBeOverridden = false,
                )
            }

            if (matchingOverride?.decision == PolicyDecision.DENY) {
                return PolicyEvaluation(
                    toolName = normalizedTool,
                    riskLevel = McpToolRisk.CRITICAL,
                    decision = PolicyDecision.DENY,
                    reason = "Explicit DENY policy override '${matchingOverride.id}' blocked critical tool '$normalizedTool'",
                    matchedDestructivePattern = matchedPattern,
                    canBeOverridden = false,
                )
            }

            val approvalReason = when {
                destructiveMatch != null -> destructiveMatch.reason
                isSystemFsWrite -> "Writing to /system via fs_write requires explicit approval"
                normalizedTool == McpToolNames.PHONE_CALL_CONTROL ->
                    "Phone call control is a CRITICAL tool and always requires explicit user approval"
                normalizedTool == McpToolNames.SMS_MANAGE ->
                    "SMS send/management is a CRITICAL tool and always requires explicit user approval"
                normalizedTool == McpToolNames.ROOT_EXEC ->
                    "Root shell execution (root_exec) is a CRITICAL tool and always requires explicit user approval"
                else ->
                    "CRITICAL risk tool '$normalizedTool' always requires explicit user approval"
            }

            return PolicyEvaluation(
                toolName = normalizedTool,
                riskLevel = McpToolRisk.CRITICAL,
                decision = PolicyDecision.REQUIRE_APPROVAL,
                reason = approvalReason,
                matchedDestructivePattern = matchedPattern,
                canBeOverridden = false,
            )
        }

        // 4. PLAN mode denies mutating tools (cannot be relaxed to ALLOW)
        if (currentMode == PermissionMode.PLAN && isMutating) {
            return PolicyEvaluation(
                toolName = normalizedTool,
                riskLevel = effectiveRisk,
                decision = PolicyDecision.DENY,
                reason = "PLAN mode denies mutating tool '$normalizedTool'",
                canBeOverridden = false,
            )
        }

        // 5. Per-project or global rule override (non-critical, non-protected-package calls only)
        if (matchingOverride != null) {
            return PolicyEvaluation(
                toolName = normalizedTool,
                riskLevel = effectiveRisk,
                decision = matchingOverride.decision,
                reason = "Applied policy rule override '${matchingOverride.id}' (project=${matchingOverride.projectId ?: "global"})",
                canBeOverridden = true,
            )
        }

        // 6. Standard mode-based evaluation
        return when (currentMode) {
            PermissionMode.AUTO -> when (effectiveRisk) {
                McpToolRisk.LOW, McpToolRisk.MEDIUM -> PolicyEvaluation(
                    toolName = normalizedTool,
                    riskLevel = effectiveRisk,
                    decision = PolicyDecision.ALLOW,
                    reason = "AUTO mode permits $effectiveRisk tool '$normalizedTool' on non-protected package",
                    canBeOverridden = true,
                )
                McpToolRisk.HIGH -> PolicyEvaluation(
                    toolName = normalizedTool,
                    riskLevel = effectiveRisk,
                    decision = PolicyDecision.REQUIRE_APPROVAL,
                    reason = "HIGH risk tool '$normalizedTool' requires approval even in AUTO mode unless explicitly overridden",
                    canBeOverridden = true,
                )
                McpToolRisk.CRITICAL -> PolicyEvaluation(
                    toolName = normalizedTool,
                    riskLevel = McpToolRisk.CRITICAL,
                    decision = PolicyDecision.REQUIRE_APPROVAL,
                    reason = "CRITICAL tool '$normalizedTool' always requires approval",
                    canBeOverridden = false,
                )
            }

            PermissionMode.ASK -> PolicyEvaluation(
                toolName = normalizedTool,
                riskLevel = effectiveRisk,
                decision = PolicyDecision.REQUIRE_APPROVAL,
                reason = "ASK mode requires user approval for '$normalizedTool' ($effectiveRisk)",
                canBeOverridden = true,
            )

            PermissionMode.PLAN -> when (effectiveRisk) {
                McpToolRisk.LOW, McpToolRisk.MEDIUM -> PolicyEvaluation(
                    toolName = normalizedTool,
                    riskLevel = effectiveRisk,
                    decision = PolicyDecision.ALLOW,
                    reason = "PLAN mode permits read-only $effectiveRisk inspection tool '$normalizedTool'",
                    canBeOverridden = true,
                )
                McpToolRisk.HIGH, McpToolRisk.CRITICAL -> PolicyEvaluation(
                    toolName = normalizedTool,
                    riskLevel = effectiveRisk,
                    decision = PolicyDecision.REQUIRE_APPROVAL,
                    reason = "PLAN mode requires approval for privileged inspection tool '$normalizedTool'",
                    canBeOverridden = true,
                )
            }
        }
    }

    override suspend fun setPermissionMode(mode: PermissionMode) {
        stateMutex.withLock {
            hydrateUnlocked()
            _permissionMode.value = mode
            store.savePermissionMode(mode)
        }
    }

    override suspend fun addProtectedPackage(packageName: String) {
        val trimmed = packageName.trim()
        if (trimmed.isEmpty()) return
        stateMutex.withLock {
            hydrateUnlocked()
            val updated = _denylistPackages.value + trimmed
            _denylistPackages.value = updated
            store.saveProtectedPackages(updated)
        }
    }

    override suspend fun removeProtectedPackage(packageName: String) {
        val trimmed = packageName.trim()
        if (trimmed.isEmpty()) return
        stateMutex.withLock {
            hydrateUnlocked()
            val updated = _denylistPackages.value - trimmed
            _denylistPackages.value = updated
            store.saveProtectedPackages(updated)
        }
    }

    override suspend fun setRuleOverride(override: PolicyRuleOverride) {
        require(override.id.isNotBlank()) { "PolicyRuleOverride.id must not be blank" }
        require(override.toolName.isNotBlank()) { "PolicyRuleOverride.toolName must not be blank" }
        stateMutex.withLock {
            hydrateUnlocked()
            val updated = _ruleOverrides.value
                .filterNot { it.id == override.id } + override
            _ruleOverrides.value = updated
            store.saveRuleOverrides(updated)
        }
    }

    override suspend fun clearRuleOverride(overrideId: String) {
        stateMutex.withLock {
            hydrateUnlocked()
            val updated = _ruleOverrides.value.filterNot { it.id == overrideId }
            _ruleOverrides.value = updated
            store.saveRuleOverrides(updated)
        }
    }

    override suspend fun triggerEmergencyKillSwitch(reason: String): Result<Unit> {
        return killSwitchCoordinator.trigger(reason)
    }

    private suspend fun ensureHydrated() {
        if (hydrated) return
        stateMutex.withLock {
            hydrateUnlocked()
        }
    }

    private suspend fun hydrateUnlocked() {
        if (hydrated) return
        val snapshot = store.loadSnapshot()
        _denylistPackages.value = snapshot.protectedPackages
        _permissionMode.value = snapshot.permissionMode
        _ruleOverrides.value = snapshot.ruleOverrides
        hydrated = true
    }

    private fun findMatchingOverride(
        toolName: String,
        projectId: String?,
    ): PolicyRuleOverride? {
        val overrides = _ruleOverrides.value
        if (projectId != null) {
            overrides.lastOrNull { it.projectId == projectId && it.toolName == toolName }
                ?.let { return it }
        }
        return overrides.lastOrNull { it.projectId == null && it.toolName == toolName }
    }

    private fun extractCommandArgument(toolName: String, args: Map<String, String>): String {
        if (toolName != McpToolNames.ROOT_EXEC) return ""
        return args["command"] ?: args["cmd"] ?: ""
    }

    private fun computeEffectiveRisk(
        toolName: String,
        declaredRisk: McpToolRisk,
        args: Map<String, String>,
        destructiveMatch: DestructiveCommandDetector.DetectionMatch?,
        isSystemFsWrite: Boolean,
    ): McpToolRisk {
        if (destructiveMatch != null || isSystemFsWrite) return McpToolRisk.CRITICAL

        val intrinsicRisk = when (toolName) {
            McpToolNames.ROOT_EXEC,
            McpToolNames.PHONE_CALL_CONTROL,
            -> McpToolRisk.CRITICAL

            McpToolNames.SMS_MANAGE -> {
                val action = args["action"]?.trim()?.lowercase().orEmpty()
                if (action == "list" || action == "read") {
                    declaredRisk
                } else {
                    // "send" or unspecified action is ALWAYS CRITICAL (fail-closed)
                    McpToolRisk.CRITICAL
                }
            }

            else -> declaredRisk
        }

        return maxRisk(declaredRisk, intrinsicRisk)
    }

    private fun isMutatingInvocation(toolName: String, args: Map<String, String>): Boolean {
        val action = args["action"]?.trim()?.lowercase().orEmpty()
        return when (toolName) {
            McpToolNames.UI_GET_TREE,
            McpToolNames.FS_READ,
            McpToolNames.FS_LIST,
            McpToolNames.SCREEN_CAPTURE,
            -> false

            McpToolNames.UI_ACTION,
            McpToolNames.UI_INPUT_RAW,
            McpToolNames.FS_WRITE,
            McpToolNames.ROOT_EXEC,
            McpToolNames.PHONE_CALL_CONTROL,
            -> true

            McpToolNames.SMS_MANAGE -> action !in setOf("list", "read")
            McpToolNames.APP_LAUNCH_OR_MANAGE -> action != "list"
            McpToolNames.CONTACTS_CALENDAR_QUERY -> action != "query"
            McpToolNames.NOTIFICATIONS_MANAGE -> action != "list"
            McpToolNames.DEVICE_CONTROL -> action !in setOf("get_setting", "get_clip")
            McpToolNames.MEDIA_LOCATION_CAPTURE -> action != "get_location"
            else -> true
        }
    }

    private fun isSystemPath(rawPath: String): Boolean {
        val parts = rawPath.trim().replace('\\', '/').split('/')
        val resolved = ArrayList<String>(parts.size)
        for (part in parts) {
            when (part) {
                "", "." -> continue
                ".." -> if (resolved.isNotEmpty()) resolved.removeAt(resolved.lastIndex)
                else -> resolved.add(part)
            }
        }
        return resolved.firstOrNull() == "system"
    }

    private fun maxRisk(a: McpToolRisk, b: McpToolRisk): McpToolRisk {
        return if (a.ordinal >= b.ordinal) a else b
    }

    companion object {
        val PROTECTED_UI_TOOLS: Set<String> = setOf(
            McpToolNames.UI_ACTION,
            McpToolNames.UI_INPUT_RAW,
            McpToolNames.SCREEN_CAPTURE,
        )
    }
}
