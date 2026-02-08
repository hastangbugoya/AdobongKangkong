package com.example.adobongkangkong.domain.mealprep.model

import com.example.adobongkangkong.data.local.db.entity.MealSlot

data class MealTemplate(
    val id: Long,
    val name: String,
    val defaultSlot: MealSlot? = null,
    val items: List<MealTemplateItem>
)
