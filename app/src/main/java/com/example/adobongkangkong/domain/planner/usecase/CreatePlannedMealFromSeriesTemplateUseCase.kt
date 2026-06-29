package com.example.adobongkangkong.domain.planner.usecase

import androidx.room.withTransaction
import com.example.adobongkangkong.data.local.db.NutriDatabase
import com.example.adobongkangkong.data.local.db.entity.MealSlot
import com.example.adobongkangkong.data.local.db.entity.PlannedItemEntity
import com.example.adobongkangkong.data.local.db.entity.PlannedOccurrenceStatus
import com.example.adobongkangkong.domain.planner.model.PlannedItemSource
import com.example.adobongkangkong.domain.repository.PlannedItemRepository
import com.example.adobongkangkong.domain.repository.PlannedSeriesItemRepository
import javax.inject.Inject

/**
 * Creates a new planned meal occurrence for a series and copies series template items into it.
 *
 * Transactional for idempotency:
 * - If anything fails, meal insert is rolled back and EnsureSeriesOccurrences can safely retry.
 * - Only used when EnsureSeriesOccurrences has determined the meal does not already exist.
 *
 * Recipe variant rule:
 * - FOOD template rows must create FOOD planned items with recipeVariantId = null.
 * - RECIPE template rows must copy recipeVariantId into PlannedItemEntity.
 * - RECIPE_BATCH is intentionally not represented at the series-template layer while
 *   cooked/prepared batch planning is paused.
 */
class CreatePlannedMealFromSeriesTemplateUseCase @Inject constructor(
    private val db: NutriDatabase,
    private val createPlannedMeal: CreatePlannedMealUseCase,
    private val seriesItems: PlannedSeriesItemRepository,
    private val plannedItems: PlannedItemRepository
) {
    suspend operator fun invoke(
        dateIso: String,
        slot: MealSlot,
        seriesId: Long,
        customLabel: String? = null,
        nameOverride: String? = null,
        sortOrder: Int? = null,
        status: String = PlannedOccurrenceStatus.ACTIVE.name
    ): Long = db.withTransaction {

        val mealId = createPlannedMeal(
            dateIso = dateIso,
            slot = slot,
            customLabel = customLabel,
            nameOverride = nameOverride,
            sortOrder = sortOrder,
            seriesId = seriesId,
            status = status
        )

        val templates = seriesItems.getForSeries(seriesId)

        // Copy template → planned_items ONLY for this newly created meal.
        // This will not run for existing meals (EnsureSeriesOccurrences must enforce that).
        for (t in templates) {
            val (type, refId, recipeVariantId) = when {
                t.foodId != null && t.recipeId == null -> {
                    Triple(
                        PlannedItemSource.FOOD,
                        t.foodId,
                        null
                    )
                }

                t.recipeId != null && t.foodId == null -> {
                    Triple(
                        PlannedItemSource.RECIPE,
                        t.recipeId,
                        t.recipeVariantId
                    )
                }

                else -> error(
                    "Invalid PlannedSeriesItemEntity id=${t.id}: " +
                            "exactly one of foodId/recipeId must be set"
                )
            }

            val item = PlannedItemEntity(
                mealId = mealId,
                type = type,
                refId = refId,
                recipeVariantId = recipeVariantId,
                grams = t.grams,
                servings = t.servings,
                sortOrder = t.sortOrder
            )

            plannedItems.insert(item)
        }

        mealId
    }
}