package com.example.adobongkangkong.data.settings

import com.example.adobongkangkong.domain.settings.MealReminderIntensity
import com.example.adobongkangkong.domain.settings.UserPreferencesRepository
import com.example.adobongkangkong.domain.settings.WeightLogReminderMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.example.adobongkangkong.domain.weight.BodyWeightTrendSelectionMethod

/**
 * In-memory implementation used during development.
 *
 * Later this will be replaced with a DataStore-backed version
 * without changing the rest of the app.
 *
 * Nutrition threshold defaults mirror DataStoreUserPreferencesRepository:
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
 * Caffeine widget defaults:
 * - All 3 quick-log food slots start unconfigured.
 *
 * Weight-log reminder defaults:
 * - Mode: NO_WARNING
 * - Interval: 7 days
 * - Last reset anchor: null
 */
@Singleton
class InMemoryUserPreferencesRepository @Inject constructor() :
    UserPreferencesRepository {

    private val _lockPortrait = MutableStateFlow(true)
    override val lockPortrait: StateFlow<Boolean> = _lockPortrait

    private val _privacyLockEnabled = MutableStateFlow(false)
    override val privacyLockEnabled: StateFlow<Boolean> = _privacyLockEnabled

    private val _privacyLockTimeoutMinutes = MutableStateFlow<Int?>(null)
    override val privacyLockTimeoutMinutes: StateFlow<Int?> = _privacyLockTimeoutMinutes

    private val _mealRemindersEnabled = MutableStateFlow(false)
    override val mealRemindersEnabled: StateFlow<Boolean> = _mealRemindersEnabled

    private val _mealReminderStartMinutes = MutableStateFlow(8 * 60)
    override val mealReminderStartMinutes: StateFlow<Int> = _mealReminderStartMinutes

    private val _mealReminderIntervalMinutes = MutableStateFlow(3 * 60)
    override val mealReminderIntervalMinutes: StateFlow<Int> = _mealReminderIntervalMinutes

    private val _mealReminderEndMinutes = MutableStateFlow(21 * 60)
    override val mealReminderEndMinutes: StateFlow<Int> = _mealReminderEndMinutes

    private val _mealReminderIntensity =
        MutableStateFlow(MealReminderIntensity.GENTLE)

    override val mealReminderIntensity: StateFlow<MealReminderIntensity> =
        _mealReminderIntensity

    private val _caffeineWidgetSlot1FoodId = MutableStateFlow<Long?>(null)
    override val caffeineWidgetSlot1FoodId: StateFlow<Long?> =
        _caffeineWidgetSlot1FoodId

    private val _caffeineWidgetSlot2FoodId = MutableStateFlow<Long?>(null)
    override val caffeineWidgetSlot2FoodId: StateFlow<Long?> =
        _caffeineWidgetSlot2FoodId

    private val _caffeineWidgetSlot3FoodId = MutableStateFlow<Long?>(null)
    override val caffeineWidgetSlot3FoodId: StateFlow<Long?> =
        _caffeineWidgetSlot3FoodId

    private val _weightLogReminderMode =
        MutableStateFlow(WeightLogReminderMode.NO_WARNING)

    override val weightLogReminderMode: StateFlow<WeightLogReminderMode> =
        _weightLogReminderMode

    private val _weightLogIntervalDays = MutableStateFlow(7)
    override val weightLogIntervalDays: StateFlow<Int> =
        _weightLogIntervalDays

    private val _weightLogLastPromptResetEpochDay = MutableStateFlow<Long?>(null)
    override val weightLogLastPromptResetEpochDay: StateFlow<Long?> =
        _weightLogLastPromptResetEpochDay

    private val _productCheckSodiumLimitMg = MutableStateFlow(400.0)
    override val productCheckSodiumLimitMg: StateFlow<Double> =
        _productCheckSodiumLimitMg

    private val _productCheckSugarLimitG = MutableStateFlow(10.0)
    override val productCheckSugarLimitG: StateFlow<Double> =
        _productCheckSugarLimitG

    private val _quickAddSodiumCautionMg = MutableStateFlow(600.0)
    override val quickAddSodiumCautionMg: StateFlow<Double> =
        _quickAddSodiumCautionMg

    private val _quickAddSugarCautionG = MutableStateFlow(15.0)
    override val quickAddSugarCautionG: StateFlow<Double> =
        _quickAddSugarCautionG

    private val _plannerDailySodiumLimitMg = MutableStateFlow(2300.0)
    override val plannerDailySodiumLimitMg: StateFlow<Double> =
        _plannerDailySodiumLimitMg

    private val _plannerDailySugarLimitG = MutableStateFlow(36.0)
    override val plannerDailySugarLimitG: StateFlow<Double> =
        _plannerDailySugarLimitG

    private val _laxDayCaloriesLimitKcal = MutableStateFlow(3000.0)
    override val laxDayCaloriesLimitKcal: StateFlow<Double> =
        _laxDayCaloriesLimitKcal

    private val _laxDayProteinGoalG = MutableStateFlow(160.0)
    override val laxDayProteinGoalG: StateFlow<Double> =
        _laxDayProteinGoalG

    private val _laxDayCarbsLimitG = MutableStateFlow(400.0)
    override val laxDayCarbsLimitG: StateFlow<Double> =
        _laxDayCarbsLimitG

    private val _laxDayFatLimitG = MutableStateFlow(120.0)
    override val laxDayFatLimitG: StateFlow<Double> =
        _laxDayFatLimitG

    private val _laxDaySodiumLimitMg = MutableStateFlow(2300.0)
    override val laxDaySodiumLimitMg: StateFlow<Double> =
        _laxDaySodiumLimitMg

    private val _laxDaySugarLimitG = MutableStateFlow(80.0)
    override val laxDaySugarLimitG: StateFlow<Double> =
        _laxDaySugarLimitG

    override val weightTrendSelectionMethod =
        MutableStateFlow(BodyWeightTrendSelectionMethod.CLOSEST_TO_PREFERRED_TIME)

    override val weightTrendPreferredTimeMinutes =
        MutableStateFlow(7 * 60)

    override val weightImportMinimumGapMinutes =
        MutableStateFlow(4 * 60)

    override val weightImportDuplicateWindowMinutes =
        MutableStateFlow(30)

    override val weightImportDuplicateToleranceKg =
        MutableStateFlow(0.1)

    override fun setPrivacyLockEnabled(enabled: Boolean) {
        _privacyLockEnabled.value = enabled
    }

    override fun setPrivacyLockTimeoutMinutes(minutes: Int?) {
        _privacyLockTimeoutMinutes.value = minutes
    }

    override fun setMealRemindersEnabled(enabled: Boolean) {
        _mealRemindersEnabled.value = enabled
    }

    override fun setMealReminderStartMinutes(minutes: Int) {
        _mealReminderStartMinutes.value = minutes.coerceIn(0, 23 * 60 + 59)
    }

    override fun setMealReminderIntervalMinutes(minutes: Int) {
        _mealReminderIntervalMinutes.value = minutes.coerceAtLeast(15)
    }

    override fun setMealReminderEndMinutes(minutes: Int) {
        _mealReminderEndMinutes.value = minutes.coerceIn(0, 23 * 60 + 59)
    }

    override fun setMealReminderIntensity(intensity: MealReminderIntensity) {
        _mealReminderIntensity.value = intensity
    }

    override fun setCaffeineWidgetSlotFoodId(slotIndex: Int, foodId: Long?) {
        val safeFoodId = foodId?.takeIf { it > 0L }

        when (slotIndex) {
            1 -> _caffeineWidgetSlot1FoodId.value = safeFoodId
            2 -> _caffeineWidgetSlot2FoodId.value = safeFoodId
            3 -> _caffeineWidgetSlot3FoodId.value = safeFoodId
        }
    }

    override fun setWeightLogReminderMode(mode: WeightLogReminderMode) {
        _weightLogReminderMode.value = mode
    }

    override fun setWeightLogIntervalDays(days: Int) {
        _weightLogIntervalDays.value = days.coerceAtLeast(1)
    }

    override fun setWeightLogLastPromptResetEpochDay(epochDay: Long?) {
        _weightLogLastPromptResetEpochDay.value = epochDay
    }

    override fun setProductCheckSodiumLimitMg(value: Double) {
        _productCheckSodiumLimitMg.value = value.coerceAtLeast(0.0)
    }

    override fun setProductCheckSugarLimitG(value: Double) {
        _productCheckSugarLimitG.value = value.coerceAtLeast(0.0)
    }

    override fun setQuickAddSodiumCautionMg(value: Double) {
        _quickAddSodiumCautionMg.value = value.coerceAtLeast(0.0)
    }

    override fun setQuickAddSugarCautionG(value: Double) {
        _quickAddSugarCautionG.value = value.coerceAtLeast(0.0)
    }

    override fun setPlannerDailySodiumLimitMg(value: Double) {
        _plannerDailySodiumLimitMg.value = value.coerceAtLeast(0.0)
    }

    override fun setPlannerDailySugarLimitG(value: Double) {
        _plannerDailySugarLimitG.value = value.coerceAtLeast(0.0)
    }


    override fun setLaxDayCaloriesLimitKcal(value: Double) {
        _laxDayCaloriesLimitKcal.value = value.coerceAtLeast(0.0)
    }

    override fun setLaxDayProteinGoalG(value: Double) {
        _laxDayProteinGoalG.value = value.coerceAtLeast(0.0)
    }

    override fun setLaxDayCarbsLimitG(value: Double) {
        _laxDayCarbsLimitG.value = value.coerceAtLeast(0.0)
    }

    override fun setLaxDayFatLimitG(value: Double) {
        _laxDayFatLimitG.value = value.coerceAtLeast(0.0)
    }

    override fun setLaxDaySodiumLimitMg(value: Double) {
        _laxDaySodiumLimitMg.value = value.coerceAtLeast(0.0)
    }

    override fun setLaxDaySugarLimitG(value: Double) {
        _laxDaySugarLimitG.value = value.coerceAtLeast(0.0)
    }

    override fun setWeightTrendSelectionMethod(method: BodyWeightTrendSelectionMethod) {
        weightTrendSelectionMethod.value = method
    }

    override fun setWeightTrendPreferredTimeMinutes(minutes: Int) {
        weightTrendPreferredTimeMinutes.value = minutes.coerceIn(0, 23 * 60 + 59)
    }

    override fun setWeightImportMinimumGapMinutes(minutes: Int) {
        weightImportMinimumGapMinutes.value = minutes.coerceAtLeast(0)
    }

    override fun setWeightImportDuplicateWindowMinutes(minutes: Int) {
        weightImportDuplicateWindowMinutes.value = minutes.coerceAtLeast(0)
    }

    override fun setWeightImportDuplicateToleranceKg(value: Double) {
        weightImportDuplicateToleranceKg.value = value.coerceAtLeast(0.0)
    }

}