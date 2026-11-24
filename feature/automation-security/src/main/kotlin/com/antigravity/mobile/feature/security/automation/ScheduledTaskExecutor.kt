package com.antigravity.mobile.feature.security.automation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.antigravity.mobile.contracts.AcpClient
import com.antigravity.mobile.contracts.AcpSessionConfig
import com.antigravity.mobile.contracts.ScheduledTask
import com.antigravity.mobile.contracts.ScheduledTaskRun
import com.antigravity.mobile.contracts.ScheduledTaskRunStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun interface ScheduledTaskExecutor {
    suspend fun execute(task: ScheduledTask): Result<String>
}

class AcpScheduledTaskExecutor(
    private val acpClient: AcpClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ScheduledTaskExecutor {

    override suspend fun execute(task: ScheduledTask): Result<String> = withContext(ioDispatcher) {
        try {
            val existingSession = acpClient.activeSession.value
                ?.takeIf { it.profileId == task.profileId }

            val sessionInfo = if (existingSession != null) {
                existingSession
            } else {
                acpClient.startSession(
                    AcpSessionConfig(profileId = task.profileId),
                ).getOrElse { err ->
                    return@withContext Result.failure(err)
                }
            }

            acpClient.sendMessage(
                sessionId = sessionInfo.sessionId,
                prompt = task.prompt,
            ).getOrElse { err ->
                return@withContext Result.failure(err)
            }

            Result.success("Dispatched scheduled prompt to ACP session ${sessionInfo.sessionId}")
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }
}

fun interface ScheduledTaskNotifier {
    fun notifyRunCompleted(task: ScheduledTask, run: ScheduledTaskRun)
}

class AndroidScheduledTaskNotifier(
    private val context: Context,
) : ScheduledTaskNotifier {

    override fun notifyRunCompleted(task: ScheduledTask, run: ScheduledTaskRun) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Zamanlanmış Görevler (/schedule)",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Antigravity zamanlanmış otomasyon sonuç bildirimleri"
            }
            manager.createNotificationChannel(channel)
        }

        val succeeded = run.status == ScheduledTaskRunStatus.SUCCEEDED
        val title = if (succeeded) {
            "Görev tamamlandı: ${task.name}"
        } else {
            "Görev başarısız: ${task.name}"
        }
        val body = when {
            succeeded && run.summary.isNotBlank() -> run.summary
            !succeeded && !run.errorMessage.isNullOrBlank() -> run.errorMessage!!
            succeeded -> "Zamanlanmış görev başarıyla yürütüldü."
            else -> "Zamanlanmış görev yürütülürken hata oluştu."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            manager.notify(notificationId(task.id), notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS might not be granted yet on Android 13+.
        }
    }

    companion object {
        const val CHANNEL_ID = "antigravity_scheduled_tasks"

        fun notificationId(taskId: String): Int = 55_000 + (taskId.hashCode() and 0x7FFF)
    }
}
