package com.example.adobongkangkong.ui.heatmap.model

import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.trend.model.TargetStatus
import java.time.LocalDate

data class HeatmapDay(
    val date: LocalDate,
    val nutrientKey: NutrientKey,
    val value: Double?,
    val min: Double?,
    val target: Double?,
    val max: Double?,
    val status: TargetStatus
)
