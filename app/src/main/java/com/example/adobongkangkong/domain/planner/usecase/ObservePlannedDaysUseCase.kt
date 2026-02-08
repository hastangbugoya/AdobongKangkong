package com.example.adobongkangkong.domain.planner.usecase

import com.example.adobongkangkong.data.local.db.dao.FoodDao
import com.example.adobongkangkong.data.local.db.dao.RecipeBatchDao
import com.example.adobongkangkong.data.local.db.dao.RecipeDao
import com.example.adobongkangkong.data.local.db.entity.MealSlot
import com.example.adobongkangkong.data.local.db.entity.PlannedItemEntity
import com.example.adobongkangkong.data.local.db.entity.PlannedMealEntity
import com.example.adobongkangkong.domain.planner.model.PlannedDay
import com.example.adobongkangkong.domain.planner.model.PlannedItem
import com.example.adobongkangkong.domain.planner.model.PlannedItemSource
import com.example.adobongkangkong.domain.planner.model.PlannedMeal
import com.example.adobongkangkong.domain.repository.PlannedItemRepository
import com.example.adobongkangkong.domain.repository.PlannedMealRepository
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest

class ObservePlannedDaysUseCase @Inject constructor(
    private val meals: PlannedMealRepository,
    private val items: PlannedItemRepository,

    // Title resolution (derived) — no planner schema changes
    private val foodDao: FoodDao,
    private val recipeBatchDao: RecipeBatchDao,
    private val recipeDao: RecipeDao
) {
    /**
     * Observe a derived list of PlannedDay for an inclusive ISO date range (yyyy-MM-dd).
     *
     * Note: This emits only dates that have at least one meal.
     * (observeDay() below guarantees a day emission even if empty.)
     */
    operator fun invoke(
        startDateIso: String,
        endDateIso: String
    ): Flow<List<PlannedDay>> = observeInternal(startDateIso, endDateIso)

    /**
     * Convenience overload: observe a single derived PlannedDay.
     *
     * Semantics: always emit a PlannedDay for the requested date, even if empty.
     */
    fun observeDay(dateIso: String): Flow<PlannedDay> {
        val date = LocalDate.parse(dateIso)

        return observeInternal(dateIso, dateIso).map { days ->
            days.firstOrNull() ?: PlannedDay(
                date = date,
                mealsBySlot = emptyMealsBySlot()
            )
        }
    }

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
                val perMealFlows: List<Flow<MealAndItems>> = mealEntities.map { mealEntity ->
                    items.observeItemsForMeal(mealEntity.id).map { itemEntities ->
                        MealAndItems(mealEntity, itemEntities)
                    }
                }

                // First, combine all meal+items flows into a single sorted list of MealAndItems rows
                val rowsFlow: Flow<List<MealAndItems>> =
                    combine(perMealFlows) { array ->
                        array
                            .toList()
                            .sortedWith(
                                compareBy<MealAndItems> { LocalDate.parse(it.meal.date) }
                                    .thenBy { it.meal.sortOrder }
                                    .thenBy { it.meal.id }
                            )
                    }

                // Then, in a suspend stage, resolve titles and build PlannedDay(s)
                rowsFlow.mapLatest { rows ->
                    val titleMap = resolveItemTitles(rows)

                    rows
                        .groupBy { LocalDate.parse(it.meal.date) }
                        .toSortedMap()
                        .map { (date, mealsForDayRows) ->
                            val plannedMeals: List<PlannedMeal> = mealsForDayRows
                                .sortedWith(compareBy<MealAndItems> { it.meal.sortOrder }.thenBy { it.meal.id })
                                .map { (mealEntity, itemEntities) ->
                                    mapMeal(mealEntity, itemEntities, titleMap)
                                }

                            PlannedDay(
                                date = date,
                                mealsBySlot = plannedMeals
                                    .groupBy { it.slot }
                                    .toMealsBySlotWithEmpties()
                            )
                        }
                }
            }
        }
    }

    private data class MealAndItems(
        val meal: PlannedMealEntity,
        val items: List<PlannedItemEntity>
    )

    /**
     * Build a map of (PlannedItemEntity.id -> display title) for all items currently observed.
     *
     * FOOD: title from FoodEntity.name
     * RECIPE: assumes sourceId is recipeBatchId -> look up RecipeBatchEntity -> recipeId -> RecipeEntity.name
     */
    private suspend fun resolveItemTitles(rows: List<MealAndItems>): Map<Long, String> {
        val allItems: List<PlannedItemEntity> = rows.flatMap { it.items }

        val foodIds: List<Long> = allItems
            .asSequence()
            .filter { it.type == PlannedItemSource.FOOD }
            .map { it.refId }
            .distinct()
            .toList()

//        android.util.Log.d("PlannerTitles", "ObservePlannedDaysUseCase> resolveItemTitles> foodIds=$foodIds")
//        val foods = if (foodIds.isEmpty()) emptyList() else foodDao.getByIds(foodIds)
//        android.util.Log.d("PlannerTitles", "ObservePlannedDaysUseCase> resolveItemTitles> foodsFromDb=${foods.map { it.id to it.name }}")

        val batchIds: List<Long> = allItems
            .asSequence()
            .filter { it.type == PlannedItemSource.RECIPE }
            .map { it.refId }
            .distinct()
            .toList()

        // FOOD titles in one query
        val foodTitleById: Map<Long, String> =
            if (foodIds.isEmpty()) emptyMap()
            else foodDao.getByIds(foodIds).associate { it.id to it.name }

        // RECIPE titles via batch -> recipe -> name
        val recipeTitleByBatchId: MutableMap<Long, String> = mutableMapOf()
        for (batchId in batchIds) {
            val batch = recipeBatchDao.getById(batchId) ?: continue
            val recipe = recipeDao.getById(batch.recipeId) ?: continue
            recipeTitleByBatchId[batchId] = recipe.name
        }

        // Map itemId -> title
        return allItems.associateNotNull { item ->
            val title = when (item.type) {
                PlannedItemSource.FOOD -> foodTitleById[item.refId]
                PlannedItemSource.RECIPE -> recipeTitleByBatchId[item.refId]
            }
            if (title.isNullOrBlank()) null else (item.id to title)
        }
    }

    private fun mapMeal(
        meal: PlannedMealEntity,
        itemEntities: List<PlannedItemEntity>,
        titleMapByItemId: Map<Long, String>
    ): PlannedMeal {
        val parsedDate = LocalDate.parse(meal.date)
        val slot = meal.slot

        val plannedItems: List<PlannedItem> = itemEntities
            .sortedWith(compareBy<PlannedItemEntity> { it.sortOrder }.thenBy { it.id })
            .map { mapItem(it, titleMapByItemId) }

        val title: String? =
            meal.nameOverride?.takeIf { it.isNotBlank() }
                ?: when (slot) {
                    MealSlot.CUSTOM -> meal.customLabel?.takeIf { it.isNotBlank() }
                        ?: throw IllegalStateException(
                            "PlannedMealEntity(${meal.id}) is CUSTOM but customLabel is null/blank"
                        )
                    else -> null
                }

        return PlannedMeal(
            id = meal.id,
            date = parsedDate,
            slot = slot,
            title = title,
            items = plannedItems
        )
    }

    private fun mapItem(
        entity: PlannedItemEntity,
        titleMapByItemId: Map<Long, String>
    ): PlannedItem {
        return PlannedItem(
            id = entity.id,
            sourceType = entity.type,
            sourceId = entity.refId,
            qtyGrams = entity.grams,
            qtyServings = entity.servings,
            title = titleMapByItemId[entity.id]
        )
    }

    private fun emptyMealsBySlot(): Map<MealSlot, List<PlannedMeal>> {
        // Ensure UI can render all sections without special-casing missing keys.
        return MealSlot.entries.associateWith { emptyList() }
    }

    private fun Map<MealSlot, List<PlannedMeal>>.toMealsBySlotWithEmpties(): Map<MealSlot, List<PlannedMeal>> {
        val base = emptyMealsBySlot().toMutableMap()
        forEach { (slot, meals) -> base[slot] = meals }
        return base
    }

    private inline fun <T, K, V> Iterable<T>.associateNotNull(transform: (T) -> Pair<K, V>?): Map<K, V> {
        val out = LinkedHashMap<K, V>()
        for (e in this) {
            val p = transform(e) ?: continue
            out[p.first] = p.second
        }
        return out
    }
}
