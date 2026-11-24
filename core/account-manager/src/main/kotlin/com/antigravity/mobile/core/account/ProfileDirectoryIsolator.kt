package com.antigravity.mobile.core.account

import com.antigravity.mobile.contracts.ChrootConstants
import com.antigravity.mobile.contracts.ProfilePaths
import com.antigravity.mobile.contracts.RootExecutor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

data class DesktopImportResult(
    val detectedEmail: String? = null,
    val importedFilesCount: Int = 0,
)

/**
 * Manages isolated per-profile filesystem directories under `/data/local/ag_profiles/<profileId>/`
 * and supports importing desktop `~/.gemini` / `antigravity-cli` credential profiles.
 */
interface ProfileDirectoryIsolator {
    suspend fun ensureDirectories(paths: ProfilePaths): Result<Unit>

    suspend fun deleteDirectories(paths: ProfilePaths): Result<Unit>

    suspend fun importDesktopProfile(
        paths: ProfilePaths,
        sourcePath: String,
    ): Result<DesktopImportResult>
}

class DefaultProfileDirectoryIsolator(
    private val rootExecutor: RootExecutor,
    private val hostProfilesBaseDir: String = ChrootConstants.DEFAULT_PROFILES_HOST_PATH,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ProfileDirectoryIsolator {

    override suspend fun ensureDirectories(paths: ProfilePaths): Result<Unit> = withContext(ioDispatcher) {
        try {
            validateProfileUnderBase(paths)
            val dirs = listOf(
                paths.hostRootDir,
                "${paths.hostRootDir}/home",
                paths.hostGeminiDir,
                paths.hostConfigDir,
                "${paths.hostRootDir}/data",
                "${paths.hostRootDir}/data/conversations",
                "${paths.hostRootDir}/cache",
            )
            val quotedDirs = dirs.joinToString(" ") { AccountShellQuoting.quote(it) }
            val qRoot = AccountShellQuoting.quote(paths.hostRootDir)
            val cmd = "mkdir -p $quotedDirs && chmod 700 $qRoot"
            val res = rootExecutor.exec(cmd, timeoutMs = 15_000L)
            if (!res.isSuccess) {
                return@withContext Result.failure(
                    IOException("Failed to initialize profile directories for ${paths.profileId}: ${res.stderr}"),
                )
            }
            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    override suspend fun deleteDirectories(paths: ProfilePaths): Result<Unit> = withContext(ioDispatcher) {
        try {
            validateProfileUnderBase(paths)
            val qRoot = AccountShellQuoting.quote(paths.hostRootDir)
            val res = rootExecutor.exec("rm -rf $qRoot", timeoutMs = 15_000L)
            if (!res.isSuccess) {
                return@withContext Result.failure(
                    IOException("Failed to remove profile directory ${paths.hostRootDir}: ${res.stderr}"),
                )
            }
            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    override suspend fun importDesktopProfile(
        paths: ProfilePaths,
        sourcePath: String,
    ): Result<DesktopImportResult> = withContext(ioDispatcher) {
        try {
            validateProfileUnderBase(paths)
            val cleanSource = AccountShellQuoting.validateAbsolutePath(sourcePath, "sourcePath")
            ensureDirectories(paths).getOrThrow()

            val qSource = AccountShellQuoting.quote(cleanSource)
            val qTargetGemini = AccountShellQuoting.quote(paths.hostGeminiDir)
            val qTargetHome = AccountShellQuoting.quote("${paths.hostRootDir}/home")

            val lower = cleanSource.lowercase()
            val copyCmd = when {
                lower.endsWith(".tar.gz") || lower.endsWith(".tgz") -> {
                    "[ -f $qSource ] && tar -xzf $qSource -C $qTargetGemini && " +
                        "if [ -d $qTargetGemini/.gemini ]; then cp -a $qTargetGemini/.gemini/. $qTargetGemini/ && rm -rf $qTargetGemini/.gemini; fi"
                }
                lower.endsWith(".zip") -> {
                    "[ -f $qSource ] && unzip -qo $qSource -d $qTargetGemini && " +
                        "if [ -d $qTargetGemini/.gemini ]; then cp -a $qTargetGemini/.gemini/. $qTargetGemini/ && rm -rf $qTargetGemini/.gemini; fi"
                }
                else -> {
                    "[ -d $qSource ] && cp -a $qSource/. $qTargetGemini/"
                }
            }

            val importRes = rootExecutor.exec(
                command = "$copyCmd && chmod -R u+rwX,go-rwx $qTargetHome",
                timeoutMs = 60_000L,
            )
            if (!importRes.isSuccess) {
                return@withContext Result.failure(
                    IOException(
                        "Failed to import desktop profile from '$cleanSource': " +
                            importRes.stderr.ifBlank { importRes.stdout },
                    ),
                )
            }

            val countRes = rootExecutor.exec(
                command = "find $qTargetGemini -type f 2>/dev/null | wc -l",
                timeoutMs = 10_000L,
            )
            val fileCount = countRes.stdout.trim().toIntOrNull() ?: 0
            if (fileCount <= 0) {
                return@withContext Result.failure(
                    IOException("Imported desktop profile from '$cleanSource' contained no files"),
                )
            }

            // Try to detect email from google_accounts.json if present (never log secrets/tokens).
            val email = detectEmailFromProfileDir(paths.hostGeminiDir)
            Result.success(DesktopImportResult(detectedEmail = email, importedFilesCount = fileCount))
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    private suspend fun detectEmailFromProfileDir(hostGeminiDir: String): String? {
        val qAccountsJson = AccountShellQuoting.quote("$hostGeminiDir/google_accounts.json")
        val res = rootExecutor.exec(
            command = "[ -f $qAccountsJson ] && cat $qAccountsJson",
            timeoutMs = 5_000L,
        )
        if (!res.isSuccess || res.stdout.isBlank()) return null
        return EMAIL_REGEX.find(res.stdout)?.value
    }

    private fun validateProfileUnderBase(paths: ProfilePaths) {
        val normalizedBase = hostProfilesBaseDir.trimEnd('/')
        val expectedPrefix = "$normalizedBase/"
        require(paths.hostRootDir.startsWith(expectedPrefix) && paths.hostRootDir.length > expectedPrefix.length) {
            "Profile hostRootDir '${paths.hostRootDir}' must be strictly inside '$normalizedBase'"
        }
    }

    companion object {
        private val EMAIL_REGEX = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    }
}
