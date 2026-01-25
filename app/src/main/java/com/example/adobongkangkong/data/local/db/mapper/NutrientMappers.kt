package com.example.adobongkangkong.data.local.db.mapper

import com.example.adobongkangkong.data.local.db.entity.NutrientEntity
import com.example.adobongkangkong.domain.model.Nutrient
import com.example.adobongkangkong.domain.model.NutrientCategory
import com.example.adobongkangkong.domain.model.NutrientUnit

/**
 * Maps database NutrientEntity → domain Nutrient.
 */
fun NutrientEntity.toDomain(): Nutrient =
    Nutrient(
        id = id,
        code = code,
        displayName = displayName,
        unit = NutrientUnit.fromDb(unit.name),
        category = NutrientCategory.fromDb(category.name)
    )

fun Nutrient.toEntity(): NutrientEntity =
    NutrientEntity(
        id = id,
        code = code,
        displayName = displayName,
        unit = unit,
        category = category
    )

