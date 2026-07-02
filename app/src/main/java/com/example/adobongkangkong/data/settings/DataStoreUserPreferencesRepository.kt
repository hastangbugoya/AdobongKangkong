package com.example.adobongkangkong.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.adobongkangkong.domain.settings.MealReminderIntensity
import com.example.adobongkangkong.domain.settings.UserPreferencesRepository
import com.example.adobongkangkong.domain.settings.WeightLogReminderMode
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
 * - caffeine widget quick-log food slots
 * - weight-log dashboard ribbon settings
 * - nutrition caution thresholds
 *
 * Nutrition threshold defaults:
 * - Product Check sodium: 400 mg per serving
 * - Product Check sugar: 10 g per serving
 * - Quick Add sodium: 600 mg per logged entry
 * - Quick Add sugar: 15 g per logged entry
 * - Planner Day sodium: 2300 mg per planned day
 * - Planner Day total sugar: 36 g per planned day
 * - Lax rules day calories: 3000 kcal
 * - Lax rules day protein: 160 g
 * - Lax rules day carbs: 400 g
 * - Lax rules day fat: 120 g
 * - Lax rules day sodium: 2300 mg
 * - Lax rules day total sugar: 80 g
 *
 * Weight-log reminder defaults:
 * - Mode: NO_WARNING
 * - Interval: 7 days
 * - Last reset anchor: null
 *
 * ============================================================
 * FOR FUTURE DEV
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
 * Caffeine widget slot settings only store selected food IDs.
 * The widget must still read caffeine totals from normal log/nutrient data.
 *
 * Weight logs themselves are historical records and belong in Room.
 * Only the dashboard reminder/ribbon preference state belongs here.
 *
 * Keep the threshold groups separate. Lax rules day goal values are alternate
 * evaluation preferences for marked days; they do not edit stored logs or normal
 * daily nutrient targets.
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

    override val caffeineWidgetSlot1FoodId: StateFlow<Long?> =
        context.userPreferencesDataStore.data
            .map { preferences ->
                preferences[CAFFEINE_WIDGET_SLOT_1_FOOD_ID_KEY]?.takeIf { it > 0L }
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = null
            )

    override val caffeineWidgetSlot2FoodId: StateFlow<Long?> =
        context.userPreferencesDataStore.data
            .map { preferences ->
                preferences[CAFFEINE_WIDGET_SLOT_2_FOOD_ID_KEY]?.takeIf { it > 0L }
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = null
            )

    override val caffeineWidgetSlot3FoodId: StateFlow<Long?> =
        context.userPreferencesDataStore.data
            .map { preferences ->
                preferences[CAFFEINE_WIDGET_SLOT_3_FOOD_ID_KEY]?.takeIf { it > 0L }
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = null
            )

    override val weightLogReminderMode: StateFlow<WeightLogReminderMode> =
        context.userPreferencesDataStore.data
            .map { preferences ->
                WeightLogReminderMode.fromStoredName(
                    preferences[WEIGHT_LOG_REMINDER_MODE_KEY]
                )
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = WeightLogReminderMode.NO_WARNING
            )

    override val weightLogIntervalDays: StateFlow<Int> =
        context.userPreferencesDataStore.data
            .map { preferences ->
                (preferences[WEIGHT_LOG_INTERVAL_DAYS_KEY] ?: DEFAULT_WEIGHT_LOG_INTERVAL_DAYS)
                    .coerceAtLeast(1)
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = DEFAULT_WEIGHT_LOG_INTERVAL_DAYS
            )

    override val weightLogLastPromptResetEpochDay: StateFlow<Long?> =
        context.userPreferencesDataStore.data
            .map { preferences ->
                preferences[WEIGHT_LOG_LAST_PROMPT_RESET_EPOCH_DAY_KEY]
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = null
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

    override val plannerDailySodiumLimitMg: StateFlow<Double> =
        context.userPreferencesDataStore.data
            .map { preferences ->
                preferences[PLANNER_DAILY_SODIUM_LIMIT_MG_KEY]
                    ?: DEFAULT_PLANNER_DAILY_SODIUM_LIMIT_MG
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = DEFAULT_PLANNER_DAILY_SODIUM_LIMIT_MG
            )

    override val plannerDailySugarLimitG: StateFlow<Double> =
        context.userPreferencesDataStore.data
            .map { preferences ->
                preferences[PLANNER_DAILY_SUGAR_LIMIT_G_KEY]
                    ?: DEFAULT_PLANNER_DAILY_SUGAR_LIMIT_G
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = DEFAULT_PLANNER_DAILY_SUGAR_LIMIT_G
            )

    override val laxDayCaloriesLimitKcal: StateFlow<Double> =
        context.userPreferencesDataStore.data
            .map { preferences ->
                preferences[LAX_DAY_CALORIES_LIMIT_KCAL_KEY]
                    ?: DEFAULT_LAX_DAY_CALORIES_LIMIT_KCAL
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = DEFAULT_LAX_DAY_CALORIES_LIMIT_KCAL
            )

    override val laxDayProteinGoalG: StateFlow<Double> =
        context.userPreferencesDataStore.data
            .map { preferences ->
                preferences[LAX_DAY_PROTEIN_GOAL_G_KEY]
                    ?: DEFAULT_LAX_DAY_PROTEIN_GOAL_G
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = DEFAULT_LAX_DAY_PROTEIN_GOAL_G
            )

    override val laxDayCarbsLimitG: StateFlow<Double> =
        context.userPreferencesDataStore.data
            .map { preferences ->
                preferences[LAX_DAY_CARBS_LIMIT_G_KEY]
                    ?: DEFAULT_LAX_DAY_CARBS_LIMIT_G
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = DEFAULT_LAX_DAY_CARBS_LIMIT_G
            )

    override val laxDayFatLimitG: StateFlow<Double> =
        context.userPreferencesDataStore.data
            .map { preferences ->
                preferences[LAX_DAY_FAT_LIMIT_G_KEY]
                    ?: DEFAULT_LAX_DAY_FAT_LIMIT_G
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = DEFAULT_LAX_DAY_FAT_LIMIT_G
            )

    override val laxDaySodiumLimitMg: StateFlow<Double> =
        context.userPreferencesDataStore.data
            .map { preferences ->
                preferences[LAX_DAY_SODIUM_LIMIT_MG_KEY]
                    ?: DEFAULT_LAX_DAY_SODIUM_LIMIT_MG
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = DEFAULT_LAX_DAY_SODIUM_LIMIT_MG
            )

    override val laxDaySugarLimitG: StateFlow<Double> =
        context.userPreferencesDataStore.data
            .map { preferences ->
                preferences[LAX_DAY_SUGAR_LIMIT_G_KEY]
                    ?: DEFAULT_LAX_DAY_SUGAR_LIMIT_G
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = DEFAULT_LAX_DAY_SUGAR_LIMIT_G
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

    override fun setCaffeineWidgetSlotFoodId(slotIndex: Int, foodId: Long?) {
        val key = when (slotIndex) {
            1 -> CAFFEINE_WIDGET_SLOT_1_FOOD_ID_KEY
            2 -> CAFFEINE_WIDGET_SLOT_2_FOOD_ID_KEY
            3 -> CAFFEINE_WIDGET_SLOT_3_FOOD_ID_KEY
            else -> return
        }

        scope.launch {
            context.userPreferencesDataStore.edit { preferences ->
                val safeFoodId = foodId?.takeIf { it > 0L }
                if (safeFoodId == null) {
                    preferences.remove(key)
                } else {
                    preferences[key] = safeFoodId
                }
            }
        }
    }

    override fun setWeightLogReminderMode(mode: WeightLogReminderMode) {
        scope.launch {
            context.userPreferencesDataStore.edit { preferences ->
                preferences[WEIGHT_LOG_REMINDER_MODE_KEY] = mode.name
            }
        }
    }

    override fun setWeightLogIntervalDays(days: Int) {
        scope.launch {
            context.userPreferencesDataStore.edit { preferences ->
                preferences[WEIGHT_LOG_INTERVAL_DAYS_KEY] = days.coerceAtLeast(1)
            }
        }
    }

    override fun setWeightLogLastPromptResetEpochDay(epochDay: Long?) {
        scope.launch {
            context.userPreferencesDataStore.edit { preferences ->
                if (epochDay == null) {
                    preferences.remove(WEIGHT_LOG_LAST_PROMPT_RESET_EPOCH_DAY_KEY)
                } else {
                    preferences[WEIGHT_LOG_LAST_PROMPT_RESET_EPOCH_DAY_KEY] = epochDay
                }
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

    override fun setPlannerDailySodiumLimitMg(value: Double) {
        scope.launch {
            context.userPreferencesDataStore.edit { preferences ->
                preferences[PLANNER_DAILY_SODIUM_LIMIT_MG_KEY] =
                    value.coerceAtLeast(0.0)
            }
        }
    }

    override fun setPlannerDailySugarLimitG(value: Double) {
        scope.launch {
            context.userPreferencesDataStore.edit { preferences ->
                preferences[PLANNER_DAILY_SUGAR_LIMIT_G_KEY] =
                    value.coerceAtLeast(0.0)
            }
        }
    }

    override fun setLaxDayCaloriesLimitKcal(value: Double) {
        scope.launch {
            context.userPreferencesDataStore.edit { preferences ->
                preferences[LAX_DAY_CALORIES_LIMIT_KCAL_KEY] =
                    value.coerceAtLeast(0.0)
            }
        }
    }

    override fun setLaxDayProteinGoalG(value: Double) {
        scope.launch {
            context.userPreferencesDataStore.edit { preferences ->
                preferences[LAX_DAY_PROTEIN_GOAL_G_KEY] =
                    value.coerceAtLeast(0.0)
            }
        }
    }

    override fun setLaxDayCarbsLimitG(value: Double) {
        scope.launch {
            context.userPreferencesDataStore.edit { preferences ->
                preferences[LAX_DAY_CARBS_LIMIT_G_KEY] =
                    value.coerceAtLeast(0.0)
            }
        }
    }

    override fun setLaxDayFatLimitG(value: Double) {
        scope.launch {
            context.userPreferencesDataStore.edit { preferences ->
                preferences[LAX_DAY_FAT_LIMIT_G_KEY] =
                    value.coerceAtLeast(0.0)
            }
        }
    }

    override fun setLaxDaySodiumLimitMg(value: Double) {
        scope.launch {
            context.userPreferencesDataStore.edit { preferences ->
                preferences[LAX_DAY_SODIUM_LIMIT_MG_KEY] =
                    value.coerceAtLeast(0.0)
            }
        }
    }

    override fun setLaxDaySugarLimitG(value: Double) {
        scope.launch {
            context.userPreferencesDataStore.edit { preferences ->
                preferences[LAX_DAY_SUGAR_LIMIT_G_KEY] =
                    value.coerceAtLeast(0.0)
            }
        }
    }

    private companion object {
        const val DEFAULT_MEAL_REMINDER_START_MINUTES = 8 * 60
        const val DEFAULT_MEAL_REMINDER_INTERVAL_MINUTES = 3 * 60
        const val DEFAULT_MEAL_REMINDER_END_MINUTES = 21 * 60

        const val DEFAULT_WEIGHT_LOG_INTERVAL_DAYS = 7

        const val DEFAULT_PRODUCT_CHECK_SODIUM_LIMIT_MG = 400.0
        const val DEFAULT_PRODUCT_CHECK_SUGAR_LIMIT_G = 10.0
        const val DEFAULT_QUICK_ADD_SODIUM_CAUTION_MG = 600.0
        const val DEFAULT_QUICK_ADD_SUGAR_CAUTION_G = 15.0

        const val DEFAULT_PLANNER_DAILY_SODIUM_LIMIT_MG = 2300.0
        const val DEFAULT_PLANNER_DAILY_SUGAR_LIMIT_G = 36.0

        const val DEFAULT_LAX_DAY_CALORIES_LIMIT_KCAL = 3000.0
        const val DEFAULT_LAX_DAY_PROTEIN_GOAL_G = 160.0
        const val DEFAULT_LAX_DAY_CARBS_LIMIT_G = 400.0
        const val DEFAULT_LAX_DAY_FAT_LIMIT_G = 120.0
        const val DEFAULT_LAX_DAY_SODIUM_LIMIT_MG = 2300.0
        const val DEFAULT_LAX_DAY_SUGAR_LIMIT_G = 80.0

        val LOCK_PORTRAIT_KEY = booleanPreferencesKey("lock_portrait")
        val PRIVACY_LOCK_ENABLED_KEY = booleanPreferencesKey("privacy_lock_enabled")
        val PRIVACY_LOCK_TIMEOUT_MINUTES_KEY = intPreferencesKey("privacy_lock_timeout_minutes")

        val MEAL_REMINDERS_ENABLED_KEY = booleanPreferencesKey("meal_reminders_enabled")
        val MEAL_REMINDER_START_MINUTES_KEY = intPreferencesKey("meal_reminder_start_minutes")
        val MEAL_REMINDER_INTERVAL_MINUTES_KEY = intPreferencesKey("meal_reminder_interval_minutes")
        val MEAL_REMINDER_END_MINUTES_KEY = intPreferencesKey("meal_reminder_end_minutes")
        val MEAL_REMINDER_INTENSITY_KEY = stringPreferencesKey("meal_reminder_intensity")

        val CAFFEINE_WIDGET_SLOT_1_FOOD_ID_KEY =
            longPreferencesKey("caffeine_widget_slot_1_food_id")
        val CAFFEINE_WIDGET_SLOT_2_FOOD_ID_KEY =
            longPreferencesKey("caffeine_widget_slot_2_food_id")
        val CAFFEINE_WIDGET_SLOT_3_FOOD_ID_KEY =
            longPreferencesKey("caffeine_widget_slot_3_food_id")

        val WEIGHT_LOG_REMINDER_MODE_KEY =
            stringPreferencesKey("weight_log_reminder_mode")
        val WEIGHT_LOG_INTERVAL_DAYS_KEY =
            intPreferencesKey("weight_log_interval_days")
        val WEIGHT_LOG_LAST_PROMPT_RESET_EPOCH_DAY_KEY =
            longPreferencesKey("weight_log_last_prompt_reset_epoch_day")

        val PRODUCT_CHECK_SODIUM_LIMIT_MG_KEY =
            doublePreferencesKey("product_check_sodium_limit_mg")
        val PRODUCT_CHECK_SUGAR_LIMIT_G_KEY =
            doublePreferencesKey("product_check_sugar_limit_g")
        val QUICK_ADD_SODIUM_CAUTION_MG_KEY =
            doublePreferencesKey("quick_add_sodium_caution_mg")
        val QUICK_ADD_SUGAR_CAUTION_G_KEY =
            doublePreferencesKey("quick_add_sugar_caution_g")

        val PLANNER_DAILY_SODIUM_LIMIT_MG_KEY =
            doublePreferencesKey("planner_daily_sodium_limit_mg")
        val PLANNER_DAILY_SUGAR_LIMIT_G_KEY =
            doublePreferencesKey("planner_daily_sugar_limit_g")

        val LAX_DAY_CALORIES_LIMIT_KCAL_KEY =
            doublePreferencesKey("lax_day_calories_limit_kcal")
        val LAX_DAY_PROTEIN_GOAL_G_KEY =
            doublePreferencesKey("lax_day_protein_goal_g")
        val LAX_DAY_CARBS_LIMIT_G_KEY =
            doublePreferencesKey("lax_day_carbs_limit_g")
        val LAX_DAY_FAT_LIMIT_G_KEY =
            doublePreferencesKey("lax_day_fat_limit_g")
        val LAX_DAY_SODIUM_LIMIT_MG_KEY =
            doublePreferencesKey("lax_day_sodium_limit_mg")
        val LAX_DAY_SUGAR_LIMIT_G_KEY =
            doublePreferencesKey("lax_day_sugar_limit_g")
    }
}