package com.antigravity.mobile.core.accessibility

import com.antigravity.mobile.contracts.UiActionRequest
import com.antigravity.mobile.contracts.UiActionResult
import com.antigravity.mobile.contracts.UiActionType
import com.antigravity.mobile.contracts.UiNode
import com.antigravity.mobile.contracts.UiTreeProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service-side contract implemented by [AntigravityAccessibilityService] and fake test hosts.
 */
interface AccessibilityHost {
    fun activeRootNode(): AccessibilityNodeHandle?
    fun activePackageName(): String?
    fun performGlobalAction(action: UiActionType): Boolean
}

/**
 * Shared process-wide bridge connecting the Android-instantiated [AntigravityAccessibilityService]
 * with DI-managed [AccessibilityUiTreeProvider] consumers.
 */
class AccessibilityServiceBridge {

    private val hostRef = AtomicReference<AccessibilityHost?>(null)

    private val _isServiceConnected = MutableStateFlow(false)
    val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

    private val _killSwitchTriggered = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val killSwitchTriggered: SharedFlow<Unit> = _killSwitchTriggered.asSharedFlow()

    fun attachHost(host: AccessibilityHost) {
        hostRef.set(host)
        _isServiceConnected.value = true
    }

    fun detachHost(host: AccessibilityHost? = null) {
        if (host == null) {
            hostRef.set(null)
        } else {
            hostRef.compareAndSet(host, null)
        }
        _isServiceConnected.value = hostRef.get() != null
    }

    fun currentHost(): AccessibilityHost? = hostRef.get()

    fun emitKillSwitch() {
        _killSwitchTriggered.tryEmit(Unit)
    }

    companion object {
        val instance: AccessibilityServiceBridge = AccessibilityServiceBridge()
    }
}

/**
 * Production [UiTreeProvider] delegating tree capture and node actions off the Main Thread
 * (`Dispatchers.Default`) to the connected [AccessibilityHost].
 */
