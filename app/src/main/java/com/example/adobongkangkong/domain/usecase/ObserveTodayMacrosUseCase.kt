package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.core.time.todayRange
import com.example.adobongkangkong.domain.model.MacroTotals
import com.example.adobongkangkong.domain.repository.SummaryRepository
import kotlinx.coroutines.flow.Flow
import java.time.ZoneId
import javax.inject.Inject

class ObserveTodayMacrosUseCase @Inject constructor(
    private val summaryRepository: SummaryRepository
) {
    operator fun invoke(zoneId: ZoneId = ZoneId.systemDefault()): Flow<MacroTotals> {
        val range = todayRange(zoneId)
        return summaryRepository.observeMacroTotals(range.startInclusive, range.endExclusive)
    }
}