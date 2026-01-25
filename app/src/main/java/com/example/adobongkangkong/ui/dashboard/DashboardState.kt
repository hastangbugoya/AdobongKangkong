package com.example.adobongkangkong.ui.dashboard

import com.example.adobongkangkong.domain.model.MacroTotals
import com.example.adobongkangkong.domain.model.TodayLogItem
import com.example.adobongkangkong.ui.common.bottomsheet.BlockingSheetModel

data class DashboardTargets(
    val caloriesKcal: Double = 2000.0,
    val proteinG: Double = 150.0,
    val carbsG: Double = 200.0,
    val fatG: Double = 70.0
)

data class DashboardState(
    val totals: MacroTotals = MacroTotals(),
    val targets: DashboardTargets = DashboardTargets(),
    val todayItems: List<TodayLogItem> = emptyList(),

    // Overlay / modal UI
    val blockingSheet: BlockingSheetModel? = null,
    val blockedFoodId: Long? = null,

    // One-shot navigation request consumed by DashboardScreen
    val navigateToEditFoodId: Long? = null,

    val snackbarMessage: String? = null
)


