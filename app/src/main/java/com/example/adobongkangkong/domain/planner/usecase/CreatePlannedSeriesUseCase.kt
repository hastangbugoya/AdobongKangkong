package com.example.adobongkangkong.domain.planner.usecase

import com.example.adobongkangkong.data.local.db.entity.MealSlot
import com.example.adobongkangkong.data.local.db.entity.PlannedSeriesEndConditionType
import com.example.adobongkangkong.data.local.db.entity.PlannedSeriesEntity
import com.example.adobongkangkong.data.local.db.entity.PlannedSeriesSlotRuleEntity
import com.example.adobongkangkong.domain.repository.PlannedSeriesRepository
import javax.inject.Inject

/**
 * Creates a Planned Series (recurring planned-meal generator definition) and its slot rules.
 *
 * ----------------------------------------------------------------------------
 * Purpose
 * ----------------------------------------------------------------------------
 *
 * A **Planned Series** is a recurrence definition that says:
 *
 * - “Starting at date X…”
 * - “Repeat on these weekday + meal-slot combinations…”
 * - “Until a date / for N occurrences / indefinitely…”
 * - “Using this existing planned meal as the template source…”
 *
 * The series itself does **not** represent concrete meals on specific dates.
 * Instead, it stores recurrence rules so a separate expansion process can materialize
 * (or re-materialize) concrete PlannedMeal + PlannedItem rows for a date range.
 *
 * ----------------------------------------------------------------------------
 * Rationale (why this use case exists)
 * ----------------------------------------------------------------------------
 *
 * Recurrence is a cross-cutting concern that must be consistent across:
 *
 * - planner day rendering
 * - “make this a series” flows
 * - future “regenerate occurrences” flows
 * - future editing of a series (rules and end conditions)
 *
 * This use case centralizes:
 *
 * - validation of end conditions and slot rules
 * - stable persistence shape (PlannedSeriesEntity + PlannedSeriesSlotRuleEntity)
 * - atomic “insert series then replace rules” write sequence
 *
 * It prevents ViewModels/UI from:
 *
 * - manually constructing DB entities,
 * - duplicating rule validation,
 * - inventing inconsistent interpretations of “weekday”, “CUSTOM label”, etc.
 *
 * ----------------------------------------------------------------------------
 * Behavior
 * ----------------------------------------------------------------------------
 *
 * - Validates [Input] for internal consistency:
 *   - slotRules non-empty
 *   - endConditionValue presence matches endConditionType requirements
 *   - weekday range is 1..7 (Mon..Sun)
 *   - CUSTOM slot requires customLabel
 *   - no duplicate (weekday, slot) rules
 * - Writes a new [PlannedSeriesEntity] with created/updated timestamps.
 * - Converts slot rule inputs into [PlannedSeriesSlotRuleEntity] rows and persists them
 *   via replaceSlotRules(seriesId, rules).
 *
 * ----------------------------------------------------------------------------
 * Parameters
 * ----------------------------------------------------------------------------
 *
 * @param input Aggregated inputs required to create a series + its slot rules.
 *
 * ----------------------------------------------------------------------------
 * Return
 * ----------------------------------------------------------------------------
 *
 * @return Newly created seriesId.
 *
 * ----------------------------------------------------------------------------
 * Edge cases
 * ----------------------------------------------------------------------------
 *
 * - INDEFINITE end condition:
 *   - endConditionValue may be provided by caller; validation allows it but the system
 *     should treat it as ignored (caller responsibility to keep null).
 *
 * - UNTIL_DATE end condition:
 *   - this use case validates only that endConditionValue is non-blank.
 *   - callers must ensure it is a valid ISO yyyy-MM-dd.
 *
 * - REPEAT_COUNT end condition:
 *   - this use case validates only that endConditionValue is non-blank.
 *   - callers must ensure it is parseable and positive.
 *
 * ----------------------------------------------------------------------------
 * Pitfalls / gotchas
 * ----------------------------------------------------------------------------
 *
 * - weekday is 1=Mon..7=Sun (ISO-like), NOT Java Calendar constants.
 * - Duplicate slot rules are blocked to prevent ambiguous expansion behavior.
 * - This use case does NOT create occurrences (PlannedMealEntity rows for future dates).
 *   It only writes the recurrence definition. Expansion is a separate operation.
 *
 * - sourceMealId must refer to an existing planned meal that acts as the template.
 *   This use case does not verify existence; caller / higher layer should ensure it.
 *
 * ----------------------------------------------------------------------------
 * Architectural rules
 * ----------------------------------------------------------------------------
 *
 * - Domain write use case: no UI state mutation, no navigation.
 * - Repository boundary only (PlannedSeriesRepository).
 * - Separation of concerns:
 *   - Series definition persistence here
 *   - Series expansion / occurrence materialization elsewhere
 *
 * - Planner date membership is ISO-string based; this use case stores ISO strings and
 *   must not introduce timestamp-based date logic.
 */
