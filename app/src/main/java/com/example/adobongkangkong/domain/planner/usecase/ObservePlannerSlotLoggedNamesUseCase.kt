package com.example.adobongkangkong.domain.planner.usecase

import com.example.adobongkangkong.data.local.db.entity.MealSlot
import com.example.adobongkangkong.domain.repository.LogRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Observes logged item names for a single day and groups them by meal slot.
 *
 * Phase 1 behavior:
 * - Uses all log entries for the day.
 * - Groups by the log entry's stamped mealSlot.
 * - Returns names only (no quantities, no planner reconciliation).
 *
 * This is intentionally lightweight so Planner can show awareness banners like:
 * "2 items logged: Eggs, Coffee"
 */
class ObservePlannerSlotLoggedNamesUseCase @Inject constructor(
    private val logRepository: LogRepository,
) {

    operator fun invoke(
        dateIso: String
    ): Flow<Map<MealSlot, List<String>>> {
        return logRepository.observeDay(dateIso)
            .map { entries ->
                entries
                    .asSequence()
                    .filter { it.mealSlot != null }
                    .groupBy(
                        keySelector = { it.mealSlot!! },
                        valueTransform = { entry ->
                            entry.itemName.trim().ifBlank { "Unnamed item" }
                        }
                    )
                    .mapValues { (_, names) -> names.filter { it.isNotBlank() } }
            }
    }
}