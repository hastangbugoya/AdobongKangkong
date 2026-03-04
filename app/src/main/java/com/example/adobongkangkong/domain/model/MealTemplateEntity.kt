package com.example.adobongkangkong.domain.model

import com.example.adobongkangkong.data.local.db.entity.MealSlot

data class MealTemplateSummary(
    val id: Long,
    val name: String,
    val defaultSlot: MealSlot?
)