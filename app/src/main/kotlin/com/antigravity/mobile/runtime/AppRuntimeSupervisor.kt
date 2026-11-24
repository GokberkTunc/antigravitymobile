package com.antigravity.mobile.runtime

import com.antigravity.mobile.contracts.AcpClient
import com.antigravity.mobile.contracts.AndroidMcpServer
import com.antigravity.mobile.contracts.UiNode
import com.antigravity.mobile.contracts.UiTreeProvider
import com.antigravity.mobile.di.AppIoDispatcher
import com.antigravity.mobile.di.ApplicationScope
import com.antigravity.mobile.feature.security.killswitch.KillSwitchCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lightweight logging facade so [AppRuntimeSupervisor] stays deterministically testable on JVM
 * while emitting structured non-secret diagnostics to Android `logcat` on device.
 */
interface AppRuntimeLogger {
    fun info(tag: String, message: String)
    fun warn(tag: String, message: String, throwable: Throwable? = null)
}

/**
 * Process-wide runtime coordinator started from [com.antigravity.mobile.AntigravityApplication].
 *
 * Responsibilities (all strictly off the Android Main Thread):
 * 1. Begins observing hardware & overlay emergency Kill-Switch triggers via [KillSwitchCoordinator].
 * 2. Boots the embedded loopback [AndroidMcpServer] on `127.0.0.1` (ephemeral port, never logging bearer token).
 * 3. Observes [UiTreeProvider.isServiceConnected] and runs a lightweight `ui_get_tree` sanity capture.
 * 4. Logs [AcpClient.activeSession] handshake state transitions (zero tokens or credentials).
 */
@Singleton
class AppRuntimeSupervisor @Inject constructor(
    private val killSwitchCoordinator: KillSwitchCoordinator,
    private val androidMcpServer: AndroidMcpServer,
    private val uiTreeProvider: UiTreeProvider,
    private val acpClient: AcpClient,
    @ApplicationScope private val appScope: CoroutineScope,
    @AppIoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val logger: AppRuntimeLogger,
) {
    private val started = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) return

        // 1. Emergency Kill-Switch observation (500ms Volume Up+Down + Overlay Stop button)
        appScope.launch(ioDispatcher) {
            try {
                killSwitchCoordinator.startObserving(appScope)
                logger.info(LOG_TAG, "KillSwitchCoordinator active and observing triggers")
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                logger.warn(LOG_TAG, "Failed to start KillSwitchCoordinator observation", t)
            }
        }

        // 2. Embedded Android System MCP server (strictly 127.0.0.1 + Bearer auth; token NEVER logged)
        appScope.launch(ioDispatcher) {
            try {
                val endpoint = androidMcpServer.start(port = 0)
                val toolCount = androidMcpServer.registeredTools().size
                logger.info(
                    LOG_TAG,
                    "Local Android System MCP server listening on ${endpoint.host}:${endpoint.port} " +
                        "(path=${endpoint.path}, tools=$toolCount)",
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                logger.warn(LOG_TAG, "Failed to start local Android System MCP server", t)
            }
        }

        // 3. AccessibilityService connection & sample ui_get_tree verification log
        appScope.launch(ioDispatcher) {
            uiTreeProvider.isServiceConnected
                .collect { connected ->
                    logger.info(LOG_TAG, "AccessibilityService connected=$connected")
                    if (connected) {
                        try {
                            val rootNode = uiTreeProvider.captureUiTree(
                                includeInvisible = false,
                                maxDepth = SAMPLE_UI_TREE_DEPTH,
                                maxNodes = SAMPLE_UI_TREE_NODES,
                            )
                            val nodeCount = countUiNodes(rootNode)
                            logger.info(
                                LOG_TAG,
                                "ui_get_tree sample captured: rootPackage=${rootNode?.packageName ?: "none"}, " +
                                    "nodes=$nodeCount",
                            )
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (t: Throwable) {
                            logger.warn(LOG_TAG, "ui_get_tree sample capture failed", t)
                        }
                    }
                }
        }

        // 4. ACP session handshake observation (non-secret status only)
        appScope.launch(ioDispatcher) {
            acpClient.activeSession
                .collect { session ->
                    if (session != null) {
                        logger.info(
                            LOG_TAG,
                            "agy acp handshake active: session=${session.sessionId}, " +
                                "model=${session.model.name}, state=${session.state.name}",
                        )
                    }
                }
        }
    }

    private fun countUiNodes(root: UiNode?): Int {
        if (root == null) return 0
        var total = 1
        for (child in root.children) {
            total += countUiNodes(child)
        }
        return total
    }

    companion object {
        const val LOG_TAG = "AntigravityMobile"
        private const val SAMPLE_UI_TREE_DEPTH = 10
        private const val SAMPLE_UI_TREE_NODES = 64
    }
}
