package com.example.adobongkangkong.domain.settings

import kotlinx.coroutines.flow.StateFlow

/**
 * ============================================================
 * USER PREFERENCES REPOSITORY (HUMAN-READABLE OVERVIEW)
 * ============================================================
 *
 * This interface defines all user-level preferences that affect
 * app behavior globally.
 *
 * These are NOT domain entities and should NOT be stored in Room.
 * They are:
 * - lightweight
 * - user-configurable
 * - not historically important
 *
 * Backing storage is expected to be DataStore (or equivalent).
 *
 * Categories in this file:
 *
 * 1) Device / App Behavior
 *    - orientation lock
 *    - privacy lock
 *
 * 2) Notifications
 *    - meal reminders
 *
 * 3) Widget Preferences
 *    - caffeine widget quick-log food slots
 *
 * 4) Nutrition Thresholds
 *    - Product Check thresholds (scan flow)
 *    - Quick Add thresholds (logging flow)
 *
 * IMPORTANT DESIGN DISTINCTION:
 *
 * Product Check:
 *   → evaluates a food per serving BEFORE logging/import
 *
 * Quick Add:
 *   → evaluates the CURRENT LOG ENTRY amount (scaled)
 *
 * These MUST remain separate settings.
 *
 * Caffeine widget:
 *   → stores only the configured quick-log food IDs
 *   → does NOT store caffeine totals
 *   → does NOT create separate caffeine-only records
 *
 * ============================================================
 * FOR FUTURE AI / DEV ASSISTANTS
 * ============================================================
 *
 * - Do NOT move these into Room entities.
 * - Do NOT introduce relationships or joins.
 * - These are simple key-value preferences.
 *
 * Threshold semantics:
 *
 * productCheck*:
 *   - evaluated against USDA per-serving values
 *
 * quickAdd*:
 *   - evaluated against scaled nutrient totals for the current log entry
 *
 * Future expansion may include:
 * - per-meal goals
 * - per-user adaptive thresholds
 * - critical nutrient flags
 *
 * ============================================================
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

    // ============================================================
    // 🔔 Meal Reminder Preferences
    // ============================================================

    val mealRemindersEnabled: StateFlow<Boolean>

    val mealReminderStartMinutes: StateFlow<Int>

    val mealReminderIntervalMinutes: StateFlow<Int>

    val mealReminderEndMinutes: StateFlow<Int>

    val mealReminderIntensity: StateFlow<MealReminderIntensity>

    fun setMealReminderIntensity(intensity: MealReminderIntensity)

    /** Enables or disables meal reminders. */
    fun setMealRemindersEnabled(enabled: Boolean)

    /** Start time (minutes from midnight). */
    fun setMealReminderStartMinutes(minutes: Int)

    /** Interval in minutes between reminders. */
    fun setMealReminderIntervalMinutes(minutes: Int)

    /** End time (minutes from midnight). */
    fun setMealReminderEndMinutes(minutes: Int)

    // ============================================================
    // 🔒 Privacy
    // ============================================================

    /** Enables or disables the optional app privacy lock. */
    fun setPrivacyLockEnabled(enabled: Boolean)

    /** Sets the optional privacy lock timeout policy. */
    fun setPrivacyLockTimeoutMinutes(minutes: Int?)

    // ============================================================
    // ☕ Caffeine Widget Preferences
    // ============================================================

    /**
     * Food ID assigned to caffeine widget slot 1.
     *
     * Null means the slot is not configured.
     */
    val caffeineWidgetSlot1FoodId: StateFlow<Long?>

    /**
     * Food ID assigned to caffeine widget slot 2.
     *
     * Null means the slot is not configured.
     */
    val caffeineWidgetSlot2FoodId: StateFlow<Long?>

    /**
     * Food ID assigned to caffeine widget slot 3.
     *
     * Null means the slot is not configured.
     */
    val caffeineWidgetSlot3FoodId: StateFlow<Long?>

    /**
     * Sets one of the three caffeine widget food slots.
     *
     * Slot indexes are 1-based:
     * - 1 = first widget quick-log button
     * - 2 = second widget quick-log button
     * - 3 = third widget quick-log button
     *
     * Passing null clears the slot.
     */
    fun setCaffeineWidgetSlotFoodId(slotIndex: Int, foodId: Long?)

    // ============================================================
    // 🧂 Nutrition Thresholds
    // ============================================================

    /**
     * Product Check: sodium threshold per serving (mg).
     *
     * Used in:
     * - barcode scan → USDA → evaluation screen
     */
    val productCheckSodiumLimitMg: StateFlow<Double>

    /**
     * Product Check: total sugars threshold per serving (g).
     */
    val productCheckSugarLimitG: StateFlow<Double>

    /**
     * Quick Add: sodium caution threshold for a single log entry (mg).
     *
     * This is based on the *scaled amount being logged*,
     * not the base serving.
     */
    val quickAddSodiumCautionMg: StateFlow<Double>

    /**
     * Quick Add: total sugars caution threshold for a single log entry (g).
     */
    val quickAddSugarCautionG: StateFlow<Double>

    fun setProductCheckSodiumLimitMg(value: Double)

    fun setProductCheckSugarLimitG(value: Double)

    fun setQuickAddSodiumCautionMg(value: Double)

    fun setQuickAddSugarCautionG(value: Double)

    /**
     * Planner Day: sodium limit for total planned day (mg).
     */
    val plannerDailySodiumLimitMg: StateFlow<Double>

    /**
     * Planner Day: total sugar caution for planned day (g).
     */
    val plannerDailySugarLimitG: StateFlow<Double>

    fun setPlannerDailySodiumLimitMg(value: Double)

    fun setPlannerDailySugarLimitG(value: Double)
}

enum class MealReminderIntensity {
    SILENT,
    GENTLE,
    NORMAL
}