package com.example.adobongkangkong.domain.mealprep.usecase

import com.example.adobongkangkong.data.local.db.entity.MealSlot

/**
 * Represents one materialization target for a template:
 * - dateIso: yyyy-MM-dd
 * - slot: meal slot for the created planned meal
 * - customLabel: required when slot == CUSTOM
 * - mealNameOverride: optional override for the created planned meal name (defaults to template name if null)
 */
data class ApplyMealTemplateTarget(
    val dateIso: String,
    val slot: MealSlot,
    val customLabel: String? = null,
    val mealNameOverride: String? = null
)