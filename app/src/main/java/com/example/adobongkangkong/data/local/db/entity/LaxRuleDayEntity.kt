package com.example.adobongkangkong.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Marks one calendar date as a lax rules day.
 *
 * A lax rules day is a user-selected date where normal goal scoring may later be
 * replaced by alternate goal rules, such as higher calorie limits while still
 * watching sodium or sugar. The marker is intentionally stored separately from
 * meal logs so logged foods and nutrient snapshots remain honest and unchanged.
 *
 * Multiple dates in the same calendar week may be marked. The app may warn the
 * user when that happens, but persistence should allow it so users are not pushed
 * toward hiding intake or entering fake values.
 *
 * @property dateEpochDay Local calendar date stored as [java.time.LocalDate.toEpochDay].
 * @property createdAtEpochMillis Wall-clock creation timestamp for audit/debug context.
 * @property updatedAtEpochMillis Wall-clock update timestamp for future metadata changes.
 */
@Entity(tableName = "lax_rule_days")
data class LaxRuleDayEntity(
    @PrimaryKey val dateEpochDay: Long,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
