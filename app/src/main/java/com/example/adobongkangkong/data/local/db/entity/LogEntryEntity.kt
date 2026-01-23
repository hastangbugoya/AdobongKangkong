package com.example.adobongkangkong.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "log_entries", indices = [Index("timestamp")])
data class LogEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val timestamp: Instant,               // epoch millis

    val foodId: Long,                  // food or recipe
    val servings: Double,                // e.g. 1.0 serving OR grams
)