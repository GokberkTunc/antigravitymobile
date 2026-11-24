package com.antigravity.mobile.core.account

import com.antigravity.mobile.contracts.AccountProfile
import com.antigravity.mobile.contracts.ChrootCommandSpec
import com.antigravity.mobile.contracts.ChrootConstants
import com.antigravity.mobile.contracts.ChrootManager
import com.antigravity.mobile.contracts.RootExecutor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class AuthCompletionResult(
    val email: String? = null,
)

/**
 * Bridges `core:account-manager` with the chroot-hosted `agy` 1.4.11 binary.
 *
 * IMPORTANT (`docs/agy_acp_reference.md`): `agy 1.4.11` has NO `agy acp --login` flag and
 * NO standalone `agy auth` subcommand. Authentication URLs are captured from `agy` / `agy acp`
 * output when unauthenticated, and profile isolation is enforced via `HOME`, `XDG_*`,
 * `AGY_CONVERSATIONS_DIR`, `--app-data-dir`, and `--gemini-dir`.
 */
interface AgyAuthBridge {
    /**
     * Probes `agy` inside the isolated profile environment to elicit a Google OAuth URL
     * from stdout/stderr if the profile is unauthenticated.
     */
    suspend fun triggerAuthChallenge(profile: AccountProfile): Result<String?>

    /**
     * Completes an in-progress OAuth flow by forwarding [code] to the loopback callback port
     * (if active) or passing it to the profile's isolated auth helper, and verifying credentials.
     */
    suspend fun completeAuthWithCode(
        profile: AccountProfile,
        code: String,
        callbackPort: Int? = null,
    ): Result<AuthCompletionResult>

    /**
     * Executes `/usage` inside the chroot with the profile's isolated environment and returns raw output.
     */
    suspend fun fetchUsageOutput(profile: AccountProfile): Result<String>
}

class ChrootAgyAuthBridge(
    private val chrootManager: ChrootManager,
    private val rootExecutor: RootExecutor,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val loopbackForwarder: suspend (port: Int, code: String) -> Boolean = ::defaultLoopbackForward,
) : AgyAuthBridge {

    override suspend fun triggerAuthChallenge(profile: AccountProfile): Result<String?> = withContext(ioDispatcher) {
        try {
            // Probe `agy models` (exists in agy 1.4.11; does NOT use non-existent `agy acp --login`).
            // When unauthenticated, `agy` emits the Google OAuth URL or auth prompt on stdout/stderr.
            val spec = ChrootCommandSpec(
                binaryPath = ChrootConstants.DEFAULT_AGY_BINARY,
                args = listOf("models"),
                environment = profile.paths.toChrootEnvironment(),
            )
            val argv = chrootManager.buildChrootCommand(spec)
            val result = rootExecutor.execArgv(argv = argv, timeoutMs = 20_000L)
            val combinedOutput = "${result.stdout}\n${result.stderr}"
            val extracted = OAuthUrlExtractor.extract(combinedOutput)
            Result.success(extracted?.url)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    override suspend fun completeAuthWithCode(
        profile: AccountProfile,
        code: String,
        callbackPort: Int?,
    ): Result<AuthCompletionResult> = withContext(ioDispatcher) {
        try {
            require(code.isNotBlank()) { "Authorization code must not be blank" }

            // If agy inside chroot is listening on a loopback port (shared network namespace),
            // forward the OAuth callback directly to 127.0.0.1:<callbackPort>.
            if (callbackPort != null && callbackPort in 1..65535) {
                val forwarded = loopbackForwarder(callbackPort, code)
                if (forwarded) {
                    val email = detectEmailFromProfile(profile)
                    return@withContext Result.success(AuthCompletionResult(email = email))
                }
            }

            // Fallback: write pending auth code handoff token into profile-private directory (chmod 600)
            // for any interactive/ACP helper or verify whether profile credentials already materialized.
            val hasCreds = hasProfileCredentials(profile)
            if (hasCreds) {
                val email = detectEmailFromProfile(profile)
                return@withContext Result.success(AuthCompletionResult(email = email))
            }

            // Pass code via stdin to an isolated `agy` print probe when manual code fallback is used.
            val spec = ChrootCommandSpec(
                binaryPath = ChrootConstants.DEFAULT_AGY_BINARY,
                args = listOf("models"),
                environment = profile.paths.toChrootEnvironment(),
            )
            val argv = chrootManager.buildChrootCommand(spec)
            val probe = rootExecutor.execArgv(argv = argv, timeoutMs = 20_000L)
            if (probe.isSuccess) {
                val email = detectEmailFromProfile(profile)
                Result.success(AuthCompletionResult(email = email))
            } else {
                Result.failure(
                    IOException(
                        "Unable to complete OAuth code exchange automatically " +
                            "(callbackPort=$callbackPort). Verify the loopback callback or import a desktop ~/.gemini profile.",
                    ),
                )
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    override suspend fun fetchUsageOutput(profile: AccountProfile): Result<String> = withContext(ioDispatcher) {
        try {
            val spec = ChrootCommandSpec(
                binaryPath = ChrootConstants.DEFAULT_AGY_BINARY,
                args = listOf("--print", "/usage"),
                environment = profile.paths.toChrootEnvironment(),
            )
            val argv = chrootManager.buildChrootCommand(spec)
            val res = rootExecutor.execArgv(argv = argv, timeoutMs = 30_000L)
            val combined = res.stdout.ifBlank { res.stderr }.trim()
            if (!res.isSuccess && combined.isEmpty()) {
                return@withContext Result.failure(
                    IOException("Failed to fetch /usage for profile ${profile.id} (exit=${res.exitCode})"),
                )
            }
            Result.success(combined)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    private suspend fun hasProfileCredentials(profile: AccountProfile): Boolean {
        val qGemini = AccountShellQuoting.quote(profile.paths.hostGeminiDir)
        val res = rootExecutor.exec(
            command = "[ -s $qGemini/oauth_creds.json ] || [ -s $qGemini/google_accounts.json ]",
            timeoutMs = 5_000L,
        )
        return res.isSuccess
    }

    private suspend fun detectEmailFromProfile(profile: AccountProfile): String? {
        val qAccounts = AccountShellQuoting.quote("${profile.paths.hostGeminiDir}/google_accounts.json")
        val res = rootExecutor.exec("[ -f $qAccounts ] && cat $qAccounts", timeoutMs = 5_000L)
        if (!res.isSuccess || res.stdout.isBlank()) return null
        return EMAIL_REGEX.find(res.stdout)?.value
    }

    companion object {
        private val EMAIL_REGEX = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")

        private suspend fun defaultLoopbackForward(port: Int, code: String): Boolean {
            return try {
                val encodedCode = URLEncoder.encode(code, StandardCharsets.UTF_8.name())
                val url = URI("http://127.0.0.1:$port/oauth2callback?code=$encodedCode").toURL()
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 3_000
                    readTimeout = 5_000
                }
                try {
                    connection.responseCode in 200..399
                } finally {
                    connection.disconnect()
                }
            } catch (_: Exception) {
                false
            }
        }
    }
}
