package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.domain.model.MacroTotals
import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.nutrition.NutrientMap
import com.example.adobongkangkong.domain.repository.LogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Observes macro totals for a single calendar day from immutable snapshot logs.
 *
 * ## What this use case does
 * - Reads logged entries for exactly one day.
 * - Sums the stored `nutrients: NutrientMap` from each log entry (snapshot-at-log-time).
 * - Emits a [MacroTotals] object with calories/protein/carbs/fat.
 *
 * ## Why snapshots (and no joins)
 * Logs store nutrient snapshots so historical totals remain correct even if:
 * - the underlying food is edited,
 * - foods/recipes are deleted,
 * - nutrient definitions change later.
 *
 * ## Critical rule for correctness (Day Log bug prevention)
 * Day membership is determined ONLY by `logDateIso` (yyyy-MM-dd).
 * This use case must NOT use timestamp windows to decide which day an entry belongs to.
 *
 * ## Notes on ZoneId
 * - When a [LocalDate] is explicitly provided, the caller has already chosen the calendar day.
 *   In that case, [zoneId] does not affect which logs are included (we query by `date.toString()`).
 * - [zoneId] is used only by the convenience overload that computes "today".
 */
class ObserveTodayMacrosUseCase @Inject constructor(
    private val logRepository: LogRepository,
) {

    /**
     * Observes macro totals for the provided calendar day.
     *
     * Day membership is defined as:
     * `log_entries.logDateIso == date.toString()` (yyyy-MM-dd)
     *
     * @param date The calendar day to summarize.
     * @param zoneId Kept for call-site compatibility; does not affect day membership when [date] is provided.
     * @return A stream of [MacroTotals] for that day, updated whenever underlying log rows change.
     */
    @Suppress("UNUSED_PARAMETER")
    operator fun invoke(
        date: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Flow<MacroTotals> {
        val dateIso = date.toString() // yyyy-MM-dd

        return logRepository.observeDay(dateIso)
            .map { logs ->
                if (logs.isEmpty()) return@map MacroTotals()

                val totals: NutrientMap =
                    logs.fold(NutrientMap.EMPTY) { acc, log -> acc + log.nutrients }

                MacroTotals(
                    caloriesKcal = totals[NutrientKey.CALORIES_KCAL] ?: 0.0,
                    proteinG = totals[NutrientKey.PROTEIN_G] ?: 0.0,
                    carbsG = totals[NutrientKey.CARBS_G] ?: 0.0,
                    fatG = totals[NutrientKey.FAT_G] ?: 0.0,
                )
            }
    }

    /**
     * Observes macro totals for "today" in the provided [zoneId].
     *
     * @param zoneId The timezone used to compute "today" as a [LocalDate].
     * @return A stream of [MacroTotals] for today.
     */
    operator fun invoke(
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Flow<MacroTotals> = invoke(LocalDate.now(zoneId), zoneId)
}