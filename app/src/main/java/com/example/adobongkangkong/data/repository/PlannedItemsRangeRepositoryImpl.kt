package com.example.adobongkangkong.data.repository

import com.example.adobongkangkong.data.local.db.dao.PlannedItemDao
import com.example.adobongkangkong.data.local.db.dao.PlannedMealDao
import com.example.adobongkangkong.domain.repository.PlannedItemWithDateRow
import com.example.adobongkangkong.domain.repository.PlannedItemsRangeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class PlannedItemsRangeRepositoryImpl @Inject constructor(
    private val plannedMealDao: PlannedMealDao,
    private val plannedItemDao: PlannedItemDao,
) : PlannedItemsRangeRepository {

    override fun observePlannedItemsInRange(
        startIso: String,
        endIso: String
    ): Flow<List<PlannedItemWithDateRow>> {
        return plannedMealDao
            .observeMealsInRange(startIso, endIso)
            .flatMapLatest { meals ->
                flow {
                    val rows = buildList {
                        for (meal in meals) {
                            val items = plannedItemDao.getItemsForMeal(meal.id)
                            for (it in items) {
                                add(
                                    PlannedItemWithDateRow(
                                        dateIso = meal.date,
                                        type = it.type,
                                        refId = it.refId,
                                        grams = it.grams,
                                        servings = it.servings
                                    )
                                )
                            }
                        }
                    }
                    emit(rows)
                }
            }
    }
}