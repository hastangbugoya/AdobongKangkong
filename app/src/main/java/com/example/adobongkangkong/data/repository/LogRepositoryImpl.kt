package com.example.adobongkangkong.data.repository

import com.example.adobongkangkong.data.local.db.DbTypeConverters
import com.example.adobongkangkong.data.local.db.dao.LogEntryDao
import com.example.adobongkangkong.data.local.db.dao.TodayLogRow
import com.example.adobongkangkong.data.local.db.entity.LogEntryEntity
import com.example.adobongkangkong.domain.model.LogEntry
import com.example.adobongkangkong.domain.model.TodayLogItem
import com.example.adobongkangkong.domain.nutrition.MacroKeys
import com.example.adobongkangkong.domain.nutrition.NutrientMap
import com.example.adobongkangkong.domain.repository.LogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

class LogRepositoryImpl @Inject constructor(
    private val dao: LogEntryDao,
    private val converters: DbTypeConverters
) : LogRepository {

    override suspend fun insert(entry: LogEntry) {
        dao.insert(entry.toEntity(converters))
    }

    override fun observeRange(
        startInclusive: Instant,
        endExclusive: Instant
    ): Flow<List<LogEntry>> =
        dao.observeRange(startInclusive, endExclusive)
            .map { entities -> entities.map { it.toDomain(converters) } }

    override fun observeTodayItems(
        startInclusive: Instant,
        endExclusive: Instant
    ): Flow<List<TodayLogItem>> =
        dao.observeTodayLogRows(startInclusive, endExclusive)
            .map { rows -> rows.map { it.toDomain(converters) } }

    override suspend fun deleteById(logId: Long) {
        dao.deleteById(logId)
    }
}

private fun TodayLogRow.toDomain(converters: DbTypeConverters): TodayLogItem {
    // TodayLogRow stores snapshot totals as JSON
    val nutrients: NutrientMap = converters.nutrientMapFromJson(nutrientsJson)

    return TodayLogItem(
        logId = logId,
        timestamp = timestamp,
        itemName = itemName,
        caloriesKcal = nutrients[MacroKeys.CALORIES],
        proteinG = nutrients[MacroKeys.PROTEIN],
        carbsG = nutrients[MacroKeys.CARBS],
        fatG = nutrients[MacroKeys.FAT],
    )
}

private fun LogEntry.toEntity(converters: DbTypeConverters): LogEntryEntity =
    LogEntryEntity(
        id = id,
        timestamp = timestamp,
        itemName = itemName,
        foodStableId = foodStableId,
        nutrientsJson = converters.nutrientMapToJson(nutrients),
    )

private fun LogEntryEntity.toDomain(converters: DbTypeConverters): LogEntry =
    LogEntry(
        id = id,
        timestamp = timestamp,
        itemName = itemName,
        foodStableId = foodStableId,
        nutrients = converters.nutrientMapFromJson(nutrientsJson),
    )
