package com.example.adobongkangkong.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * DB persistence for a logged consumption event.
 *
 * Snapshot-at-log-time:
 * - We store stable ID + name + computed totals so logs are resilient to food deletion/changes.
 */
@Entity(tableName = "log_entries")
data class LogEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val timestamp: Instant,
    val itemName: String,
    val foodStableId: String?,     // matches domain, nullable
    val nutrientsJson: String      // NutrientMap totals as JSON
)
