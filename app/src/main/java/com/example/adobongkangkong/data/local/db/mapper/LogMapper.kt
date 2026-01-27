package com.example.adobongkangkong.data.local.db.mapper

import com.example.adobongkangkong.data.local.db.DbTypeConverters
import com.example.adobongkangkong.data.local.db.dao.TodayLogRow
import com.example.adobongkangkong.data.local.db.entity.LogEntryEntity
import com.example.adobongkangkong.domain.model.LogEntry
import com.example.adobongkangkong.domain.model.TodayLogItem
import com.example.adobongkangkong.domain.nutrition.MacroKeys
import com.example.adobongkangkong.domain.nutrition.NutrientMap


private fun TodayLogRow.toDomain(converters: DbTypeConverters): TodayLogItem {
    val nutrients: NutrientMap = converters.nutrientMapFromJson(nutrientsJson)

    return TodayLogItem(
        logId = logId,
        timestamp = timestamp,
        itemName = itemName,
        caloriesKcal = nutrients[MacroKeys.CALORIES],
        proteinG     = nutrients[MacroKeys.PROTEIN],
        carbsG       = nutrients[MacroKeys.CARBS],
        fatG         = nutrients[MacroKeys.FAT],
    )
}


internal fun LogEntry.toEntity(converters: DbTypeConverters): LogEntryEntity =
    LogEntryEntity(
        id = id,
        timestamp = timestamp,
        itemName = itemName,
        foodStableId = foodStableId,
        nutrientsJson =  converters.nutrientMapToJson(nutrients)
    )

internal fun LogEntryEntity.toDomain(converters: DbTypeConverters): LogEntry =
    LogEntry(
        id = id,
        timestamp = timestamp,
        itemName = itemName,
        foodStableId = foodStableId,
        nutrients = converters.nutrientMapFromJson(nutrientsJson)
    )