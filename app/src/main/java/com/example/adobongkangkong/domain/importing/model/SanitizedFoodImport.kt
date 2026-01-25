package com.example.adobongkangkong.domain.importing.model

import com.example.adobongkangkong.domain.model.ServingUnit

data class SanitizedFoodImport(
    val rowRef: ImportRowRef,
    val name: String,
    val servingUnit: ServingUnit,          // your enum (or similar)
    val gramsPerServing: Double?,          // nullable; required for volume units
    val nutrients: List<SanitizedNutrientImport>
)