package com.example.adobongkangkong.data.repository

import com.example.adobongkangkong.data.local.db.dao.SummaryDao
import com.example.adobongkangkong.domain.model.MacroTotals
import com.example.adobongkangkong.domain.nutrition.NutrientCodes
import com.example.adobongkangkong.domain.repository.SummaryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

class SummaryRepositoryImpl @Inject constructor(
    private val summaryDao: SummaryDao
) : SummaryRepository {

    override fun observeMacroTotals(
        startInclusive: Instant,
        endExclusive: Instant
    ): Flow<MacroTotals> {
        return summaryDao.observeTotalsByNutrientCode(startInclusive, endExclusive)
            .map { rows ->
                fun amount(code: String): Double =
                    rows.firstOrNull { it.nutrientCode == code }?.totalAmount ?: 0.0

                MacroTotals(
                    caloriesKcal = amount(NutrientCodes.CALORIES),
                    proteinG = amount(NutrientCodes.PROTEIN),
                    carbsG = amount(NutrientCodes.CARBS),
                    fatG = amount(NutrientCodes.FAT)
                )
            }
    }
}
