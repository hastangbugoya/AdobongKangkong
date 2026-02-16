package com.example.adobongkangkong.ui.calendar

import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.ui.calendar.model.CalendarDay
import com.example.adobongkangkong.ui.dashboard.pinned.model.DashboardPinOption
import java.time.YearMonth

/**
 * Single UI state for Heatmap screen.
 *
 * - selectedNutrient: what the user explicitly chose (nullable)
 * - resolvedNutrient: what we actually use to build the heatmap (never null)
 */
data class CalendarState(
    val month: YearMonth = YearMonth.now(),
    val nutrientOptions: List<DashboardPinOption> = emptyList(),
    val selectedNutrient: NutrientKey? = null,
    val resolvedNutrient: NutrientKey = NutrientKey.CALORIES_KCAL, // <- change if your enum differs
    val selectedDay: CalendarDay? = null,
    val calendarDays: List<CalendarDay> = emptyList()
)
