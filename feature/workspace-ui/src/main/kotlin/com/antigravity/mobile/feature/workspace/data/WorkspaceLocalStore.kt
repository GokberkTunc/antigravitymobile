package com.antigravity.mobile.feature.workspace.data

import android.content.Context
import com.antigravity.mobile.contracts.ContractsJson
import com.antigravity.mobile.contracts.WorkspaceProject
import com.antigravity.mobile.feature.workspace.model.AppSettings
import com.antigravity.mobile.feature.workspace.model.WorkspaceConversation
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File
import javax.inject.Singleton

@Serializable
data class WorkspaceSnapshot(
    val projects: List<WorkspaceProject> = emptyList(),
    val conversations: List<WorkspaceConversation> = emptyList(),
    val activeProjectId: String? = null,
    val activeConversationId: String? = null,
    val settings: AppSettings = AppSettings(),
    /**
     * Marks that the snapshot was explicitly persisted (distinguishing a user who genuinely
     * deleted all projects from a first-ever install where no snapshot file exists on disk).
     */
    val workspaceInitialized: Boolean = true,
)

/**
 * Lightweight local persistence boundary for workspace projects, nested conversations,
 * active selection, and user settings.
 */
interface WorkspaceLocalStore {
    suspend fun load(): WorkspaceSnapshot?
    suspend fun save(snapshot: WorkspaceSnapshot)
}

/**
 * In-memory implementation for unit tests and previews.
 */
class InMemoryWorkspaceLocalStore(
    initialSnapshot: WorkspaceSnapshot? = null,
) : WorkspaceLocalStore {
    @Volatile
    private var snapshot: WorkspaceSnapshot? = initialSnapshot

    override suspend fun load(): WorkspaceSnapshot? = snapshot

    override suspend fun save(snapshot: WorkspaceSnapshot) {
        this.snapshot = snapshot
    }
}

/**
 * Atomic JSON-file persistence backed by `context.filesDir` running exclusively on [Dispatchers.IO].
 */
class FileWorkspaceLocalStore(
    private val file: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : WorkspaceLocalStore {

    private val mutex = Mutex()

    override suspend fun load(): WorkspaceSnapshot? = withContext(ioDispatcher) {
        mutex.withLock {
            if (!file.exists()) return@withLock null
            try {
                val raw = file.readText(Charsets.UTF_8)
                if (raw.isBlank()) null else ContractsJson.decodeFromString<WorkspaceSnapshot>(raw)
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Throwable) {
                null
            }
        }
    }

    override suspend fun save(snapshot: WorkspaceSnapshot) = withContext(ioDispatcher) {
        mutex.withLock {
            try {
                file.parentFile?.mkdirs()
                val encoded = ContractsJson.encodeToString(snapshot)
                val tempFile = File(file.parentFile, "${file.name}.tmp")
                tempFile.writeText(encoded, Charsets.UTF_8)
                if (!tempFile.renameTo(file)) {
                    tempFile.copyTo(file, overwrite = true)
                    tempFile.delete()
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Throwable) {
                // Best-effort persistence: never crash UI flow on disk full / IO errors.
            }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object WorkspaceUiDataModule {

    private const val SNAPSHOT_FILE_NAME = "antigravity_workspace_state.json"

    @Provides
    @Singleton
    fun provideWorkspaceLocalStore(
        @ApplicationContext context: Context,
    ): WorkspaceLocalStore = FileWorkspaceLocalStore(
        file = File(context.filesDir, SNAPSHOT_FILE_NAME),
    )
}
