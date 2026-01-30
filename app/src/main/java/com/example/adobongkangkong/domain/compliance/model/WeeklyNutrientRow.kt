package com.example.adobongkangkong.domain.compliance.model

import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.trend.model.TargetStatus
import java.time.LocalDate

data class WeeklyNutrientRow(
    val key: NutrientKey,
    val statuses: Map<LocalDate, TargetStatus> // size 7, aligned to weekDates
)