package com.example.adobongkangkong.ui.dashboard.pinned.model

import com.example.adobongkangkong.domain.model.NutrientCategory
import com.example.adobongkangkong.domain.nutrition.NutrientKey

data class DashboardPinOption(
    val key: NutrientKey,
    val displayName: String,
    val unit: String?,
    val category: NutrientCategory
)
