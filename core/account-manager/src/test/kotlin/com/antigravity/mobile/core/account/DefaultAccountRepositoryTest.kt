package com.antigravity.mobile.core.account

import com.antigravity.mobile.contracts.AccountPlanTier
import com.antigravity.mobile.contracts.AccountProfile
import com.antigravity.mobile.contracts.AuthFlowState
import com.antigravity.mobile.contracts.ChrootCommandSpec
import com.antigravity.mobile.contracts.ChrootManager
import com.antigravity.mobile.contracts.ChrootState
import com.antigravity.mobile.contracts.MountStatus
import com.antigravity.mobile.contracts.RootExecutor
import com.antigravity.mobile.contracts.ShellResult
import com.antigravity.mobile.contracts.UsageQuota
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.PipedReader
import java.io.PipedWriter
import java.io.StringReader
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultAccountRepositoryTest {

    private class FakeAccountStore : AccountStore {
        private val items = LinkedHashMap<String, AccountProfile>()
        private val flow = MutableStateFlow<List<AccountProfile>>(emptyList())

        override val profilesFlow: Flow<List<AccountProfile>> = flow

        override suspend fun listProfiles(): List<AccountProfile> = items.values.toList()

        override suspend fun getProfile(profileId: String): AccountProfile? = items[profileId]

        override suspend fun upsertProfile(profile: AccountProfile) {
            items[profile.id] = profile
            flow.value = items.values.toList()
        }

        override suspend fun deleteProfile(profileId: String) {
            items.remove(profileId)
            flow.value = items.values.toList()
        }

        override suspend fun setActiveProfile(profileId: String?, nowEpochMs: Long) {
            for ((key, value) in items.entries.toList()) {
                items[key] = if (key == profileId) {
                    value.copy(isActive = true, lastUsedAtEpochMs = nowEpochMs)
                } else {
                    value.copy(isActive = false)
                }
            }
            flow.value = items.values.toList()
        }

        override suspend fun updateQuota(profileId: String, quota: UsageQuota): AccountProfile? {
            val cur = items[profileId] ?: return null
            val updated = cur.copy(quota = quota)
            items[profileId] = updated
            flow.value = items.values.toList()
            return updated
        }
    }

    @Test
    fun `profile creation isolates UUID directories, sets first active, and supports switching`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = FakeAccountStore()
        val isolator = mockk<ProfileDirectoryIsolator>()
        coEvery { isolator.ensureDirectories(any()) } returns Result.success(Unit)

        var counter = 0
        var clock = 10_000L
        val repo = DefaultAccountRepository(
            store = store,
            isolator = isolator,
            authBridge = mockk(relaxed = true),
            browserLauncher = mockk(relaxed = true),
            ioDispatcher = dispatcher,
            uuidProvider = { "uuid-${++counter}" },
            clockMs = { clock },
            scope = backgroundScope,
        )

        val p1 = repo.createProfile("Personal", "p1@gmail.com").getOrThrow()
        assertEquals("uuid-1", p1.id)
        assertTrue(p1.isActive)
        assertEquals("/profiles/uuid-1/home", p1.paths.chrootHome)
        assertEquals("/profiles/uuid-1/config", p1.paths.chrootConfig)
        assertEquals("/profiles/uuid-1/data", p1.paths.chrootData)
        assertEquals("/profiles/uuid-1/cache", p1.paths.chrootCache)
        assertEquals("/profiles/uuid-1/data/conversations", p1.paths.chrootConversationsDir)

        val env = p1.paths.toChrootEnvironment()
        assertEquals("/profiles/uuid-1/home", env["HOME"])
        assertEquals("/profiles/uuid-1/data/conversations", env["AGY_CONVERSATIONS_DIR"])

        clock = 20_000L
        val p2 = repo.createProfile("Work", "p2@gmail.com").getOrThrow()
        assertFalse(p2.isActive)
        assertEquals("uuid-1", repo.activeProfile.value?.id)

        clock = 30_000L
        val switched = repo.switchActiveProfile("uuid-2").getOrThrow()
        assertTrue(switched.isActive)
        assertEquals("uuid-2", repo.activeProfile.value?.id)
        assertEquals(30_000L, switched.lastUsedAtEpochMs)
    }

    @Test
    fun `hybrid OAuth flow captures URL, opens Chrome Custom Tabs, handles loopback callback and manual code`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = FakeAccountStore()
        val isolator = mockk<ProfileDirectoryIsolator>()
        val authBridge = mockk<AgyAuthBridge>()
        val browserLauncher = mockk<OAuthBrowserLauncher>()

        coEvery { isolator.ensureDirectories(any()) } returns Result.success(Unit)
        every { browserLauncher.launchAuthUrl(any()) } returns true
        coEvery { authBridge.completeAuthWithCode(any(), any(), any()) } returns
            Result.success(AuthCompletionResult(email = "verified@gmail.com"))

        val repo = DefaultAccountRepository(
            store = store,
            isolator = isolator,
            authBridge = authBridge,
            browserLauncher = browserLauncher,
            ioDispatcher = dispatcher,
            uuidProvider = { "uuid-oauth" },
            clockMs = { 50_000L },
            scope = backgroundScope,
        )

        repo.createProfile("OAuth Account").getOrThrow()

        val authUrl = "https://accounts.google.com/o/oauth2/v2/auth?client_id=abc" +
            "&redirect_uri=http%3A%2F%2F127.0.0.1%3A44123%2Foauth2callback&response_type=code"
        val challenge = repo.startLoginFlow("uuid-oauth", capturedAuthUrl = authUrl).getOrThrow()
        assertEquals(44123, challenge.callbackPort)
        assertTrue(challenge.supportsManualCode)
        assertTrue(repo.authFlowState.value is AuthFlowState.AwaitingBrowserAuth)
        verify { browserLauncher.launchAuthUrl(authUrl) }

        val authed = repo.handleLoopbackCallback(
            profileId = "uuid-oauth",
            callbackUri = "http://127.0.0.1:44123/oauth2callback?code=4%2F0A_callback_code&state=s1",
        ).getOrThrow()

        assertTrue(authed.isAuthenticated)
        assertEquals("verified@gmail.com", authed.email)
        assertEquals(AuthFlowState.Authenticated("uuid-oauth", "verified@gmail.com"), repo.authFlowState.value)
        coVerify { authBridge.completeAuthWithCode(any(), "4/0A_callback_code", 44123) }
    }

    @Test
    fun `ChrootAgyAuthBridge runs interactive agy acp --login PKCE flow and submits code to stdin`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val chrootManager = mockk<ChrootManager>()
        val rootExecutor = mockk<RootExecutor>()
        val specSlot = slot<ChrootCommandSpec>()

        every { chrootManager.state } returns MutableStateFlow(
            ChrootState.Mounted(mountStatus = MountStatus(procMounted = true)),
        )
        coEvery { chrootManager.buildChrootCommand(capture(specSlot)) } returns
            listOf("su", "--mount-master", "-c", "chroot /data/local/ag_rootfs ... agy acp --login")
        coEvery { rootExecutor.exec(any(), any(), any()) } returns ShellResult(
            exitCode = 0,
            stdout = """{"active":"pkce@gmail.com"}""",
            stderr = "",
        )

        val pipeWriter = PipedWriter()
        val pipeReader = BufferedReader(PipedReader(pipeWriter))
        val oauthUrl = "https://accounts.google.com/o/oauth2/auth?access_type=offline&client_id=abc" +
            "&code_challenge=xyz&code_challenge_method=S256" +
            "&redirect_uri=https%3A%2F%2Fantigravity.google%2Foauth-callback&response_type=code&state=s1"

        pipeWriter.write("Open this link in a browser to log in with Google:\n\n  $oauthUrl\n\n")
        pipeWriter.flush()

        val writtenLines = mutableListOf<String>()
        var destroyed = false
        val codeSubmittedLatch = CountDownLatch(1)

        val fakeProcess = object : AuthInteractiveProcess {
            override val stdoutAndStderr: BufferedReader = pipeReader
            override fun writeLine(line: String) {
                writtenLines.add(line)
                codeSubmittedLatch.countDown()
                pipeWriter.write("Login succeeded!\n")
                pipeWriter.close()
            }

            override fun waitForExit(timeoutMs: Long): Int {
                codeSubmittedLatch.await(timeoutMs, TimeUnit.MILLISECONDS)
                return 0
            }

            override fun destroy() {
                destroyed = true
                runCatching { pipeWriter.close() }
            }
        }

        val bridge = ChrootAgyAuthBridge(
            chrootManager = chrootManager,
            rootExecutor = rootExecutor,
            ioDispatcher = dispatcher,
            processStarter = AuthProcessStarter { fakeProcess },
        )

        val profile = AccountProfile(
            id = "p1",
            displayName = "Test",
            paths = com.antigravity.mobile.contracts.ProfilePaths.forProfileId("p1"),
        )

        val urlRes = bridge.triggerAuthChallenge(profile)
        assertTrue(urlRes.isSuccess)
        assertEquals(oauthUrl, urlRes.getOrNull())
        assertEquals(listOf("acp", "--login"), specSlot.captured.args)
        assertEquals("/profiles/p1/home", specSlot.captured.environment["HOME"])
        assertFalse("Process must stay alive until code is submitted", destroyed)

        val completeRes = bridge.completeAuthWithCode(profile, "4/0A_pkce_code_123")
        assertTrue(completeRes.isSuccess)
        assertEquals("pkce@gmail.com", completeRes.getOrNull()?.email)
        assertEquals(listOf("4/0A_pkce_code_123"), writtenLines)
        assertTrue("Process must be cleaned up after completion", destroyed)
    }

    @Test
    fun `ChrootAgyAuthBridge falls back to agy models when agy acp --login exits without URL`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val chrootManager = mockk<ChrootManager>()
        val rootExecutor = mockk<RootExecutor>()
        val capturedSpecs = mutableListOf<ChrootCommandSpec>()

        every { chrootManager.state } returns MutableStateFlow(
            ChrootState.Mounted(mountStatus = MountStatus(procMounted = true)),
        )
        coEvery { chrootManager.buildChrootCommand(capture(capturedSpecs)) } returns
            listOf("su", "--mount-master", "-c", "chroot /data/local/ag_rootfs ...")
        coEvery { rootExecutor.execArgv(any(), any(), any()) } returns ShellResult(
            exitCode = 1,
            stdout = "",
            stderr = "Login at https://accounts.google.com/o/oauth2/v2/auth?client_id=x&redirect_uri=http%3A%2F%2F127.0.0.1%3A55555%2Foauth2callback",
        )

        var fallbackProcDestroyed = false
        val failingInteractiveProcess = object : AuthInteractiveProcess {
            override val stdoutAndStderr: BufferedReader = BufferedReader(StringReader("unknown flag: --login\n"))
            override fun writeLine(line: String) = Unit
            override fun waitForExit(timeoutMs: Long): Int = 2
            override fun destroy() {
                fallbackProcDestroyed = true
            }
        }

        val bridge = ChrootAgyAuthBridge(
            chrootManager = chrootManager,
            rootExecutor = rootExecutor,
            ioDispatcher = dispatcher,
            processStarter = AuthProcessStarter { failingInteractiveProcess },
        )

        val profile = AccountProfile(
            id = "p2",
            displayName = "Legacy",
            paths = com.antigravity.mobile.contracts.ProfilePaths.forProfileId("p2"),
        )
        val urlRes = bridge.triggerAuthChallenge(profile)
        assertTrue(urlRes.isSuccess)
        assertNotNull(urlRes.getOrNull())
        assertTrue(urlRes.getOrNull()!!.contains("127.0.0.1"))
        assertTrue(fallbackProcDestroyed)
        assertEquals(listOf("acp", "--login"), capturedSpecs[0].args)
        assertEquals(listOf("models"), capturedSpecs[1].args)
    }

    @Test
    fun `importDesktopProfile marks profile authenticated and captures email`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = FakeAccountStore()
        val isolator = mockk<ProfileDirectoryIsolator>()
        coEvery { isolator.ensureDirectories(any()) } returns Result.success(Unit)
        coEvery { isolator.importDesktopProfile(any(), "/sdcard/gemini.tar.gz") } returns
            Result.success(DesktopImportResult(detectedEmail = "desktop@gmail.com", importedFilesCount = 5))

        val repo = DefaultAccountRepository(
            store = store,
            isolator = isolator,
            authBridge = mockk(relaxed = true),
            browserLauncher = mockk(relaxed = true),
            ioDispatcher = dispatcher,
            uuidProvider = { "uuid-desktop" },
            clockMs = { 70_000L },
            scope = backgroundScope,
        )

        repo.createProfile("Desktop Import").getOrThrow()
        val imported = repo.importDesktopProfile("uuid-desktop", "/sdcard/gemini.tar.gz").getOrThrow()

        assertTrue(imported.isAuthenticated)
        assertEquals("desktop@gmail.com", imported.email)
        assertEquals(AuthFlowState.Authenticated("uuid-desktop", "desktop@gmail.com"), repo.authFlowState.value)
    }

    @Test
    fun `refreshUsage caches parsed quota and suggestFallbackProfile picks best healthy alternative`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = FakeAccountStore()
        val isolator = mockk<ProfileDirectoryIsolator>()
        val authBridge = mockk<AgyAuthBridge>()
        coEvery { isolator.ensureDirectories(any()) } returns Result.success(Unit)

        var idSeq = 0
        val repo = DefaultAccountRepository(
            store = store,
            isolator = isolator,
            authBridge = authBridge,
            browserLauncher = mockk(relaxed = true),
            ioDispatcher = dispatcher,
            uuidProvider = { "acc-${++idSeq}" },
            clockMs = { 100_000L },
            scope = backgroundScope,
        )

        // Create 4 profiles:
        // acc-1: active, authenticated, near limit (5% remaining)
        // acc-2: authenticated, exhausted (0% remaining) -> must NOT be recommended
        // acc-3: unauthenticated, 100% -> must NOT be recommended
        // acc-4: authenticated, Pro, 80% remaining -> BEST fallback
        val a1 = repo.createProfile("Primary").getOrThrow()
        val a2 = repo.createProfile("Exhausted").getOrThrow()
        val a3 = repo.createProfile("NotLoggedIn").getOrThrow()
        val a4 = repo.createProfile("Backup Pro").getOrThrow()

        store.upsertProfile(a1.copy(isAuthenticated = true))
        store.upsertProfile(a2.copy(isAuthenticated = true, quota = UsageQuota(AccountPlanTier.ULTRA, remainingPercent = 0.0)))
        store.upsertProfile(a3.copy(isAuthenticated = false, quota = UsageQuota(AccountPlanTier.ULTRA, remainingPercent = 100.0)))
        store.upsertProfile(a4.copy(isAuthenticated = true, quota = UsageQuota(AccountPlanTier.PRO, remainingPercent = 80.0)))

        coEvery { authBridge.fetchUsageOutput(match { it.id == a1.id }) } returns
            Result.success("Plan: Free\n5% remaining (95 / 100 requests)")

        val quota = repo.refreshUsage(a1.id).getOrThrow()
        assertEquals(AccountPlanTier.FREE, quota.planTier)
        assertTrue(quota.isNearLimit)

        val fallback = repo.suggestFallbackProfile(a1.id)
        assertNotNull(fallback)
        assertEquals(a4.id, fallback!!.id)

        // When no authenticated non-exhausted alternative exists, returns null
        store.upsertProfile(a4.copy(isAuthenticated = true, quota = UsageQuota(AccountPlanTier.PRO, remainingPercent = 0.0)))
        assertNull(repo.suggestFallbackProfile(a1.id))
        advanceUntilIdle()
    }
}
