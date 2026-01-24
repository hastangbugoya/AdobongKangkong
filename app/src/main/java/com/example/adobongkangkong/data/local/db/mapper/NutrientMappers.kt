package com.example.adobongkangkong.data.local.db.mapper

import com.example.adobongkangkong.data.local.db.entity.NutrientEntity
import com.example.adobongkangkong.domain.model.Nutrient

fun NutrientEntity.toDomain(): Nutrient =
    Nutrient(
        id = id,
        code = code,
        displayName = displayName,
        unit = unit,
        category = category
    )

fun Nutrient.toEntity(): NutrientEntity =
    NutrientEntity(
        id = id,
        code = code,
        displayName = displayName,
        unit = unit,
        category = category
    )

