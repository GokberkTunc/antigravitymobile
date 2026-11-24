package com.antigravity.mobile.contracts

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class AccountPlanTier {
    FREE,
    PRO,
    ULTRA,
    ENTERPRISE,
    UNKNOWN,
}

@Serializable
data class ProfilePaths(
    val profileId: String,
    val hostRootDir: String,
    val chrootHome: String,
    val chrootConfig: String,
    val chrootData: String,
    val chrootCache: String,
    val chrootConversationsDir: String,
    val hostConfigDir: String,
    val hostGeminiDir: String,
    val chrootGeminiDir: String,
) {
    init {
        require(profileId.isNotBlank()) { "profileId must not be blank" }
        require(!profileId.contains('/') && !profileId.contains("..")) {
            "profileId must not contain path separators or traversal sequences"
        }
    }

    fun toChrootEnvironment(): Map<String, String> = mapOf(
        "HOME" to chrootHome,
        "XDG_CONFIG_HOME" to chrootConfig,
        "XDG_DATA_HOME" to chrootData,
        "XDG_CACHE_HOME" to chrootCache,
        "AGY_CONVERSATIONS_DIR" to chrootConversationsDir,
        "PATH" to "/usr/local/bin:/usr/bin:/bin",
    )

    companion object {
        fun forProfileId(
            profileId: String,
            hostBaseDir: String = ChrootConstants.DEFAULT_PROFILES_HOST_PATH,
            chrootBaseDir: String = ChrootConstants.DEFAULT_PROFILES_CHROOT_PATH,
        ): ProfilePaths {
            val hostRoot = "$hostBaseDir/$profileId"
            val chrootRoot = "$chrootBaseDir/$profileId"
            return ProfilePaths(
                profileId = profileId,
                hostRootDir = hostRoot,
                chrootHome = "$chrootRoot/home",
                chrootConfig = "$chrootRoot/config",
                chrootData = "$chrootRoot/data",
                chrootCache = "$chrootRoot/cache",
                chrootConversationsDir = "$chrootRoot/data/conversations",
                hostConfigDir = "$hostRoot/config",
                hostGeminiDir = "$hostRoot/home/.gemini",
                chrootGeminiDir = "$chrootRoot/home/.gemini",
            )
        }
    }
}

@Serializable
data class UsageQuota(
    val planTier: AccountPlanTier = AccountPlanTier.UNKNOWN,
    val remainingPercent: Double? = null,
    val usedRequests: Long? = null,
    val limitRequests: Long? = null,
    val resetAtEpochMs: Long? = null,
    val rawSummary: String = "",
    val updatedAtEpochMs: Long = 0L,
) {
    val isNearLimit: Boolean
        get() = remainingPercent != null && remainingPercent <= 15.0
}

@Serializable
data class AccountProfile(
    val id: String,
    val displayName: String,
    val email: String? = null,
    val avatarUrl: String? = null,
    val isAuthenticated: Boolean = false,
    val isActive: Boolean = false,
    val paths: ProfilePaths,
    val quota: UsageQuota = UsageQuota(),
    val createdAtEpochMs: Long = 0L,
    val lastUsedAtEpochMs: Long = 0L,
)

@Serializable
data class LoginChallenge(
    val profileId: String,
    val authUrl: String,
    val callbackPort: Int? = null,
    val instructions: String = "",
    val supportsManualCode: Boolean = true,
    val issuedAtEpochMs: Long = 0L,
)

@Serializable
sealed interface AuthFlowState {
    @Serializable
    @SerialName("idle")
    data object Idle : AuthFlowState

    @Serializable
    @SerialName("starting")
    data class Starting(
        val profileId: String,
    ) : AuthFlowState

    @Serializable
    @SerialName("awaiting_browser_auth")
    data class AwaitingBrowserAuth(
        val challenge: LoginChallenge,
    ) : AuthFlowState

    @Serializable
    @SerialName("submitting_code")
    data class SubmittingCode(
        val profileId: String,
    ) : AuthFlowState

    @Serializable
    @SerialName("authenticated")
    data class Authenticated(
        val profileId: String,
        val email: String? = null,
    ) : AuthFlowState

    @Serializable
    @SerialName("failed")
    data class Failed(
        val profileId: String,
        val reason: String,
    ) : AuthFlowState
}

/**
 * Manages isolated Google account profiles, hybrid OAuth sign-in
 * (Chrome Custom Tabs + 127.0.0.1 callback + manual code + desktop `~/.gemini` import),
 * and `/usage` quota tracking.
 */
interface AccountRepository {
    val profiles: StateFlow<List<AccountProfile>>
    val activeProfile: StateFlow<AccountProfile?>
    val authFlowState: StateFlow<AuthFlowState>

    suspend fun listProfiles(): List<AccountProfile>

    suspend fun createProfile(
        displayName: String,
        email: String? = null,
    ): Result<AccountProfile>

    suspend fun deleteProfile(profileId: String): Result<Unit>

    suspend fun startLoginFlow(
        profileId: String,
        capturedAuthUrl: String? = null,
    ): Result<LoginChallenge>

    suspend fun submitAuthCode(
        profileId: String,
        code: String,
    ): Result<AccountProfile>

    suspend fun handleLoopbackCallback(
        profileId: String,
        callbackUri: String,
    ): Result<AccountProfile>

    suspend fun importDesktopProfile(
        profileId: String,
        sourcePath: String,
    ): Result<AccountProfile>

    suspend fun switchActiveProfile(profileId: String): Result<AccountProfile>

    suspend fun refreshUsage(profileId: String): Result<UsageQuota>

    suspend fun suggestFallbackProfile(currentProfileId: String): AccountProfile?
}
