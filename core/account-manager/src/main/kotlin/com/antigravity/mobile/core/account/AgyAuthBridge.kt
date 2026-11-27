package com.antigravity.mobile.core.account

import com.antigravity.mobile.contracts.AccountProfile
import com.antigravity.mobile.contracts.ChrootCommandSpec
import com.antigravity.mobile.contracts.ChrootConstants
import com.antigravity.mobile.contracts.ChrootManager
import com.antigravity.mobile.contracts.ChrootState
import com.antigravity.mobile.contracts.RootExecutor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class AuthCompletionResult(
    val email: String? = null,
)

/**
 * Abstraction over a long-lived interactive auth child process (such as `agy acp --login`)
 * that emits a PKCE Google OAuth URL on stdout/stderr and reads the resulting authorization
 * code or redirect URL from stdin.
 */
interface AuthInteractiveProcess {
    val stdoutAndStderr: BufferedReader
    fun writeLine(line: String)
    fun waitForExit(timeoutMs: Long): Int?
    fun destroy()
}

fun interface AuthProcessStarter {
    fun start(argv: List<String>): AuthInteractiveProcess
}

private class ProcessBuilderInteractiveAuthProcess(
    private val process: Process,
) : AuthInteractiveProcess {
    override val stdoutAndStderr: BufferedReader = BufferedReader(
        InputStreamReader(process.inputStream, StandardCharsets.UTF_8),
    )
    private val stdinWriter = BufferedWriter(
        OutputStreamWriter(process.outputStream, StandardCharsets.UTF_8),
    )

    override fun writeLine(line: String) {
        stdinWriter.write(line)
        stdinWriter.newLine()
        stdinWriter.flush()
        // Closing stdin signals EOF after submitting the authorization code so `agy acp --login` completes cleanly.
        runCatching { stdinWriter.close() }
    }

    override fun waitForExit(timeoutMs: Long): Int? {
        val finished = process.waitFor(timeoutMs.coerceAtLeast(1L), TimeUnit.MILLISECONDS)
        return if (finished) process.exitValue() else null
    }

    override fun destroy() {
        runCatching { stdinWriter.close() }
        runCatching { stdoutAndStderr.close() }
        runCatching { process.destroyForcibly() }
    }
}

object ProcessBuilderAuthProcessStarter : AuthProcessStarter {
    override fun start(argv: List<String>): AuthInteractiveProcess {
        require(argv.isNotEmpty()) { "argv must not be empty" }
        val process = ProcessBuilder(argv)
            .redirectErrorStream(true)
            .start()
        return ProcessBuilderInteractiveAuthProcess(process)
    }
}

/**
 * Bridges `core:account-manager` with the chroot-hosted `agy` binary.
 *
 * - Supports `agy 1.5.16+` interactive `agy acp --login`, keeping the process alive while the user
 *   signs in via Chrome Custom Tabs so PKCE `code_verifier`/`state` remain intact until the
 *   authorization code or landing URL is submitted to stdin.
 * - Falls back to `agy models` output probing and `127.0.0.1` loopback callback forwarding for
 *   older `agy 1.4.x` builds.
 */
interface AgyAuthBridge {
    /**
     * Probes `agy` inside the isolated profile environment to elicit a Google OAuth URL
     * from stdout/stderr if the profile is unauthenticated.
     */
    suspend fun triggerAuthChallenge(profile: AccountProfile): Result<String?>

