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
 * Ensures that planned meal occurrences exist for a given series within a bounded ISO date window.
 *
 * - Reads recurrence rules from planned_series + planned_series_slot_rules
 * - Creates missing planned_meals rows with seriesId set (occurrence layer)
 * - Idempotent: safe to call repeatedly
 *
 * NOTE: This creates meals (occurrences) and copies series template items into newly created meals.
 * It does NOT overwrite existing meals.
 */
class EnsureSeriesOccurrencesWithinHorizonUseCase @Inject constructor(
    private val seriesRepo: PlannedSeriesRepository,
    private val mealsRepo: PlannedMealRepository,
    private val createPlannedMealFromTemplate: CreatePlannedMealFromSeriesTemplateUseCase,
) {

    /**
     * @param startDateIso yyyy-MM-dd inclusive
     * @param endDateIso yyyy-MM-dd inclusive
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
 * Pure expansion helper for weekday->slot rules over an inclusive date window.
 * Returns keys as (dateIso, slot, customLabelOrEmpty).
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