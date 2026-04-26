package com.example.adobongkangkong.domain.settings

import kotlinx.coroutines.flow.StateFlow

/**
 * User preferences that affect global app behavior.
 *
 * This is intentionally abstract so the backing implementation
 * can later move to DataStore without changing consumers.
 */
interface UserPreferencesRepository {

    /** Whether the app should be locked to portrait orientation. */
    val lockPortrait: StateFlow<Boolean>

    /**
     * Whether the optional app privacy lock is enabled.
     *
     * This only gates app UI with biometric/device credential unlock.
     * It does not encrypt the database or store biometric data.
     */
    val privacyLockEnabled: StateFlow<Boolean>

    /**
     * Privacy lock timeout in minutes.
     *
     * Values:
     * - null = lock only when the phone/device locks
     * - 0 = lock immediately when the app backgrounds
     * - positive value = lock after that many minutes in background
     */
    val privacyLockTimeoutMinutes: StateFlow<Int?>

    // 🔔 Meal Reminder Preferences

    val mealRemindersEnabled: StateFlow<Boolean>

    val mealReminderStartMinutes: StateFlow<Int>

    val mealReminderIntervalMinutes: StateFlow<Int>

    val mealReminderEndMinutes: StateFlow<Int>

    /** Enables or disables meal reminders. */
    fun setMealRemindersEnabled(enabled: Boolean)

    /** Start time (minutes from midnight). */
    fun setMealReminderStartMinutes(minutes: Int)

    /** Interval in minutes between reminders. */
    fun setMealReminderIntervalMinutes(minutes: Int)

    /** End time (minutes from midnight). */
    fun setMealReminderEndMinutes(minutes: Int)

    /** Enables or disables the optional app privacy lock. */
    fun setPrivacyLockEnabled(enabled: Boolean)

    /** Sets the optional privacy lock timeout policy. */
    fun setPrivacyLockTimeoutMinutes(minutes: Int?)
}