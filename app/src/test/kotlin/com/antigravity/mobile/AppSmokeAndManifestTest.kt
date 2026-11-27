package com.antigravity.mobile

import com.antigravity.mobile.contracts.AcpClient
import com.antigravity.mobile.contracts.AcpModel
import com.antigravity.mobile.contracts.AcpSessionInfo
import com.antigravity.mobile.contracts.AcpSessionState
import com.antigravity.mobile.contracts.AndroidMcpServer
import com.antigravity.mobile.contracts.McpEndpoint
import com.antigravity.mobile.contracts.UiBounds
import com.antigravity.mobile.contracts.UiNode
import com.antigravity.mobile.contracts.UiTreeProvider
import com.antigravity.mobile.feature.security.killswitch.KillSwitchCoordinator
import com.antigravity.mobile.runtime.AppRuntimeLogger
import com.antigravity.mobile.runtime.AppRuntimeSupervisor
import com.antigravity.mobile.system.OAuthCallbackIntentParser
import com.antigravity.mobile.system.OAuthIntentParseResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

@OptIn(ExperimentalCoroutinesApi::class)
class AppSmokeAndManifestTest {

    private fun resolveAppDir(): File {
        val cwd = File(System.getProperty("user.dir") ?: ".")
        return if (File(cwd, "src/main/AndroidManifest.xml").exists()) {
            cwd
        } else {
            File(cwd, "app")
        }
    }

    private fun resolveRepoRoot(): File = checkNotNull(resolveAppDir().parentFile) {
        "Unable to resolve repository root from ${resolveAppDir().absolutePath}"
    }

    @Test
    fun `application id and build config match frozen design decision`() {
        assertEquals("com.antigravity.mobile", BuildConfig.APPLICATION_ID)
    }

    @Test
    fun `app manifest enforces strict component isolation and no global cleartext traffic`() {
        val manifestFile = File(resolveAppDir(), "src/main/AndroidManifest.xml")
        assertTrue("AndroidManifest.xml must exist", manifestFile.exists())

        val doc = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }.newDocumentBuilder().parse(manifestFile)

        val applicationNode = doc.getElementsByTagName("application").item(0) as Element
        assertEquals(
            ".AntigravityApplication",
            applicationNode.getAttributeNS(ANDROID_NS, "name"),
        )
        assertEquals(
            "@xml/network_security_config",
            applicationNode.getAttributeNS(ANDROID_NS, "networkSecurityConfig"),
        )
        assertFalse(
            "Global usesCleartextTraffic must NEVER be true",
            applicationNode.getAttributeNS(ANDROID_NS, "usesCleartextTraffic")
                .equals("true", ignoreCase = true),
        )
        assertEquals(
            "false",
            applicationNode.getAttributeNS(ANDROID_NS, "allowBackup"),
        )

        val allowedExported = setOf(
            ".MainActivity",
            "com.antigravity.mobile.core.accessibility.AntigravityAccessibilityService",
            "com.antigravity.mobile.core.accessibility.AntigravityNotificationListenerService",
            ".system.AntigravityDeviceAdminReceiver",
        )

