package com.antigravity.mobile.feature.security.automation

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.antigravity.mobile.contracts.AutomationScheduler
import com.antigravity.mobile.contracts.ScheduledTaskRunStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/**
 * WorkManager worker executing scheduled `/schedule` tasks triggered by WorkManager or
 * handed off from [ScheduledTaskAlarmReceiver].
 */
@HiltWorker
class ScheduledTaskWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val automationScheduler: AutomationScheduler,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID)
            ?.takeIf { it.isNotBlank() }
            ?: return Result.failure()

        return try {
            val runResult = automationScheduler.triggerNow(taskId)
            runResult.fold(
                onSuccess = { run ->
                    if (run.status == ScheduledTaskRunStatus.SUCCEEDED) {
                        Result.success()
                    } else {
                        Result.failure()
                    }
                },
                onFailure = {
                    Result.failure()
                },
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            Result.failure()
        }
    }

    companion object {
        const val KEY_TASK_ID = "task_id"
    }
}
