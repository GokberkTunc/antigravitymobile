package com.antigravity.mobile.core.account

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import com.antigravity.mobile.contracts.AccountPlanTier
import com.antigravity.mobile.contracts.AccountProfile
import com.antigravity.mobile.contracts.ContractsJson
import com.antigravity.mobile.contracts.ProfilePaths
import com.antigravity.mobile.contracts.UsageQuota
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@Entity(tableName = "account_profiles")
data class AccountProfileEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val email: String?,
    val avatarUrl: String?,
    val isAuthenticated: Boolean,
    val isActive: Boolean,
    val pathsJson: String,
    val planTier: String,
    val remainingPercent: Double?,
    val usedRequests: Long?,
    val limitRequests: Long?,
    val resetAtEpochMs: Long?,
    val rawUsageSummary: String,
    val usageUpdatedAtEpochMs: Long,
    val createdAtEpochMs: Long,
    val lastUsedAtEpochMs: Long,
) {
    fun toDomain(): AccountProfile {
        val paths = try {
            ContractsJson.decodeFromString(ProfilePaths.serializer(), pathsJson)
        } catch (_: Exception) {
            ProfilePaths.forProfileId(id)
        }
        val tier = try {
            AccountPlanTier.valueOf(planTier)
        } catch (_: Exception) {
            AccountPlanTier.UNKNOWN
        }
        return AccountProfile(
            id = id,
            displayName = displayName,
            email = email,
            avatarUrl = avatarUrl,
            isAuthenticated = isAuthenticated,
            isActive = isActive,
            paths = paths,
            quota = UsageQuota(
                planTier = tier,
                remainingPercent = remainingPercent,
                usedRequests = usedRequests,
                limitRequests = limitRequests,
                resetAtEpochMs = resetAtEpochMs,
                rawSummary = rawUsageSummary,
                updatedAtEpochMs = usageUpdatedAtEpochMs,
            ),
            createdAtEpochMs = createdAtEpochMs,
            lastUsedAtEpochMs = lastUsedAtEpochMs,
        )
    }

    companion object {
        fun fromDomain(profile: AccountProfile): AccountProfileEntity = AccountProfileEntity(
            id = profile.id,
            displayName = profile.displayName,
            email = profile.email,
            avatarUrl = profile.avatarUrl,
            isAuthenticated = profile.isAuthenticated,
            isActive = profile.isActive,
            pathsJson = ContractsJson.encodeToString(ProfilePaths.serializer(), profile.paths),
            planTier = profile.quota.planTier.name,
            remainingPercent = profile.quota.remainingPercent,
            usedRequests = profile.quota.usedRequests,
            limitRequests = profile.quota.limitRequests,
            resetAtEpochMs = profile.quota.resetAtEpochMs,
            rawUsageSummary = profile.quota.rawSummary,
            usageUpdatedAtEpochMs = profile.quota.updatedAtEpochMs,
            createdAtEpochMs = profile.createdAtEpochMs,
            lastUsedAtEpochMs = profile.lastUsedAtEpochMs,
        )
    }
}

@Dao
interface AccountProfileDao {
    @Query("SELECT * FROM account_profiles ORDER BY createdAtEpochMs ASC")
    fun observeAll(): Flow<List<AccountProfileEntity>>

    @Query("SELECT * FROM account_profiles ORDER BY createdAtEpochMs ASC")
    suspend fun getAll(): List<AccountProfileEntity>

    @Query("SELECT * FROM account_profiles WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): AccountProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AccountProfileEntity)

    @Query("DELETE FROM account_profiles WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE account_profiles SET isActive = 0")
    suspend fun clearActiveFlags()

    @Query("UPDATE account_profiles SET isActive = 1, lastUsedAtEpochMs = :lastUsedAtEpochMs WHERE id = :id")
    suspend fun markActive(id: String, lastUsedAtEpochMs: Long)

    @Transaction
    suspend fun switchActive(id: String, lastUsedAtEpochMs: Long) {
        clearActiveFlags()
        markActive(id, lastUsedAtEpochMs)
    }
}

@Database(
    entities = [AccountProfileEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AccountDatabase : RoomDatabase() {
    abstract fun accountProfileDao(): AccountProfileDao
}

class ActiveAccountPreferences @Inject constructor(
    @AccountPrefsDataStore private val dataStore: DataStore<Preferences>,
) {
    val activeProfileId: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_ACTIVE_PROFILE_ID]
    }

    suspend fun setActiveProfileId(profileId: String?) {
        dataStore.edit { prefs ->
            if (profileId == null) {
                prefs.remove(KEY_ACTIVE_PROFILE_ID)
            } else {
                prefs[KEY_ACTIVE_PROFILE_ID] = profileId
            }
        }
    }

    suspend fun recordUsageRefresh(timestampMs: Long) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_USAGE_REFRESH_MS] = timestampMs
        }
    }

    companion object {
        private val KEY_ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")
        private val KEY_LAST_USAGE_REFRESH_MS = longPreferencesKey("last_usage_refresh_ms")
    }
}

/**
 * Persistence abstraction backed by Room + DataStore in production and swappable in unit tests.
 */
interface AccountStore {
    val profilesFlow: Flow<List<AccountProfile>>

    suspend fun listProfiles(): List<AccountProfile>

    suspend fun getProfile(profileId: String): AccountProfile?

    suspend fun upsertProfile(profile: AccountProfile)

    suspend fun deleteProfile(profileId: String)

    suspend fun setActiveProfile(profileId: String?, nowEpochMs: Long)

    suspend fun updateQuota(profileId: String, quota: UsageQuota): AccountProfile?
}

class RoomAccountStore @Inject constructor(
    private val dao: AccountProfileDao,
    private val preferences: ActiveAccountPreferences,
) : AccountStore {

    override val profilesFlow: Flow<List<AccountProfile>> = dao.observeAll().map { entities ->
        entities.map { it.toDomain() }
    }

    override suspend fun listProfiles(): List<AccountProfile> =
        dao.getAll().map { it.toDomain() }

    override suspend fun getProfile(profileId: String): AccountProfile? =
        dao.getById(profileId)?.toDomain()

    override suspend fun upsertProfile(profile: AccountProfile) {
        dao.upsert(AccountProfileEntity.fromDomain(profile))
        if (profile.isActive) {
            preferences.setActiveProfileId(profile.id)
        }
    }

    override suspend fun deleteProfile(profileId: String) {
        val existing = dao.getById(profileId)
        dao.deleteById(profileId)
        if (existing?.isActive == true) {
            preferences.setActiveProfileId(null)
        }
    }

    override suspend fun setActiveProfile(profileId: String?, nowEpochMs: Long) {
        if (profileId == null) {
            dao.clearActiveFlags()
            preferences.setActiveProfileId(null)
        } else {
            dao.switchActive(profileId, nowEpochMs)
            preferences.setActiveProfileId(profileId)
        }
    }

    override suspend fun updateQuota(profileId: String, quota: UsageQuota): AccountProfile? {
        val current = dao.getById(profileId)?.toDomain() ?: return null
        val updated = current.copy(quota = quota)
        dao.upsert(AccountProfileEntity.fromDomain(updated))
        preferences.recordUsageRefresh(quota.updatedAtEpochMs)
        return updated
    }
}
