package com.antigravity.mobile.core.accessibility

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.util.concurrent.atomic.AtomicReference

@Serializable
data class NotificationRecord(
    val key: String,
    val packageName: String,
    val title: String? = null,
    val text: String? = null,
    val postTimeEpochMs: Long = 0L,
    val isClearable: Boolean = true,
    val hasReplyAction: Boolean = false,
    val channelId: String? = null,
)

/**
 * Low-level service host contract implemented by [AntigravityNotificationListenerService].
 */
interface NotificationHost {
    fun listActiveNotifications(): List<NotificationRecord>
    fun dismissNotification(key: String): Boolean
    fun replyToNotification(key: String, replyText: String): Boolean
}

/**
 * Bridge consumed by `:core:android-mcp`'s `notifications_manage` tool (`list`, `dismiss`, `reply`).
 */
interface NotificationBridge {
    val isConnected: StateFlow<Boolean>

    suspend fun listActiveNotifications(): List<NotificationRecord>

    suspend fun dismissNotification(key: String): Boolean

    suspend fun replyToNotification(key: String, replyText: String): Boolean
}

class NotificationServiceBridge(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : NotificationBridge {

    private val hostRef = AtomicReference<NotificationHost?>(null)

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    fun attachHost(host: NotificationHost) {
        hostRef.set(host)
        _isConnected.value = true
    }

    fun detachHost(host: NotificationHost? = null) {
        if (host == null) {
            hostRef.set(null)
        } else {
            hostRef.compareAndSet(host, null)
        }
        _isConnected.value = hostRef.get() != null
    }

    override suspend fun listActiveNotifications(): List<NotificationRecord> =
        withContext(ioDispatcher) {
            val host = hostRef.get() ?: return@withContext emptyList()
            host.listActiveNotifications()
        }

    override suspend fun dismissNotification(key: String): Boolean = withContext(ioDispatcher) {
        val host = hostRef.get() ?: return@withContext false
        if (key.isBlank()) return@withContext false
        host.dismissNotification(key)
    }

    override suspend fun replyToNotification(key: String, replyText: String): Boolean =
        withContext(ioDispatcher) {
            val host = hostRef.get() ?: return@withContext false
            if (key.isBlank()) return@withContext false
            host.replyToNotification(key, replyText)
        }

    companion object {
        val instance: NotificationServiceBridge = NotificationServiceBridge()
    }
}

/**
 * Android [NotificationListenerService] enabling `notifications_manage` (`list`, `dismiss`,
 * and direct `RemoteInput` inline reply).
 */
class AntigravityNotificationListenerService : NotificationListenerService(), NotificationHost {

    override fun onListenerConnected() {
        super.onListenerConnected()
        NotificationServiceBridge.instance.attachHost(this)
    }

    override fun onListenerDisconnected() {
        NotificationServiceBridge.instance.detachHost(this)
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        NotificationServiceBridge.instance.detachHost(this)
        super.onDestroy()
    }

    override fun listActiveNotifications(): List<NotificationRecord> {
        val active: Array<StatusBarNotification> = activeNotifications ?: return emptyList()
        return active.map { sbn ->
            val extras = sbn.notification.extras
            val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            val text = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                ?: extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            val hasReply = findReplyAction(sbn) != null
            NotificationRecord(
                key = sbn.key,
                packageName = sbn.packageName,
                title = title,
                text = text,
                postTimeEpochMs = sbn.postTime,
                isClearable = sbn.isClearable,
                hasReplyAction = hasReply,
                channelId = sbn.notification.channelId,
            )
        }
    }

    override fun dismissNotification(key: String): Boolean {
        val target = activeNotifications?.firstOrNull { it.key == key } ?: return false
        cancelNotification(target.key)
        return true
    }

    override fun replyToNotification(key: String, replyText: String): Boolean {
        val target = activeNotifications?.firstOrNull { it.key == key } ?: return false
        val replyAction = findReplyAction(target) ?: return false
        val remoteInputs = replyAction.remoteInputs ?: return false
        if (remoteInputs.isEmpty()) return false

        val resultsBundle = Bundle().apply {
            remoteInputs.forEach { input ->
                putCharSequence(input.resultKey, replyText)
            }
        }
        val fillInIntent = Intent()
        RemoteInput.addResultsToIntent(remoteInputs, fillInIntent, resultsBundle)
        return try {
            replyAction.actionIntent.send(this, 0, fillInIntent)
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun findReplyAction(sbn: StatusBarNotification): Notification.Action? {
        return sbn.notification.actions?.firstOrNull { action ->
            val inputs = action.remoteInputs
            inputs != null && inputs.isNotEmpty() && action.actionIntent != null
        }
    }
}
