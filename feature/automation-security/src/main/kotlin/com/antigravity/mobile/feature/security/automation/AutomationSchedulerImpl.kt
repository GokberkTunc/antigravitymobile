package com.antigravity.mobile.feature.security.automation

import com.antigravity.mobile.contracts.AutomationScheduler
import com.antigravity.mobile.contracts.ScheduledTask
import com.antigravity.mobile.contracts.ScheduledTaskRun
import com.antigravity.mobile.contracts.ScheduledTaskRunStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.util.UUID

/**
 * Production implementation of [AutomationScheduler]:
 * - Persists [ScheduledTask] and [ScheduledTaskRun] via [AutomationStore] (DataStore-backed).
 * - Dispatches recurring cron and one-shot tasks via [TaskDispatchBackend] (`AlarmManager`
 *   `setExactAndAllowWhileIdle` + `WorkManager`).
 * - Executes waking tasks under a `PARTIAL_WAKE_LOCK` guarded by `try/finally` via [WakeLockRunner].
 * - Posts completion/failure result notifications via [ScheduledTaskNotifier].
 */
class AutomationSchedulerImpl(
    private val store: AutomationStore,
    private val dispatchBackend: TaskDispatchBackend,
    private val wakeLockRunner: WakeLockRunner,
    private val taskExecutor: ScheduledTaskExecutor,
    private val notifier: ScheduledTaskNotifier,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() },
    private val runIdGenerator: () -> String = { UUID.randomUUID().toString() },
) : AutomationScheduler {

    private val stateMutex = Mutex()
    private var hydrated: Boolean = false

    private val _tasks = MutableStateFlow<List<ScheduledTask>>(emptyList())
    override val tasks: StateFlow<List<ScheduledTask>> = _tasks.asStateFlow()

    private val _recentRuns = MutableStateFlow<List<ScheduledTaskRun>>(emptyList())
    override val recentRuns: StateFlow<List<ScheduledTaskRun>> = _recentRuns.asStateFlow()

    suspend fun initialize() {
        ensureHydrated()
    }

    override suspend fun scheduleTask(task: ScheduledTask): Result<ScheduledTask> =
        withContext(ioDispatcher) {
            try {
                validateTask(task)
                val now = clock()
                val nextRun = if (task.enabled) computeNextRun(task, now) else null
                val normalized = task.copy(
                    createdAtEpochMs = if (task.createdAtEpochMs > 0L) task.createdAtEpochMs else now,
                    nextRunAtEpochMs = nextRun,
                )

                stateMutex.withLock {
                    hydrateUnlocked()
                    val updated = _tasks.value.filterNot { it.id == normalized.id } + normalized
                    _tasks.value = updated
                    store.saveTasks(updated)
                }

                if (normalized.enabled && nextRun != null) {
                    dispatchBackend.schedule(normalized, nextRun)
                } else {
                    dispatchBackend.cancel(normalized.id)
                }

                Result.success(normalized)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }

    override suspend fun cancelTask(taskId: String): Result<Unit> = withContext(ioDispatcher) {
        try {
            val existed = stateMutex.withLock {
                hydrateUnlocked()
                val current = _tasks.value
                val present = current.any { it.id == taskId }
                if (present) {
                    val remaining = current.filterNot { it.id == taskId }
                    _tasks.value = remaining
                    store.saveTasks(remaining)
                }
                present
            }

            dispatchBackend.cancel(taskId)

            if (existed) {
                Result.success(Unit)
            } else {
                Result.failure(NoSuchElementException("ScheduledTask not found: $taskId"))
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    override suspend fun setTaskEnabled(taskId: String, enabled: Boolean): Result<Unit> =
        withContext(ioDispatcher) {
            try {
                val now = clock()
                val updatedTask = stateMutex.withLock {
                    hydrateUnlocked()
                    val existing = _tasks.value.firstOrNull { it.id == taskId }
                        ?: return@withContext Result.failure(
                            NoSuchElementException("ScheduledTask not found: $taskId"),
                        )

                    val nextRun = if (enabled) computeNextRun(existing, now) else null
                    val updated = existing.copy(
                        enabled = enabled,
                        nextRunAtEpochMs = nextRun,
                    )
                    val list = _tasks.value.map { if (it.id == taskId) updated else it }
                    _tasks.value = list
                    store.saveTasks(list)
                    updated
                }

                val nextRun = updatedTask.nextRunAtEpochMs
                if (updatedTask.enabled && nextRun != null) {
                    dispatchBackend.schedule(updatedTask, nextRun)
                } else {
                    dispatchBackend.cancel(taskId)
                }

                Result.success(Unit)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }

    override suspend fun triggerNow(taskId: String): Result<ScheduledTaskRun> =
        withContext(ioDispatcher) {
            try {
                val task = stateMutex.withLock {
                    hydrateUnlocked()
                    _tasks.value.firstOrNull { it.id == taskId }
                } ?: return@withContext Result.failure(
                    NoSuchElementException("ScheduledTask not found: $taskId"),
                )

                val run = if (task.wakeDevice) {
                    wakeLockRunner.withPartialWakeLock(
                        tag = "AntigravityMobile:Task_${task.id}",
                    ) {
                        executeAndRecord(task)
                    }
                } else {
                    executeAndRecord(task)
                }

                Result.success(run)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }

    private suspend fun executeAndRecord(task: ScheduledTask): ScheduledTaskRun {
        val startedAt = clock()
        val runId = runIdGenerator()
        val runningRecord = ScheduledTaskRun(
            runId = runId,
            taskId = task.id,
            status = ScheduledTaskRunStatus.RUNNING,
            startedAtEpochMs = startedAt,
        )
        upsertRun(runningRecord)

        val finalRun = try {
            val execResult = taskExecutor.execute(task)
            val finishedAt = clock()
            execResult.fold(
                onSuccess = { summary ->
                    runningRecord.copy(
                        status = ScheduledTaskRunStatus.SUCCEEDED,
                        finishedAtEpochMs = finishedAt,
                        summary = summary,
                    )
                },
                onFailure = { err ->
                    runningRecord.copy(
                        status = ScheduledTaskRunStatus.FAILED,
                        finishedAtEpochMs = finishedAt,
                        errorMessage = err.message ?: err::class.simpleName,
                    )
                },
            )
        } catch (ce: CancellationException) {
            withContext(NonCancellable) {
                val cancelledRun = runningRecord.copy(
                    status = ScheduledTaskRunStatus.CANCELLED,
                    finishedAtEpochMs = clock(),
                    errorMessage = ce.message ?: "Cancelled",
                )
                upsertRun(cancelledRun)
            }
            throw ce
        } catch (t: Throwable) {
            runningRecord.copy(
                status = ScheduledTaskRunStatus.FAILED,
                finishedAtEpochMs = clock(),
                errorMessage = t.message ?: t::class.simpleName,
            )
        }

        upsertRun(finalRun)

        // Update task metadata and reschedule next occurrence if recurring & enabled
        val finishedAt = finalRun.finishedAtEpochMs ?: clock()
        val updatedTask = stateMutex.withLock {
            hydrateUnlocked()
            val latest = _tasks.value.firstOrNull { it.id == task.id } ?: return@withLock null
            val isRecurring = !latest.cronExpression.isNullOrBlank()
            val nextRun = if (isRecurring && latest.enabled) {
                CronNextRunCalculator.nextRunAtEpochMs(
                    cronExpression = latest.cronExpression!!,
                    afterEpochMs = finishedAt,
                    zoneId = zoneIdProvider(),
                )
            } else {
                null
            }
            val updated = latest.copy(
                lastRunAtEpochMs = finishedAt,
                nextRunAtEpochMs = nextRun,
                enabled = if (isRecurring) latest.enabled else false,
            )
            val updatedList = _tasks.value.map { if (it.id == task.id) updated else it }
            _tasks.value = updatedList
            store.saveTasks(updatedList)
            updated
        }

        if (updatedTask != null) {
            val nextRun = updatedTask.nextRunAtEpochMs
            if (updatedTask.enabled && nextRun != null) {
                dispatchBackend.schedule(updatedTask, nextRun)
            } else if (updatedTask.cronExpression == null) {
                dispatchBackend.cancel(updatedTask.id)
            }
        }

        notifier.notifyRunCompleted(updatedTask ?: task, finalRun)
        return finalRun
    }

    private suspend fun upsertRun(run: ScheduledTaskRun) {
        stateMutex.withLock {
            hydrateUnlocked()
            val updated = (listOf(run) + _recentRuns.value.filterNot { it.runId == run.runId })
                .sortedByDescending { it.startedAtEpochMs }
                .take(MAX_RECENT_RUNS)
            _recentRuns.value = updated
            store.saveRuns(updated)
        }
    }

    private suspend fun ensureHydrated() {
        if (hydrated) return
        stateMutex.withLock {
            hydrateUnlocked()
        }
    }

    private suspend fun hydrateUnlocked() {
        if (hydrated) return
        _tasks.value = store.loadTasks()
        _recentRuns.value = store.loadRuns()
        hydrated = true
    }

    private fun validateTask(task: ScheduledTask) {
        require(task.id.isNotBlank()) { "ScheduledTask.id must not be blank" }
        require(task.name.isNotBlank()) { "ScheduledTask.name must not be blank" }
        require(task.prompt.isNotBlank()) { "ScheduledTask.prompt must not be blank" }
        require(task.profileId.isNotBlank()) { "ScheduledTask.profileId must not be blank" }
        val cron = task.cronExpression
        if (cron != null) {
            CronNextRunCalculator.validate(cron)
        } else {
            val trigger = task.triggerAtEpochMs
            require(trigger != null && trigger > 0L) {
                "One-shot ScheduledTask must provide a positive triggerAtEpochMs"
            }
        }
    }

    private fun computeNextRun(task: ScheduledTask, nowEpochMs: Long): Long {
        val cron = task.cronExpression
        return if (!cron.isNullOrBlank()) {
            CronNextRunCalculator.nextRunAtEpochMs(
                cronExpression = cron,
                afterEpochMs = nowEpochMs,
                zoneId = zoneIdProvider(),
            )
        } else {
            requireNotNull(task.triggerAtEpochMs) {
                "ScheduledTask missing triggerAtEpochMs and cronExpression"
            }
        }
    }

    companion object {
        const val MAX_RECENT_RUNS = 200
    }
}
