package com.example.adobongkangkong.domain.importing.model

import com.example.adobongkangkong.domain.model.NutrientUnit

data class SanitizedNutrientImport(
    val nutrientId: Long,                  // canonical nutrient row id in DB
    val amount: Double,                    // already normalized
    val unit: NutrientUnit                 // canonical internal unit
)