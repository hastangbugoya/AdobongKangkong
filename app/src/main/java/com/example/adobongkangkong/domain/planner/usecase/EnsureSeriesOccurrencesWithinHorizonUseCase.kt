package com.example.adobongkangkong.domain.planner.usecase

import com.example.adobongkangkong.data.local.db.entity.MealSlot
import com.example.adobongkangkong.data.local.db.entity.PlannedOccurrenceStatus
import com.example.adobongkangkong.data.local.db.entity.PlannedSeriesEndConditionType
import com.example.adobongkangkong.data.local.db.entity.PlannedSeriesSlotRuleEntity
import com.example.adobongkangkong.domain.repository.PlannedMealRepository
import com.example.adobongkangkong.domain.repository.PlannedSeriesRepository
import java.time.LocalDate
import javax.inject.Inject

/**
 * Ensures that concrete PlannedMeal occurrences exist for a series within an inclusive ISO date window.
 *
 * ----------------------------------------------------------------------------
 * Purpose
 * ----------------------------------------------------------------------------
 *
 * A Planned Series stores a recurrence definition (rules + end condition + template source),
 * but the planner UI operates on concrete rows:
 *
 * - planned_meals (the occurrence containers)
 * - planned_items (the occurrence contents)
 *
 * This use case materializes the occurrence layer by creating any missing planned_meals
 * for the given series within a bounded date range and copying template items into those meals.
 *
 * ----------------------------------------------------------------------------
 * Rationale (why this use case exists)
 * ----------------------------------------------------------------------------
 *
 * Recurrence expansion must be:
 *
 * - **idempotent** (safe to call repeatedly without duplicating meals),
 * - **bounded** (horizon-based, not “generate forever”),
 * - **non-destructive** (never overwrite user-edited occurrences),
 * - **ISO-date correct** (planner membership is based on yyyy-MM-dd strings, not timestamps).
 *
 * Centralizing this logic ensures every call site (planner navigation, “create series”, refresh)
 * produces the same deterministic results and avoids divergent recurrence bugs.
 *
 * ----------------------------------------------------------------------------
 * Behavior
 * ----------------------------------------------------------------------------
 *
 * - Loads the series and its slot rules.
 * - Computes an effective window by clamping the requested range against:
 *   - series.effectiveStartDate / series.effectiveEndDate
 *   - endConditionType (INDEFINITE / UNTIL_DATE / REPEAT_COUNT)
 * - Loads existing planned_meals for the series in that final window (single DB read)
 *   and builds a dedupe key set.
 * - Iterates day-by-day over the final window:
 *   - expands weekday rules into target (dateIso, slot, customLabelNormalized) keys
 *   - creates missing planned meals only
 *   - copies template items only for newly created meals
 * - Returns the number of newly created occurrences.
 *
 * ----------------------------------------------------------------------------
 * Parameters
 * ----------------------------------------------------------------------------
 * @param seriesId The series definition to expand.
 * @param startDateIso ISO yyyy-MM-dd inclusive start.
 * @param endDateIso ISO yyyy-MM-dd inclusive end.
 *
 * ----------------------------------------------------------------------------
 * Return
 * ----------------------------------------------------------------------------
 * @return The number of newly created planned meal occurrences in the final clamped window.
 *
 * ----------------------------------------------------------------------------
 * Edge cases handled
 * ----------------------------------------------------------------------------
 *
 * - Series not found → returns 0 (no-op).
 * - No slot rules → returns 0 (no-op).
 * - Requested window invalid (end < start) → throws (programmer error).
 * - Requested window outside series effective dates → returns 0 (no-op after clamp).
 * - Series UNTIL_DATE earlier than requested end → clamp end to until date.
 * - Series REPEAT_COUNT with invalid/missing count → treated as no additional clamp (legacy-safe).
 *
 * ----------------------------------------------------------------------------
 * Pitfalls / gotchas
 * ----------------------------------------------------------------------------
 *
 * - **Idempotency key**:
 *   Dedupe is based on (dateIso, slot, customLabelOrEmpty). If customLabel normalization rules
 *   change elsewhere, dedupe behavior can change.
 *
 * - **Does not overwrite**:
 *   Existing planned meals are never modified. If a user edits an occurrence, it stays edited.
 *
 * - **REPEAT_COUNT interpretation is phase-1**:
 *   Current implementation treats REPEAT_COUNT as “N days from windowStart” (not N occurrences),
 *   because “occurrences” is ambiguous when multiple slot rules exist.
 *
 * - **Template name override**:
 *   nameOverride is derived from series.sourceMealId’s meal.nameOverride at expansion time.
 *   If the source meal changes later, future expansions may pick up a different nameOverride.
 *
 * ----------------------------------------------------------------------------
 * Architectural rules
 * ----------------------------------------------------------------------------
 *
 * - No UI state mutation, no navigation.
 * - Writes occur only via CreatePlannedMealFromSeriesTemplateUseCase.
 * - Planner date membership is ISO-string based; do not introduce timestamp window logic here.
 * - Occurrence creation must remain bounded by a caller-chosen horizon/window.
 *
 * ----------------------------------------------------------------------------
 * Limitations (intentional for now)
 * ----------------------------------------------------------------------------
 *
 * - No cancellation/skip rules here (e.g., “skip specific date occurrence”).
 * - No “regen” or overwrite behavior for already-created meals.
 * - No per-occurrence status propagation beyond setting ACTIVE on new occurrences.
 *
 * ----------------------------------------------------------------------------
 * Future improvements (do NOT implement here without revisiting invariants)
 * ----------------------------------------------------------------------------
 *
 * - Define REPEAT_COUNT semantics precisely (count occurrences vs count days),
 *   and migrate existing series if behavior changes.
 * - Support per-occurrence override/cancellation rows and incorporate them into dedupe logic.
 * - Add trimming/normalization of customLabel consistently at write time to stabilize keys.
 */
