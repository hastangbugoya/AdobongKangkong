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
        Index(value = ["logDateIso"]),          // ✅ NEW
        Index(value = ["foodStableId"]),
        Index(value = ["recipeBatchId"])
    ]
)
data class LogEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,

    val timestamp: Instant,

    // ✅ NEW: “counts for” day bucket (yyyy-MM-dd)
    val logDateIso: String,

    val itemName: String,
    val foodStableId: String?,

    val amount: Double = 1.0,
    val unit: com.example.adobongkangkong.domain.model.LogUnit =
        com.example.adobongkangkong.domain.model.LogUnit.ITEM,

    val recipeBatchId: Long? = null,
    val gramsPerServingCooked: Double? = null,

    val nutrientsJson: String,
    val mealSlot: MealSlot? = null
)