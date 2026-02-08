package com.example.adobongkangkong.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * DB persistence for a logged consumption event.
 *
 * Snapshot-at-log-time:
 * - nutrientsJson stores the FINAL resolved totals for this log event (frozen).
 * - amount + unit preserves the user's input intent (grams vs servings vs item).
 * - recipeBatchId optionally ties cooked-gram logs to a specific cooked yield context.
 */
@Entity(
    tableName = "log_entries",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["foodStableId"]),
        Index(value = ["recipeBatchId"])
    ]
)
data class LogEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,

    val timestamp: Instant,

    /** Display name captured at log time (resilient to rename/deletion). */
    val itemName: String,

    /** Stable ID for export/import reconciliation, nullable for ad-hoc entries. */
    val foodStableId: String?,

    /** User input */
    val amount: Double = 1.0,

    /**
     * Stored as TEXT via TypeConverter.
     * Default ITEM is used for legacy rows.
     */
    val unit: com.example.adobongkangkong.domain.model.LogUnit =
        com.example.adobongkangkong.domain.model.LogUnit.ITEM,

    /**
     * Optional link to a cooked batch (only meaningful for recipe-based logs).
     * If present, this tells you WHICH cooked yield context was used.
     */
    val recipeBatchId: Long? = null,

    /**
     * Optional explanatory field:
     * gramsPerServingCooked = batch.cookedYieldGrams / servingsYieldUsed
     * Useful when unit=SERVING (or for UI transparency).
     */
    val gramsPerServingCooked: Double? = null,

    /** Final totals for this log event as JSON (your NutrientMap totals). */
    val nutrientsJson: String,

    val mealSlot: MealSlot? = null
)
