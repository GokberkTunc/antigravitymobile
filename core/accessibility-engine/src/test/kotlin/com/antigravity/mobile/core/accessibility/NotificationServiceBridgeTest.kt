package com.antigravity.mobile.core.accessibility

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationServiceBridgeTest {

    @Test
    fun `bridge lists, dismisses, and replies when connected and safely no-ops when disconnected`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val bridge = NotificationServiceBridge(dispatcher)

        assertFalse(bridge.isConnected.value)
        assertTrue(bridge.listActiveNotifications().isEmpty())
        assertFalse(bridge.dismissNotification("k1"))
        assertFalse(bridge.replyToNotification("k1", "hi"))

        val replies = mutableListOf<Pair<String, String>>()
        val dismissed = mutableListOf<String>()
        val sample = NotificationRecord(
            key = "0|com.whatsapp|1|null|100",
            packageName = "com.whatsapp",
            title = "Ali",
            text = "Nasılsın?",
            postTimeEpochMs = 1_700_000_000_000L,
            isClearable = true,
            hasReplyAction = true,
        )

        val host = object : NotificationHost {
            override fun listActiveNotifications(): List<NotificationRecord> = listOf(sample)
            override fun dismissNotification(key: String): Boolean {
                dismissed += key
                return key == sample.key
            }

            override fun replyToNotification(key: String, replyText: String): Boolean {
                replies += key to replyText
                return key == sample.key
            }
        }

        bridge.attachHost(host)
        assertTrue(bridge.isConnected.value)
        assertEquals(listOf(sample), bridge.listActiveNotifications())
        assertTrue(bridge.replyToNotification(sample.key, "İyiyim!"))
        assertEquals(listOf(sample.key to "İyiyim!"), replies)
        assertTrue(bridge.dismissNotification(sample.key))
        assertEquals(listOf(sample.key), dismissed)

        bridge.detachHost(host)
        assertFalse(bridge.isConnected.value)
    }
}
