package com.example.adobongkangkong.domain.mealprep.model

import com.example.adobongkangkong.data.local.db.entity.MealSlot

/**
 * Domain meal template model used by the template build/edit flows.
 *
 * Notes:
 * - id == 0L means "new / not yet persisted".
 * - [items] ordering is meaningful and should be preserved on save.
 */
data class MealTemplate(
    val id: Long = 0L,
    val name: String,
    val defaultSlot: MealSlot? = null,
    val items: List<MealTemplateItem> = emptyList()
)
