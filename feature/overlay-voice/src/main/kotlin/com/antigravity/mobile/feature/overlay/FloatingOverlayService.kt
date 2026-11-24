package com.antigravity.mobile.feature.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.antigravity.mobile.contracts.VoiceState
import com.antigravity.mobile.contracts.VoiceTranscriber
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Foreground service hosting the `SYSTEM_ALERT_WINDOW` (`TYPE_APPLICATION_OVERLAY`)
 * draggable Status Orb, live ticker, Push-to-Talk bar, critical approval card, and Stop button.
 *
 * The window uses [WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE] by default so the orb never
 * steals input focus from foreground applications; focusability is enabled strictly while the
 * user explicitly opens the optional quick text prompt field.
 */
@AndroidEntryPoint
class FloatingOverlayService : LifecycleService(), SavedStateRegistryOwner {

    @Inject
    lateinit var overlayController: OverlayControllerImpl

    @Inject
    lateinit var voiceTranscriber: VoiceTranscriber

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var voiceLocaleTag by mutableStateOf(AndroidSpeechVoiceTranscriber.DEFAULT_LOCALE_TR)

    override fun onCreate() {
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        super.onCreate()

        startForegroundCompat()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        observeVoiceState()
        observeInputFocusRequests()
        observeVisibility()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_HIDE -> {
                lifecycleScope.launch {
                    overlayController.hide()
                    stopSelf()
                }
            }

            ACTION_SHOW, null -> {
                if (Settings.canDrawOverlays(this)) {
                    ensureOverlayAttached()
                    lifecycleScope.launch {
                        overlayController.showOrb()
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        lifecycleScope.launch {
            voiceTranscriber.cancel()
        }
        detachOverlayView()
        super.onDestroy()
    }

    private fun startForegroundCompat() {
        ensureNotificationChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Antigravity Yüzen Katman",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Ajan durumu, kritik onay kartları ve acil durdurma katmanı"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Antigravity Katmanı Aktif")
            .setContentText("Durum küresi ve kritik işlem onayları etkin")
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun ensureOverlayAttached() {
        if (overlayView != null || !Settings.canDrawOverlays(this)) return
        val wm = windowManager ?: return

        val params = createDefaultOverlayLayoutParams()
        layoutParams = params

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingOverlayService)
            setViewTreeSavedStateRegistryOwner(this@FloatingOverlayService)
            setContent {
                val overlayState by overlayController.state.collectAsStateWithLifecycle()
                val isTextInputOpen by overlayController.inputFocusRequested.collectAsStateWithLifecycle()

                OverlayRootContent(
                    state = overlayState,
                    voiceLocaleTag = voiceLocaleTag,
                    isTextInputOpen = isTextInputOpen,
                    onDrag = { dx, dy -> updateOverlayPosition(dx, dy) },
                    onToggleExpanded = { overlayController.toggleExpanded() },
                    onHide = {
                        lifecycleScope.launch {
                            overlayController.hide()
                        }
                    },
                    onToggleVoice = { handleToggleVoice() },
                    onToggleLocale = {
                        voiceLocaleTag = if (voiceLocaleTag == AndroidSpeechVoiceTranscriber.DEFAULT_LOCALE_TR) {
                            AndroidSpeechVoiceTranscriber.LOCALE_EN_US
                        } else {
                            AndroidSpeechVoiceTranscriber.DEFAULT_LOCALE_TR
                        }
                    },
                    onApprove = { requestId ->
                        overlayController.resolveApproval(requestId = requestId, approved = true)
                    },
                    onDeny = { requestId ->
                        overlayController.resolveApproval(requestId = requestId, approved = false)
                    },
                    onEmergencyStop = {
                        lifecycleScope.launch {
                            voiceTranscriber.cancel()
                        }
                        overlayController.requestEmergencyStop()
                    },
                    onToggleTextInput = { open ->
                        overlayController.setInputFocusRequested(open)
                    },
                    onSubmitTextPrompt = { prompt ->
                        overlayController.submitQuickPrompt(prompt)
                    },
                )
            }
        }

        overlayView = composeView
        wm.addView(composeView, params)
    }

    private fun detachOverlayView() {
        val view = overlayView ?: return
        overlayView = null
        runCatching { windowManager?.removeView(view) }
    }

    private fun updateOverlayPosition(dx: Float, dy: Float) {
        val params = layoutParams ?: return
        val view = overlayView ?: return
        params.x += dx.roundToInt()
        params.y += dy.roundToInt()
        runCatching { windowManager?.updateViewLayout(view, params) }
    }

    private fun updateWindowFocusability(focusable: Boolean) {
        val params = layoutParams ?: return
        val view = overlayView ?: return
        val nextFlags = computeOverlayWindowFlags(focusable)
        if (params.flags != nextFlags) {
            params.flags = nextFlags
            runCatching { windowManager?.updateViewLayout(view, params) }
        }
    }

    private fun handleToggleVoice() {
        lifecycleScope.launch {
            val current = voiceTranscriber.state.value
            if (current is VoiceState.Listening || current is VoiceState.Processing) {
                val transcript = voiceTranscriber.stopListening()
                if (!transcript.isNullOrBlank()) {
                    overlayController.submitQuickPrompt(transcript)
                }
            } else {
                voiceTranscriber.startListening(voiceLocaleTag)
            }
        }
    }

    private fun observeVoiceState() {
        lifecycleScope.launch {
            voiceTranscriber.state.collectLatest { voiceState ->
                overlayController.updateVoiceState(voiceState)
            }
        }
    }

    private fun observeInputFocusRequests() {
        lifecycleScope.launch {
            overlayController.inputFocusRequested.collectLatest { focusRequested ->
                updateWindowFocusability(focusRequested)
            }
        }
    }

    private fun observeVisibility() {
        lifecycleScope.launch {
            overlayController.state.collectLatest { overlayState ->
                if (overlayState.isVisible && Settings.canDrawOverlays(this@FloatingOverlayService)) {
                    ensureOverlayAttached()
                }
            }
        }
    }

    companion object {
        const val ACTION_SHOW = "com.antigravity.mobile.overlay.ACTION_SHOW"
        const val ACTION_HIDE = "com.antigravity.mobile.overlay.ACTION_HIDE"
        private const val CHANNEL_ID = "antigravity_overlay_channel"
        private const val NOTIFICATION_ID = 4102

        fun createDefaultOverlayLayoutParams(): WindowManager.LayoutParams {
            return WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                computeOverlayWindowFlags(focusable = false),
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 24
                y = 220
            }
        }

        fun computeOverlayWindowFlags(focusable: Boolean): Int {
            val baseFlags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            return if (focusable) {
                baseFlags
            } else {
                baseFlags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            }
        }

        fun startOverlay(context: Context) {
            val intent = Intent(context, FloatingOverlayService::class.java).apply {
                action = ACTION_SHOW
            }
            context.startForegroundService(intent)
        }

        fun stopOverlay(context: Context) {
            val intent = Intent(context, FloatingOverlayService::class.java).apply {
                action = ACTION_HIDE
            }
            context.startService(intent)
        }
    }
}
