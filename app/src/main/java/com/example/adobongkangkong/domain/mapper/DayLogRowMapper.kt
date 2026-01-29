package com.example.adobongkangkong.domain.mapper

import com.example.adobongkangkong.domain.model.LogEntry
import com.example.adobongkangkong.domain.nutrition.MacroKeys
import com.example.adobongkangkong.ui.daylog.model.DayLogRow

fun LogEntry.toDayLogRow(): DayLogRow {
    val n = nutrients

    return DayLogRow(
        logId = id,
        itemName = itemName,
        timestamp = timestamp,
        caloriesKcal = n[MacroKeys.CALORIES],
        proteinG     = n[MacroKeys.PROTEIN],
        carbsG       = n[MacroKeys.CARBS],
        fatG         = n[MacroKeys.FAT]
    )
}