        val componentTags = listOf("activity", "service", "receiver", "provider")
        val seenExported = mutableSetOf<String>()
        for (tag in componentTags) {
            val nodes = doc.getElementsByTagName(tag)
            for (i in 0 until nodes.length) {
                val elem = nodes.item(i) as Element
                val name = elem.getAttributeNS(ANDROID_NS, "name")
                val exported = elem.getAttributeNS(ANDROID_NS, "exported")
                assertTrue(
                    "Component $tag($name) must explicitly declare android:exported",
                    exported == "true" || exported == "false",
                )
                if (exported == "true") {
                    assertTrue(
                        "Unexpected exported component: $tag($name)",
                        name in allowedExported,
                    )
                    seenExported += name
                }
            }
        }
        assertEquals(allowedExported, seenExported)
    }

    @Test
    fun `feature library manifests keep internal services exported false with Android 14+ FGS metadata`() {
        val repoRoot = resolveRepoRoot()
        val libraryManifests = listOf(
            File(repoRoot, "feature/automation-security/src/main/AndroidManifest.xml"),
            File(repoRoot, "feature/overlay-voice/src/main/AndroidManifest.xml"),
        )

        for (manifest in libraryManifests) {
            assertTrue("Expected manifest at ${manifest.path}", manifest.exists())
            val doc = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
            }.newDocumentBuilder().parse(manifest)

            val services = doc.getElementsByTagName("service")
            assertTrue("Expected at least one service in ${manifest.path}", services.length > 0)
            for (i in 0 until services.length) {
                val service = services.item(i) as Element
                val name = service.getAttributeNS(ANDROID_NS, "name")
                assertEquals(
                    "Internal service $name must have android:exported=\"false\"",
                    "false",
                    service.getAttributeNS(ANDROID_NS, "exported"),
                )
                val fgsType = service.getAttributeNS(ANDROID_NS, "foregroundServiceType")
                assertTrue(
                    "Service $name must declare android:foregroundServiceType",
                    fgsType.isNotBlank(),
                )
                if ("specialUse" in fgsType) {
                    val properties = service.getElementsByTagName("property")
                    var hasSpecialUseSubtype = false
                    for (p in 0 until properties.length) {
                        val prop = properties.item(p) as Element
                        if (prop.getAttributeNS(ANDROID_NS, "name") == "android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE") {
                            hasSpecialUseSubtype = prop.getAttributeNS(ANDROID_NS, "value").isNotBlank()
                        }
                    }
                    assertTrue(
                        "Service $name with specialUse must declare PROPERTY_SPECIAL_USE_FGS_SUBTYPE",
                        hasSpecialUseSubtype,
                    )
                }
            }

            val receivers = doc.getElementsByTagName("receiver")
            for (i in 0 until receivers.length) {
                val receiver = receivers.item(i) as Element
                assertEquals(
                    "Internal receiver must have android:exported=\"false\"",
                    "false",
                    receiver.getAttributeNS(ANDROID_NS, "exported"),
                )
            }
        }
    }

    @Test
    fun `xml configs enforce loopback-only cleartext, accessibility key filtering, and device admin lock`() {
        val appDir = resolveAppDir()

        val networkXml = File(appDir, "src/main/res/xml/network_security_config.xml").readText()
        assertTrue(networkXml.contains("""cleartextTrafficPermitted="false""""))
        assertTrue(networkXml.contains(">127.0.0.1<"))
        assertTrue(networkXml.contains(">localhost<"))

        val accessibilityXml = File(appDir, "src/main/res/xml/accessibility_service_config.xml").readText()
        assertTrue(accessibilityXml.contains("""android:canRetrieveWindowContent="true""""))
        assertTrue(accessibilityXml.contains("flagRequestFilterKeyEvents"))

        val deviceAdminXml = File(appDir, "src/main/res/xml/device_admin_receiver.xml").readText()
        assertTrue(deviceAdminXml.contains("<force-lock"))
    }

    @Test
    fun `oauth callback intent parser extracts codes from custom scheme and loopback redirects`() {
        val customScheme = OAuthCallbackIntentParser.parseUriString(
            "antigravity://oauth/callback?code=AUTH_CODE_123&state=xyz",
        )
        assertEquals(
            OAuthIntentParseResult.AuthorizationCode(code = "AUTH_CODE_123", state = "xyz"),
            customScheme,
        )

        val loopback = OAuthCallbackIntentParser.parseUriString(
            "http://127.0.0.1:43123/oauth2callback?code=LOOPBACK_456&state=abc",
        )
        assertEquals(
            OAuthIntentParseResult.AuthorizationCode(code = "LOOPBACK_456", state = "abc"),
            loopback,
        )

        val officialHttps = OAuthCallbackIntentParser.parseUriString(
            "https://antigravity.google/oauth-callback?code=4%2F0AfJohXmOfficialHttpsCode789&state=st42",
        )
        assertEquals(
            OAuthIntentParseResult.AuthorizationCode(
                code = "4/0AfJohXmOfficialHttpsCode789",
                state = "st42",
            ),
            officialHttps,
        )

        val officialHttpsError = OAuthCallbackIntentParser.parseUriString(
            "https://antigravity.google/oauth-callback?error=access_denied",
        )
        assertTrue(officialHttpsError is OAuthIntentParseResult.Error)

        val errorUri = OAuthCallbackIntentParser.parseUriString(
            "antigravity://oauth/callback?error=access_denied&error_description=User%20cancelled",
        )
        assertTrue(errorUri is OAuthIntentParseResult.Error)

        assertEquals(
            OAuthIntentParseResult.NotOAuthIntent,
            OAuthCallbackIntentParser.parseUriString("https://example.com/unrelated"),
        )
    }

    @Test
    fun `runtime supervisor boots kill switch, starts loopback mcp without logging token, and logs diagnostics`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val appScope = TestScope(dispatcher)

        val killSwitchCoordinator = mockk<KillSwitchCoordinator>(relaxed = true)
        val androidMcpServer = mockk<AndroidMcpServer>(relaxed = true)
        val uiTreeProvider = mockk<UiTreeProvider>(relaxed = true)
        val acpClient = mockk<AcpClient>(relaxed = true)

        val secretToken = "SUPER_SECRET_BEARER_TOKEN_DO_NOT_LOG"
        val isAccessibilityConnected = MutableStateFlow(false)
        val activeSession = MutableStateFlow<AcpSessionInfo?>(null)

        coEvery { androidMcpServer.start(0) } returns McpEndpoint(
            host = "127.0.0.1",
            port = 45678,
            bearerToken = secretToken,
            path = "/mcp",
        )
        every { androidMcpServer.registeredTools() } returns emptyList()
        every { uiTreeProvider.isServiceConnected } returns isAccessibilityConnected
        coEvery { uiTreeProvider.captureUiTree(false, any(), any()) } returns UiNode(
            nodeId = "n0",
            packageName = "com.antigravity.mobile",
            className = "android.widget.FrameLayout",
            bounds = UiBounds(0, 0, 1080, 2400),
        )
        every { acpClient.activeSession } returns activeSession

        val logs = mutableListOf<String>()
        val logger = object : AppRuntimeLogger {
            override fun info(tag: String, message: String) {
                logs += "$tag:$message"
            }

            override fun warn(tag: String, message: String, throwable: Throwable?) {
                logs += "$tag:WARN:$message"
            }
        }

        val supervisor = AppRuntimeSupervisor(
            killSwitchCoordinator = killSwitchCoordinator,
            androidMcpServer = androidMcpServer,
            uiTreeProvider = uiTreeProvider,
            acpClient = acpClient,
            appScope = appScope,
            ioDispatcher = dispatcher,
            logger = logger,
        )

        supervisor.start()
        supervisor.start() // idempotent
        appScope.advanceUntilIdle()

        isAccessibilityConnected.value = true
        activeSession.value = AcpSessionInfo(
            sessionId = "sess-42",
            profileId = "prof-1",
            workspaceDir = "/mnt/sdcard",
            model = AcpModel.GEMINI_4_NEON,
            state = AcpSessionState.READY,
        )
        appScope.advanceUntilIdle()

        coVerify(exactly = 1) { killSwitchCoordinator.startObserving(appScope) }
        coVerify(exactly = 1) { androidMcpServer.start(0) }

        assertTrue(logs.any { it.contains("127.0.0.1:45678") })
        assertTrue(logs.any { it.contains("AccessibilityService connected=true") })
        assertTrue(logs.any { it.contains("ui_get_tree sample captured") })
        assertTrue(logs.any { it.contains("agy acp handshake active: session=sess-42") })
        assertFalse(
            "Bearer token must NEVER appear in logcat output",
            logs.any { it.contains(secretToken) },
        )
    }

    companion object {
        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
