package com.antigravity.mobile.feature.security.automation

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.antigravity.mobile.contracts.ScheduledTask
import java.util.concurrent.TimeUnit

interface TaskDispatchBackend {
    fun schedule(task: ScheduledTask, triggerAtEpochMs: Long)

    fun cancel(taskId: String)

    fun enqueueImmediate(taskId: String)
}

/**
 * Coordinates Android `AlarmManager` (`setExactAndAllowWhileIdle` for Doze-piercing exact wakeups)
 * and `WorkManager` (reliable background execution) for scheduled tasks.
 */
class AndroidTaskDispatchBackend(
    private val context: Context,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) : TaskDispatchBackend {

    override fun schedule(task: ScheduledTask, triggerAtEpochMs: Long) {
        val workManager = WorkManager.getInstance(context)
        val now = nowProvider()
        val delayMs = (triggerAtEpochMs - now).coerceAtLeast(0L)

        if (task.exactTiming || task.wakeDevice) {
            // Cancel any delayed WorkManager instance; AlarmManager will wake the device and hand off
            workManager.cancelUniqueWork(uniqueWorkName(task.id))
            scheduleExactAlarm(task.id, triggerAtEpochMs)
        } else {
            cancelAlarm(task.id)
            val request = OneTimeWorkRequestBuilder<ScheduledTaskWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(ScheduledTaskWorker.KEY_TASK_ID to task.id))
                .addTag(WORK_TAG_PREFIX + task.id)
                .build()
            workManager.enqueueUniqueWork(
                uniqueWorkName(task.id),
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }

    override fun cancel(taskId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName(taskId))
        cancelAlarm(taskId)
    }

    override fun enqueueImmediate(taskId: String) {
        val request = OneTimeWorkRequestBuilder<ScheduledTaskWorker>()
            .setInputData(workDataOf(ScheduledTaskWorker.KEY_TASK_ID to taskId))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(WORK_TAG_PREFIX + taskId)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueWorkName(taskId),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private fun scheduleExactAlarm(taskId: String, triggerAtEpochMs: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return
        val pendingIntent = buildAlarmPendingIntent(
            taskId = taskId,
            flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return

        val canUseExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()

        try {
            if (canUseExact) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtEpochMs,
                    pendingIntent,
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtEpochMs,
                    pendingIntent,
                )
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtEpochMs,
                pendingIntent,
            )
        }
    }

    private fun cancelAlarm(taskId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return
        val pendingIntent = buildAlarmPendingIntent(
            taskId = taskId,
            flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun buildAlarmPendingIntent(taskId: String, flags: Int): PendingIntent? {
        val intent = Intent(context, ScheduledTaskAlarmReceiver::class.java).apply {
            action = ScheduledTaskAlarmReceiver.ACTION_RUN_SCHEDULED_TASK
            putExtra(ScheduledTaskAlarmReceiver.EXTRA_TASK_ID, taskId)
        }
        return PendingIntent.getBroadcast(
            context,
            alarmRequestCode(taskId),
            intent,
            flags,
        )
    }

    companion object {
        const val WORK_TAG_PREFIX = "antigravity_sched_task_"

        fun uniqueWorkName(taskId: String): String = "ag_scheduled_task_$taskId"

        fun alarmRequestCode(taskId: String): Int = 50_000 + (taskId.hashCode() and 0x7FFF)
    }
}
