package com.example.adobongkangkong.data.usda

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// 1) Minimal DTOs for /fdc/v1/foods/search

@Serializable
data class UsdaFoodsSearchResponse(
    val totalHits: Int = 0,
    val currentPage: Int? = null,
    val totalPages: Int? = null,
    val foods: List<UsdaFoodSearchItem> = emptyList()
)

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
    val servingSizeUnit: String? = null,
    val servingSize: Double? = null,
    val householdServingFullText: String? = null,
    val packageWeight: String? = null,
    val foodCategory: String? = null,
    val foodNutrients: List<UsdaFoodNutrient> = emptyList()
)

@Serializable
data class UsdaFoodNutrient(
    val nutrientId: Long,
    val nutrientName: String? = null,
    val unitName: String? = null,
    val value: Double? = null,

    // optional fields you might want later
    val nutrientNumber: String? = null,
    val derivationCode: String? = null,
    val derivationDescription: String? = null,
    val foodNutrientId: Long? = null,
    val percentDailyValue: Double? = null
)

// 2) Parser (works for barcode searches and general searches)
object UsdaFoodsSearchParser {

    private val json = Json {
        ignoreUnknownKeys = true   // critical: USDA adds fields frequently
        isLenient = true
        explicitNulls = false
    }

    fun parse(responseJson: String): UsdaFoodsSearchResponse =
        json.decodeFromString(UsdaFoodsSearchResponse.serializer(), responseJson)
}

// 3) Example usage (call this from a unit test / debug action)
fun debugParseUsdaFoodsSearch(jsonString: String) {
    val parsed = UsdaFoodsSearchParser.parse(jsonString)

    println("totalHits=${parsed.totalHits} foods=${parsed.foods.size}")

    val first = parsed.foods.firstOrNull()
    if (first != null) {
        println("fdcId=${first.fdcId} desc=${first.description} gtin=${first.gtinUpc}")
        println("serving=${first.servingSize} ${first.servingSizeUnit} household='${first.householdServingFullText}'")
        println("nutrients=${first.foodNutrients.size}")

        // show a few nutrients
        first.foodNutrients.take(10).forEach {
            println("  nutrientId=${it.nutrientId} name=${it.nutrientName} value=${it.value} ${it.unitName}")
        }
    }
}
