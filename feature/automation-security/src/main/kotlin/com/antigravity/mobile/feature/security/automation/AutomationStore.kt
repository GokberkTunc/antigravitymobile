package com.antigravity.mobile.feature.security.automation

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.antigravity.mobile.contracts.ContractsJson
import com.antigravity.mobile.contracts.ScheduledTask
import com.antigravity.mobile.contracts.ScheduledTaskRun
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer

interface AutomationStore {
    suspend fun loadTasks(): List<ScheduledTask>

    suspend fun saveTasks(tasks: List<ScheduledTask>)

    suspend fun loadRuns(): List<ScheduledTaskRun>

    suspend fun saveRuns(runs: List<ScheduledTaskRun>)
}

class DataStoreAutomationStore(
    private val dataStore: DataStore<Preferences>,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AutomationStore {

    override suspend fun loadTasks(): List<ScheduledTask> = withContext(ioDispatcher) {
        val raw = dataStore.data.first()[KEY_SCHEDULED_TASKS] ?: return@withContext emptyList()
        try {
            ContractsJson.decodeFromString(ListSerializer(ScheduledTask.serializer()), raw)
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun saveTasks(tasks: List<ScheduledTask>) {
        withContext(ioDispatcher) {
            val encoded = ContractsJson.encodeToString(
                ListSerializer(ScheduledTask.serializer()),
                tasks,
            )
            dataStore.edit { prefs ->
                prefs[KEY_SCHEDULED_TASKS] = encoded
            }
        }
    }

    override suspend fun loadRuns(): List<ScheduledTaskRun> = withContext(ioDispatcher) {
        val raw = dataStore.data.first()[KEY_SCHEDULED_RUNS] ?: return@withContext emptyList()
        try {
            ContractsJson.decodeFromString(ListSerializer(ScheduledTaskRun.serializer()), raw)
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun saveRuns(runs: List<ScheduledTaskRun>) {
        withContext(ioDispatcher) {
            val encoded = ContractsJson.encodeToString(
                ListSerializer(ScheduledTaskRun.serializer()),
                runs,
            )
            dataStore.edit { prefs ->
                prefs[KEY_SCHEDULED_RUNS] = encoded
            }
        }
    }

    companion object {
        internal val KEY_SCHEDULED_TASKS = stringPreferencesKey("scheduled_tasks_json")
        internal val KEY_SCHEDULED_RUNS = stringPreferencesKey("scheduled_runs_json")
    }
}
