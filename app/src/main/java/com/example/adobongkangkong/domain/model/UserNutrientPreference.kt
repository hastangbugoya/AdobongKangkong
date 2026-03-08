package com.example.adobongkangkong.domain.model

import com.example.adobongkangkong.domain.nutrition.NutrientKey

/**
 * User preference row for a nutrient in dashboard settings.
 *
 * - [position] is 0/1 for dashboard pinned slots.
 * - [position] is null for preference-only rows (e.g. critical but not pinned).
 */
data class UserNutrientPreference(
    val key: NutrientKey,
    val isPinned: Boolean,
    val isCritical: Boolean,
    val position: Int?
)
