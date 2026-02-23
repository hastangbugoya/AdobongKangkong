package com.example.adobongkangkong.data.repository

import com.example.adobongkangkong.data.local.db.dao.PlannedMealDao
import com.example.adobongkangkong.data.local.db.entity.PlannedMealEntity
import com.example.adobongkangkong.domain.repository.PlannedMealRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class PlannedMealRepositoryImpl @Inject constructor(
    private val dao: PlannedMealDao
) : PlannedMealRepository {

    override fun observeMealsForDate(dateIso: String): Flow<List<PlannedMealEntity>> =
        dao.observeMealsForDate(dateIso)

    override fun observeMealsInRange(startDateIso: String, endDateIso: String): Flow<List<PlannedMealEntity>> =
        dao.observeMealsInRange(startDateIso, endDateIso)

    override suspend fun getById(id: Long): PlannedMealEntity? =
        dao.getById(id)

    override suspend fun insert(entity: PlannedMealEntity): Long =
        dao.insert(entity)

    override suspend fun update(entity: PlannedMealEntity) =
        dao.update(entity)

    override suspend fun delete(entity: PlannedMealEntity) =
        dao.delete(entity)

    override suspend fun deleteById(mealId: Long) =
        dao.deleteMealById(mealId)

    override suspend fun deleteForDate(dateIso: String) =
        dao.deleteMealsForDate(dateIso)

    override suspend fun getMaxSortOrderForDate(dateIso: String): Int =
        dao.getMaxSortOrderForDate(dateIso)

    override suspend fun getMealsForSeriesInRange(seriesId: Long, startDateIso: String, endDateIso: String): List<PlannedMealEntity> =
        dao.getMealsForSeriesInRange(seriesId, startDateIso, endDateIso)
}