@Singleton
class AccessibilityUiTreeProvider(
    private val bridge: AccessibilityServiceBridge,
    private val snapshotBuilder: UiTreeSnapshotBuilder,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : UiTreeProvider {

    @Inject
    constructor(
        bridge: AccessibilityServiceBridge,
        snapshotBuilder: UiTreeSnapshotBuilder,
    ) : this(
        bridge = bridge,
        snapshotBuilder = snapshotBuilder,
        defaultDispatcher = Dispatchers.Default,
    )

    private val lastSnapshotRef = AtomicReference<UiTreeSnapshot?>(null)

    override val isServiceConnected: StateFlow<Boolean>
        get() = bridge.isServiceConnected

    override val killSwitchTriggered: SharedFlow<Unit>
        get() = bridge.killSwitchTriggered

    override suspend fun getActivePackageName(): String? = withContext(defaultDispatcher) {
        try {
            val host = bridge.currentHost() ?: return@withContext null
            host.activePackageName()
                ?: host.activeRootNode()?.packageName?.takeIf { it.isNotBlank() }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            null
        }
    }

    override suspend fun captureUiTree(
        includeInvisible: Boolean,
        maxDepth: Int,
        maxNodes: Int,
    ): UiNode? = withContext(defaultDispatcher) {
        val host = bridge.currentHost() ?: return@withContext null
        val snapshot = snapshotBuilder.build(
            rootHandle = host.activeRootNode(),
            includeInvisible = includeInvisible,
            maxDepth = maxDepth,
            maxNodes = maxNodes,
        )
        lastSnapshotRef.set(snapshot)
        snapshot.root
    }

    override suspend fun performAction(request: UiActionRequest): UiActionResult =
        withContext(defaultDispatcher) {
            val host = bridge.currentHost()
                ?: return@withContext UiActionResult(
                    success = false,
                    message = "AccessibilityService is not connected",
                )

            if (request.action in GLOBAL_ACTIONS) {
                val ok = host.performGlobalAction(request.action)
                return@withContext UiActionResult(
                    success = ok,
                    message = if (ok) {
                        "Executed global action ${request.action}"
                    } else {
                        "Global action ${request.action} was rejected by Android"
                    },
                )
            }

            // 1. Direct lookup by cached nodeId from latest snapshot
            val requestedNodeId = request.nodeId?.trim()?.takeIf { it.isNotEmpty() }
            if (requestedNodeId != null) {
                val cached = lastSnapshotRef.get()?.nodesById?.get(requestedNodeId)
                if (cached != null) {
                    val ok = cached.performAction(request.action, request.value)
                    return@withContext UiActionResult(
                        success = ok,
                        message = if (ok) {
                            "Performed ${request.action} on $requestedNodeId"
                        } else {
                            "Node $requestedNodeId rejected action ${request.action}"
                        },
                        matchedNodeId = requestedNodeId,
                    )
                }
            }

            // 2. Refresh snapshot and search by resourceId or text/contentDesc
            val targetResId = request.targetResourceId?.trim()?.takeIf { it.isNotEmpty() }
            val targetText = request.targetText?.trim()?.takeIf { it.isNotEmpty() }
            if (targetResId == null && targetText == null) {
                return@withContext UiActionResult(
                    success = false,
                    message = if (requestedNodeId != null) {
                        "Node '$requestedNodeId' not found in latest UI tree snapshot"
                    } else {
                        "Either nodeId, targetResourceId, or targetText is required for ${request.action}"
                    },
                )
            }

            val freshSnapshot = snapshotBuilder.build(
                rootHandle = host.activeRootNode(),
                includeInvisible = false,
                maxDepth = UiTreeSnapshotBuilder.DEFAULT_MAX_DEPTH,
                maxNodes = UiTreeSnapshotBuilder.DEFAULT_MAX_NODES,
            )
            lastSnapshotRef.set(freshSnapshot)

            val matched = findMatchingNode(
                snapshot = freshSnapshot,
                targetResourceId = targetResId,
                targetText = targetText,
            ) ?: return@withContext UiActionResult(
                success = false,
                message = "No UI node matched resourceId=$targetResId text=$targetText",
            )

            val (matchedId, handle) = matched
            val ok = handle.performAction(request.action, request.value)
            UiActionResult(
                success = ok,
                message = if (ok) {
                    "Performed ${request.action} on $matchedId"
                } else {
                    "Matched node $matchedId rejected action ${request.action}"
                },
                matchedNodeId = matchedId,
            )
        }

    private fun findMatchingNode(
        snapshot: UiTreeSnapshot,
        targetResourceId: String?,
        targetText: String?,
    ): Pair<String, AccessibilityNodeHandle>? {
        if (targetResourceId != null) {
            // Exact resourceId match first
            snapshot.uiNodesById.entries.firstOrNull { (_, node) ->
                node.resourceId.equals(targetResourceId, ignoreCase = false)
            }?.let { (id, _) ->
                snapshot.nodesById[id]?.let { return id to it }
            }
            // Suffix match (e.g. "call_button" matching "com.android.dialer:id/call_button")
            snapshot.uiNodesById.entries.firstOrNull { (_, node) ->
                val res = node.resourceId ?: return@firstOrNull false
                res.endsWith(":id/$targetResourceId") || res.endsWith("/$targetResourceId")
            }?.let { (id, _) ->
                snapshot.nodesById[id]?.let { return id to it }
            }
        }

        if (targetText != null) {
            // Exact case-insensitive text or contentDesc match first
            snapshot.uiNodesById.entries.firstOrNull { (_, node) ->
                node.text.equals(targetText, ignoreCase = true) ||
                    node.contentDesc.equals(targetText, ignoreCase = true)
            }?.let { (id, _) ->
                snapshot.nodesById[id]?.let { return id to it }
            }
            // Substring fallback
            snapshot.uiNodesById.entries.firstOrNull { (_, node) ->
                node.text?.contains(targetText, ignoreCase = true) == true ||
                    node.contentDesc?.contains(targetText, ignoreCase = true) == true
            }?.let { (id, _) ->
                snapshot.nodesById[id]?.let { return id to it }
            }
        }

        return null
    }

    companion object {
        private val GLOBAL_ACTIONS: Set<UiActionType> = setOf(
            UiActionType.BACK,
            UiActionType.HOME,
            UiActionType.RECENTS,
            UiActionType.NOTIFICATIONS,
        )
    }
}
