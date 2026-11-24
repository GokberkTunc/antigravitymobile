package com.antigravity.mobile.core.root

import com.antigravity.mobile.contracts.RootExecutor
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import com.topjohnwu.superuser.io.SuFileOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class RootFileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long = 0L,
    val lastModifiedEpochMs: Long = 0L,
)

@Serializable
data class RootFileReadResult(
    val path: String,
    val content: String,
    val offset: Long,
    val bytesRead: Int,
    val truncated: Boolean,
)

@Serializable
data class RootFileWriteResult(
    val path: String,
    val bytesWritten: Int,
    val append: Boolean,
)

@Serializable
data class RootFileListResult(
    val path: String,
    val entries: List<RootFileEntry>,
    val totalCount: Int,
    val truncated: Boolean,
)

/**
 * Privileged filesystem repository backing MCP `fs_read`, `fs_write`, and `fs_list`.
 * Uses `libsu`'s `SuFile` when available on device with root-shell fallback, always off Main Thread.
 */
interface SuFileRepository {
    suspend fun readFile(
        path: String,
        offset: Long = 0L,
        limit: Int = DEFAULT_READ_LIMIT,
    ): Result<RootFileReadResult>

    suspend fun writeFile(
        path: String,
        content: String,
        append: Boolean = false,
    ): Result<RootFileWriteResult>

    suspend fun listDirectory(
        path: String,
        offset: Int = 0,
        limit: Int = DEFAULT_LIST_LIMIT,
    ): Result<RootFileListResult>

    companion object {
        const val DEFAULT_READ_LIMIT = 65_536
        const val DEFAULT_LIST_LIMIT = 200
    }
}

/**
 * Low-level `SuFile` I/O adapter so production uses `libsu-io` directly while unit tests
 * can exercise both `SuFile` and `RootExecutor` fallback paths deterministically.
 */
interface SuFileGateway {
    fun isAvailable(): Boolean
    fun readBytes(path: String, offset: Long, limit: Int): Pair<ByteArray, Boolean>
    fun writeBytes(path: String, bytes: ByteArray, append: Boolean)
    fun listEntries(path: String): List<RootFileEntry>
}

@Singleton
class LibsuSuFileGateway @Inject constructor() : SuFileGateway {
    override fun isAvailable(): Boolean = try {
        SuFile.open("/").exists()
    } catch (_: Throwable) {
        false
    }

    override fun readBytes(path: String, offset: Long, limit: Int): Pair<ByteArray, Boolean> {
        val suFile = SuFile.open(path)
        require(suFile.exists()) { "Path does not exist: $path" }
        require(!suFile.isDirectory) { "Path is a directory: $path" }
        SuFileInputStream.open(suFile).use { input ->
            var remainingSkip = offset.coerceAtLeast(0L)
            while (remainingSkip > 0L) {
                val skipped = input.skip(remainingSkip)
                if (skipped <= 0L) break
                remainingSkip -= skipped
            }
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(minOf(8192, limit.coerceAtLeast(1)))
            var remaining = limit
            while (remaining > 0) {
                val read = input.read(chunk, 0, minOf(chunk.size, remaining))
                if (read <= 0) break
                buffer.write(chunk, 0, read)
                remaining -= read
            }
            val hasMore = input.read() != -1
            return buffer.toByteArray() to hasMore
        }
    }

    override fun writeBytes(path: String, bytes: ByteArray, append: Boolean) {
        val suFile = SuFile.open(path)
        suFile.parentFile?.let { parent ->
            if (!parent.exists()) {
                parent.mkdirs()
            }
        }
        SuFileOutputStream.open(suFile, append).use { output ->
            output.write(bytes)
            output.flush()
        }
    }

    override fun listEntries(path: String): List<RootFileEntry> {
        val dir = SuFile.open(path)
        require(dir.exists()) { "Directory does not exist: $path" }
        require(dir.isDirectory) { "Path is not a directory: $path" }
        val children = dir.listFiles().orEmpty()
        return children
            .sortedBy { it.name }
            .map { child ->
                RootFileEntry(
                    name = child.name,
                    path = child.absolutePath,
                    isDirectory = child.isDirectory,
                    sizeBytes = if (child.isDirectory) 0L else child.length(),
                    lastModifiedEpochMs = child.lastModified(),
                )
            }
    }
}