class EnsureSeriesOccurrencesWithinHorizonUseCase @Inject constructor(
    private val seriesRepo: PlannedSeriesRepository,
    private val mealsRepo: PlannedMealRepository,
    private val createPlannedMealFromTemplate: CreatePlannedMealFromSeriesTemplateUseCase,
) {

    /**
     * Ensures occurrences for [seriesId] exist between [startDateIso] and [endDateIso] (inclusive).
     *
     * Day membership is determined by ISO date strings, not timestamps.
     *
     * @param startDateIso yyyy-MM-dd inclusive
     * @param endDateIso yyyy-MM-dd inclusive
     * @return count of newly created meals (existing meals are not modified)
     */
    suspend fun execute(seriesId: Long, startDateIso: String, endDateIso: String): Int {
        require(startDateIso.isNotBlank()) { "startDateIso must not be blank" }
        require(endDateIso.isNotBlank()) { "endDateIso must not be blank" }

        val series = seriesRepo.getSeriesById(seriesId)
            ?: return 0

        val slotRules = seriesRepo.getSlotRulesForSeries(seriesId)
        if (slotRules.isEmpty()) return 0

        val templateNameOverride = series.sourceMealId
            ?.let { mealsRepo.getById(it)?.nameOverride }

        // Clamp requested window to series effective dates
        val requestedStart = LocalDate.parse(startDateIso)
        val requestedEnd = LocalDate.parse(endDateIso)
        require(!requestedEnd.isBefore(requestedStart)) { "endDateIso must be >= startDateIso" }

        val seriesStart = LocalDate.parse(series.effectiveStartDate)
        val seriesEnd: LocalDate? = series.effectiveEndDate?.let { LocalDate.parse(it) }

        var windowStart = maxOf(requestedStart, seriesStart)
        var windowEnd = requestedEnd
        if (seriesEnd != null) windowEnd = minOf(windowEnd, seriesEnd)

        if (windowEnd.isBefore(windowStart)) return 0

        // Further clamp by endCondition (UNTIL_DATE / REPEAT_COUNT / INDEFINITE)
        windowEnd = applyEndConditionClamp(series.endConditionType, series.endConditionValue, windowStart, windowEnd)

        if (windowEnd.isBefore(windowStart)) return 0

        // Load existing occurrences for dedupe (single DB hit)
        val existing = mealsRepo.getMealsForSeriesInRange(seriesId, windowStart.toString(), windowEnd.toString())

        // Key by (dateIso, slotName, customLabelNormalized)
        val existingKeys = existing.asSequence()
            .map { Triple(it.date, it.slot, it.customLabel ?: "") }
            .toHashSet()

        // Build weekday -> list of rules for that weekday (usually 0..few)
        val rulesByWeekday = slotRules.groupBy { it.weekday }

        var createdCount = 0
        var d = windowStart
        while (!d.isAfter(windowEnd)) {
            val weekday = d.dayOfWeek.value // ISO 1=Mon..7=Sun

            val rulesForDay = rulesByWeekday[weekday].orEmpty()
            if (rulesForDay.isNotEmpty()) {
                val dateIso = d.toString()
                for (r in rulesForDay) {
                    val key = Triple(dateIso, r.slot, (r.customLabel ?: ""))
                    if (!existingKeys.contains(key)) {

                        // Create + copy template items ONLY for newly created meals.
                        // Transactional inside CreatePlannedMealFromSeriesTemplateUseCase for idempotency.
                        createPlannedMealFromTemplate(
                            dateIso = dateIso,
                            slot = r.slot,
                            customLabel = r.customLabel,
                            nameOverride = templateNameOverride,
                            sortOrder = null,
                            seriesId = seriesId,
                            status = PlannedOccurrenceStatus.ACTIVE.name
                        )

                        existingKeys.add(key)
                        createdCount++
                    }
                }
            }
            d = d.plusDays(1)
        }

        return createdCount
    }

    private fun applyEndConditionClamp(
        endConditionType: String,
        endConditionValue: String?,
        windowStart: LocalDate,
        windowEnd: LocalDate
    ): LocalDate {
        return when (endConditionType) {
            PlannedSeriesEndConditionType.INDEFINITE -> windowEnd

            PlannedSeriesEndConditionType.UNTIL_DATE -> {
                val until = endConditionValue?.let { LocalDate.parse(it) } ?: windowEnd
                minOf(windowEnd, until)
            }

            PlannedSeriesEndConditionType.REPEAT_COUNT -> {
                // Repeat N times in terms of OCCURRENCES is ambiguous when multiple slot rules exist.
                // Phase 1 interpretation: cap by DAYS from windowStart (N days).
                val nDays = endConditionValue?.toIntOrNull()
                if (nDays == null || nDays <= 0) windowEnd
                else {
                    val cap = windowStart.plusDays((nDays - 1).toLong())
                    minOf(windowEnd, cap)
                }
            }

            else -> windowEnd
        }
    }
}

