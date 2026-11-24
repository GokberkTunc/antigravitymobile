package com.antigravity.mobile.feature.security.policy

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.antigravity.mobile.contracts.ContractsJson
import com.antigravity.mobile.contracts.PermissionMode
import com.antigravity.mobile.contracts.PolicyRuleOverride
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer

data class SecurityPolicySnapshot(
    val protectedPackages: Set<String> = DefaultProtectedPackages.ALL,
    val permissionMode: PermissionMode = PermissionMode.ASK,
    val ruleOverrides: List<PolicyRuleOverride> = emptyList(),
)

interface SecurityPolicyStore {
    suspend fun loadSnapshot(): SecurityPolicySnapshot

    suspend fun saveProtectedPackages(packages: Set<String>)

    suspend fun savePermissionMode(mode: PermissionMode)

    suspend fun saveRuleOverrides(overrides: List<PolicyRuleOverride>)
}

class DataStoreSecurityPolicyStore(
    private val dataStore: DataStore<Preferences>,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SecurityPolicyStore {

    override suspend fun loadSnapshot(): SecurityPolicySnapshot = withContext(ioDispatcher) {
        val prefs = dataStore.data.first()
        val storedPackages = prefs[KEY_PROTECTED_PACKAGES]
        val packages = if (storedPackages == null) {
            DefaultProtectedPackages.ALL
        } else {
            storedPackages.toSet()
        }

        val mode = prefs[KEY_PERMISSION_MODE]?.let { raw ->
            PermissionMode.entries.firstOrNull { it.name == raw }
        } ?: PermissionMode.ASK

        val overrides = prefs[KEY_RULE_OVERRIDES]?.let { rawJson ->
            try {
                ContractsJson.decodeFromString(
                    ListSerializer(PolicyRuleOverride.serializer()),
                    rawJson,
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                emptyList()
            }
        } ?: emptyList()

        SecurityPolicySnapshot(
            protectedPackages = packages,
            permissionMode = mode,
            ruleOverrides = overrides,
        )
    }

    override suspend fun saveProtectedPackages(packages: Set<String>) {
        withContext(ioDispatcher) {
            dataStore.edit { prefs ->
                prefs[KEY_PROTECTED_PACKAGES] = packages.toSet()
            }
        }
    }

    override suspend fun savePermissionMode(mode: PermissionMode) {
        withContext(ioDispatcher) {
            dataStore.edit { prefs ->
                prefs[KEY_PERMISSION_MODE] = mode.name
            }
        }
    }

    override suspend fun saveRuleOverrides(overrides: List<PolicyRuleOverride>) {
        withContext(ioDispatcher) {
            val encoded = ContractsJson.encodeToString(
                ListSerializer(PolicyRuleOverride.serializer()),
                overrides,
            )
            dataStore.edit { prefs ->
                prefs[KEY_RULE_OVERRIDES] = encoded
            }
        }
    }

    companion object {
        internal val KEY_PROTECTED_PACKAGES = stringSetPreferencesKey("protected_packages")
        internal val KEY_PERMISSION_MODE = stringPreferencesKey("permission_mode")
        internal val KEY_RULE_OVERRIDES = stringPreferencesKey("rule_overrides_json")
    }
}
