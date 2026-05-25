package com.example.adobongkangkong.domain.weight

import java.time.LocalDate

/**
 * Domain model for one persisted body-weight log.
 *
 * MVP rule:
 * - One body-weight log per calendar date.
 * - Logging weight for an existing date updates that date's row.
 *
 * This model is intentionally separate from dashboard reminder settings.
 * Reminder mode/interval/dismiss state belongs in UserPreferences/DataStore.
 */
data class BodyWeightLog(
    val id: Long = 0L,
    val date: LocalDate,
    val weight: Double,
    val unit: BodyWeightUnit,
    val note: String? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)

/**
 * Supported body-weight units.
 *
 * Stored as stable string codes in Room through BodyWeightLogEntity.unit.
 */
enum class BodyWeightUnit(
    val code: String,
    val symbol: String
) {
    LB("LB", "lb"),
    KG("KG", "kg");

    companion object {
        fun fromCode(code: String): BodyWeightUnit =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: LB
    }
}
