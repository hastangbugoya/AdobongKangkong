package com.example.adobongkangkong.domain.model

import com.example.adobongkangkong.domain.nutrition.NutrientKey
/**
 * UI-only draft state for editing a nutrient target inside the dashboard
 * settings sheet. Holds raw text input, validation errors, and saving state.
 *
 * Not persisted. Converted to [TargetEdit] only when user taps Save.
 */
data class TargetDraft(
    val key: NutrientKey,
    val min: String,
    val target: String,
    val max: String,
    val isDirty: Boolean = false,
    val error: String? = null,
    val isSaving: Boolean = false
)
