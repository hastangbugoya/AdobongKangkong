package com.example.adobongkangkong.domain.planner.usecase

import com.example.adobongkangkong.data.local.db.dao.FoodDao
import com.example.adobongkangkong.data.local.db.dao.RecipeBatchDao
import com.example.adobongkangkong.data.local.db.dao.RecipeDao
import com.example.adobongkangkong.data.local.db.entity.MealSlot
import com.example.adobongkangkong.data.local.db.entity.PlannedItemEntity
import com.example.adobongkangkong.data.local.db.entity.PlannedMealEntity
import com.example.adobongkangkong.data.local.db.entity.IouEntity
import com.example.adobongkangkong.domain.planner.model.PlannedDay
import com.example.adobongkangkong.domain.planner.model.Iou
import com.example.adobongkangkong.domain.planner.model.PlannedItem
import com.example.adobongkangkong.domain.planner.model.PlannedItemSource
import com.example.adobongkangkong.domain.planner.model.PlannedMeal
import com.example.adobongkangkong.domain.repository.PlannedItemRepository
import com.example.adobongkangkong.domain.repository.PlannedMealRepository
import com.example.adobongkangkong.domain.repository.IouRepository
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Observes a derived planner model ([PlannedDay] → [PlannedMeal] → [PlannedItem]) for an ISO date range.
 *
 * =============================================================================
 * PURPOSE
 * =============================================================================
 *
 * This use case produces the **full planner-day domain model** used by PlannerDay-style screens:
 *
 * - Groups planned meals by calendar date and meal slot
 * - Hydrates each planned meal with its planned items
 * - Resolves item titles (food / recipe / recipe batch) as a derived UI-friendly field
 *
 * Output is a list of [PlannedDay] objects ordered by date, each containing meals grouped by [MealSlot]
 * (including empty slot buckets so UI rendering can be stable/predictable).
 *
 *
 * =============================================================================
 * RATIONALE (WHY THIS EXISTS)
 * =============================================================================
 *
 * The database stores planner data as normalized tables:
 *
 * - planned_meals (occurrences)
 * - planned_items (items per meal)
 *
 * The UI needs a denormalized, structured model that is easy to render and reason about:
 *
 * - “Give me the day model for this date”
 * - “Give me all planned days in a range”
 *
 * This use case centralizes that derivation so:
 *
 * - UI is not forced to coordinate multiple repositories/DAOs
 * - ordering rules are consistent everywhere
 * - title resolution logic stays centralized and does not leak to the UI layer
 *
 *
 * =============================================================================
 * BEHAVIOR
 * =============================================================================
 *
 * - Observes planned meals in the requested inclusive ISO range.
 * - For each meal, observes planned items (reactive per-meal).
 * - Combines all per-meal streams into one emission.
 * - Sorts meals deterministically by:
 *     1) date
 *     2) sortOrder
 *     3) id
 * - Groups meals by day, then by slot.
 * - Ensures each day contains entries for every [MealSlot] (empty lists where missing).
 * - Resolves item titles using DAO lookups:
 *     - FOOD → FoodDao.getByIds(...)
 *     - RECIPE → RecipeDao.getById(...)
 *     - RECIPE_BATCH → RecipeBatchDao.getById(...) then RecipeDao.getById(batch.recipeId)
 *
 *
 * =============================================================================
 * PARAMETERS
 * =============================================================================
 *
 * invoke(startDateIso, endDateIso)
 * - [startDateIso] inclusive range start, ISO yyyy-MM-dd
 * - [endDateIso] inclusive range end, ISO yyyy-MM-dd
 *
 * observeDay(dateIso)
 * - [dateIso] ISO yyyy-MM-dd
 * - Always emits a [PlannedDay] for that date (even if empty)
 *
 *
 * =============================================================================
 * RETURN
 * =============================================================================
 *
 * - Range API returns:
 *     Flow<List<PlannedDay>>
 *   where the list contains only days that have at least one meal.
 *
 * - Single-day API returns:
 *     Flow<PlannedDay>
 *   and guarantees a day emission even when empty.
 *
 *
 * =============================================================================
 * EDGE CASES HANDLED
 * =============================================================================
 *
 * - Empty range (no meals) → emits emptyList() (range) or an empty PlannedDay (single-day)
 * - Invalid range (end < start) → throws (require)
 * - CUSTOM meals with missing customLabel → throws (IllegalStateException)
 * - Missing title lookups (deleted foods/recipes/batches) → title omitted for that item
 *
 *
 * =============================================================================
 * PITFALLS / GOTCHAS
 * =============================================================================
 *
 * - combine(perMealFlows) scales with number of meals in range.
 *   Large ranges with many meals can increase overhead and subscription count.
 *
 * - Title resolution performs DB reads on each emission of the combined model.
 *   If meals/items update frequently, this can become a hotspot.
 *
 * - This use case uses DAOs directly for derived “display fields”.
 *   This is intentional for now, but it means this is not a pure domain-only use case.
 *
 * - This model is “planner schedule”, not “consumption”.
 *   Do not mix with log/heatmap totals in this use case.
 *
 *
 * =============================================================================
 * ARCHITECTURAL RULES
 * =============================================================================
 *
 * - PlannedDay is derived; we persist planned meals and items only.
 * - Day membership is derived from PlannedMealEntity.date (ISO yyyy-MM-dd).
 * - Title resolution is derived only; no schema changes are required for titles.
 * - Do not join planner rows to foods/recipes for nutrition here; this is a schedule model.
 */
