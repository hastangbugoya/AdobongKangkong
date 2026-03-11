package com.example.adobongkangkong.domain.planner.usecase

import com.example.adobongkangkong.data.local.db.entity.MealSlot
import com.example.adobongkangkong.domain.planner.model.PlannedDay
import com.example.adobongkangkong.domain.planner.model.PlannedItemSource
import com.example.adobongkangkong.domain.planner.model.QuickAddPlannedItemCandidate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

class ObserveTodayPlannedItemsForQuickAddUseCase @Inject constructor(
    private val observePlannedDayUseCase: ObservePlannedDayUseCase
) {

    operator fun invoke(): Flow<Map<MealSlot, List<QuickAddPlannedItemCandidate>>> {

        val today = LocalDate.now().toString()

        return observePlannedDayUseCase(today)
            .map { day -> mapDay(day) }
    }

    private fun mapDay(
        day: PlannedDay?
    ): Map<MealSlot, List<QuickAddPlannedItemCandidate>> {

        if (day == null) return emptyMap()

        val result = mutableMapOf<MealSlot, MutableList<QuickAddPlannedItemCandidate>>()

        day.mealsBySlot.forEach { (slot, meals) ->

            val list = result.getOrPut(slot) { mutableListOf() }

            meals.forEach { meal ->

                meal.items.forEach { item ->

                    val type =
                        when (item.sourceType) {
                            PlannedItemSource.FOOD -> QuickAddPlannedItemCandidate.Type.FOOD
                            PlannedItemSource.RECIPE -> QuickAddPlannedItemCandidate.Type.RECIPE
                            PlannedItemSource.RECIPE_BATCH -> QuickAddPlannedItemCandidate.Type.RECIPE_BATCH
                            else -> return@forEach
                        }

                    list.add(
                        QuickAddPlannedItemCandidate(
                            id = item.id,
                            title = item.title ?: "",
                            slot = slot,
                            type = type,
                            foodId = item.sourceId,
                            recipeId = item.sourceId,
                            batchId = item.sourceId,
                            plannedServings = item.qtyServings,
                            plannedGrams = item.qtyGrams
                        )
                    )
                }
            }
        }

        return result
    }
}