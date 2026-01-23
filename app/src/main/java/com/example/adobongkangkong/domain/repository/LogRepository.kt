package com.example.adobongkangkong.domain.repository

import com.example.adobongkangkong.domain.model.LogEntry
import com.example.adobongkangkong.domain.model.TodayLogItem
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface LogRepository {

    suspend fun insert(entry: LogEntry)

    fun observeRange(
        startInclusive: Instant,
        endExclusive: Instant
    ): Flow<List<LogEntry>>

    fun observeTodayItems(
        startInclusive: Instant,
        endExclusive: Instant
    ): Flow<List<TodayLogItem>>

    suspend fun deleteById(logId: Long)

}