/**
 * Computes the set of target occurrence keys implied by slot rules over an inclusive date window.
 *
 * ----------------------------------------------------------------------------
 * Purpose
 * ----------------------------------------------------------------------------
 *
 * This is a pure helper used to expand:
 *
 * - (weekday, slot, customLabel) rules
 *
 * into:
 *
 * - (dateIso, slot, customLabelOrEmpty) keys
 *
 * without performing any IO.
 *
 * It is useful for:
 *
 * - unit testing expansion behavior
 * - debugging series schedules
 * - precomputing target sets for diffing against existing occurrences
 *
 * ----------------------------------------------------------------------------
 * Behavior
 * ----------------------------------------------------------------------------
 *
 * - Iterates from [windowStart] to [windowEnd] inclusive.
 * - For each date, selects the rules matching date.dayOfWeek.value (ISO 1=Mon..7=Sun).
 * - Emits a Triple(dateIso, slot, customLabelOrEmpty) for each rule hit.
 *
 * ----------------------------------------------------------------------------
 * Parameters
 * ----------------------------------------------------------------------------
 * @param windowStart Inclusive start date.
 * @param windowEnd Inclusive end date.
 * @param slotRules Series slot rules (weekday-based).
 *
 * ----------------------------------------------------------------------------
 * Return
 * ----------------------------------------------------------------------------
 * @return List of all target keys implied by the rules within the window.
 *
 * ----------------------------------------------------------------------------
 * Gotchas
 * ----------------------------------------------------------------------------
 * - No dedupe: if slotRules contains duplicates, output will contain duplicates.
 *   (CreatePlannedSeriesUseCase blocks duplicates; this helper assumes valid input.)
 */
internal fun computeTargetKeysForWindow(
    windowStart: LocalDate,
    windowEnd: LocalDate,
    slotRules: List<PlannedSeriesSlotRuleEntity>
): List<Triple<String, MealSlot, String>> {
    if (slotRules.isEmpty()) return emptyList()

    val rulesByWeekday = slotRules.groupBy { it.weekday }

    val out = ArrayList<Triple<String, MealSlot, String>>(16)
    var d = windowStart
    while (!d.isAfter(windowEnd)) {
        val weekday = d.dayOfWeek.value // ISO 1=Mon..7=Sun
        val rulesForDay = rulesByWeekday[weekday].orEmpty()
        if (rulesForDay.isNotEmpty()) {
            val dateIso = d.toString()
            for (r in rulesForDay) {
                out.add(Triple(dateIso, r.slot, r.customLabel ?: ""))
            }
        }
        d = d.plusDays(1)
    }
    return out
}

/**
 * =============================================================================
 * FOR FUTURE AI ASSISTANT — EnsureSeriesOccurrencesWithinHorizonUseCase invariants
 * =============================================================================
 *
 * Invariants (MUST NOT CHANGE)
 * - Must be idempotent: repeated calls must not duplicate meals.
 * - Must never overwrite existing planned meals or their items.
 * - Must clamp expansion window by:
 *   1) requested start/end
 *   2) series effectiveStart/effectiveEnd
 *   3) endConditionType/value
 * - Must use ISO weekday convention: LocalDate.dayOfWeek.value (1=Mon..7=Sun).
 * - Must use ISO date strings for membership: yyyy-MM-dd via LocalDate.toString().
 *
 * Do not refactor / do not “improve”
 * - Do NOT switch to timestamp-based filtering or time-zone windows.
 * - Do NOT make this generate “forever”; keep caller-bounded horizon semantics.
 * - Do NOT reinterpret REPEAT_COUNT without migrating existing data and updating docs/tests.
 *
 * Architectural boundaries
 * - This use case is an occurrence materializer (definition → concrete rows).
 * - Concrete meal/item creation is delegated to CreatePlannedMealFromSeriesTemplateUseCase
 *   (which must remain transactional for correctness under concurrent calls).
 *
 * Performance considerations
 * - Must keep “existing occurrences” load to a single repository call per execute().
 * - The loop is O(days * rules-per-day). Keep rules-per-day small and validated upstream.
 * - If horizons grow large in the future, consider chunking by week or using precomputed keys.
 */