    /**
     * Completes an in-progress OAuth flow by submitting [code] to the active `agy acp --login`
     * process stdin, forwarding it to a loopback callback port, or verifying credentials.
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
    private val processStarter: AuthProcessStarter = ProcessBuilderAuthProcessStarter,
) : AgyAuthBridge {

    private val activeLoginProcesses = ConcurrentHashMap<String, AuthInteractiveProcess>()

    private data class AuthUrlProbeResult(
        val extracted: ExtractedOAuthUrl?,
        val output: String,
    )

    override suspend fun triggerAuthChallenge(profile: AccountProfile): Result<String?> = withContext(ioDispatcher) {
        try {
            if (chrootManager.state.value !is ChrootState.Mounted) {
                val mountResult = chrootManager.mountAll()
                if (mountResult.isFailure) {
                    val cause = mountResult.exceptionOrNull()
                    return@withContext Result.failure(
                        IOException(buildMissingSetupMessage(cause?.message), cause),
                    )
                }
            }

            if (chrootManager.state.value is ChrootState.NotInstalled) {
                return@withContext Result.failure(
                    IOException(buildMissingSetupMessage(null)),
                )
            }

            val rootfsPath = (chrootManager.state.value as? ChrootState.Mounted)?.rootfsPath
                ?: ChrootConstants.DEFAULT_ROOTFS_PATH
            if (!isAgyInstalledInChroot(rootfsPath, ChrootConstants.DEFAULT_AGY_BINARY)) {
                return@withContext Result.failure(
                    IOException(buildMissingSetupMessage("agy CLI bulunamadı: ${ChrootConstants.DEFAULT_AGY_BINARY}")),
                )
            }

            activeLoginProcesses.remove(profile.id)?.destroy()

            val loginSpec = ChrootCommandSpec(
                binaryPath = ChrootConstants.DEFAULT_AGY_BINARY,
                args = listOf("acp", "--login"),
                environment = profile.paths.toChrootEnvironment(),
            )
            val loginArgv = chrootManager.buildChrootCommand(loginSpec)
            val startResult = runCatching { processStarter.start(loginArgv) }
            val loginProcess = startResult.getOrNull()
            var interactiveOutput = ""

            if (loginProcess != null) {
                val probeResult = awaitAuthUrlFromProcess(loginProcess)
                if (probeResult.extracted != null) {
                    activeLoginProcesses[profile.id] = loginProcess
                    return@withContext Result.success(probeResult.extracted.url)
                }
                interactiveOutput = probeResult.output
                loginProcess.destroy()
            }

            // Fallback for older `agy 1.4.x` builds without `agy acp --login`.
            val fallbackSpec = ChrootCommandSpec(
                binaryPath = ChrootConstants.DEFAULT_AGY_BINARY,
                args = listOf("models"),
                environment = profile.paths.toChrootEnvironment(),
            )
            val fallbackArgv = chrootManager.buildChrootCommand(fallbackSpec)
            val result = rootExecutor.execArgv(argv = fallbackArgv, timeoutMs = 20_000L)
            val combinedOutput = "${result.stdout}\n${result.stderr}"
            val extracted = OAuthUrlExtractor.extract(combinedOutput)
            if (extracted != null) {
                return@withContext Result.success(extracted.url)
            }

            val diagnosticRaw = listOf(
                result.stderr,
                result.stdout,
                interactiveOutput,
                startResult.exceptionOrNull()?.message.orEmpty(),
            ).firstOrNull { it.isNotBlank() }.orEmpty()

            if (isMissingChrootOrBinaryError(diagnosticRaw)) {
                return@withContext Result.failure(
                    IOException(buildMissingSetupMessage(diagnosticRaw)),
                )
            }

            if (!result.isSuccess) {
                val detail = sanitizeDiagnostic(diagnosticRaw)
                val message = if (detail.isNotBlank()) {
                    "Google OAuth giriş bağlantısı alınamadı (exit=${result.exitCode}): $detail"
                } else {
                    "Google OAuth giriş bağlantısı alınamadı (exit=${result.exitCode}). Chroot ve internet bağlantınızı kontrol edin."
                }
                return@withContext Result.failure(IOException(message))
            }

            Result.success(null)
        } catch (ce: CancellationException) {
            activeLoginProcesses.remove(profile.id)?.destroy()
            throw ce
        } catch (t: Throwable) {
            activeLoginProcesses.remove(profile.id)?.destroy()
            Result.failure(t)
        }
    }

    private suspend fun isAgyInstalledInChroot(
        rootfsPath: String,
        agyBinary: String,
    ): Boolean {
        val qRootfs = AccountShellQuoting.quote(rootfsPath)
        val qBinary = AccountShellQuoting.quote(agyBinary)
        val qHostBinary = AccountShellQuoting.quote("$rootfsPath$agyBinary")
        val insideCheck = AccountShellQuoting.quote("[ -x $qBinary ]")
        val cmd = "chroot $qRootfs /bin/sh -c $insideCheck 2>/dev/null || [ -x $qHostBinary ] || [ -L $qHostBinary ]"
        val res = rootExecutor.exec(command = cmd, timeoutMs = 5_000L)
        return res.isSuccess
    }

    private suspend fun awaitAuthUrlFromProcess(
        process: AuthInteractiveProcess,
        timeoutMs: Long = DEFAULT_AUTH_URL_TIMEOUT_MS,
    ): AuthUrlProbeResult = withContext(ioDispatcher) {
        val buffer = StringBuilder()
        val readerDeferred = async(ioDispatcher) {
            val reader = process.stdoutAndStderr
            while (true) {
                val line = try {
                    reader.readLine()
                } catch (_: IOException) {
                    null
                } ?: break
                val snapshot = synchronized(buffer) {
                    if (buffer.length < 16_384) {
                        buffer.appendLine(line)
                    }
                    buffer.toString()
                }
                val extracted = OAuthUrlExtractor.extract(snapshot)
                if (extracted != null) {
                    return@async extracted
                }
            }
            null
        }

        try {
            val result = withTimeoutOrNull(timeoutMs) {
                readerDeferred.await()
            }
            if (result == null) {
                process.destroy()
                readerDeferred.cancel()
            }
            val captured = synchronized(buffer) { buffer.toString() }
            AuthUrlProbeResult(extracted = result, output = captured)
        } catch (ce: CancellationException) {
            process.destroy()
            readerDeferred.cancel()
            throw ce
        }
    }

    override suspend fun completeAuthWithCode(
        profile: AccountProfile,
        code: String,
        callbackPort: Int?,
    ): Result<AuthCompletionResult> = withContext(ioDispatcher) {
        val activeProcess = activeLoginProcesses.remove(profile.id)
        if (activeProcess != null) {
            return@withContext completeInteractiveLoginProcess(profile, activeProcess, code)
        }

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

            // Fallback: verify whether profile credentials already materialized.
            if (hasProfileCredentials(profile)) {
                val email = detectEmailFromProfile(profile)
                return@withContext Result.success(AuthCompletionResult(email = email))
            }

            // Probe `agy models` inside the isolated profile to confirm credentials are valid.
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
                        "OAuth kodu otomatik doğrulanamadı (callbackPort=$callbackPort). " +
                            "Lütfen 'Chrome Custom Tabs ile Google Girişi' adımını yeniden başlatın veya masaüstü ~/.gemini profili içe aktarın.",
                    ),
                )
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    private suspend fun completeInteractiveLoginProcess(
        profile: AccountProfile,
        process: AuthInteractiveProcess,
        code: String,
    ): Result<AuthCompletionResult> = withContext(ioDispatcher) {
        try {
            require(code.isNotBlank()) { "Authorization code must not be blank" }
            process.writeLine(code)

            val outputDeferred = async(ioDispatcher) {
                val lines = ArrayList<String>()
                while (true) {
                    val line = try {
                        process.stdoutAndStderr.readLine()
                    } catch (_: IOException) {
                        null
                    } ?: break
                    lines.add(line)
                    if (lines.size > 40) {
                        lines.removeAt(0)
                    }
                }
                lines.joinToString("\n")
            }

            val exitCode = process.waitForExit(25_000L)
            if (exitCode == null) {
                process.destroy()
            }
            val tailOutput = withTimeoutOrNull(2_000L) { outputDeferred.await() }
                .orEmpty()
                .trim()

            if (exitCode == 0 || hasProfileCredentials(profile)) {
                val email = detectEmailFromProfile(profile, fallbackOutput = tailOutput)
                Result.success(AuthCompletionResult(email = email))
            } else {
                val detail = sanitizeDiagnostic(tailOutput).ifBlank { "No diagnostic output" }
                Result.failure(
                    IOException("agy acp --login başarısız (exit=${exitCode ?: "timeout"}): $detail"),
                )
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        } finally {
            process.destroy()
        }
    }

    override suspend fun fetchUsageOutput(profile: AccountProfile): Result<String> = withContext(ioDispatcher) {
        try {
            if (chrootManager.state.value !is ChrootState.Mounted) {
                chrootManager.mountAll().getOrThrow()
            }
            val spec = ChrootCommandSpec(
                binaryPath = ChrootConstants.DEFAULT_AGY_BINARY,
                args = listOf("--print", "/usage"),
                environment = profile.paths.toChrootEnvironment(),
            )
            val argv = chrootManager.buildChrootCommand(spec)
            val res = rootExecutor.execArgv(argv = argv, timeoutMs = 30_000L)
            val stdout = res.stdout
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim('\n', '\r')
            val stderr = res.stderr
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim('\n', '\r')

            if (res.isSuccess) {
                val output = stdout.ifBlank { stderr }
                if (output.isBlank()) {
                    return@withContext Result.failure(
                        IOException("Empty /usage output for profile ${profile.id}"),
                    )
                }
                return@withContext Result.success(output)
            }

            // Preserve structured multi-bucket stdout even if a CLI build exited non-zero.
            if (stdout.isNotBlank() && UsageOutputParser.parseBuckets(stdout).isNotEmpty()) {
                return@withContext Result.success(stdout)
            }

            val detail = stderr.ifBlank { stdout }.ifBlank { "no output" }.take(300)
            Result.failure(
                IOException("Failed to fetch /usage for profile ${profile.id} (exit=${res.exitCode}): $detail"),
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    internal suspend fun hasProfileCredentials(profile: AccountProfile): Boolean {
        val candidateFiles = listOf(
            "${profile.paths.hostGeminiDir}/oauth_creds.json",
            "${profile.paths.hostGeminiDir}/google_accounts.json",
            "${profile.paths.hostGeminiDir}/antigravity-cli/antigravity-oauth-token",
            "${profile.paths.hostRootDir}/home/.gemini/oauth_creds.json",
            "${profile.paths.hostRootDir}/home/.gemini/google_accounts.json",
            "${profile.paths.hostRootDir}/home/.gemini/antigravity-cli/antigravity-oauth-token",
            "${profile.paths.hostConfigDir}/antigravity-cli/oauth_creds.json",
            "${profile.paths.hostConfigDir}/antigravity-cli/google_accounts.json",
            "${profile.paths.hostConfigDir}/antigravity-cli/antigravity-oauth-token",
            "${profile.paths.hostRootDir}/data/antigravity-cli/oauth_creds.json",
            "${profile.paths.hostRootDir}/data/antigravity-cli/google_accounts.json",
            "${profile.paths.hostRootDir}/data/antigravity-cli/antigravity-oauth-token",
        ).distinct()
        val checkExpr = candidateFiles.joinToString(" || ") { path ->
            "[ -s ${AccountShellQuoting.quote(path)} ]"
        }
        val res = rootExecutor.exec(command = checkExpr, timeoutMs = 5_000L)
        return res.isSuccess
    }

    internal suspend fun detectEmailFromProfile(
        profile: AccountProfile,
        fallbackOutput: String? = null,
    ): String? {
        val accountFiles = listOf(
            "${profile.paths.hostGeminiDir}/google_accounts.json",
            "${profile.paths.hostRootDir}/home/.gemini/google_accounts.json",
            "${profile.paths.hostConfigDir}/antigravity-cli/google_accounts.json",
            "${profile.paths.hostRootDir}/data/antigravity-cli/google_accounts.json",
        ).distinct()
        val accountsCatExpr = accountFiles.joinToString(" ") { AccountShellQuoting.quote(it) }
        val accountsRes = rootExecutor.exec("cat $accountsCatExpr 2>/dev/null || true", timeoutMs = 5_000L)
        if (accountsRes.isSuccess && accountsRes.stdout.isNotBlank()) {
            EMAIL_REGEX.find(accountsRes.stdout)?.value?.let { return it }
        }

        if (!fallbackOutput.isNullOrBlank()) {
            EMAIL_REGEX.find(fallbackOutput)?.value?.let { return it }
        }

        // Best-effort email extraction from agy 1.5.17+ OAuth token files; never log or leak token payloads.
        val tokenFiles = listOf(
            "${profile.paths.hostGeminiDir}/antigravity-cli/antigravity-oauth-token",
            "${profile.paths.hostRootDir}/home/.gemini/antigravity-cli/antigravity-oauth-token",
            "${profile.paths.hostConfigDir}/antigravity-cli/antigravity-oauth-token",
            "${profile.paths.hostRootDir}/data/antigravity-cli/antigravity-oauth-token",
        ).distinct()
        val tokenCatExpr = tokenFiles.joinToString(" ") { AccountShellQuoting.quote(it) }
        val tokenRes = rootExecutor.exec("cat $tokenCatExpr 2>/dev/null || true", timeoutMs = 5_000L)
        if (tokenRes.isSuccess && tokenRes.stdout.isNotBlank()) {
            extractEmailFromTokenContent(tokenRes.stdout)?.let { return it }
        }

        val logDirs = listOf(
            "${profile.paths.hostGeminiDir}/antigravity-cli/log",
            "${profile.paths.hostRootDir}/home/.gemini/antigravity-cli/log",
        ).distinct()
        val logTargets = logDirs.flatMap { dir ->
            val qDir = AccountShellQuoting.quote(dir)
            listOf("$qDir/cli.log", "$qDir/acp-login-*.log")
        }.joinToString(" ")
        val logRes = rootExecutor.exec("tail -n 200 $logTargets 2>/dev/null || true", timeoutMs = 5_000L)
        if (logRes.isSuccess && logRes.stdout.isNotBlank()) {
            EMAIL_REGEX.findAll(logRes.stdout).lastOrNull()?.value?.let { return it }
        }

        return null
    }

    companion object {
        private const val DEFAULT_AUTH_URL_TIMEOUT_MS = 20_000L
        private val EMAIL_REGEX = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
        private val JWT_PAYLOAD_REGEX = Regex("""eyJ[A-Za-z0-9_-]+\.([A-Za-z0-9_-]+)\.[A-Za-z0-9_-]+""")
        private val TOKEN_REDACT_REGEX = Regex(
            """(?:Bearer\s+[A-Za-z0-9._~+/=-]+|ya29\.[A-Za-z0-9._-]+|1//[A-Za-z0-9._-]+)""",
            RegexOption.IGNORE_CASE,
        )

        private fun buildMissingSetupMessage(detail: String?): String = buildString {
            append("ARM64 chroot veya agy CLI kurulu değil. ")
            append("Önce Kurulum Sihirbazı'ndan 3. adımı (Rootfs & agy CLI) tamamlayın.")
            val clean = detail?.let(::sanitizeDiagnostic)?.takeIf { it.isNotBlank() }
            if (clean != null) {
                append(" ($clean)")
            }
        }

        private fun isMissingChrootOrBinaryError(output: String): Boolean {
            if (output.isBlank()) return false
            val lower = output.lowercase()
            return lower.contains("no such file or directory") ||
                lower.contains("not found") ||
                lower.contains("cannot change root directory") ||
                lower.contains("can't execute") ||
                lower.contains("failed to run command")
        }

        private fun sanitizeDiagnostic(raw: String): String {
            val redacted = TOKEN_REDACT_REGEX.replace(raw, "[REDACTED]")
            return redacted
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .take(3)
                .joinToString(" | ")
                .take(240)
        }

        private fun extractEmailFromTokenContent(raw: String): String? {
            EMAIL_REGEX.find(raw)?.value?.let { return it }
            for (match in JWT_PAYLOAD_REGEX.findAll(raw)) {
                val payloadB64 = match.groupValues.getOrNull(1) ?: continue
                val decoded = runCatching {
                    String(Base64.getUrlDecoder().decode(payloadB64), StandardCharsets.UTF_8)
                }.getOrNull() ?: continue
                EMAIL_REGEX.find(decoded)?.value?.let { return it }
            }
            return null
        }

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