class CreatePlannedSeriesUseCase @Inject constructor(
    private val repo: PlannedSeriesRepository
) {

    /**
     * Input describing a single weekly slot rule for a series.
     *
     * A “slot rule” defines one occurrence pattern such as:
     * - Monday + Lunch
     * - Friday + CUSTOM("Brunch")
     *
     * Fields
     * - [weekday]: 1=Mon .. 7=Sun (must remain stable for expansion logic).
     * - [slot]: planned meal slot (Breakfast/Lunch/Dinner/Snack/CUSTOM).
     * - [customLabel]: required only when slot == CUSTOM; otherwise should be null.
     */
    data class SlotRuleInput(
        val weekday: Int,          // 1=Mon..7=Sun
        val slot: MealSlot,
        val customLabel: String? = null
    )

    /**
     * Aggregated input for creating a series.
     *
     * Fields (high-level meaning)
     *
     * - [effectiveStartDateIso]:
     *   First ISO day (yyyy-MM-dd) the series may generate occurrences for.
     *
     * - [effectiveEndDateIso]:
     *   Optional ISO day (yyyy-MM-dd) after which the series should never generate occurrences.
     *   This is an additional clamp separate from endConditionType/value.
     *
     * - [endConditionType]:
     *   Strongly typed recurrence end condition:
     *   UNTIL_DATE / REPEAT_COUNT / INDEFINITE.
     *
     * - [endConditionValue]:
     *   Meaning depends on [endConditionType]:
     *   - UNTIL_DATE: an ISO yyyy-MM-dd (non-blank required)
     *   - REPEAT_COUNT: a count value as string (non-blank required)
     *   - INDEFINITE: should be null (allowed but ignored if provided)
     *
     * - [slotRules]:
     *   Weekly schedule rules (must be non-empty, must not contain duplicates).
     *
     * - [sourceMealId]:
     *   The planned meal that serves as the template source for generating occurrences.
     *   (The source meal itself is NOT required to be part of the series.)
     *
     * - [nameOverride]:
     *   Human label for the series (UI-facing). Currently not persisted into the series entity
     *   in this file; callers typically store/display this elsewhere or will add persistence later.
     */
    data class Input(
        val effectiveStartDateIso: String,   // yyyy-MM-dd
        val effectiveEndDateIso: String? = null,
        val endConditionType: PlannedSeriesEndConditionType,
        val endConditionValue: String? = null,
        val slotRules: List<SlotRuleInput>,
        val sourceMealId: Long,
        val nameOverride: String
    )

    /**
     * Creates the series definition and its slot rules.
     *
     * @param input The full series definition to persist.
     * @return seriesId of the newly inserted series.
     */
    suspend fun execute(input: Input): Long {
        validate(input)

        val now = System.currentTimeMillis()

        val seriesId = repo.insertSeries(
            PlannedSeriesEntity(
                effectiveStartDate = input.effectiveStartDateIso,
                effectiveEndDate = input.effectiveEndDateIso,
                endConditionType = input.endConditionType,
                endConditionValue = input.endConditionValue,
                sourceMealId = input.sourceMealId,
                createdAtEpochMs = now,
                updatedAtEpochMs = now
            )
        )

        val rules = input.slotRules.map { r ->
            PlannedSeriesSlotRuleEntity(
                seriesId = seriesId,
                weekday = r.weekday,
                slot = r.slot,
                customLabel = r.customLabel,
                createdAtEpochMs = now
            )
        }

        repo.replaceSlotRules(seriesId, rules)
        return seriesId
    }

    private fun validate(input: Input) {
        require(input.slotRules.isNotEmpty()) { "slotRules cannot be empty" }

        when (input.endConditionType) {
            PlannedSeriesEndConditionType.UNTIL_DATE -> require(!input.endConditionValue.isNullOrBlank()) {
                "endConditionValue (until date ISO) required for UNTIL_DATE"
            }

            PlannedSeriesEndConditionType.REPEAT_COUNT -> require(!input.endConditionValue.isNullOrBlank()) {
                "endConditionValue (repeat count) required for REPEAT_COUNT"
            }

            PlannedSeriesEndConditionType.INDEFINITE -> {
                // endConditionValue should be null; allow but ignore if passed
            }
        }

        input.slotRules.forEach { r ->
            require(r.weekday in 1..7) { "weekday must be 1..7, was ${r.weekday}" }
            if (r.slot == MealSlot.CUSTOM) {
                require(!r.customLabel.isNullOrBlank()) { "customLabel required when slot == CUSTOM" }
            }
        }

        // Prevent duplicates like (Mon,LUNCH) appearing twice
        val dedupeKey = input.slotRules.map { it.weekday to it.slot }
        require(dedupeKey.size == dedupeKey.distinct().size) { "Duplicate (weekday, slot) rules not allowed" }
    }
}

/**
 * =============================================================================
 * FOR FUTURE AI ASSISTANT — CreatePlannedSeriesUseCase invariants and boundaries
 * =============================================================================
 *
 * Invariants (MUST NOT CHANGE)
 * - weekday uses 1=Mon..7=Sun. Expansion logic depends on this exact convention.
 * - slotRules must be non-empty.
 * - (weekday, slot) pairs must be unique (no duplicates).
 * - CUSTOM slot requires non-blank customLabel.
 * - endConditionType must be one of:
 *   - UNTIL_DATE
 *   - REPEAT_COUNT
 *   - INDEFINITE
 * - endConditionValue is required (non-blank) for UNTIL_DATE and REPEAT_COUNT.
 * - This use case ONLY persists series definition + rules. It does not generate occurrences.
 *
 * Do not refactor / do not “improve”
 * - Do NOT convert weekday to different conventions (e.g., Sunday=1).
 * - Do NOT materialize planned meals/items here; keep expansion separate.
 * - Do NOT replace endConditionType/endConditionValue with different storage without
 *   migrating the DB and all expansion logic.
 *
 * Architectural boundaries
 * - Writes only via PlannedSeriesRepository.
 * - No UI state, no navigation, no logging.
 *
 * Migration notes
 * - Series stores ISO yyyy-MM-dd strings. Keep this stable if moving to KMP date APIs:
 *   LocalDate.toString() format must remain the canonical persisted day identity.
 *
 * Performance notes
 * - execute() is O(rules) and should remain a simple insert + replace rules write.
 * - Avoid adding reads here (e.g., checking sourceMealId existence) unless required and
 *   carefully justified; expansion/use layers can handle missing source by blocking/warning.
 */