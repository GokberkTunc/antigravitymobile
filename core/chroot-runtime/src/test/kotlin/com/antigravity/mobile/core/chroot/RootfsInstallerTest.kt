package com.antigravity.mobile.core.chroot

import com.antigravity.mobile.contracts.RootExecutor
import com.antigravity.mobile.contracts.RootfsSource
import com.antigravity.mobile.contracts.ShellResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class RootfsInstallerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `bundled asset source stages, extracts, initializes dirs, and cleans staging file`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val rootExecutor = mockk<RootExecutor>()
        val downloader = mockk<RootfsDownloader>(relaxed = true)
        val stagingDir = tempFolder.newFolder("staging")
        val assetBytes = "fake-tar-gz-payload".toByteArray()
        val assetProvider = object : AssetStreamProvider {
            override fun openAsset(assetPath: String) =
                if (assetPath == "rootfs/arm64-rootfs.tar.gz") ByteArrayInputStream(assetBytes) else null
        }

        coEvery { rootExecutor.exec(any(), any(), any()) } returns ShellResult(exitCode = 0, stdout = "", stderr = "")

        val installer = DefaultRootfsInstaller(
            rootExecutor = rootExecutor,
            assetStreamProvider = assetProvider,
            downloader = downloader,
            stagingDir = stagingDir,
            ioDispatcher = dispatcher,
        )

        val progressEvents = mutableListOf<Pair<String, Int?>>()
        val result = installer.install(
            source = RootfsSource.BundledAsset(),
            rootfsPath = "/data/local/ag_rootfs",
            onProgress = { stage, pct -> progressEvents += stage to pct },
        )

        assertTrue(result.isSuccess)
        assertTrue(progressEvents.any { it.second == 100 })
        // Staging file must be cleaned up in finally
        assertEquals(0, stagingDir.listFiles()?.size ?: 0)
        coVerify { rootExecutor.exec(match { it.contains("tar -xzf") && it.contains("/data/local/ag_rootfs") }, any(), any()) }
    }

    @Test
    fun `missing bundled asset fails with BundledAssetNotFoundException`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val rootExecutor = mockk<RootExecutor>(relaxed = true)
        val downloader = mockk<RootfsDownloader>(relaxed = true)
        val stagingDir = tempFolder.newFolder("staging_missing")
        val assetProvider = object : AssetStreamProvider {
            override fun openAsset(assetPath: String) = null
        }

        val installer = DefaultRootfsInstaller(
            rootExecutor = rootExecutor,
            assetStreamProvider = assetProvider,
            downloader = downloader,
            stagingDir = stagingDir,
            ioDispatcher = dispatcher,
        )

        val result = installer.install(RootfsSource.BundledAsset(), "/data/local/ag_rootfs")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is BundledAssetNotFoundException)
    }

    @Test
    fun `remote download verifies sha256 and aborts before tar extraction on mismatch`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val rootExecutor = mockk<RootExecutor>(relaxed = true)
        val stagingDir = tempFolder.newFolder("staging_remote")
        val assetProvider = object : AssetStreamProvider {
            override fun openAsset(assetPath: String) = null
        }
        val payloadBytes = "corrupted-rootfs-archive".toByteArray()
        val downloader = object : RootfsDownloader {
            override suspend fun download(url: String, destination: File, onProgress: (Int?) -> Unit): Result<File> {
                destination.writeBytes(payloadBytes)
                onProgress(100)
                return Result.success(destination)
            }
        }

        val installer = DefaultRootfsInstaller(
            rootExecutor = rootExecutor,
            assetStreamProvider = assetProvider,
            downloader = downloader,
            stagingDir = stagingDir,
            ioDispatcher = dispatcher,
        )

        val result = installer.install(
            source = RootfsSource.RemoteDownload(
                url = "https://example.com/arm64-rootfs.tar.gz",
                sha256 = "0000000000000000000000000000000000000000000000000000000000000000",
            ),
            rootfsPath = "/data/local/ag_rootfs",
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RootfsIntegrityException)
        assertEquals(0, stagingDir.listFiles()?.size ?: 0)
        coVerify(exactly = 0) { rootExecutor.exec(match { it.contains("tar -xzf") }, any(), any()) }
    }

    @Test
    fun `remote download succeeds when sha256 matches`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val rootExecutor = mockk<RootExecutor>()
        val stagingDir = tempFolder.newFolder("staging_remote_ok")
        val payloadBytes = "valid-arm64-rootfs-archive".toByteArray()
        val expectedHashFile = tempFolder.newFile("hash_ref.bin").apply { writeBytes(payloadBytes) }
        val expectedHash = Sha256Verifier.computeHex(expectedHashFile, dispatcher)

        val downloader = object : RootfsDownloader {
            override suspend fun download(url: String, destination: File, onProgress: (Int?) -> Unit): Result<File> {
                destination.writeBytes(payloadBytes)
                onProgress(50)
                onProgress(100)
                return Result.success(destination)
            }
        }
        coEvery { rootExecutor.exec(any(), any(), any()) } returns ShellResult(exitCode = 0, stdout = "", stderr = "")

        val installer = DefaultRootfsInstaller(
            rootExecutor = rootExecutor,
            assetStreamProvider = object : AssetStreamProvider {
                override fun openAsset(assetPath: String) = null
            },
            downloader = downloader,
            stagingDir = stagingDir,
            ioDispatcher = dispatcher,
        )

        val result = installer.install(
            source = RootfsSource.RemoteDownload(
                url = "https://example.com/arm64-rootfs.tar.gz",
                sha256 = expectedHash.uppercase(),
            ),
            rootfsPath = "/data/local/ag_rootfs",
        )

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { rootExecutor.exec(match { it.contains("tar -xzf") }, any(), any()) }
    }

    @Test
    fun `local file import verifies sha256 via root sha256sum when file is root-only`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val rootExecutor = mockk<RootExecutor>()
        val stagingDir = tempFolder.newFolder("staging_local")
        val expectedHash = "a".repeat(64)

        coEvery { rootExecutor.exec("[ -f /sdcard/Download/arm64-rootfs.tar.gz ]", any(), any()) } returns
            ShellResult(exitCode = 0, stdout = "", stderr = "")
        coEvery { rootExecutor.exec("sha256sum /sdcard/Download/arm64-rootfs.tar.gz", any(), any()) } returns
            ShellResult(exitCode = 0, stdout = "$expectedHash  /sdcard/Download/arm64-rootfs.tar.gz\n", stderr = "")
        coEvery { rootExecutor.exec(match { it.contains("tar -xzf") || it.contains("mkdir -p") || it.contains("-d /data/local/ag_rootfs") }, any(), any()) } returns
            ShellResult(exitCode = 0, stdout = "", stderr = "")

        val installer = DefaultRootfsInstaller(
            rootExecutor = rootExecutor,
            assetStreamProvider = object : AssetStreamProvider {
                override fun openAsset(assetPath: String) = null
            },
            downloader = mockk(relaxed = true),
            stagingDir = stagingDir,
            ioDispatcher = dispatcher,
        )

        val result = installer.install(
            source = RootfsSource.LocalFile(
                absolutePath = "/sdcard/Download/arm64-rootfs.tar.gz",
                sha256 = expectedHash,
            ),
            rootfsPath = "/data/local/ag_rootfs",
        )
        assertTrue(result.isSuccess)
    }

    @Test
    fun `download url validation enforces HTTPS except loopback`() {
        KtorRootfsDownloader.validateDownloadUrl("https://cdn.example.org/rootfs.tar.gz")
        KtorRootfsDownloader.validateDownloadUrl("http://127.0.0.1:8080/rootfs.tar.gz")

        val rejected = listOf(
            "http://example.com/rootfs.tar.gz",
            "file:///etc/passwd",
            "ftp://example.com/rootfs.tar.gz",
        )
        for (url in rejected) {
            val valid = try {
                KtorRootfsDownloader.validateDownloadUrl(url)
                true
            } catch (_: IllegalArgumentException) {
                false
            }
            assertFalse("Expected URL '$url' to be rejected", valid)
        }
    }
}
