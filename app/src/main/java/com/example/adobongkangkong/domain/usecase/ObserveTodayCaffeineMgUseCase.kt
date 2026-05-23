package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.nutrition.NutrientMap
import com.example.adobongkangkong.domain.repository.LogRepository
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Observes total caffeine for a single calendar day from immutable snapshot logs.
 *
 * ## Purpose
 * Provide a reactive stream of the day’s caffeine total by aggregating the caffeine nutrient
 * stored on normal food log entries.
 *
 * ## Caffeine truth source
 * - Uses [NutrientKey.CAFFEINE_MG] from each log entry's immutable [NutrientMap].
 * - Does not read current Food rows.
 * - Does not guess caffeine.
 * - Does not create or depend on caffeine-only records.
 *
 * ## Day membership
 * Day membership is defined by `logDateIso == date.toString()` only.
 * This mirrors [ObserveTodayMacrosUseCase] and avoids timezone/DST bugs.
 */
class ObserveTodayCaffeineMgUseCase @Inject constructor(
    private val logRepository: LogRepository,
) {

    /**
     * Observes caffeine total for the provided calendar day.
     *
     * @param date The calendar day to summarize.
     * @param zoneId Kept for call-site compatibility; does not affect explicit date membership.
     */
    @Suppress("UNUSED_PARAMETER")
    operator fun invoke(
        date: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Flow<Double> {
        val dateIso = date.toString()

        return logRepository.observeDay(dateIso)
            .map { logs ->
                if (logs.isEmpty()) return@map 0.0

                val totals: NutrientMap =
                    logs.fold(NutrientMap.EMPTY) { acc, log -> acc + log.nutrients }

                totals[NutrientKey.CAFFEINE_MG]
            }
    }

    /**
     * Observes caffeine total for "today" in the provided [zoneId].
     */
    operator fun invoke(
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Flow<Double> = invoke(LocalDate.now(zoneId), zoneId)
}

/**
 * FUTURE-AI / MAINTENANCE KDoc (Do not remove)
 *
 * Invariants:
 * - `logDateIso` is authoritative for day membership.
 * - Aggregation must use immutable `log.nutrients` snapshots.
 * - Missing caffeine must be treated as 0.0.
 * - Do not join to Food, Recipe, USDA, or Nutrient tables here.
 * - Do not introduce a separate caffeine logging table.
 */