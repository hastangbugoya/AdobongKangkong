package com.example.adobongkangkong.ui.daylog.usecase

import com.example.adobongkangkong.data.local.db.dao.FoodDao
import com.example.adobongkangkong.data.local.db.dao.RecipeBatchDao
import com.example.adobongkangkong.domain.nutrition.MacroKeys
import com.example.adobongkangkong.domain.repository.LogRepository
import com.example.adobongkangkong.ui.daylog.model.DayLogRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Observes Day Log rows for a single ISO day (yyyy-MM-dd).
 *
 * IMPORTANT:
 * - This use case is day-based and MUST be driven by `logDateIso`.
 * - It must NOT use timestamp bounds for day membership.
 */
class ObserveDayLogRowsUseCase @Inject constructor(
    private val logRepository: LogRepository,
    private val foods: FoodDao,
    private val recipeBatches: RecipeBatchDao
) {

    /**
     * @param dateIso ISO date string (yyyy-MM-dd) for the selected day.
     * @return A stream of UI rows for Day Log, containing only entries whose `logDateIso == dateIso`.
     */
    operator fun invoke(dateIso: String): Flow<List<DayLogRow>> {
        return logRepository.observeDay(dateIso)
            .map { logs ->
                val stableIds = logs.mapNotNull { it.foodStableId }.distinct()
                val stableIdToFoodId: Map<String, Long?> =
                    stableIds.associateWith { stableId -> foods.getIdByStableId(stableId) }

                val batchIds = logs.mapNotNull { it.recipeBatchId }.distinct()
                val batchIdToFoodId: Map<Long, Long> =
                    if (batchIds.isEmpty()) emptyMap()
                    else recipeBatches.getByIds(batchIds).associate { b -> b.id to b.batchFoodId }

                logs.map { log ->
                    val n = log.nutrients

                    val bannerFoodId: Long? = when {
                        log.recipeBatchId != null -> batchIdToFoodId[log.recipeBatchId]
                        log.foodStableId != null -> stableIdToFoodId[log.foodStableId]
                        else -> null
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
    }
}