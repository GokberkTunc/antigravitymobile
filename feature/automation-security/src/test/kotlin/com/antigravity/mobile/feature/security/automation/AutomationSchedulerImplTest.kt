package com.antigravity.mobile.feature.security.automation

import android.os.PowerManager
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.antigravity.mobile.contracts.ScheduledTask
import com.antigravity.mobile.contracts.ScheduledTaskRun
import com.antigravity.mobile.contracts.ScheduledTaskRunStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class AutomationSchedulerImplTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class RecordingDispatchBackend : TaskDispatchBackend {
        val scheduled = mutableListOf<Pair<ScheduledTask, Long>>()
        val cancelled = mutableListOf<String>()
        val immediate = mutableListOf<String>()

        override fun schedule(task: ScheduledTask, triggerAtEpochMs: Long) {
            scheduled.add(task to triggerAtEpochMs)
        }

        override fun cancel(taskId: String) {
            cancelled.add(taskId)
        }

        override fun enqueueImmediate(taskId: String) {
            immediate.add(taskId)
        }
    }

    private class RecordingWakeLockRunner : WakeLockRunner {
        var acquireCount = 0
        var releaseCount = 0

        override suspend fun <T> withPartialWakeLock(
            tag: String,
            timeoutMs: Long,
            block: suspend () -> T,
        ): T {
            acquireCount++
            try {
                return block()
            } finally {
                releaseCount++
            }
        }
    }

    private fun createScheduler(
        scope: TestScope,
        fileName: String = "automation_test.preferences_pb",
        backend: RecordingDispatchBackend = RecordingDispatchBackend(),
        wakeLockRunner: RecordingWakeLockRunner = RecordingWakeLockRunner(),
        executor: ScheduledTaskExecutor = ScheduledTaskExecutor { Result.success("Completed ${it.name}") },
        notifier: ScheduledTaskNotifier = ScheduledTaskNotifier { _, _ -> },
        nowEpochMs: Long = Instant.parse("2026-11-24T08:00:00Z").toEpochMilli(),
    ): AutomationSchedulerImpl {
        val dispatcher = UnconfinedTestDispatcher(scope.testScheduler)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope.backgroundScope,
            produceFile = { File(tempFolder.root, fileName) },
        )
        val store = DataStoreAutomationStore(dataStore, dispatcher)
        var runCounter = 0
        return AutomationSchedulerImpl(
            store = store,
            dispatchBackend = backend,
            wakeLockRunner = wakeLockRunner,
            taskExecutor = executor,
            notifier = notifier,
            ioDispatcher = dispatcher,
            clock = { nowEpochMs },
            zoneIdProvider = { ZoneOffset.UTC },
            runIdGenerator = { "run-${++runCounter}" },
        )
    }

    @Test
    fun `CronNextRunCalculator computes next trigger times accurately and rejects invalid cron`() {
        val base = ZonedDateTime.of(2026, 11, 24, 8, 12, 30, 0, ZoneOffset.UTC)
            .toInstant().toEpochMilli()

        // Every 15 minutes -> 08:15:00 UTC
        val next15 = CronNextRunCalculator.nextRunAtEpochMs("*/15 * * * *", base, ZoneOffset.UTC)
        assertEquals(
            ZonedDateTime.of(2026, 11, 24, 8, 15, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli(),
            next15,
        )

        // Daily at 09:30 -> 09:30:00 UTC same day
        val nextDaily = CronNextRunCalculator.nextRunAtEpochMs("30 9 * * *", base, ZoneOffset.UTC)
        assertEquals(
            ZonedDateTime.of(2026, 11, 24, 9, 30, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli(),
            nextDaily,
        )

        // Invalid expressions must throw
        for (bad in listOf("* * *", "60 * * * *", "*/0 * * * *", "abc * * * *")) {
            try {
                CronNextRunCalculator.validate(bad)
                fail("Expected IllegalArgumentException for invalid cron '$bad'")
            } catch (_: IllegalArgumentException) {
                // expected
            }
        }
    }

    @Test
    fun `CRUD lifecycle - scheduleTask, setTaskEnabled, and cancelTask update StateFlow and dispatch backend`() =
        runTest {
            val backend = RecordingDispatchBackend()
            val scheduler = createScheduler(this, backend = backend)

            val task = ScheduledTask(
                id = "morning-summary",
                name = "Sabah Özeti",
                prompt = "Bugünkü bildirimleri ve takvimi özetle",
                profileId = "prof-1",
                cronExpression = "0 9 * * *",
                exactTiming = true,
                wakeDevice = true,
            )

            // Create / schedule
            val scheduledResult = scheduler.scheduleTask(task)
            assertTrue(scheduledResult.isSuccess)
            val saved = scheduledResult.getOrThrow()
            assertNotNull(saved.nextRunAtEpochMs)
            assertEquals(1, scheduler.tasks.value.size)
            assertEquals(1, backend.scheduled.size)
            assertEquals("morning-summary", backend.scheduled.first().first.id)

            // Disable -> cancels backend alarm/work and clears nextRunAtEpochMs
            val disableResult = scheduler.setTaskEnabled("morning-summary", false)
            assertTrue(disableResult.isSuccess)
            assertFalse(scheduler.tasks.value.first().enabled)
            assertNull(scheduler.tasks.value.first().nextRunAtEpochMs)
            assertTrue("morning-summary" in backend.cancelled)

            // Re-enable -> recomputes nextRunAtEpochMs and re-schedules
            val enableResult = scheduler.setTaskEnabled("morning-summary", true)
            assertTrue(enableResult.isSuccess)
            assertTrue(scheduler.tasks.value.first().enabled)
            assertNotNull(scheduler.tasks.value.first().nextRunAtEpochMs)
            assertEquals(2, backend.scheduled.size)

            // Delete / cancel
            val cancelResult = scheduler.cancelTask("morning-summary")
            assertTrue(cancelResult.isSuccess)
            assertTrue(scheduler.tasks.value.isEmpty())
            assertEquals(2, backend.cancelled.count { it == "morning-summary" })

            // Cancelling missing task fails cleanly
            assertTrue(scheduler.cancelTask("non-existent").isFailure)
        }

    @Test
    fun `scheduleTask rejects invalid cron expression`() = runTest {
        val scheduler = createScheduler(this)
        val badTask = ScheduledTask(
            id = "bad-cron",
            name = "Invalid",
            prompt = "Test",
            profileId = "prof-1",
            cronExpression = "99 99 * * *",
        )
        val result = scheduler.scheduleTask(badTask)
        assertTrue(result.isFailure)
        assertTrue(scheduler.tasks.value.isEmpty())
    }

    @Test
    fun `triggerNow runs under PARTIAL_WAKE_LOCK, persists run history, reschedules recurring cron, and notifies`() =
        runTest {
            val backend = RecordingDispatchBackend()
            val wakeLock = RecordingWakeLockRunner()
            val notifications = mutableListOf<Pair<ScheduledTask, ScheduledTaskRun>>()

            val scheduler = createScheduler(
                scope = this,
                backend = backend,
                wakeLockRunner = wakeLock,
                executor = { Result.success("3 yeni e-posta özetlendi") },
                notifier = { t, r -> notifications.add(t to r) },
            )

            val task = ScheduledTask(
                id = "hourly-check",
                name = "Saatlik Kontrol",
                prompt = "Durumu kontrol et",
                profileId = "prof-1",
                cronExpression = "0 * * * *",
                wakeDevice = true,
            )
            scheduler.scheduleTask(task)

            val runResult = scheduler.triggerNow("hourly-check")
            assertTrue(runResult.isSuccess)

            val run = runResult.getOrThrow()
            assertEquals(ScheduledTaskRunStatus.SUCCEEDED, run.status)
            assertEquals("3 yeni e-posta özetlendi", run.summary)
            assertEquals(1, wakeLock.acquireCount)
            assertEquals(1, wakeLock.releaseCount)

            // Run persisted in recentRuns
            assertEquals(1, scheduler.recentRuns.value.size)
            assertEquals(run.runId, scheduler.recentRuns.value.first().runId)

            // Recurring cron task rescheduled next occurrence
            assertEquals(2, backend.scheduled.size)

            // Result notification posted
            assertEquals(1, notifications.size)
            assertEquals("hourly-check", notifications.first().first.id)
            assertEquals(ScheduledTaskRunStatus.SUCCEEDED, notifications.first().second.status)
        }

    @Test
    fun `triggerNow releases wake lock and records FAILED run when executor throws`() = runTest {
        val wakeLock = RecordingWakeLockRunner()
        val notifications = mutableListOf<ScheduledTaskRun>()

        val scheduler = createScheduler(
            scope = this,
            wakeLockRunner = wakeLock,
            executor = { throw IllegalStateException("ACP unreachable") },
            notifier = { _, r -> notifications.add(r) },
        )

        val oneShot = ScheduledTask(
            id = "oneshot-1",
            name = "Tek Seferlik",
            prompt = "Rapor oluştur",
            profileId = "prof-1",
            triggerAtEpochMs = Instant.parse("2026-11-24T10:00:00Z").toEpochMilli(),
            wakeDevice = true,
        )
        scheduler.scheduleTask(oneShot)

        val result = scheduler.triggerNow("oneshot-1")
        assertTrue(result.isSuccess)
        val run = result.getOrThrow()
        assertEquals(ScheduledTaskRunStatus.FAILED, run.status)
        assertEquals("ACP unreachable", run.errorMessage)
        assertEquals(1, wakeLock.acquireCount)
        assertEquals(1, wakeLock.releaseCount)
        assertEquals(1, notifications.size)
        assertEquals(ScheduledTaskRunStatus.FAILED, notifications.first().status)
        // One-shot task disabled after firing
        assertFalse(scheduler.tasks.value.first().enabled)
    }

    @Test
    fun `AndroidWakeLockRunner always releases PARTIAL_WAKE_LOCK in finally even on exception`() = runTest {
        val powerManager = mockk<PowerManager>()
        val wakeLock = mockk<PowerManager.WakeLock>(relaxed = true)
        every {
            powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TestTag")
        } returns wakeLock
        every { wakeLock.isHeld } returns true

        val runner = AndroidWakeLockRunner(powerManager)

        try {
            runner.withPartialWakeLock(tag = "TestTag", timeoutMs = 30_000L) {
                throw IllegalStateException("Boom")
            }
            fail("Expected IllegalStateException to propagate")
        } catch (expected: IllegalStateException) {
            assertEquals("Boom", expected.message)
        }

        verify(exactly = 1) { wakeLock.setReferenceCounted(false) }
        verify(exactly = 1) { wakeLock.acquire(30_000L) }
        verify(exactly = 1) { wakeLock.release() }
    }
}
