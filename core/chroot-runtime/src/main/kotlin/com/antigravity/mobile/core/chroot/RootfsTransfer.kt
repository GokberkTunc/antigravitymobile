package com.antigravity.mobile.core.chroot

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject

/**
 * Provides streams for APK bundled assets so rootfs extraction can be unit-tested cleanly on JVM.
 */
interface AssetStreamProvider {
    fun openAsset(assetPath: String): InputStream?
}

class AndroidAssetStreamProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : AssetStreamProvider {
    override fun openAsset(assetPath: String): InputStream? = try {
        context.assets.open(assetPath)
    } catch (_: IOException) {
        null
    }
}

/**
 * Thrown when [com.antigravity.mobile.contracts.RootfsSource.BundledAsset] is requested
 * but the APK does not contain the bundled rootfs archive.
 */
class BundledAssetNotFoundException(
    val assetPath: String,
) : IOException("Bundled rootfs asset not found in APK: '$assetPath'. Use RemoteDownload or LocalFile fallback.")

/**
 * Thrown when a rootfs archive fails SHA-256 integrity verification.
 */
class RootfsIntegrityException(
    message: String,
) : SecurityException(message)

/**
 * Streaming SHA-256 checksum utility executed on [Dispatchers.IO].
 */
object Sha256Verifier {
    private const val BUFFER_SIZE = 64 * 1024

    suspend fun computeHex(
        file: File,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): String = withContext(ioDispatcher) {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun constantTimeEqualsHex(actual: String, expected: String): Boolean {
        val normActual = actual.trim().lowercase()
        val normExpected = expected.trim().lowercase()
        if (normActual.length != normExpected.length || normActual.isEmpty()) return false
        var diff = 0
        for (i in normActual.indices) {
            diff = diff or (normActual[i].code xor normExpected[i].code)
        }
        return diff == 0
    }
}

/**
 * Downloads remote ARM64 glibc rootfs archives with progress reporting.
 */
interface RootfsDownloader {
    suspend fun download(
        url: String,
        destination: File,
        onProgress: (percent: Int?) -> Unit = {},
    ): Result<File>
}

/**
 * Downloads remote rootfs archives using Android's native [HttpURLConnection] / Conscrypt TLS
 * stack (hostname-aware `X509ExtendedTrustManager`), avoiding Ktor CIO's 2-arg `checkServerTrusted`
 * incompatibility with `<domain-config>` entries in Android `network_security_config.xml`.
 */
class KtorRootfsDownloader @Inject constructor(
    @ChrootIoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : RootfsDownloader {

    override suspend fun download(
        url: String,
        destination: File,
        onProgress: (percent: Int?) -> Unit,
    ): Result<File> = withContext(ioDispatcher) {
        try {
            destination.parentFile?.mkdirs()
            var currentUrl = url
            var redirects = 0
            while (true) {
                validateDownloadUrl(currentUrl)
                val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    instanceFollowRedirects = false
                    connectTimeout = 30_000
                    readTimeout = 60_000
                    setRequestProperty("Accept-Encoding", "identity")
                }
                try {
                    val statusCode = connection.responseCode
                    if (statusCode in 301..303 || statusCode == 307 || statusCode == 308) {
                        val location = connection.getHeaderField("Location")
                            ?: throw IOException("Redirect (HTTP $statusCode) missing Location header")
                        if (++redirects > MAX_REDIRECTS) {
                            throw IOException("Too many redirects while downloading rootfs")
                        }
                        currentUrl = URI(currentUrl).resolve(location).toString()
                        continue
                    }
                    if (statusCode !in 200..299) {
                        throw IOException("Rootfs download failed with HTTP $statusCode")
                    }
                    val totalBytes = connection.contentLengthLong
                    var downloaded = 0L
                    val buffer = ByteArray(64 * 1024)
                    connection.inputStream.use { input ->
                        FileOutputStream(destination).use { output ->
                            while (true) {
                                val bytesRead = input.read(buffer)
                                if (bytesRead <= 0) break
                                output.write(buffer, 0, bytesRead)
                                downloaded += bytesRead
                                if (totalBytes > 0L) {
                                    val percent = ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 100)
                                    onProgress(percent)
                                } else {
                                    onProgress(null)
                                }
                            }
                            output.flush()
                        }
                    }
                    break
                } finally {
                    connection.disconnect()
                }
            }
            Result.success(destination)
        } catch (ce: CancellationException) {
            destination.delete()
            throw ce
        } catch (t: Throwable) {
            destination.delete()
            Result.failure(t)
        }
    }

    companion object {
        private const val MAX_REDIRECTS = 5

        fun validateDownloadUrl(url: String) {
            val uri = try {
                URI(url)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid rootfs download URL: '$url'", e)
            }
            val scheme = uri.scheme?.lowercase()
            val host = uri.host?.lowercase()
            val isLoopback = host == "127.0.0.1" || host == "localhost" || host == "::1"
            require(scheme == "https" || (scheme == "http" && isLoopback)) {
                "Rootfs download URL must use HTTPS (got scheme='$scheme', host='$host')"
            }
        }
    }
}
