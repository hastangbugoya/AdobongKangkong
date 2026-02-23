package com.example.adobongkangkong.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "planned_series_slot_rules",
    foreignKeys = [
        ForeignKey(
            entity = PlannedSeriesEntity::class,
            parentColumns = ["id"],
            childColumns = ["seriesId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["seriesId"]),
        Index(value = ["seriesId", "weekday"]),
        Index(value = ["seriesId", "weekday", "slot"])
    ]
)
data class PlannedSeriesSlotRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    val seriesId: Long,

    /** 1=Mon .. 7=Sun */
    val weekday: Int,

    /** BREAKFAST | LUNCH | DINNER | SNACK | CUSTOM */
    val slot: MealSlot,

    /** Only meaningful when slot == CUSTOM */
    val customLabel: String? = null,

    val createdAtEpochMs: Long
)