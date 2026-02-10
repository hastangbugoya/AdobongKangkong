package com.example.adobongkangkong.domain.model

data class Food(
    val id: Long,
    val stableId: String,

    val name: String,
    val brand: String?,

    val servingSize: Double,
    val servingUnit: ServingUnit,

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
