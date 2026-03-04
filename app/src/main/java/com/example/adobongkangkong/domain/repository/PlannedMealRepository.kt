package com.example.adobongkangkong.domain.repository

import com.example.adobongkangkong.data.local.db.entity.PlannedMealEntity
import kotlinx.coroutines.flow.Flow

interface PlannedMealRepository {

    fun observeMealsForDate(dateIso: String): Flow<List<PlannedMealEntity>>

    fun observeMealsInRange(
        startDateIso: String,
        endDateIso: String
    ): Flow<List<PlannedMealEntity>>

    suspend fun getById(id: Long): PlannedMealEntity?

    suspend fun insert(entity: PlannedMealEntity): Long

    suspend fun update(entity: PlannedMealEntity)

    suspend fun delete(entity: PlannedMealEntity)

    suspend fun deleteById(mealId: Long)

    suspend fun deleteForDate(dateIso: String)

    suspend fun getMaxSortOrderForDate(dateIso: String): Int

    suspend fun getMealsForSeriesInRange(seriesId: Long, startDateIso: String, endDateIso: String): List<PlannedMealEntity>

    suspend fun markLoggedIfNotYet(plannedMealId: Long, loggedAtEpochMs: Long): Boolean
}