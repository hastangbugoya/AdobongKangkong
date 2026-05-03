package com.example.adobongkangkong.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.adobongkangkong.domain.settings.MealReminderIntensity
import com.example.adobongkangkong.domain.settings.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val Context.userPreferencesDataStore by preferencesDataStore(
    name = "user_preferences"
)

/**
 * ============================================================
 * DATASTORE USER PREFERENCES REPOSITORY
 * ============================================================
 *
 * DataStore-backed implementation for global user preferences.
 *
 * Stores app-wide settings such as:
 * - orientation lock
 * - privacy lock policy
 * - meal logging reminder settings
 * - nutrition caution thresholds
 *
 * Nutrition threshold defaults:
 * - Product Check sodium: 400 mg per serving
 * - Product Check sugar: 10 g per serving
 * - Quick Add sodium: 600 mg per logged entry
 * - Quick Add sugar: 15 g per logged entry
 *
 * ============================================================
 * FOR FUTURE AI / DEV ASSISTANTS
 * ============================================================
 *
 * Do NOT move these settings to Room.
 *
 * These are key-value user preferences, not domain records.
 * They do not require joins, history, migrations, or relational integrity.
 *
 * Product Check thresholds are used before import/logging.
 * Quick Add thresholds are used after scaling the amount being logged.
 *
 * Keep the two threshold groups separate.
 * ============================================================
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

    override val mealRemindersEnabled: StateFlow<Boolean> =
        context.userPreferencesDataStore.data
            .map { preferences ->
                preferences[MEAL_REMINDERS_ENABLED_KEY] ?: false
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = false
            )

    override val mealReminderStartMinutes: StateFlow<Int> =
        context.userPreferencesDataStore.data
            .map { preferences ->
                preferences[MEAL_REMINDER_START_MINUTES_KEY] ?: DEFAULT_MEAL_REMINDER_START_MINUTES
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = DEFAULT_MEAL_REMINDER_START_MINUTES
            )

    override val mealReminderIntervalMinutes: StateFlow<Int> =
        context.userPreferencesDataStore.data
            .map { preferences ->
                preferences[MEAL_REMINDER_INTERVAL_MINUTES_KEY] ?: DEFAULT_MEAL_REMINDER_INTERVAL_MINUTES
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = DEFAULT_MEAL_REMINDER_INTERVAL_MINUTES
            )

    override val mealReminderEndMinutes: StateFlow<Int> =
        context.userPreferencesDataStore.data
            .map { preferences ->
                preferences[MEAL_REMINDER_END_MINUTES_KEY] ?: DEFAULT_MEAL_REMINDER_END_MINUTES
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = DEFAULT_MEAL_REMINDER_END_MINUTES
            )

    override val mealReminderIntensity: StateFlow<MealReminderIntensity> =
        context.userPreferencesDataStore.data
            .map { preferences ->
                preferences[MEAL_REMINDER_INTENSITY_KEY]
                    ?.let { raw ->
                        runCatching { MealReminderIntensity.valueOf(raw) }.getOrNull()
                    }
                    ?: MealReminderIntensity.GENTLE
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = MealReminderIntensity.GENTLE
            )

    override val productCheckSodiumLimitMg: StateFlow<Double> =
        context.userPreferencesDataStore.data
            .map { preferences ->
                preferences[PRODUCT_CHECK_SODIUM_LIMIT_MG_KEY]
                    ?: DEFAULT_PRODUCT_CHECK_SODIUM_LIMIT_MG
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = DEFAULT_PRODUCT_CHECK_SODIUM_LIMIT_MG
            )

    override val productCheckSugarLimitG: StateFlow<Double> =
        context.userPreferencesDataStore.data
            .map { preferences ->
                preferences[PRODUCT_CHECK_SUGAR_LIMIT_G_KEY]
                    ?: DEFAULT_PRODUCT_CHECK_SUGAR_LIMIT_G
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = DEFAULT_PRODUCT_CHECK_SUGAR_LIMIT_G
            )

    override val quickAddSodiumCautionMg: StateFlow<Double> =
        context.userPreferencesDataStore.data
            .map { preferences ->
                preferences[QUICK_ADD_SODIUM_CAUTION_MG_KEY]
                    ?: DEFAULT_QUICK_ADD_SODIUM_CAUTION_MG
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = DEFAULT_QUICK_ADD_SODIUM_CAUTION_MG
            )

    override val quickAddSugarCautionG: StateFlow<Double> =
        context.userPreferencesDataStore.data
            .map { preferences ->
                preferences[QUICK_ADD_SUGAR_CAUTION_G_KEY]
                    ?: DEFAULT_QUICK_ADD_SUGAR_CAUTION_G
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = DEFAULT_QUICK_ADD_SUGAR_CAUTION_G
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

    override fun setMealRemindersEnabled(enabled: Boolean) {
        scope.launch {
            context.userPreferencesDataStore.edit { preferences ->
                preferences[MEAL_REMINDERS_ENABLED_KEY] = enabled
            }
        }
    }

    override fun setMealReminderStartMinutes(minutes: Int) {
        scope.launch {
            context.userPreferencesDataStore.edit { preferences ->
                preferences[MEAL_REMINDER_START_MINUTES_KEY] = minutes.coerceIn(0, 23 * 60 + 59)
            }
        }
    }

    override fun setMealReminderIntervalMinutes(minutes: Int) {
        scope.launch {
            context.userPreferencesDataStore.edit { preferences ->
                preferences[MEAL_REMINDER_INTERVAL_MINUTES_KEY] = minutes.coerceAtLeast(15)
            }
        }
    }

    override fun setMealReminderEndMinutes(minutes: Int) {
        scope.launch {
            context.userPreferencesDataStore.edit { preferences ->
                preferences[MEAL_REMINDER_END_MINUTES_KEY] = minutes.coerceIn(0, 23 * 60 + 59)
            }
        }
    }

    override fun setMealReminderIntensity(intensity: MealReminderIntensity) {
        scope.launch {
            context.userPreferencesDataStore.edit { preferences ->
                preferences[MEAL_REMINDER_INTENSITY_KEY] = intensity.name
            }
        }
    }

    override fun setProductCheckSodiumLimitMg(value: Double) {
        scope.launch {
            context.userPreferencesDataStore.edit { preferences ->
                preferences[PRODUCT_CHECK_SODIUM_LIMIT_MG_KEY] =
                    value.coerceAtLeast(0.0)
            }
        }
    }

    override fun setProductCheckSugarLimitG(value: Double) {
        scope.launch {
            context.userPreferencesDataStore.edit { preferences ->
                preferences[PRODUCT_CHECK_SUGAR_LIMIT_G_KEY] =
                    value.coerceAtLeast(0.0)
            }
        }
    }

    override fun setQuickAddSodiumCautionMg(value: Double) {
        scope.launch {
            context.userPreferencesDataStore.edit { preferences ->
                preferences[QUICK_ADD_SODIUM_CAUTION_MG_KEY] =
                    value.coerceAtLeast(0.0)
            }
        }
    }

    override fun setQuickAddSugarCautionG(value: Double) {
        scope.launch {
            context.userPreferencesDataStore.edit { preferences ->
                preferences[QUICK_ADD_SUGAR_CAUTION_G_KEY] =
                    value.coerceAtLeast(0.0)
            }
        }
    }

    private companion object {
        const val DEFAULT_MEAL_REMINDER_START_MINUTES = 8 * 60
        const val DEFAULT_MEAL_REMINDER_INTERVAL_MINUTES = 3 * 60
        const val DEFAULT_MEAL_REMINDER_END_MINUTES = 21 * 60

        const val DEFAULT_PRODUCT_CHECK_SODIUM_LIMIT_MG = 400.0
        const val DEFAULT_PRODUCT_CHECK_SUGAR_LIMIT_G = 10.0
        const val DEFAULT_QUICK_ADD_SODIUM_CAUTION_MG = 600.0
        const val DEFAULT_QUICK_ADD_SUGAR_CAUTION_G = 15.0

        val LOCK_PORTRAIT_KEY = booleanPreferencesKey("lock_portrait")
        val PRIVACY_LOCK_ENABLED_KEY = booleanPreferencesKey("privacy_lock_enabled")
        val PRIVACY_LOCK_TIMEOUT_MINUTES_KEY = intPreferencesKey("privacy_lock_timeout_minutes")

        val MEAL_REMINDERS_ENABLED_KEY = booleanPreferencesKey("meal_reminders_enabled")
        val MEAL_REMINDER_START_MINUTES_KEY = intPreferencesKey("meal_reminder_start_minutes")
        val MEAL_REMINDER_INTERVAL_MINUTES_KEY = intPreferencesKey("meal_reminder_interval_minutes")
        val MEAL_REMINDER_END_MINUTES_KEY = intPreferencesKey("meal_reminder_end_minutes")
        val MEAL_REMINDER_INTENSITY_KEY = stringPreferencesKey("meal_reminder_intensity")

        val PRODUCT_CHECK_SODIUM_LIMIT_MG_KEY =
            doublePreferencesKey("product_check_sodium_limit_mg")
        val PRODUCT_CHECK_SUGAR_LIMIT_G_KEY =
            doublePreferencesKey("product_check_sugar_limit_g")
        val QUICK_ADD_SODIUM_CAUTION_MG_KEY =
            doublePreferencesKey("quick_add_sodium_caution_mg")
        val QUICK_ADD_SUGAR_CAUTION_G_KEY =
            doublePreferencesKey("quick_add_sugar_caution_g")
    }
}