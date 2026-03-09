package com.example.adobongkangkong.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Planner IOU entry.
 *
 * An IOU is a narrative placeholder for consumed food when nutrition is unknown.
 *
 * Notes:
 * - IOUs may optionally carry rough macro estimates for reminder/display purposes.
 * - IOUs do NOT contribute to macro totals.
 * - Macro estimate fields are nullable and may be absent.
 */
@Entity(
    tableName = "ious",
    indices = [
        Index(value = ["dateIso"]),
        Index(value = ["dateIso", "createdAtEpochMs"])
    ]
)
data class IouEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /** ISO yyyy-MM-dd. */
    val dateIso: String,

    /** User-entered narrative description (required). */
    val description: String,

    /** Optional rough macro estimate fields for reminder/UI only. */
    val estimatedCaloriesKcal: Double? = null,
    val estimatedProteinG: Double? = null,
    val estimatedCarbsG: Double? = null,
    val estimatedFatG: Double? = null,

    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)
