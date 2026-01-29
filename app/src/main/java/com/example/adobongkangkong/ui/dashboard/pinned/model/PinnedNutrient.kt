package com.example.adobongkangkong.ui.dashboard.pinned.model

import com.example.adobongkangkong.domain.nutrition.NutrientKey

class PinnedNutrient {
    data class PinnedNutrient(
        val position: Int,
        val key: NutrientKey
    )
}