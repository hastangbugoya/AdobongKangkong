package com.example.adobongkangkong.domain.planner.usecase

import com.example.adobongkangkong.data.local.db.entity.MealTemplateEntity
import com.example.adobongkangkong.data.local.db.entity.MealTemplateItemEntity
import com.example.adobongkangkong.domain.repository.MealTemplateItemRepository
import com.example.adobongkangkong.domain.repository.MealTemplateRepository
import com.example.adobongkangkong.domain.repository.PlannedItemRepository
import com.example.adobongkangkong.domain.repository.PlannedMealRepository
import javax.inject.Inject

/**
 * Creates a new **MealTemplate** from an existing **PlannedMeal** (and its PlannedItems).
 *
 * ## What is a MealTemplate?
 * A MealTemplate is a reusable, user-curated “meal blueprint”:
 * - It is **not tied to any date**.
 * - It can be applied later to create a new planned meal occurrence (possibly on many dates).
 * - It captures a stable list of items (foods / recipes / recipe batches + quantities) and an optional
 *   preferred/default slot.
 *
 * ## Why this exists
 * Planned meals are occurrences (date-bound). Templates are reusable building blocks.
 * This use case is the canonical “save this planned meal as a template” operation so the UI does not
 * have to re-implement:
 * - naming fallback rules
 * - copying items
 * - stable ordering for template items
 *
 * ## Behavior (copy semantics)
 * This is a **copy**:
 * - The created template is independent after creation.
 * - Future edits to the original planned meal do **not** change the template.
 * - Future edits to the template do **not** change the original planned meal.
 *
 * ## Ordering contract
 * Planned items may have sparse or large `sortOrder` values (e.g., Int.MAX_VALUE append semantics).
 * Template items are normalized to a compact, deterministic `0..n-1` ordering based on the planned
 * meal’s current ordering `(sortOrder, id)`.
 *
 * ## Naming rules (resolvedName)
 * Priority:
 * 1) explicit [templateName] (trimmed, non-blank)
 * 2) meal.nameOverride (trimmed, non-blank)
 * 3) meal.slot.name (fallback token)
 *
 * ## Edge cases / pitfalls
 * - Throws if the planned meal does not exist (caller should treat as a hard failure).
 * - Copies quantities exactly as stored (grams and/or servings); it does not “canonicalize” amounts.
 * - If a planned meal has zero items, the template will be created with zero items (allowed).
 */
class SavePlannedMealAsTemplateUseCase @Inject constructor(
    private val plannedMeals: PlannedMealRepository,
    private val plannedItems: PlannedItemRepository,
    private val templates: MealTemplateRepository,
    private val templateItems: MealTemplateItemRepository
) {
    suspend operator fun invoke(
        plannedMealId: Long,
        templateName: String? = null,
        defaultSlotFromMeal: Boolean = true
    ): Long {
        require(plannedMealId > 0) { "plannedMealId must be > 0" }

        val meal = plannedMeals.getById(plannedMealId)
            ?: throw IllegalArgumentException("Planned meal not found: id=$plannedMealId")

        val items = plannedItems.getItemsForMeal(plannedMealId)
            .sortedWith(compareBy({ it.sortOrder }, { it.id }))

        val resolvedName = templateName
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: meal.nameOverride
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            ?: run {
                // Fallback: use slot token if no explicit nameOverride.
                meal.slot.name
            }

        val templateId = templates.insert(
            MealTemplateEntity(
                name = resolvedName,
                defaultSlot = if (defaultSlotFromMeal) meal.slot else null
            )
        )

        // Normalize template item ordering to 0..n-1, regardless of planned sortOrder values.
        items.forEachIndexed { index, planned ->
            templateItems.insert(
                MealTemplateItemEntity(
                    templateId = templateId,
                    type = planned.type,
                    refId = planned.refId,
                    grams = planned.grams,
                    servings = planned.servings,
                    sortOrder = index
                )
            )
        }

        return templateId
    }
}

/**
 * FOR FUTURE AI ASSISTANT — notes for safe changes
 *
 * - Keep this operation a COPY (template must remain independent from planned meals).
 * - Preserve naming priority (templateName > meal.nameOverride > slot token).
 * - Preserve deterministic ordering: planned items sorted by (sortOrder, id) then normalized to 0..n-1.
 * - Do not inject UI concerns (dialogs/snackbars/nav) here; this is domain-only.
 *
 * If templates later gain extra fields (notes, tags, per-item metadata):
 * - Add them to MealTemplateEntity / MealTemplateItemEntity
 * - Extend the copy mapping here in a backward-compatible way.
 */