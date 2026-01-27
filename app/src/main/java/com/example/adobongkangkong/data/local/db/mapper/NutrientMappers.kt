package com.example.adobongkangkong.data.local.db.mapper

import com.example.adobongkangkong.data.local.db.entity.NutrientEntity
import com.example.adobongkangkong.domain.model.Nutrient
import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.nutrition.NutrientMap


fun NutrientMap.toCodeMap(): Map<String, Double> =
    entries().associate { (k, v) -> k.value to v }

fun NutrientMap.Companion.fromCodeMap(map: Map<String, Double>): NutrientMap =
    NutrientMap(map.mapKeys { NutrientKey(it.key) })

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