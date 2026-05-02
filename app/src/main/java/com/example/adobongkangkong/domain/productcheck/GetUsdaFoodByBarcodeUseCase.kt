package com.example.adobongkangkong.domain.productcheck

import com.example.adobongkangkong.data.usda.UsdaFoodsSearchParser
import com.example.adobongkangkong.data.usda.UsdaFoodsSearchService
import javax.inject.Inject

/**
 * GetUsdaFoodByBarcodeUseCase
 *
 * Read-only USDA lookup using barcode.
 *
 * IMPORTANT:
 * - No DB writes
 * - No import
 * - No persistence
 */
class GetUsdaFoodByBarcodeUseCase @Inject constructor(
    private val service: UsdaFoodsSearchService
) {

    data class Result(
        val name: String,
        val brand: String?,
        val servingText: String?,
        val sodiumMg: Double?,
        val sugarG: Double?
    )

    suspend fun execute(barcode: String): Result? {
        val json = service.searchByBarcode(barcode) ?: return null

        val parsed = UsdaFoodsSearchParser.parse(json)
        val food = parsed.foods.firstOrNull() ?: return null

        val nutrients = food.foodNutrients

        fun findNutrientById(id: Long): Double? {
            return nutrients
                .firstOrNull { it.nutrientId == id }
                ?.value
        }

        val sodium = findNutrientById(USDA_SODIUM_NA_ID)
        val sugar = findNutrientById(USDA_TOTAL_SUGARS_NLEA_ID)
            ?: findNutrientById(USDA_TOTAL_SUGARS_LEGACY_ID)

        val servingText = food.householdServingFullText
            ?: run {
                val ss = food.servingSize
                val unit = food.servingSizeUnit
                if (ss != null && !unit.isNullOrBlank()) "$ss $unit" else null
            }

        return Result(
            name = food.description ?: "Unknown food",
            brand = food.brandName ?: food.brandOwner,
            servingText = servingText,
            sodiumMg = sodium,
            sugarG = sugar
        )
    }

    private companion object {
        private const val USDA_SODIUM_NA_ID = 1093L
        private const val USDA_TOTAL_SUGARS_NLEA_ID = 2000L
        private const val USDA_TOTAL_SUGARS_LEGACY_ID = 1063L
    }
}