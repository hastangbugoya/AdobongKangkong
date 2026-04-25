package com.example.adobongkangkong.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.adobongkangkong.domain.settings.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userPreferencesDataStore by preferencesDataStore(
    name = "user_preferences"
)

/**
 * DataStore-backed implementation for global user preferences.
 *
 * This persists app-wide settings such as orientation lock and privacy lock policy.
 */
@Singleton
class DataStoreUserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : UserPreferencesRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val lockPortrait: StateFlow<Boolean> =
        context.userPreferencesDataStore.data
            .map { preferences ->
                preferences[LOCK_PORTRAIT_KEY] ?: true
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = true
            )

    override val privacyLockEnabled: StateFlow<Boolean> =
        context.userPreferencesDataStore.data
            .map { preferences ->
                preferences[PRIVACY_LOCK_ENABLED_KEY] ?: false
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = false
            )

    override val privacyLockTimeoutMinutes: StateFlow<Int?> =
        context.userPreferencesDataStore.data
            .map { preferences ->
                preferences[PRIVACY_LOCK_TIMEOUT_MINUTES_KEY]?.takeIf { it >= 0 }
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = null
            )

    override fun setPrivacyLockEnabled(enabled: Boolean) {
        scope.launch {
            context.userPreferencesDataStore.edit { preferences ->
                preferences[PRIVACY_LOCK_ENABLED_KEY] = enabled
            }
        }
    }

    override fun setPrivacyLockTimeoutMinutes(minutes: Int?) {
        scope.launch {
            context.userPreferencesDataStore.edit { preferences ->
                if (minutes == null) {
                    preferences.remove(PRIVACY_LOCK_TIMEOUT_MINUTES_KEY)
                } else {
                    preferences[PRIVACY_LOCK_TIMEOUT_MINUTES_KEY] = minutes.coerceAtLeast(0)
                }
            }
        }
    }


    private companion object {
        val LOCK_PORTRAIT_KEY = booleanPreferencesKey("lock_portrait")
        val PRIVACY_LOCK_ENABLED_KEY = booleanPreferencesKey("privacy_lock_enabled")
        val PRIVACY_LOCK_TIMEOUT_MINUTES_KEY = intPreferencesKey("privacy_lock_timeout_minutes")
    }
}