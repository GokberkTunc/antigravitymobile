package com.antigravity.mobile.feature.security.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.antigravity.mobile.feature.security.killswitch.KillSwitchCoordinator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Android 14/15/17 compliant foreground service hosting long-running agent lifecycle supervision,
 * emergency kill-switch observation, and persistent status notification with a one-tap Stop action.
 *
 * Manifest contract: `android:exported="false"`, `android:foregroundServiceType="specialUse|microphone"`,
 * and `android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE`.
 */
@AndroidEntryPoint
class AgentForegroundService : LifecycleService() {

    @Inject
    lateinit var killSwitchCoordinator: KillSwitchCoordinator

    private val serviceJob = SupervisorJob()
    val serviceScope: CoroutineScope = CoroutineScope(serviceJob + Dispatchers.IO)

    @Volatile
    private var currentStatusText: String = DEFAULT_STATUS_TEXT

    @Volatile
    private var microphoneActive: Boolean = false

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_STOP_AGENT -> {
                handleStopAgentAction()
                return START_NOT_STICKY
            }

            ACTION_UPDATE_STATUS -> {
                val status = intent?.getStringExtra(EXTRA_STATUS_TEXT)
                if (!status.isNullOrBlank()) {
                    currentStatusText = status
                }
                if (intent?.hasExtra(EXTRA_INCLUDE_MICROPHONE) == true) {
                    microphoneActive = intent.getBooleanExtra(EXTRA_INCLUDE_MICROPHONE, false)
                }
                promoteToForeground(currentStatusText, microphoneActive)
                return START_STICKY
            }

            ACTION_START -> {
                val status = intent?.getStringExtra(EXTRA_STATUS_TEXT)
                if (!status.isNullOrBlank()) {
                    currentStatusText = status
                }
                microphoneActive = intent?.getBooleanExtra(EXTRA_INCLUDE_MICROPHONE, false) ?: false
                promoteToForeground(currentStatusText, microphoneActive)
                if (::killSwitchCoordinator.isInitialized) {
                    serviceScope.launch {
                        killSwitchCoordinator.startObserving(serviceScope)
                    }
                }
                return START_STICKY
            }

            else -> {
                promoteToForeground(currentStatusText, microphoneActive)
                return START_STICKY
            }
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        if (::killSwitchCoordinator.isInitialized) {
            // Observation jobs are children of serviceScope and will be cancelled below.
        }
        serviceScope.cancel("AgentForegroundService destroyed")
        super.onDestroy()
    }

    private fun handleStopAgentAction() {
        if (::killSwitchCoordinator.isInitialized) {
            serviceScope.launch {
                killSwitchCoordinator.trigger(REASON_NOTIFICATION_STOP)
                ServiceCompat.stopForeground(
                    this@AgentForegroundService,
                    ServiceCompat.STOP_FOREGROUND_REMOVE,
                )
                stopSelf()
            }
        } else {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun promoteToForeground(statusText: String, includeMicrophone: Boolean) {
        ensureNotificationChannel()
        val hasRecordAudio = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        val fgsType = computeForegroundServiceType(
            sdkInt = Build.VERSION.SDK_INT,
            includeMicrophone = includeMicrophone,
            hasRecordAudioPermission = hasRecordAudio,
        )
        val notification = buildNotification(statusText)
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgsType)
    }

    private fun buildNotification(statusText: String): Notification {
        val stopIntent = Intent(this, AgentForegroundService::class.java).apply {
            action = ACTION_STOP_AGENT
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            STOP_REQUEST_CODE,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(NOTIFICATION_TITLE)
            .setContentText(statusText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_media_pause,
                STOP_ACTION_LABEL,
                stopPendingIntent,
            )
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Antigravity yerel ajan oturumu ve arka plan kontrol servisi"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "antigravity_agent_foreground"
        const val CHANNEL_NAME = "Antigravity Ajan Servisi"
        const val NOTIFICATION_ID = 4101
        const val STOP_REQUEST_CODE = 4102

        const val NOTIFICATION_TITLE = "Antigravity Mobile"
        const val DEFAULT_STATUS_TEXT = "Ajan hazır ve yerel MCP köprüsü etkin"
        const val STOP_ACTION_LABEL = "Durdur"
        const val REASON_NOTIFICATION_STOP = "Foreground notification Stop action"

        const val ACTION_START = "com.antigravity.mobile.feature.security.action.START_AGENT_FGS"
        const val ACTION_UPDATE_STATUS = "com.antigravity.mobile.feature.security.action.UPDATE_AGENT_FGS"
        const val ACTION_STOP_AGENT = "com.antigravity.mobile.feature.security.action.STOP_AGENT"

        const val EXTRA_STATUS_TEXT = "extra_status_text"
        const val EXTRA_INCLUDE_MICROPHONE = "extra_include_microphone"

        /**
         * Computes the Android API-level compliant foreground service type bitmask.
         * - API 34+ (Android 14/15/17): always includes `FOREGROUND_SERVICE_TYPE_SPECIAL_USE`,
         *   and adds `FOREGROUND_SERVICE_TYPE_MICROPHONE` only when requested AND `RECORD_AUDIO`
         *   is granted (preventing SecurityException on Android 14+).
         * - API 30..33: adds `FOREGROUND_SERVICE_TYPE_MICROPHONE` when requested AND granted.
         * - API 29: returns `0`.
         */
        fun computeForegroundServiceType(
            sdkInt: Int,
            includeMicrophone: Boolean,
            hasRecordAudioPermission: Boolean,
        ): Int {
            var type = 0
            if (sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            }
            if (
                sdkInt >= Build.VERSION_CODES.R &&
                includeMicrophone &&
                hasRecordAudioPermission
            ) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            return type
        }

        fun createStartIntent(
            context: Context,
            statusText: String = DEFAULT_STATUS_TEXT,
            includeMicrophone: Boolean = false,
        ): Intent = Intent(context, AgentForegroundService::class.java).apply {
            action = ACTION_START
            putExtra(EXTRA_STATUS_TEXT, statusText)
            putExtra(EXTRA_INCLUDE_MICROPHONE, includeMicrophone)
        }

        fun createUpdateStatusIntent(
            context: Context,
            statusText: String,
            includeMicrophone: Boolean? = null,
        ): Intent = Intent(context, AgentForegroundService::class.java).apply {
            action = ACTION_UPDATE_STATUS
            putExtra(EXTRA_STATUS_TEXT, statusText)
            if (includeMicrophone != null) {
                putExtra(EXTRA_INCLUDE_MICROPHONE, includeMicrophone)
            }
        }

        fun createStopIntent(context: Context): Intent =
            Intent(context, AgentForegroundService::class.java).apply {
                action = ACTION_STOP_AGENT
            }
    }
}
