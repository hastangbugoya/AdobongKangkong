package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.core.time.dayRange
import com.example.adobongkangkong.core.time.todayRange
import com.example.adobongkangkong.domain.model.LogEntry
import com.example.adobongkangkong.domain.model.MacroTotals
import com.example.adobongkangkong.domain.nutrition.MacroKeys
import com.example.adobongkangkong.domain.nutrition.NutrientCodes
import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.nutrition.NutrientMap
import com.example.adobongkangkong.domain.repository.LogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Observes today's macro totals from immutable snapshot logs.
 *
 * Logs contain `nutrients: NutrientMap` captured at log-time, so totals are computed by
 * summing those maps. This keeps history valid even if foods are edited/deleted later.
 */
class ObserveTodayMacrosUseCase @Inject constructor(
    private val logRepository: LogRepository,
) {

    operator fun invoke(
        date: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Flow<MacroTotals> {
        val range = dayRange(date, zoneId)

        return logRepository.observeRange(range.startInclusive, range.endExclusive)
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

    operator fun invoke(
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Flow<MacroTotals> = invoke(LocalDate.now(zoneId), zoneId)
}