@Singleton
class LibsuFileRepository(
    private val rootExecutor: RootExecutor,
    private val suFileGateway: SuFileGateway,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SuFileRepository {

    @Inject
    constructor(
        rootExecutor: RootExecutor,
        suFileGateway: SuFileGateway,
    ) : this(
        rootExecutor = rootExecutor,
        suFileGateway = suFileGateway,
        ioDispatcher = Dispatchers.IO,
    )

    override suspend fun readFile(
        path: String,
        offset: Long,
        limit: Int,
    ): Result<RootFileReadResult> = withContext(ioDispatcher) {
        val safePath = path.trim()
        if (safePath.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("path must not be blank"))
        }
        val safeOffset = offset.coerceAtLeast(0L)
        val safeLimit = limit.coerceIn(1, MAX_READ_LIMIT)

        try {
            if (suFileGateway.isAvailable()) {
                val (bytes, truncated) = suFileGateway.readBytes(safePath, safeOffset, safeLimit)
                return@withContext Result.success(
                    RootFileReadResult(
                        path = safePath,
                        content = bytes.toString(Charsets.UTF_8),
                        offset = safeOffset,
                        bytesRead = bytes.size,
                        truncated = truncated,
                    ),
                )
            }

            // Shell fallback using safe POSIX quoting + base64 to preserve binary/newline fidelity.
            val readPlusOne = safeLimit + 1
            val quoted = ShellEscaping.quote(safePath)
            val cmd = "dd if=$quoted bs=1 skip=$safeOffset count=$readPlusOne 2>/dev/null | base64 | tr -d '\\n'"
            val res = rootExecutor.exec(cmd)
            if (!res.isSuccess) {
                return@withContext Result.failure(
                    IllegalStateException("fs_read failed for $safePath: ${res.stderr.ifBlank { "exit=${res.exitCode}" }}"),
                )
            }
            val decoded = if (res.stdout.isBlank()) {
                ByteArray(0)
            } else {
                Base64.getDecoder().decode(res.stdout.trim())
            }
            val truncated = decoded.size > safeLimit
            val slice = if (truncated) decoded.copyOf(safeLimit) else decoded
            Result.success(
                RootFileReadResult(
                    path = safePath,
                    content = slice.toString(Charsets.UTF_8),
                    offset = safeOffset,
                    bytesRead = slice.size,
                    truncated = truncated,
                ),
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    override suspend fun writeFile(
        path: String,
        content: String,
        append: Boolean,
    ): Result<RootFileWriteResult> = withContext(ioDispatcher) {
        val safePath = path.trim()
        if (safePath.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("path must not be blank"))
        }
        val bytes = content.toByteArray(Charsets.UTF_8)
        try {
            if (suFileGateway.isAvailable()) {
                suFileGateway.writeBytes(safePath, bytes, append)
                return@withContext Result.success(
                    RootFileWriteResult(
                        path = safePath,
                        bytesWritten = bytes.size,
                        append = append,
                    ),
                )
            }

            val encoded = Base64.getEncoder().encodeToString(bytes)
            val quotedPath = ShellEscaping.quote(safePath)
            val redirect = if (append) ">>" else ">"
            val cmd = "mkdir -p \"\$(dirname $quotedPath)\" && printf %s ${ShellEscaping.quote(encoded)} | base64 -d $redirect $quotedPath"
            val res = rootExecutor.exec(cmd)
            if (!res.isSuccess) {
                return@withContext Result.failure(
                    IllegalStateException("fs_write failed for $safePath: ${res.stderr.ifBlank { "exit=${res.exitCode}" }}"),
                )
            }
            Result.success(
                RootFileWriteResult(
                    path = safePath,
                    bytesWritten = bytes.size,
                    append = append,
                ),
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    override suspend fun listDirectory(
        path: String,
        offset: Int,
        limit: Int,
    ): Result<RootFileListResult> = withContext(ioDispatcher) {
        val safePath = path.trim()
        if (safePath.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("path must not be blank"))
        }
        val safeOffset = offset.coerceAtLeast(0)
        val safeLimit = limit.coerceIn(1, MAX_LIST_LIMIT)

        try {
            val allEntries = if (suFileGateway.isAvailable()) {
                suFileGateway.listEntries(safePath)
            } else {
                listViaRootShell(safePath)
            }
            val paged = allEntries.drop(safeOffset).take(safeLimit)
            Result.success(
                RootFileListResult(
                    path = safePath,
                    entries = paged,
                    totalCount = allEntries.size,
                    truncated = safeOffset + paged.size < allEntries.size,
                ),
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    private suspend fun listViaRootShell(path: String): List<RootFileEntry> {
        val quoted = ShellEscaping.quote(path)
        val cmd = "ls -1Ap $quoted"
        val res = rootExecutor.exec(cmd)
        if (!res.isSuccess) {
            throw IllegalStateException(
                "fs_list failed for $path: ${res.stderr.ifBlank { "exit=${res.exitCode}" }}",
            )
        }
        val normalizedBase = path.trimEnd('/').ifEmpty { "/" }
        return res.stdout
            .lineSequence()
            .map { it.trimEnd('\r') }
            .filter { it.isNotEmpty() }
            .map { raw ->
                val isDir = raw.endsWith("/")
                val name = if (isDir) raw.dropLast(1) else raw
                val fullPath = if (normalizedBase == "/") "/$name" else "$normalizedBase/$name"
                RootFileEntry(
                    name = name,
                    path = fullPath,
                    isDirectory = isDir,
                )
            }
            .sortedBy { it.name }
            .toList()
    }

    companion object {
        private const val MAX_READ_LIMIT = 1_048_576
        private const val MAX_LIST_LIMIT = 2_000
    }
}
