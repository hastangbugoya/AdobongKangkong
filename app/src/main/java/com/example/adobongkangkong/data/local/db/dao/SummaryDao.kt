package com.example.adobongkangkong.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.Instant

data class NutrientTotalRow(
    val nutrientCode: String,   // e.g. "CALORIES"
    val unit: String,           // "kcal", "g", "mg"
    val totalAmount: Double
)

@Dao
interface SummaryDao {

    @Query("""
        SELECT n.code AS nutrientCode,
               n.unit AS unit,
               SUM(fn.nutrientAmountPerBasis * le.servings) AS totalAmount
        FROM log_entries le
        JOIN food_nutrients fn ON fn.foodId = le.foodId
        JOIN nutrients n ON n.id = fn.nutrientId
        WHERE le.timestamp >= :startInclusive AND le.timestamp < :endExclusive
        GROUP BY n.code, n.unit
    """)
    fun observeTotalsByNutrientCode(
        startInclusive: Instant,
        endExclusive: Instant
    ): Flow<List<NutrientTotalRow>>
}
