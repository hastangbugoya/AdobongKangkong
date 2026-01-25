package com.example.adobongkangkong.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "import_runs")
data class ImportRunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,

    /** epoch millis */
    val startedAt: Long,
    /** epoch millis; null while running */
    val finishedAt: Long?,

    val source: String, // e.g. "assets:foods.csv" or "file:..."
    val totalRows: Int,

    val foodsInserted: Int,
    val nutrientsUpserted: Int,
    val foodNutrientsUpserted: Int,

    val skippedRows: Int,

    val warningCount: Int,
    val errorCount: Int
)
