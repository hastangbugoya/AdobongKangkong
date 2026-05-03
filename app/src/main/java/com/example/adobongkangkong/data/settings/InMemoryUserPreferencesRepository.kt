package com.example.adobongkangkong.data.settings

import com.example.adobongkangkong.domain.settings.MealReminderIntensity
import com.example.adobongkangkong.domain.settings.UserPreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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
}