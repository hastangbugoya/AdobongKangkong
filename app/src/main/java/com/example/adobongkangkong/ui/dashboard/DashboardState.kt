package com.example.adobongkangkong.ui.dashboard

import com.example.adobongkangkong.domain.model.MacroTotals
import com.example.adobongkangkong.domain.model.TodayLogItem
import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.trend.model.DashboardNutrientCard
import com.example.adobongkangkong.ui.common.bottomsheet.BlockingSheetModel
import com.example.adobongkangkong.ui.dashboard.pinned.model.DashboardPinOption

data class DashboardState(
    val totals: MacroTotals = MacroTotals(),
    val todayItems: List<TodayLogItem> = emptyList(),

    /** Ordered cards (fixed 4 + 2 pinned/defaults) */
    val nutrientCards: List<DashboardNutrientCard> = emptyList(),

    /** Current pinned keys in slot order (0 then 1); may be empty if unset. */
    val pinnedKeys: List<NutrientKey> = emptyList(),

    // Overlay / modal UI
    val blockingSheet: BlockingSheetModel? = null,
    val blockedFoodId: Long? = null,

    // Dashboard settings sheet
    val settingsSheetOpen: Boolean = false,

    // One-shot navigation request consumed by DashboardScreen
    val navigateToEditFoodId: Long? = null,

    val snackbarMessage: String? = null
)
