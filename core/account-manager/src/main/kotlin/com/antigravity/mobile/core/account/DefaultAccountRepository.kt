package com.antigravity.mobile.core.account

import com.antigravity.mobile.contracts.AccountPlanTier
import com.antigravity.mobile.contracts.AccountProfile
import com.antigravity.mobile.contracts.AccountRepository
import com.antigravity.mobile.contracts.AuthFlowState
import com.antigravity.mobile.contracts.ChrootConstants
import com.antigravity.mobile.contracts.LoginChallenge
import com.antigravity.mobile.contracts.ProfilePaths
import com.antigravity.mobile.contracts.UsageQuota
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Default implementation of [AccountRepository] providing:
 * - Isolated per-UUID profile directories (`/data/local/ag_profiles/<profileId>/{home,config,data,cache}` + `AGY_CONVERSATIONS_DIR`)
 * - Hybrid Google OAuth flow compatible with `agy 1.4.11` (URL capture -> Chrome Custom Tabs + `127.0.0.1` callback + manual code + desktop `~/.gemini` import)
 * - `/usage` parsing, Room/DataStore caching, and one-tap fallback profile recommendation when nearing quota limits
 */
class DefaultAccountRepository(
    private val store: AccountStore,
    private val isolator: ProfileDirectoryIsolator,
    private val authBridge: AgyAuthBridge,
    private val browserLauncher: OAuthBrowserLauncher,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val hostProfilesBaseDir: String = ChrootConstants.DEFAULT_PROFILES_HOST_PATH,
    private val chrootProfilesBaseDir: String = ChrootConstants.DEFAULT_PROFILES_CHROOT_PATH,
    private val uuidProvider: () -> String = { UUID.randomUUID().toString() },
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + ioDispatcher),
) : AccountRepository {

    private val repoMutex = Mutex()

    private val _profiles = MutableStateFlow<List<AccountProfile>>(emptyList())
    override val profiles: StateFlow<List<AccountProfile>> = _profiles.asStateFlow()

    private val _activeProfile = MutableStateFlow<AccountProfile?>(null)
    override val activeProfile: StateFlow<AccountProfile?> = _activeProfile.asStateFlow()

    private val _authFlowState = MutableStateFlow<AuthFlowState>(AuthFlowState.Idle)
    override val authFlowState: StateFlow<AuthFlowState> = _authFlowState.asStateFlow()

    private val activeChallenges = LinkedHashMap<String, LoginChallenge>()

    init {
        scope.launch {
            store.profilesFlow.collect { list ->
                publishProfiles(list)
            }
        }
    }

    override suspend fun listProfiles(): List<AccountProfile> = withContext(ioDispatcher) {
        val current = store.listProfiles()
        publishProfiles(current)
        current
    }

    override suspend fun createProfile(
        displayName: String,
        email: String?,
    ): Result<AccountProfile> = repoMutex.withLock {
        withContext(ioDispatcher) {
            try {
                val trimmedName = displayName.trim()
                require(trimmedName.isNotEmpty()) { "displayName must not be blank" }

                val profileId = uuidProvider()
                val paths = ProfilePaths.forProfileId(
                    profileId = profileId,
                    hostBaseDir = hostProfilesBaseDir,
                    chrootBaseDir = chrootProfilesBaseDir,
                )

                isolator.ensureDirectories(paths).getOrThrow()

                val existing = store.listProfiles()
                val shouldBeActive = existing.none { it.isActive }
                val now = clockMs()

                val profile = AccountProfile(
                    id = profileId,
                    displayName = trimmedName,
                    email = email?.trim()?.takeIf { it.isNotEmpty() },
                    isAuthenticated = false,
                    isActive = shouldBeActive,
                    paths = paths,
                    quota = UsageQuota(),
                    createdAtEpochMs = now,
                    lastUsedAtEpochMs = if (shouldBeActive) now else 0L,
                )

                store.upsertProfile(profile)
                refreshSnapshotsLocked()
                Result.success(profile)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }
    }

    override suspend fun deleteProfile(profileId: String): Result<Unit> = repoMutex.withLock {
        withContext(ioDispatcher) {
            try {
                val existing = store.getProfile(profileId)
                    ?: return@withContext Result.failure(NoSuchElementException("Profile not found: $profileId"))

                isolator.deleteDirectories(existing.paths).getOrThrow()
                store.deleteProfile(profileId)
                activeChallenges.remove(profileId)

                if (existing.isActive) {
                    val remaining = store.listProfiles()
                    val replacement = rankFallbackCandidates(remaining, excludeProfileId = profileId).firstOrNull()
                        ?: remaining.firstOrNull()
                    if (replacement != null) {
                        store.setActiveProfile(replacement.id, clockMs())
                    } else {
                        store.setActiveProfile(null, clockMs())
                    }
                }

                refreshSnapshotsLocked()
                Result.success(Unit)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }
    }

    override suspend fun startLoginFlow(
        profileId: String,
        capturedAuthUrl: String?,
    ): Result<LoginChallenge> = repoMutex.withLock {
        withContext(ioDispatcher) {
            try {
                val profile = store.getProfile(profileId)
                    ?: return@withContext Result.failure(NoSuchElementException("Profile not found: $profileId"))

                _authFlowState.value = AuthFlowState.Starting(profileId)
                isolator.ensureDirectories(profile.paths).getOrThrow()

                val rawAuthText = capturedAuthUrl ?: authBridge.triggerAuthChallenge(profile).getOrThrow()
                val extracted = rawAuthText?.let { OAuthUrlExtractor.extract(it) }
                if (extracted == null) {
                    val reason = "No Google OAuth authorization URL could be captured from agy output. " +
                        "You can paste a manual auth code or import a desktop ~/.gemini profile."
                    _authFlowState.value = AuthFlowState.Failed(profileId, reason)
                    return@withContext Result.failure(IllegalStateException(reason))
                }

                val challenge = LoginChallenge(
                    profileId = profileId,
                    authUrl = extracted.url,
                    callbackPort = extracted.callbackPort,
                    instructions = buildChallengeInstructions(extracted.callbackPort),
                    supportsManualCode = true,
                    issuedAtEpochMs = clockMs(),
                )
                activeChallenges[profileId] = challenge
                _authFlowState.value = AuthFlowState.AwaitingBrowserAuth(challenge)

                // Best-effort launch in Chrome Custom Tabs; UI can also open challenge.authUrl directly.
                browserLauncher.launchAuthUrl(challenge.authUrl)

                Result.success(challenge)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                _authFlowState.value = AuthFlowState.Failed(
                    profileId = profileId,
                    reason = t.message ?: "Failed to start OAuth login flow",
                )
                Result.failure(t)
            }
        }
    }

    override suspend fun submitAuthCode(
        profileId: String,
        code: String,
    ): Result<AccountProfile> = repoMutex.withLock {
        withContext(ioDispatcher) {
            try {
                val profile = store.getProfile(profileId)
                    ?: return@withContext Result.failure(NoSuchElementException("Profile not found: $profileId"))

                val extractedCode = OAuthCallbackParser.extractCodeFromUserInput(code).getOrElse { err ->
                    _authFlowState.value = AuthFlowState.Failed(profileId, err.message ?: "Invalid auth code")
                    return@withContext Result.failure(err)
                }

                _authFlowState.value = AuthFlowState.SubmittingCode(profileId)
                val callbackPort = activeChallenges[profileId]?.callbackPort

                val completion = authBridge.completeAuthWithCode(
                    profile = profile,
                    code = extractedCode,
                    callbackPort = callbackPort,
                ).getOrElse { err ->
                    _authFlowState.value = AuthFlowState.Failed(profileId, err.message ?: "Auth code rejected")
                    return@withContext Result.failure(err)
                }

                val updated = profile.copy(
                    email = completion.email ?: profile.email,
                    isAuthenticated = true,
                    lastUsedAtEpochMs = clockMs(),
                )
                store.upsertProfile(updated)
                activeChallenges.remove(profileId)
                refreshSnapshotsLocked()
                _authFlowState.value = AuthFlowState.Authenticated(profileId, updated.email)
                Result.success(updated)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                _authFlowState.value = AuthFlowState.Failed(
                    profileId = profileId,
                    reason = t.message ?: "Failed to submit auth code",
                )
                Result.failure(t)
            }
        }
    }

    override suspend fun handleLoopbackCallback(
        profileId: String,
        callbackUri: String,
    ): Result<AccountProfile> {
        return when (val parsed = OAuthCallbackParser.parse(callbackUri)) {
            is OAuthCallbackResult.Success -> {
                if (parsed.port != null) {
                    repoMutex.withLock {
                        val existing = activeChallenges[profileId]
                        if (existing != null && existing.callbackPort == null) {
                            activeChallenges[profileId] = existing.copy(callbackPort = parsed.port)
                        }
                    }
                }
                submitAuthCode(profileId, parsed.code)
            }
            is OAuthCallbackResult.OAuthError -> {
                val reason = buildString {
                    append("Google OAuth failed: ${parsed.error}")
                    if (!parsed.description.isNullOrBlank()) {
                        append(" (${parsed.description})")
                    }
                }
                _authFlowState.value = AuthFlowState.Failed(profileId, reason)
                Result.failure(IllegalStateException(reason))
            }
            is OAuthCallbackResult.Invalid -> {
                _authFlowState.value = AuthFlowState.Failed(profileId, parsed.reason)
                Result.failure(IllegalArgumentException(parsed.reason))
            }
        }
    }

    override suspend fun importDesktopProfile(
        profileId: String,
        sourcePath: String,
    ): Result<AccountProfile> = repoMutex.withLock {
        withContext(ioDispatcher) {
            try {
                val profile = store.getProfile(profileId)
                    ?: return@withContext Result.failure(NoSuchElementException("Profile not found: $profileId"))

                _authFlowState.value = AuthFlowState.Starting(profileId)
                val importResult = isolator.importDesktopProfile(profile.paths, sourcePath).getOrElse { err ->
                    _authFlowState.value = AuthFlowState.Failed(
                        profileId = profileId,
                        reason = err.message ?: "Desktop profile import failed",
                    )
                    return@withContext Result.failure(err)
                }

                val updated = profile.copy(
                    email = importResult.detectedEmail ?: profile.email,
                    isAuthenticated = true,
                    lastUsedAtEpochMs = clockMs(),
                )
                store.upsertProfile(updated)
                activeChallenges.remove(profileId)
                refreshSnapshotsLocked()
                _authFlowState.value = AuthFlowState.Authenticated(profileId, updated.email)
                Result.success(updated)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                _authFlowState.value = AuthFlowState.Failed(
                    profileId = profileId,
                    reason = t.message ?: "Desktop profile import failed",
                )
                Result.failure(t)
            }
        }
    }

    override suspend fun switchActiveProfile(profileId: String): Result<AccountProfile> = repoMutex.withLock {
        withContext(ioDispatcher) {
            try {
                val target = store.getProfile(profileId)
                    ?: return@withContext Result.failure(NoSuchElementException("Profile not found: $profileId"))

                isolator.ensureDirectories(target.paths).getOrThrow()
                val now = clockMs()
                store.setActiveProfile(profileId, now)
                refreshSnapshotsLocked()
                val updated = store.getProfile(profileId) ?: target.copy(isActive = true, lastUsedAtEpochMs = now)
                Result.success(updated)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }
    }

    override suspend fun refreshUsage(profileId: String): Result<UsageQuota> = repoMutex.withLock {
        withContext(ioDispatcher) {
            try {
                val profile = store.getProfile(profileId)
                    ?: return@withContext Result.failure(NoSuchElementException("Profile not found: $profileId"))

                val rawOutput = authBridge.fetchUsageOutput(profile).getOrThrow()
                val quota = UsageOutputParser.parse(rawOutput, nowEpochMs = clockMs())
                store.updateQuota(profileId, quota)
                refreshSnapshotsLocked()
                Result.success(quota)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }
    }

    override suspend fun suggestFallbackProfile(currentProfileId: String): AccountProfile? =
        withContext(ioDispatcher) {
            val all = store.listProfiles()
            rankFallbackCandidates(all, excludeProfileId = currentProfileId).firstOrNull()
        }

    private suspend fun refreshSnapshotsLocked() {
        publishProfiles(store.listProfiles())
    }

    private fun publishProfiles(list: List<AccountProfile>) {
        _profiles.value = list
        _activeProfile.value = list.firstOrNull { it.isActive }
    }

    companion object {
        internal fun rankFallbackCandidates(
            profiles: List<AccountProfile>,
            excludeProfileId: String,
        ): List<AccountProfile> {
            return profiles
                .asSequence()
                .filter { it.id != excludeProfileId }
                .filter { it.isAuthenticated }
                .filter { profile ->
                    val rem = profile.quota.remainingPercent
                    rem == null || rem > 0.0
                }
                .sortedWith(
                    compareByDescending<AccountProfile> { !it.quota.isNearLimit }
                        .thenByDescending { it.quota.remainingPercent ?: 50.0 }
                        .thenByDescending { tierPriority(it.quota.planTier) }
                        .thenBy { it.lastUsedAtEpochMs },
                )
                .toList()
        }

        private fun tierPriority(tier: AccountPlanTier): Int = when (tier) {
            AccountPlanTier.ULTRA -> 4
            AccountPlanTier.ENTERPRISE -> 3
            AccountPlanTier.PRO -> 2
            AccountPlanTier.FREE -> 1
            AccountPlanTier.UNKNOWN -> 0
        }

        private fun buildChallengeInstructions(callbackPort: Int?): String = if (callbackPort != null) {
            "Sign in with your Google account in Chrome Custom Tabs. " +
                "The redirect to 127.0.0.1:$callbackPort will be captured automatically, " +
                "or you may paste the authorization code / redirect URL manually."
        } else {
            "Sign in with your Google account in Chrome Custom Tabs, " +
                "then paste the authorization code (or import a desktop ~/.gemini profile)."
        }
    }
}
