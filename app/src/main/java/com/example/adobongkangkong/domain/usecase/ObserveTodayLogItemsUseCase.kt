package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.core.time.todayRange
import com.example.adobongkangkong.domain.model.TodayLogItem
import com.example.adobongkangkong.domain.repository.LogRepository
import kotlinx.coroutines.flow.Flow
import java.time.ZoneId
import javax.inject.Inject

class ObserveTodayLogItemsUseCase @Inject constructor(
    private val logRepository: LogRepository
) {
    operator fun invoke(zoneId: ZoneId = ZoneId.systemDefault()): Flow<List<TodayLogItem>> {
        val range = todayRange(zoneId)
        return logRepository.observeTodayItems(range.startInclusive, range.endExclusive)
    }
}