class ObservePlannedDaysUseCase @Inject constructor(
    private val meals: PlannedMealRepository,
    private val items: PlannedItemRepository,
    private val ious: IouRepository,

    // Title resolution (derived) — no planner schema changes
    private val foodDao: FoodDao,
    private val recipeBatchDao: RecipeBatchDao,
    private val recipeDao: RecipeDao
) {
    /**
     * Observe a derived list of [PlannedDay] for an inclusive ISO date range (yyyy-MM-dd).
     *
     * Semantics:
     * - Emits only dates that have at least one planned meal.
     * - Ordering is deterministic and stable across emissions.
     */
    operator fun invoke(
        startDateIso: String,
        endDateIso: String
    ): Flow<List<PlannedDay>> = observeInternal(startDateIso, endDateIso)

    /**
     * Convenience overload: observe a single derived [PlannedDay].
     *
     * Semantics:
     * - Always emits a [PlannedDay] for the requested date, even if empty.
     * - If upstream ever emits multiple days (range logic), we pick the requested date robustly.
     */
    fun observeDay(dateIso: String): Flow<PlannedDay> {
        val date = LocalDate.parse(dateIso)

        return observeInternal(dateIso, dateIso).map { days ->
            days.firstOrNull { it.date == date } ?: PlannedDay(
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

        val mealsFlow = meals.observeMealsInRange(startDateIso, endDateIso)
        val iousFlow = ious.observeInRange(startDateIso, endDateIso)

        return combine(mealsFlow, iousFlow) { mealEntities, iouEntities ->
            MealAndIousSeed(mealEntities = mealEntities, iouEntities = iouEntities)
        }.flatMapLatest { seed ->
            val mealEntities = seed.mealEntities
            val iouEntities = seed.iouEntities

            if (mealEntities.isEmpty()) {
                if (iouEntities.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    flowOf(daysFromIousOnly(iouEntities))
                }
            } else {
                val perMealFlows: List<Flow<MealAndItems>> = mealEntities.map { mealEntity ->
                    items.observeItemsForMeal(mealEntity.id).map { itemEntities ->
                        MealAndItems(mealEntity, itemEntities)
                    }
                }

                combine(perMealFlows) { array ->
                    val rows: List<MealAndItems> = array
                        .toList()
                        .sortedWith(
                            compareBy<MealAndItems> { LocalDate.parse(it.meal.date) }
                                .thenBy { it.meal.sortOrder }
                                .thenBy { it.meal.id }
                        )

                    val titleByItemId: Map<Long, String> = resolveItemTitles(rows)

                    val iousByDate: Map<LocalDate, List<Iou>> =
                        iouEntities
                            .groupBy { LocalDate.parse(it.dateIso) }
                            .mapValues { (_, list) -> list.map { it.toDomainIou() } }

                    val daysFromMeals: List<PlannedDay> = rows
                        .groupBy { LocalDate.parse(it.meal.date) }
                        .toSortedMap()
                        .map { (date, mealsForDayRows) ->
                            val plannedMeals: List<PlannedMeal> = mealsForDayRows
                                .sortedWith(compareBy<MealAndItems> { it.meal.sortOrder }.thenBy { it.meal.id })
                                .map { (mealEntity, itemEntities) ->
                                    mapMeal(mealEntity, itemEntities, titleByItemId)
                                }

                            PlannedDay(
                                date = date,
                                mealsBySlot = plannedMeals
                                    .groupBy { it.slot }
                                    .toMealsBySlotWithEmpties(),
                                ious = iousByDate[date].orEmpty()
                            )
                        }

                    // Add IOU-only dates (no meals) in-range.
                    val mealDates: Set<LocalDate> = daysFromMeals.map { it.date }.toSet()
                    val iouOnlyDays: List<PlannedDay> = iousByDate
                        .filterKeys { it !in mealDates }
                        .toSortedMap()
                        .map { (date, dayIous) ->
                            PlannedDay(
                                date = date,
                                mealsBySlot = emptyMealsBySlot(),
                                ious = dayIous
                            )
                        }

                    (daysFromMeals + iouOnlyDays).sortedBy { it.date }
                }
            }
        }
    }

    private data class MealAndIousSeed(
        val mealEntities: List<PlannedMealEntity>,
        val iouEntities: List<IouEntity>
    )

    private fun daysFromIousOnly(iouEntities: List<IouEntity>): List<PlannedDay> {
        val iousByDate = iouEntities
            .groupBy { LocalDate.parse(it.dateIso) }
            .toSortedMap()
            .mapValues { (_, list) -> list.map { it.toDomainIou() } }

        return iousByDate.map { (date, dayIous) ->
            PlannedDay(
                date = date,
                mealsBySlot = emptyMealsBySlot(),
                ious = dayIous
            )
        }
    }

    private fun IouEntity.toDomainIou(): Iou =
        Iou(
            id = id,
            description = description,
            createdAt = Instant.ofEpochMilli(createdAtEpochMs),
            updatedAt = Instant.ofEpochMilli(updatedAtEpochMs)
        )

    private data class MealAndItems(
        val meal: PlannedMealEntity,
        val items: List<PlannedItemEntity>
    )

    private fun mapMeal(
        meal: PlannedMealEntity,
        itemEntities: List<PlannedItemEntity>,
        titleByItemId: Map<Long, String>
    ): PlannedMeal {
        val parsedDate = LocalDate.parse(meal.date)
        val slot = meal.slot

        val plannedItems: List<PlannedItem> = itemEntities
            .sortedWith(compareBy<PlannedItemEntity> { it.sortOrder }.thenBy { it.id })
            .map { entity ->
                mapItem(entity, titleByItemId[entity.id])
            }

        val title: String? =
            meal.nameOverride
                ?.trim()
                ?.takeIf { it.isNotBlank() }
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
            items = plannedItems,
            seriesId = meal.seriesId
        )
    }

    private fun mapItem(entity: PlannedItemEntity, resolvedTitle: String?): PlannedItem {
        return PlannedItem(
            id = entity.id,
            sourceType = entity.type,
            sourceId = entity.refId,
            qtyGrams = entity.grams,
            qtyServings = entity.servings,
            title = resolvedTitle
        )
    }

    private suspend fun resolveItemTitles(rows: List<MealAndItems>): Map<Long, String> {
        val allItems: List<PlannedItemEntity> = rows.flatMap { it.items }

        val foodIds: List<Long> = allItems
            .asSequence()
            .filter { it.type == PlannedItemSource.FOOD }
            .map { it.refId }
            .distinct()
            .toList()

        val batchIds: List<Long> = allItems
            .asSequence()
            .filter { it.type == PlannedItemSource.RECIPE_BATCH }
            .map { it.refId }
            .distinct()
            .toList()

        val recipeIds: List<Long> = allItems
            .asSequence()
            .filter { it.type == PlannedItemSource.RECIPE }
            .map { it.refId }
            .distinct()
            .toList()

        val foodTitleById: Map<Long, String> =
            if (foodIds.isEmpty()) emptyMap()
            else foodDao.getByIds(foodIds).associate { it.id to it.name }

        val recipeTitleById: MutableMap<Long, String> = mutableMapOf()
        for (recipeId in recipeIds) {
            val recipe = recipeDao.getById(recipeId) ?: continue
            recipeTitleById[recipeId] = recipe.name
        }

        val recipeTitleByBatchId: MutableMap<Long, String> = mutableMapOf()
        for (batchId in batchIds) {
            val batch = recipeBatchDao.getById(batchId) ?: continue
            val recipe = recipeDao.getById(batch.recipeId) ?: continue
            recipeTitleByBatchId[batchId] = recipe.name
        }

        return allItems.associateNotNull { item ->
            val title = when (item.type) {
                PlannedItemSource.FOOD -> foodTitleById[item.refId]
                PlannedItemSource.RECIPE -> recipeTitleById[item.refId]
                PlannedItemSource.RECIPE_BATCH -> recipeTitleByBatchId[item.refId]
            }
            if (title.isNullOrBlank()) null else (item.id to title)
        }
    }

    private fun emptyMealsBySlot(): Map<MealSlot, List<PlannedMeal>> {
        return MealSlot.entries.associateWith { emptyList() }
    }

    private fun Map<MealSlot, List<PlannedMeal>>.toMealsBySlotWithEmpties(): Map<MealSlot, List<PlannedMeal>> {
        val base = emptyMealsBySlot().toMutableMap()
        forEach { (slot, meals) -> base[slot] = meals }
        return base
    }
}

private inline fun <T, K, V> Iterable<T>.associateNotNull(transform: (T) -> Pair<K, V>?): Map<K, V> {
    val result = LinkedHashMap<K, V>()
    for (element in this) {
        val pair = transform(element) ?: continue
        result[pair.first] = pair.second
    }
    return result
}

/**
 * =============================================================================
 * FOR FUTURE AI / FUTURE DEVELOPER — invariants, consolidation notes, and perf warnings
 * =============================================================================
 *
 * IMPORTANT: This is NOT the same as ObservePlannedDaysInMonthUseCase
 *
 * ObservePlannedDaysInMonthUseCase:
 * - returns only markers: Set<LocalDate>
 * - intentionally lightweight for month calendar indicators
 * - should remain cheap to observe frequently
 *
 * ObservePlannedDaysUseCase (this file):
 * - returns full derived models: PlannedDay → PlannedMeal → PlannedItem
 * - performs per-meal reactive subscriptions (combine of many flows)
 * - resolves titles via DAO reads (derived display fields)
 *
 * They serve different UI needs and should generally NOT be consolidated.
 *
 *
 * If you still want a “thin wrapper” for reuse
 *
 * A safe consolidation is:
 * - Keep ObservePlannedDaysInMonthUseCase as-is (marker-only).
 * - Add a new thin use case for:
 *     ObservePlannedDaysInRangeMarkersUseCase
 *   that delegates to ObservePlannedDaysInMonthUseCase.observePlannedDatesInRange(...)
 *
 * But do NOT make ObservePlannedDaysUseCase depend on marker-only use case:
 * it needs full meal/item hydration and title resolution.
 *
 *
 * INVARIANTS (MUST NOT CHANGE)
 *
 * - Date identity uses PlannedMealEntity.date ISO yyyy-MM-dd.
 * - CUSTOM slot must have non-blank customLabel (else error).
 * - Do not mutate DB state here; this is an observer/derivation only.
 * - Do not join nutrition/logging here; this is scheduling only.
 *
 *
 * PERFORMANCE CONSIDERATIONS
 *
 * - combine(perMealFlows) creates one Flow subscription per meal in range.
 *   This is fine for PlannerDay (small range) but risky for large horizons.
 *
 * If performance becomes a problem:
 * - Replace per-meal observation with a single query that returns items for all meals in range,
 *   then group in memory (one Flow instead of N flows).
 * - Cache title resolution for stable IDs (food/recipe/batch) to avoid repeated DAO hits.
 *
 *
 * MIGRATION / BOUNDARIES
 *
 * - This use case currently depends on Room DAOs (FoodDao/RecipeDao/RecipeBatchDao).
 *   If moving toward strict Clean Architecture or KMP:
 *   - introduce repository interfaces for title resolution,
 *   - move DB-specific code behind data layer implementations.
 */