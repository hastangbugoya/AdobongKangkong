package com.example.adobongkangkong.domain.planner.usecase

import com.example.adobongkangkong.data.local.db.entity.MealSlot
import com.example.adobongkangkong.data.local.db.entity.PlannedMealEntity
import com.example.adobongkangkong.data.local.db.entity.PlannedOccurrenceStatus
import com.example.adobongkangkong.domain.repository.PlannedMealRepository
import javax.inject.Inject

/**
 * Creates a Planned Meal row for a specific ISO calendar day (yyyy-MM-dd).
 *
 * ----------------------------------------------------------------------------
 * Purpose
 * ----------------------------------------------------------------------------
 *
 * A **Planned Meal** is the planner container that anchors:
 *
 * - one calendar day (authoritative `dateIso`)
 * - one meal slot (Breakfast/Lunch/Dinner/Snack/CUSTOM)
 * - zero or more planned items (foods / recipes / recipe batches)
 *
 * It is the “header row” the planner UI renders for a day and slot, and it is the parent
 * reference for all items planned inside that meal.
 *
 * ----------------------------------------------------------------------------
 * Rationale (why this use case exists)
 * ----------------------------------------------------------------------------
 *
 * The planner system persists *meals and items*, not a separate PlannedDay table.
 *
 * PlannedDay is a derived view (date → meals → items). This use case exists to:
 *
 * - centralize creation rules for PlannedMealEntity (especially slot/CUSTOM label handling),
 * - keep ViewModels/UI from constructing persistence entities directly,
 * - enforce stable ordering rules without requiring read-before-write.
 *
 * ----------------------------------------------------------------------------
 * Behavior
 * ----------------------------------------------------------------------------
 *
 * - Requires [dateIso] to be non-blank and assumes ISO day format `yyyy-MM-dd`.
 * - Handles CUSTOM slot labeling:
 *   - If slot == CUSTOM: [customLabel] is required and normalized (trim, non-blank).
 *   - Else: customLabel is stored as null.
 * - Normalizes nameOverride:
 *   - trims and stores null if blank.
 * - Ordering:
 *   - If [sortOrder] provided, persists it.
 *   - Else uses Int.MAX_VALUE to “append” without a DB read and relies on read ordering:
 *     `ORDER BY sortOrder ASC, id ASC`.
 * - Persists optional series linkage and occurrence status.
 *
 * ----------------------------------------------------------------------------
 * Parameters
 * ----------------------------------------------------------------------------
 * @param dateIso ISO day string (yyyy-MM-dd). Authoritative day key for planner membership.
 * @param slot Meal slot for the planned meal container.
 * @param customLabel Required when slot == CUSTOM; ignored otherwise.
 * @param nameOverride Optional display override for the meal name shown in UI.
 * @param sortOrder Optional explicit ordering within the day (per slot list ordering).
 * @param seriesId Optional link to a planned series (recurrence). Null for non-series meals.
 * @param status Occurrence status as a string enum name (defaults to ACTIVE).
 *
 * ----------------------------------------------------------------------------
 * Return
 * ----------------------------------------------------------------------------
 * @return The inserted PlannedMealEntity row id.
 *
 * ----------------------------------------------------------------------------
 * Edge cases
 * ----------------------------------------------------------------------------
 *
 * - slot == CUSTOM with blank/missing customLabel:
 *   - blocked (throws) to prevent “nameless” CUSTOM meals.
 *
 * - dateIso format:
 *   - this use case only checks non-blank; callers must supply valid ISO yyyy-MM-dd.
 *   - planner correctness relies on ISO-date-based membership across the system.
 *
 * ----------------------------------------------------------------------------
 * Pitfalls / gotchas
 * ----------------------------------------------------------------------------
 *
 * - Do NOT convert dateIso from timestamps here.
 *   Day membership is ISO-date-based (not time-window-based) to prevent Day Log style bugs.
 *
 * - Do NOT add read-before-write logic to compute “max sort order”.
 *   The Int.MAX_VALUE append pattern is intentional for performance and determinism.
 *
 * ----------------------------------------------------------------------------
 * Architectural rules
 * ----------------------------------------------------------------------------
 *
 * - Domain write use case: no UI state mutation, no navigation.
 * - Repository boundary only (PlannedMealRepository).
 * - PlannedDay is derived; do not persist a separate “day” row here.
 */
class CreatePlannedMealUseCase @Inject constructor(
    private val meals: PlannedMealRepository
) {
    suspend operator fun invoke(
        dateIso: String,
        slot: MealSlot,
        customLabel: String? = null,
        nameOverride: String? = null,
        sortOrder: Int? = null,
        seriesId: Long? = null,
        status: String = PlannedOccurrenceStatus.ACTIVE.name
    ): Long {
        require(dateIso.isNotBlank()) { "dateIso must not be blank" }

        val normalizedCustomLabel: String? = when (slot) {
            MealSlot.CUSTOM -> {
                val label = customLabel?.trim().orEmpty()
                require(label.isNotBlank()) { "customLabel is required when slot == CUSTOM" }
                label
            }
            else -> null
        }
        val finalSortOrder = sortOrder ?: Int.MAX_VALUE

        val entity = PlannedMealEntity(
            date = dateIso,
            slot = slot,
            customLabel = normalizedCustomLabel,
            nameOverride = nameOverride?.trim()?.takeIf { it.isNotBlank() },
            sortOrder = finalSortOrder,
            seriesId = seriesId,
            status = status,
        )

        return meals.insert(entity)
    }
}

/**
 * =============================================================================
 * FOR FUTURE AI ASSISTANT — CreatePlannedMealUseCase invariants and boundaries
 * =============================================================================
 *
 * Invariants (MUST NOT CHANGE)
 * - dateIso is the authoritative day key and must remain ISO yyyy-MM-dd across planner features.
 * - CUSTOM slot requires a non-blank customLabel; non-CUSTOM must store customLabel as null.
 * - Default sortOrder is Int.MAX_VALUE (append semantics) to avoid read-before-write.
 * - Read ordering must remain compatible: ORDER BY (sortOrder ASC, id ASC).
 *
 * Do not refactor / do not “improve”
 * - Do NOT “helpfully” parse timestamps into dateIso here. Callers choose the day.
 * - Do NOT add DB reads to compute sort order unless the entire ordering model changes.
 * - Do NOT enforce additional constraints here that belong to higher layers
 *   (e.g., “only one meal per slot per day” if you later support duplicates).
 *
 * Architectural boundaries
 * - This use case writes only PlannedMealEntity via PlannedMealRepository.
 * - PlannedDay is derived (date → meals → items). Do not introduce a PlannedDay table here.
 *
 * Migration notes
 * - If moving to KMP/time APIs later, keep the stored key as ISO yyyy-MM-dd String.
 *   (LocalDate.toString() format) remains the system’s stable day identity.
 *
 * Performance notes
 * - Must remain O(1) insert by default.
 */