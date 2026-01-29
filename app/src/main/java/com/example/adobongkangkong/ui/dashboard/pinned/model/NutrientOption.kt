package com.example.adobongkangkong.ui.dashboard.pinned.model

import com.example.adobongkangkong.domain.nutrition.NutrientKey

data class NutrientOption(
    val key: NutrientKey,
    val displayName: String,
    val unit: String?
)