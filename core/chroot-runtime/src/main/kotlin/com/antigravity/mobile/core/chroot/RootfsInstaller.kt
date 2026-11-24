package com.antigravity.mobile.core.chroot

import com.antigravity.mobile.contracts.RootExecutor
import com.antigravity.mobile.contracts.RootfsSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject

/**
 * Handles acquiring, verifying (SHA-256), and extracting an ARM64 glibc rootfs tarball
 * into the target chroot root directory.
 */
interface RootfsInstaller {
    suspend fun isInstalled(rootfsPath: String): Boolean

    suspend fun install(
        source: RootfsSource,
        rootfsPath: String,
        onProgress: (stage: String, percent: Int?) -> Unit = { _, _ -> },
    ): Result<Unit>
}

class DefaultRootfsInstaller @Inject constructor(
    private val rootExecutor: RootExecutor,
    private val assetStreamProvider: AssetStreamProvider,
    private val downloader: RootfsDownloader,
    @ChrootStagingDir private val stagingDir: File,
    @ChrootIoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : RootfsInstaller {

    override suspend fun isInstalled(rootfsPath: String): Boolean = withContext(ioDispatcher) {
        val cleanRootfs = PosixShellQuoting.validateAbsolutePath(rootfsPath, "rootfsPath")
        val qRootfs = PosixShellQuoting.quote(cleanRootfs)
        val check = rootExecutor.exec(
            command = "[ -d $qRootfs ] && ([ -x $qRootfs/bin/sh ] || [ -x $qRootfs/usr/bin/env ])",
            timeoutMs = 10_000L,
        )
        check.isSuccess
    }

    override suspend fun install(
        source: RootfsSource,
        rootfsPath: String,
        onProgress: (stage: String, percent: Int?) -> Unit,
    ): Result<Unit> = withContext(ioDispatcher) {
        val cleanRootfs = try {
            PosixShellQuoting.validateAbsolutePath(rootfsPath, "rootfsPath")
        } catch (e: IllegalArgumentException) {
            return@withContext Result.failure(e)
        }

        var tempArchive: File? = null
        try {
            val archivePathToExtract: String = when (source) {
                is RootfsSource.BundledAsset -> {
                    onProgress("Copying bundled ARM64 rootfs asset", 5)
                    val assetStream = assetStreamProvider.openAsset(source.assetPath)
                        ?: return@withContext Result.failure(BundledAssetNotFoundException(source.assetPath))
                    stagingDir.mkdirs()
                    val staged = File(stagingDir, "bundled-arm64-rootfs.tar.gz")
                    tempArchive = staged
                    assetStream.use { input ->
                        FileOutputStream(staged).use { output ->
                            input.copyTo(output, bufferSize = 64 * 1024)
                            output.flush()
                        }
                    }
                    onProgress("Bundled rootfs staged", 35)
                    staged.absolutePath
                }

                is RootfsSource.RemoteDownload -> {
                    onProgress("Downloading ARM64 rootfs archive", 5)
                    stagingDir.mkdirs()
                    val staged = File(stagingDir, "remote-arm64-rootfs.tar.gz")
                    tempArchive = staged
                    downloader.download(source.url, staged) { dlPercent ->
                        val scaled = dlPercent?.let { (it * 50) / 100 }
                        onProgress("Downloading ARM64 rootfs archive", scaled)
                    }.getOrThrow()

                    if (!source.sha256.isNullOrBlank()) {
                        onProgress("Verifying SHA-256 checksum", 55)
                        val actual = Sha256Verifier.computeHex(staged, ioDispatcher)
                        if (!Sha256Verifier.constantTimeEqualsHex(actual, source.sha256!!)) {
                            throw RootfsIntegrityException(
                                "SHA-256 mismatch for remote rootfs: expected=${source.sha256}, actual=$actual",
                            )
                        }
                    }
                    staged.absolutePath
                }

                is RootfsSource.LocalFile -> {
                    val cleanLocal = PosixShellQuoting.validateAbsolutePath(source.absolutePath, "localFilePath")
                    onProgress("Validating local rootfs archive", 10)
                    val localFile = File(cleanLocal)
                    if (localFile.exists() && localFile.canRead()) {
                        if (!source.sha256.isNullOrBlank()) {
                            onProgress("Verifying SHA-256 checksum", 25)
                            val actual = Sha256Verifier.computeHex(localFile, ioDispatcher)
                            if (!Sha256Verifier.constantTimeEqualsHex(actual, source.sha256!!)) {
                                throw RootfsIntegrityException(
                                    "SHA-256 mismatch for local rootfs: expected=${source.sha256}, actual=$actual",
                                )
                            }
                        }
                    } else {
                        val qLocal = PosixShellQuoting.quote(cleanLocal)
                        val existsRes = rootExecutor.exec("[ -f $qLocal ]", timeoutMs = 10_000L)
                        if (!existsRes.isSuccess) {
                            throw IOException("Local rootfs file not found: $cleanLocal")
                        }
                        if (!source.sha256.isNullOrBlank()) {
                            onProgress("Verifying SHA-256 checksum", 25)
                            val hashRes = rootExecutor.exec("sha256sum $qLocal", timeoutMs = 60_000L)
                            if (!hashRes.isSuccess) {
                                throw IOException("Failed to compute SHA-256 for $cleanLocal: ${hashRes.stderr}")
                            }
                            val actual = hashRes.stdout.trim().split(Regex("\\s+")).firstOrNull().orEmpty()
                            if (!Sha256Verifier.constantTimeEqualsHex(actual, source.sha256!!)) {
                                throw RootfsIntegrityException(
                                    "SHA-256 mismatch for local rootfs: expected=${source.sha256}, actual=$actual",
                                )
                            }
                        }
                    }
                    cleanLocal
                }
            }

            onProgress("Extracting ARM64 glibc rootfs", 65)
            extractArchive(archivePathToExtract, cleanRootfs)

            onProgress("Initializing rootfs directories", 90)
            initializeDirectories(cleanRootfs)

            if (!isInstalled(cleanRootfs)) {
                throw IOException(
                    "Rootfs extraction completed but neither /bin/sh nor /usr/bin/env exists under $cleanRootfs",
                )
            }

            onProgress("Rootfs ready", 100)
            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        } finally {
            tempArchive?.delete()
        }
    }

    private suspend fun extractArchive(archivePath: String, rootfsPath: String) {
        val qArchive = PosixShellQuoting.quote(archivePath)
        val qRootfs = PosixShellQuoting.quote(rootfsPath)
        val cmd = "mkdir -p $qRootfs && chmod 755 $qRootfs && tar -xzf $qArchive -C $qRootfs"
        val result = rootExecutor.exec(command = cmd, timeoutMs = 300_000L)
        if (!result.isSuccess) {
            throw IOException(
                "Failed to extract rootfs archive '$archivePath' into '$rootfsPath' " +
                    "(exit=${result.exitCode}): ${result.stderr.ifBlank { result.stdout }}",
            )
        }
    }

    private suspend fun initializeDirectories(rootfsPath: String) {
        val qRootfs = PosixShellQuoting.quote(rootfsPath)
        val subdirs = listOf(
            "proc",
            "sys",
            "dev",
            "dev/pts",
            "mnt/sdcard",
            "mnt/android_root",
            "profiles",
            "etc",
            "tmp",
            "usr/local/bin",
        ).joinToString(" ") { PosixShellQuoting.quote("$rootfsPath/$it") }

        val cmd = "mkdir -p $subdirs && chmod 1777 $qRootfs/tmp"
        val res = rootExecutor.exec(command = cmd, timeoutMs = 15_000L)
        if (!res.isSuccess) {
            throw IOException("Failed to create chroot mountpoint directories: ${res.stderr}")
        }
    }
}
