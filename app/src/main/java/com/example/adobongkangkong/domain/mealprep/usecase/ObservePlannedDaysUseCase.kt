package com.example.adobongkangkong.domain.mealprep.usecase

import com.example.adobongkangkong.data.local.db.entity.MealSlot
import com.example.adobongkangkong.data.local.db.entity.PlannedItemEntity
import com.example.adobongkangkong.data.local.db.entity.PlannedMealEntity
import com.example.adobongkangkong.domain.mealprep.model.FoodPlannedItem
import com.example.adobongkangkong.domain.mealprep.model.PlannedDay
import com.example.adobongkangkong.domain.mealprep.model.PlannedMeal
import com.example.adobongkangkong.domain.mealprep.model.PlannedQuantity
import com.example.adobongkangkong.domain.mealprep.model.RecipeBatchPlannedItem
import com.example.adobongkangkong.domain.repository.PlannedItemRepository
import com.example.adobongkangkong.domain.repository.PlannedMealRepository
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class ObservePlannedDaysUseCase @Inject constructor(
    private val meals: PlannedMealRepository,
    private val items: PlannedItemRepository
) {
    /**
     * Observe a derived list of PlannedDay for an inclusive ISO date range (yyyy-MM-dd).
     *
     * This is a thin wrapper over the canonical implementation.
     */
    operator fun invoke(
        startDateIso: String,
        endDateIso: String
    ): Flow<List<PlannedDay>> = observeInternal(startDateIso, endDateIso)

    /**
     * Convenience overload: observe a single derived PlannedDay.
     *
     * Implementation delegates to the canonical range observer to avoid duplicated logic.
     */
    fun observeDay(dateIso: String): Flow<PlannedDay> {
        val date = LocalDate.parse(dateIso)

        return observeInternal(dateIso, dateIso).map { days ->
            // Semantics: always emit a PlannedDay for the requested date, even if empty.
            days.firstOrNull() ?: PlannedDay(date = date, meals = emptyList(), notes = null)
        }
    }

    /**
     * Canonical implementation.
     *
     * - Observe meals in range
     * - For each meal, observe its items
     * - Combine into PlannedDay list grouped by date
     */
    private fun observeInternal(
        startDateIso: String,
        endDateIso: String
    ): Flow<List<PlannedDay>> {
        require(startDateIso.isNotBlank()) { "startDateIso must not be blank" }
        require(endDateIso.isNotBlank()) { "endDateIso must not be blank" }

        val start = LocalDate.parse(startDateIso)
        val end = LocalDate.parse(endDateIso)
        require(!end.isBefore(start)) { "endDateIso must be >= startDateIso" }

        return meals.observeMealsInRange(startDateIso, endDateIso).flatMapLatest { mealEntities ->
            if (mealEntities.isEmpty()) {
                flowOf(emptyList())
            } else {
                val perMealFlows: List<Flow<PlannedMeal>> = mealEntities.map { mealEntity ->
                    items.observeItemsForMeal(mealEntity.id).map { itemEntities ->
                        mapMeal(mealEntity, itemEntities)
                    }
                }

                combine(perMealFlows) { plannedMealsArray ->
                    val plannedMeals = plannedMealsArray
                        .toList()
                        .sortedWith(compareBy<PlannedMeal> { it.date }.thenBy { it.sortOrder })

                    plannedMeals
                        .groupBy { it.date }
                        .toSortedMap()
                        .map { (date, mealsForDay) ->
                            PlannedDay(
                                date = date,
                                meals = mealsForDay.sortedBy { it.sortOrder },
                                notes = null
                            )
                        }
                }
            }
        }
    }

    private fun mapMeal(
        meal: PlannedMealEntity,
        itemEntities: List<PlannedItemEntity>
    ): PlannedMeal {
        val parsedDate = LocalDate.parse(meal.date)
        val slot = meal.slot

        val plannedItems = itemEntities
            .sortedWith(compareBy<PlannedItemEntity> { it.sortOrder }.thenBy { it.id })
            .map { mapItem(it) }

        val customLabel = when (slot) {
            MealSlot.CUSTOM -> meal.customLabel?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("PlannedMealEntity(${meal.id}) is CUSTOM but customLabel is null/blank")
            else -> null
        }

        return PlannedMeal(
            id = meal.id,
            date = parsedDate,
            slot = slot,
            customLabel = customLabel,
            nameOverride = meal.nameOverride?.takeIf { it.isNotBlank() },
            items = plannedItems,
            sortOrder = meal.sortOrder
        )
    }

    private fun mapItem(entity: PlannedItemEntity): com.example.adobongkangkong.domain.mealprep.model.PlannedItem {
        val quantity = PlannedQuantity(
            grams = entity.grams,
            servings = entity.servings
        )

        return when (entity.type.trim().uppercase()) {
            "FOOD" -> FoodPlannedItem(
                foodId = entity.refId,
                quantity = quantity
            )

            "RECIPE_BATCH" -> RecipeBatchPlannedItem(
                recipeBatchId = entity.refId,
                quantity = quantity
            )

            else -> throw IllegalStateException("Unknown PlannedItemEntity.type='${entity.type}' (id=${entity.id})")
        }
    }
}
