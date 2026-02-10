package com.example.adobongkangkong.domain.usda.model

import com.example.adobongkangkong.data.usda.UsdaFoodNutrient
import kotlinx.serialization.Serializable

@Serializable
data class UsdaFoodSearchItem(
    val fdcId: Long,
    val description: String? = null,
    val dataType: String? = null,
    val gtinUpc: String? = null,
    val brandOwner: String? = null,
    val brandName: String? = null,
    val ingredients: String? = null,
    val publishedDate: String? = null,
    val modifiedDate: String? = null, // <-- ADD THIS
    val servingSizeUnit: String? = null,
    val servingSize: Double? = null,
    val householdServingFullText: String? = null,
    val packageWeight: String? = null,
    val foodCategory: String? = null,
    val foodNutrients: List<UsdaFoodNutrient> = emptyList()
)