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
 * - IOUs intentionally have NO nutrition fields.
 * - IOUs do NOT contribute to macro totals.
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

    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)
