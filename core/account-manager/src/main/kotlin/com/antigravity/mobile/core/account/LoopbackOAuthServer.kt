package com.antigravity.mobile.core.account

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.uri
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress
import java.net.ServerSocket
import javax.inject.Inject

/**
 * Abstraction for launching the Google OAuth authorization URL in Chrome Custom Tabs.
 */
interface OAuthBrowserLauncher {
    fun launchAuthUrl(authUrl: String): Boolean
}

class AndroidChromeCustomTabsLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
) : OAuthBrowserLauncher {

    override fun launchAuthUrl(authUrl: String): Boolean {
        if (!OAuthUrlExtractor.isValidGoogleAuthUrl(authUrl)) {
            return false
        }
        return try {
            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
            customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            customTabsIntent.launchUrl(context, Uri.parse(authUrl))
            true
        } catch (_: Exception) {
            false
        }
    }
}

/**
 * Lightweight ephemeral HTTP server bound strictly to `127.0.0.1` for capturing
 * Google OAuth redirect callbacks from Chrome Custom Tabs.
 */
class LoopbackOAuthServer(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private var serverScope: CoroutineScope? = null
    private var callbackDeferred: CompletableDeferred<String>? = null

    /**
     * Starts listening strictly on `127.0.0.1:<requestedPort>` (or ephemeral port when `0`)
     * and returns the actual bound loopback port.
     */
    suspend fun start(requestedPort: Int = 0): Int = withContext(ioDispatcher) {
        stop()
        val deferred = CompletableDeferred<String>()
        callbackDeferred = deferred

        val bindPort = if (requestedPort == 0) {
            ServerSocket(0, 1, InetAddress.getByName(LOOPBACK_HOST)).use { it.localPort }
        } else {
            requestedPort
        }

        // Own scope ensures Ktor 3's embeddedServer does NOT attach its selector job
        // to the caller's `withContext` job (which would prevent `start()` from returning).
        val ownedScope = CoroutineScope(SupervisorJob() + ioDispatcher)
        serverScope = ownedScope
        val engine = createEmbeddedServer(ownedScope, bindPort, deferred)
        engine.start(wait = false)
        server = engine
        bindPort
    }

    private fun createEmbeddedServer(
        scope: CoroutineScope,
        bindPort: Int,
        deferred: CompletableDeferred<String>,
    ): EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration> =
        scope.embeddedServer(
            factory = CIO,
            port = bindPort,
            host = LOOPBACK_HOST,
        ) {
            routing {
                val handleCallback: suspend io.ktor.server.routing.RoutingContext.() -> Unit = {
                    val fullUri = "http://$LOOPBACK_HOST:$bindPort${call.request.uri}"
                    val parsed = OAuthCallbackParser.parse(fullUri)
                    if (!deferred.isCompleted) {
                        deferred.complete(fullUri)
                    }
                    call.response.header(HttpHeaders.Connection, "close")
                    when (parsed) {
                        is OAuthCallbackResult.Success -> {
                            call.respondText(
                                text = SUCCESS_HTML,
                                contentType = ContentType.Text.Html,
                                status = HttpStatusCode.OK,
                            )
                        }
                        is OAuthCallbackResult.OAuthError -> {
                            call.respondText(
                                text = ERROR_HTML,
                                contentType = ContentType.Text.Html,
                                status = HttpStatusCode.BadRequest,
                            )
                        }
                        is OAuthCallbackResult.Invalid -> {
                            call.respondText(
                                text = INVALID_HTML,
                                contentType = ContentType.Text.Html,
                                status = HttpStatusCode.BadRequest,
                            )
                        }
                    }
                }
                get("/") { handleCallback() }
                get("/oauth2callback") { handleCallback() }
                get("/callback") { handleCallback() }
            }
        }

    /**
     * Suspends until a loopback callback URI is received or [timeoutMs] expires.
     */
    suspend fun awaitCallback(timeoutMs: Long = 180_000L): String? {
        val deferred = callbackDeferred ?: return null
        return try {
            withTimeoutOrNull(timeoutMs) {
                deferred.await()
            }
        } catch (ce: CancellationException) {
            throw ce
        }
    }

    fun stop() {
        server?.stop(gracePeriodMillis = 50L, timeoutMillis = 200L)
        server = null
        serverScope?.cancel()
        serverScope = null
        callbackDeferred?.cancel()
        callbackDeferred = null
    }

    companion object {
        const val LOOPBACK_HOST = "127.0.0.1"

        private const val SUCCESS_HTML =
            "<!doctype html><html><body style=\"font-family:sans-serif;padding:24px\">" +
                "<h2>Antigravity Mobile</h2>" +
                "<p>Google authentication received. You may close this tab and return to Antigravity Mobile.</p>" +
                "</body></html>"

        private const val ERROR_HTML =
            "<!doctype html><html><body style=\"font-family:sans-serif;padding:24px\">" +
                "<h2>Authentication Declined</h2>" +
                "<p>Google OAuth returned an error. Return to Antigravity Mobile to retry.</p>" +
                "</body></html>"

        private const val INVALID_HTML =
            "<!doctype html><html><body style=\"font-family:sans-serif;padding:24px\">" +
                "<h2>Invalid Callback</h2>" +
                "<p>Missing OAuth authorization code.</p>" +
                "</body></html>"
    }
}
