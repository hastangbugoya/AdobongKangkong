package com.example.adobongkangkong.data.repository

import com.example.adobongkangkong.data.local.db.dao.LogEntryDao
import com.example.adobongkangkong.data.local.db.entity.LogEntryEntity
import com.example.adobongkangkong.domain.model.LogEntry
import com.example.adobongkangkong.domain.repository.LogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

class LogRepositoryImpl @Inject constructor(
    private val dao: LogEntryDao
) : LogRepository {

    override suspend fun insert(entry: LogEntry) {
        dao.insert(entry.toEntity())
    }

    override fun observeRange(
        startInclusive: Instant,
        endExclusive: Instant
    ): Flow<List<LogEntry>> =
        dao.observeRange(startInclusive, endExclusive)
            .map { list -> list.map { it.toDomain() } }
}

private fun LogEntry.toEntity() =
    LogEntryEntity(
        id = id,
        foodId = foodId,
        servings = servings,
        timestamp = timestamp,
    )

private fun LogEntryEntity.toDomain() =
    LogEntry(
        id = id,
        foodId = foodId,
        servings = servings,
        timestamp = timestamp
    )
