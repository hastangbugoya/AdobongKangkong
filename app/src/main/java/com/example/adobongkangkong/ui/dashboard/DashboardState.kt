package com.example.adobongkangkong.ui.dashboard


import com.example.adobongkangkong.domain.model.MacroTotals
import com.example.adobongkangkong.domain.model.TodayLogItem

data class DashboardTargets(
    val caloriesKcal: Double = 2000.0,
    val proteinG: Double = 150.0,
    val carbsG: Double = 200.0,
    val fatG: Double = 70.0
)

data class DashboardState(
    val totals: MacroTotals = MacroTotals(),
    val targets: DashboardTargets = DashboardTargets(),
    val todayItems: List<TodayLogItem> = emptyList()
)