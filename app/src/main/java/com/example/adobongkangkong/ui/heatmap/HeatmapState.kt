package com.example.adobongkangkong.ui.heatmap

import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.ui.dashboard.pinned.model.DashboardPinOption
import com.example.adobongkangkong.ui.heatmap.model.HeatmapDay
import java.time.YearMonth

/**
 * Single UI state for Heatmap screen.
 *
 * - selectedNutrient: what the user explicitly chose (nullable)
 * - resolvedNutrient: what we actually use to build the heatmap (never null)
 */
data class HeatmapState(
    val month: YearMonth = YearMonth.now(),
    val nutrientOptions: List<DashboardPinOption> = emptyList(),
    val selectedNutrient: NutrientKey? = null,
    val resolvedNutrient: NutrientKey = NutrientKey.CALORIES_KCAL, // <- change if your enum differs
    val selectedDay: HeatmapDay? = null,
    val heatmapDays: List<HeatmapDay> = emptyList()
)
