package com.antigravity.mobile.contracts

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

@Serializable
enum class PolicyDecision {
    ALLOW,
    REQUIRE_APPROVAL,
    DENY,
}

@Serializable
data class PolicyEvaluation(
    val toolName: String,
    val riskLevel: McpToolRisk,
    val decision: PolicyDecision,
    val reason: String,
    val matchedProtectedPackage: String? = null,
    val matchedDestructivePattern: String? = null,
    val canBeOverridden: Boolean = true,
)

@Serializable
data class PolicyRuleOverride(
    val id: String,
    val projectId: String? = null,
    val toolName: String,
    val decision: PolicyDecision,
    val createdAtEpochMs: Long = 0L,
)

@Serializable
data class ApprovalRequest(
    val requestId: String,
    val toolName: String,
    val riskLevel: McpToolRisk,
    val title: String,
    val summary: String,
    val argumentsPreview: String = "",
    val foregroundPackage: String? = null,
    val timeoutMs: Long = 60_000L,
)

/**
 * Enforces mandatory safety gates for every MCP tool invocation:
 * - `CRITICAL` tools and foreground protected packages ALWAYS require approval.
 * - `LOW` / `MEDIUM` tools may auto-allow under `PermissionMode.AUTO`.
 * - Coordinates emergency kill-switch shutdown across UI, ACP, and chroot processes.
 */
interface SecurityPolicyEngine {
    val denylistPackages: StateFlow<Set<String>>
    val permissionMode: StateFlow<PermissionMode>

    suspend fun evaluate(
        toolName: String,
        riskLevel: McpToolRisk,
        args: Map<String, String> = emptyMap(),
        foregroundPackage: String? = null,
        projectId: String? = null,
    ): PolicyEvaluation

    suspend fun setPermissionMode(mode: PermissionMode)

    suspend fun addProtectedPackage(packageName: String)

    suspend fun removeProtectedPackage(packageName: String)

    suspend fun setRuleOverride(override: PolicyRuleOverride)

    suspend fun clearRuleOverride(overrideId: String)

    suspend fun triggerEmergencyKillSwitch(reason: String = "Hardware volume combo"): Result<Unit>
}
