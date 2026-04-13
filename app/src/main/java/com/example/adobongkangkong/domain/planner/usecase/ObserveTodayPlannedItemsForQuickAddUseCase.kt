package com.example.adobongkangkong.domain.planner.usecase

import com.example.adobongkangkong.data.local.db.entity.MealSlot
import com.example.adobongkangkong.domain.planner.model.PlannedDay
import com.example.adobongkangkong.domain.planner.model.QuickAddPlannedItemCandidate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Observes planned items for the Quick Add picker for a specific ISO date.
 *
 * IMPORTANT:
 * - Do not derive the picker date from LocalDate.now().
 * - The picker must use the same target date the Quick Add sheet is opened for.
 */
class ObserveTodayPlannedItemsForQuickAddUseCase @Inject constructor(
    private val observePlannedDayUseCase: ObservePlannedDayUseCase,
    private val resolvePlannedItemToQuickAddCandidate: ResolvePlannedItemToQuickAddCandidateUseCase,
) {

    operator fun invoke(
        dateIso: String
    ): Flow<Map<MealSlot, List<QuickAddPlannedItemCandidate>>> {
        return observePlannedDayUseCase(dateIso)
            .map { day -> mapDay(day) }
    }

    private suspend fun mapDay(
        day: PlannedDay?
    ): Map<MealSlot, List<QuickAddPlannedItemCandidate>> {
        if (day == null) return emptyMap()

        val result = linkedMapOf<MealSlot, MutableList<QuickAddPlannedItemCandidate>>()

        day.mealsBySlot.forEach { (slot, meals) ->
            val list = result.getOrPut(slot) { mutableListOf() }

            meals.forEach { meal ->
                meal.items.forEach { item ->
                    val candidate = resolvePlannedItemToQuickAddCandidate(
                        item = item,
                        slot = slot,
                    ) ?: return@forEach

                    list += candidate
                }
            }
        }

        return result
    }
}