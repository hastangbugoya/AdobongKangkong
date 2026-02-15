package com.example.adobongkangkong.domain.model

import java.util.UUID

data class Food(
    val id: Long,
    val stableId: String = UUID.randomUUID().toString(),

    val name: String,
    val brand: String?,

    val servingSize: Double = 1.0,
    val servingUnit: ServingUnit = ServingUnit.SERVING,

    val gramsPerServingUnit: Double?,
    val mlPerServingUnit: Double?,

    val servingsPerPackage: Double?,

    val isRecipe: Boolean,
    val isLowSodium: Boolean? = null,

    // USDA metadata (nullable; only set for USDA-imported foods)
    val usdaFdcId: Long? = null,
    val usdaGtinUpc: String? = null,
    val usdaPublishedDate: String? = null,  // ISO yyyy-MM-dd (primary version gate)
    val usdaModifiedDate: String? = null    // ISO yyyy-MM-dd (secondary info)
)
