package com.example.adobongkangkong.domain.shared.usecase

import com.example.adobongkangkong.data.csvimport.CsvNutrientCatalog
import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.repository.UserPinnedNutrientRepository
import com.example.adobongkangkong.domain.shared.model.SharedNutritionMonthDaySummary
import com.example.adobongkangkong.domain.shared.model.SharedNutritionMonthSummary
import com.example.adobongkangkong.domain.shared.model.SharedPinnedNutrientAmount
import com.example.adobongkangkong.domain.usecase.ObserveMonthlyNutritionTotalsUseCase
import kotlinx.coroutines.flow.first
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/**
 * BuildSharedNutritionMonthSnapshotUseCase
 *
 * Produces a compact month-level shared nutrition payload for calendar hydration.
 *
 * Critical rule:
 * - mapping only
 * - no new nutrition math
 * - no UI-layer dependencies
 */
class BuildSharedNutritionMonthSnapshotUseCase @Inject constructor(
    private val observeMonthlyNutritionTotalsUseCase: ObserveMonthlyNutritionTotalsUseCase,
    private val userPinnedNutrientRepository: UserPinnedNutrientRepository
) {

    private val unitByCode: Map<String, String> =
        CsvNutrientCatalog.defs.associate { it.code to it.unit }

    suspend operator fun invoke(
        month: YearMonth,
        zoneId: ZoneId
    ): SharedNutritionMonthSummary {
        val monthTotals = observeMonthlyNutritionTotalsUseCase(
            month = month,
            zoneId = zoneId
        ).first()

        val pinnedKeys = userPinnedNutrientRepository.observePinnedKeys().first()

        val days = monthTotals.map { dailyTotals ->
            val totalsMap = dailyTotals.totalsByCode

            SharedNutritionMonthDaySummary(
                dateIso = dailyTotals.date.toString(),
                caloriesKcal = totalsMap[NutrientKey.CALORIES_KCAL],
                proteinG = totalsMap[NutrientKey.PROTEIN_G],
                carbsG = totalsMap[NutrientKey.CARBS_G],
                fatG = totalsMap[NutrientKey.FAT_G],
                pinnedNutrients = pinnedKeys.mapNotNull { key ->
                    val amount = totalsMap[key]
                    if (amount == 0.0) {
                        null
                    } else {
                        SharedPinnedNutrientAmount(
                            nutrientCode = key.value,
                            amount = amount,
                            unit = resolveUnit(key)
                        )
                    }
                }
            )
        }

        return SharedNutritionMonthSummary(
            monthIso = month.toString(),
            days = days
        )
    }

    private fun resolveUnit(key: NutrientKey): String {
        return unitByCode[key.value] ?: ""
    }
}