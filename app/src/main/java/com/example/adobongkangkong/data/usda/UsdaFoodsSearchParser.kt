package com.example.adobongkangkong.data.usda

import com.example.adobongkangkong.domain.usda.model.UsdaFoodSearchItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class UsdaFoodsSearchResponse(
    val totalHits: Int = 0,
    val currentPage: Int? = null,
    val totalPages: Int? = null,
    val foods: List<UsdaFoodSearchItem> = emptyList()
)

@Serializable
data class UsdaFoodNutrient(
    val nutrientId: Long,
    val nutrientName: String? = null,
    val unitName: String? = null,
    val value: Double? = null,
    val nutrientNumber: String? = null,
    val derivationCode: String? = null,
    val derivationDescription: String? = null,
    val foodNutrientId: Long? = null,
    val percentDailyValue: Double? = null
)

object UsdaNutrientIds {
    const val SODIUM_NA: Long = 1093L
    const val TOTAL_SUGARS_NLEA: Long = 2000L
}

fun List<UsdaFoodNutrient>.valueForNutrientId(id: Long): Double? {
    return firstOrNull { it.nutrientId == id }?.value
}

object UsdaFoodsSearchParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    fun parse(responseJson: String): UsdaFoodsSearchResponse =
        json.decodeFromString(UsdaFoodsSearchResponse.serializer(), responseJson)
}

fun debugParseUsdaFoodsSearch(jsonString: String) {
    val parsed = UsdaFoodsSearchParser.parse(jsonString)

    println("totalHits=${parsed.totalHits} foods=${parsed.foods.size}")

    val first = parsed.foods.firstOrNull()
    if (first != null) {
        println("fdcId=${first.fdcId} desc=${first.description} gtin=${first.gtinUpc}")
        println("serving=${first.servingSize} ${first.servingSizeUnit} household='${first.householdServingFullText}'")
        println("nutrients=${first.foodNutrients.size}")

        first.foodNutrients.take(20).forEach {
            println("  nutrientId=${it.nutrientId} name=${it.nutrientName} value=${it.value} ${it.unitName}")
        }
    }
}