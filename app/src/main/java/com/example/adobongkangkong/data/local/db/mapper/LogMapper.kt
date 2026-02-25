package com.example.adobongkangkong.data.local.db.mapper

import com.example.adobongkangkong.data.local.db.DbTypeConverters
import com.example.adobongkangkong.data.local.db.dao.TodayLogRow
import com.example.adobongkangkong.data.local.db.entity.LogEntryEntity
import com.example.adobongkangkong.domain.model.LogEntry
import com.example.adobongkangkong.domain.model.TodayLogItem
import com.example.adobongkangkong.domain.nutrition.MacroKeys
import com.example.adobongkangkong.domain.nutrition.NutrientMap


internal fun TodayLogRow.toDomain(converters: DbTypeConverters): TodayLogItem {
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
        nutrientsJson = converters.nutrientMapToJson(nutrients),
        // ✅ NEW
        recipeBatchId = recipeBatchId,
        gramsPerServingCooked = gramsPerServingCooked,
        mealSlot = mealSlot,
        logDateIso = logDateIso,
    )

internal fun LogEntryEntity.toDomain(converters: DbTypeConverters): LogEntry =
    LogEntry(
        id = id,
        timestamp = timestamp,
        itemName = itemName,
        foodStableId = foodStableId,
        nutrients = converters.nutrientMapFromJson(nutrientsJson),
        // ✅ NEW
        recipeBatchId = recipeBatchId,
        gramsPerServingCooked = gramsPerServingCooked,
        mealSlot = mealSlot,
        logDateIso = logDateIso,
    )

internal fun LogEntry.toLogEntryEntity(converters: DbTypeConverters): LogEntryEntity =
    LogEntryEntity(
        id = id,
        timestamp = timestamp,
        logDateIso = logDateIso,      // keep if you added it to entity
        itemName = itemName,
        foodStableId = foodStableId,
//        amount = amount,              // keep if exists in entity
//        unit = unit,                  // keep if exists in entity
        recipeBatchId = recipeBatchId,
        gramsPerServingCooked = gramsPerServingCooked,
        mealSlot = mealSlot,
        nutrientsJson = converters.nutrientMapToJson(nutrients),
    )

internal fun LogEntryEntity.toDomainLogEntry(converters: DbTypeConverters): LogEntry =
    LogEntry(
        id = id,
        timestamp = timestamp,
        logDateIso = logDateIso,      // keep if you added it to domain
        itemName = itemName,
        foodStableId = foodStableId,
//        amount = amount,              // keep if exists in domain
//        unit = unit,                  // keep if exists in domain
        recipeBatchId = recipeBatchId,
        gramsPerServingCooked = gramsPerServingCooked,
        mealSlot = mealSlot,
        nutrients = converters.nutrientMapFromJson(nutrientsJson),
    )