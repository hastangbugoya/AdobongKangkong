package com.example.adobongkangkong.ui.daylog.usecase

import com.example.adobongkangkong.data.local.db.dao.FoodDao
import com.example.adobongkangkong.data.local.db.dao.RecipeBatchDao
import com.example.adobongkangkong.data.local.db.dao.RecipeDao
import com.example.adobongkangkong.domain.nutrition.MacroKeys
import com.example.adobongkangkong.domain.repository.LogRepository
import com.example.adobongkangkong.ui.daylog.model.DayLogRow
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class ObserveDayLogRowsUseCase @Inject constructor(
    private val logRepository: LogRepository,
    private val foods: FoodDao,
    private val recipes: RecipeDao,
    private val recipeBatches: RecipeBatchDao
) {
    operator fun invoke(dateIso: String): Flow<List<DayLogRow>> {
        return logRepository.observeDay(dateIso)
            .map { logs ->
                val stableIds = logs.mapNotNull { it.foodStableId }.distinct()

                val stableIdToFoodId: Map<String, Long?> =
                    stableIds.associateWith { stableId ->
                        foods.getIdByStableId(stableId)
                    }

                val stableIdToRecipeFoodId: Map<String, Long?> =
                    stableIds.associateWith { stableId ->
                        val recipeId = recipes.getIdByStableId(stableId)
                            ?: return@associateWith null

                        recipes.getById(recipeId)?.foodId
                    }

                val batchIds = logs.mapNotNull { it.recipeBatchId }.distinct()

                val batchIdToFoodId: Map<Long, Long> =
                    if (batchIds.isEmpty()) {
                        emptyMap()
                    } else {
                        recipeBatches.getByIds(batchIds)
                            .associate { batch -> batch.id to batch.batchFoodId }
                    }

                logs.map { log ->
                    val n = log.nutrients

                    val bannerFoodId =
                        log.recipeBatchId?.let { batchId ->
                            batchIdToFoodId[batchId]
                        } ?: log.foodStableId?.let { stableId ->
                            stableIdToFoodId[stableId]
                                ?: stableIdToRecipeFoodId[stableId]
                        }

                    DayLogRow(
                        logId = log.id,
                        itemName = log.itemName,
                        timestamp = log.timestamp,
                        caloriesKcal = n[MacroKeys.CALORIES],
                        proteinG = n[MacroKeys.PROTEIN],
                        carbsG = n[MacroKeys.CARBS],
                        fatG = n[MacroKeys.FAT],
                        bannerFoodId = bannerFoodId,
                        mealSlot = log.mealSlot,
                    )
                }
            }
            .flowOn(Dispatchers.IO)
    }
}