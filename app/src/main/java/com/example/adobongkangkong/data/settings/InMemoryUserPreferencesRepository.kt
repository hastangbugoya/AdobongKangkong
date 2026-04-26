package com.example.adobongkangkong.data.settings

import com.example.adobongkangkong.domain.settings.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory implementation used during development.
 *
 * Later this will be replaced with a DataStore-backed version
 * without changing the rest of the app.
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
}