package com.example.adobongkangkong.domain.settings

/**
 * User preference for how strongly AK should surface body-weight logging reminders.
 *
 * This controls dashboard reminder behavior only.
 * It does not affect the stored body-weight logs themselves.
 */
enum class WeightLogReminderMode {

    /**
     * Do not show a dashboard weight-log ribbon.
     *
     * User can still open the weight tracker manually.
     */
    NO_WARNING,

    /**
     * Show a dashboard weight-log ribbon when due.
     *
     * User may dismiss the ribbon.
     * Dismissal resets the N-day counter.
     */
    REMINDER,

    /**
     * Show a persistent dashboard weight-log ribbon when due.
     *
     * User cannot dismiss the ribbon.
     * The ribbon disappears only after weight is logged.
     *
     * This should not block use of the app.
     */
    REQUIRE;

    companion object {
        fun fromStoredName(value: String?): WeightLogReminderMode =
            entries.firstOrNull { it.name == value } ?: NO_WARNING
    }
}