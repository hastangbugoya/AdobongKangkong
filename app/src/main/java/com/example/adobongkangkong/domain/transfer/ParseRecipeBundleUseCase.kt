package com.example.adobongkangkong.domain.transfer

import com.example.adobongkangkong.core.log.MeowLog
import kotlinx.serialization.json.*
import javax.inject.Inject

/**
 * Parses raw JSON into [RecipeBundleDto] safely.
 *
 * NEVER throws.
 * ALWAYS returns a structured result.
 */
class ParseRecipeBundleUseCase @Inject constructor() {

    sealed class Result {
        data class Success(val bundle: RecipeBundleDto) : Result()
        data class Failure(
            val reason: Reason,
            val message: String? = null
        ) : Result()
    }

    enum class Reason {
        MALFORMED_JSON,
        MISSING_REQUIRED_FIELDS,
        UNSUPPORTED_SCHEMA
    }

    fun execute(rawJson: String): Result {
        MeowLog.d("ParseRecipeBundle> START size=${rawJson.length}")

        val root = try {
            Json.parseToJsonElement(rawJson).jsonObject
        } catch (e: Exception) {
            MeowLog.e("ParseRecipeBundle> MALFORMED_JSON", e)
            return Result.Failure(Reason.MALFORMED_JSON, e.message)
        }

        val schemaVersion = root["schemaVersion"]?.jsonPrimitive?.intOrNull
        if (schemaVersion == null) {
            MeowLog.d("ParseRecipeBundle> FAIL missing schemaVersion")
            return Result.Failure(Reason.MISSING_REQUIRED_FIELDS, "Missing schemaVersion")
        }

        MeowLog.d("ParseRecipeBundle> schemaVersion=$schemaVersion")

        if (schemaVersion != RecipeBundleDto.SCHEMA_VERSION_1) {
            MeowLog.d("ParseRecipeBundle> FAIL unsupported schemaVersion=$schemaVersion")
            return Result.Failure(Reason.UNSUPPORTED_SCHEMA, "schemaVersion=$schemaVersion")
        }

        val exportedAt = root["exportedAtEpochMs"]?.jsonPrimitive?.longOrNull
        if (exportedAt == null) {
            MeowLog.d("ParseRecipeBundle> FAIL missing exportedAtEpochMs")
            return Result.Failure(Reason.MISSING_REQUIRED_FIELDS, "Missing exportedAtEpochMs")
        }

        val recipe = parseRecipe(root["recipe"])
        if (recipe == null) {
            MeowLog.d("ParseRecipeBundle> FAIL invalid recipe")
            return Result.Failure(Reason.MISSING_REQUIRED_FIELDS, "Invalid recipe")
        }

        val ingredients = parseIngredients(root["ingredients"])
        val foods = parseFoods(root["foods"])

        MeowLog.d(
            "ParseRecipeBundle> SUCCESS " +
                    "recipe=${recipe.name} " +
                    "ingredients=${ingredients.size} foods=${foods.size}"
        )

        return Result.Success(
            RecipeBundleDto(
                schemaVersion = schemaVersion,
                exportedAtEpochMs = exportedAt,
                recipe = recipe,
                ingredients = ingredients,
                foods = foods
            )
        )
    }

    private fun parseRecipe(element: JsonElement?): RecipeBundleRecipeDto? {
        val obj = element?.jsonObject ?: return null

        val stableId = obj["stableId"]?.jsonPrimitive?.contentOrNull ?: return null
        val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return null
        val servings = obj["servingsYield"]?.jsonPrimitive?.doubleOrNull ?: return null

        val totalGrams = obj["totalYieldGrams"]?.jsonPrimitive?.doubleOrNull

        return RecipeBundleRecipeDto(
            stableId = stableId,
            name = name,
            servingsYield = servings,
            totalYieldGrams = totalGrams
        )
    }

    private fun parseIngredients(element: JsonElement?): List<RecipeBundleIngredientDto> {
        val arr = element?.jsonArray ?: return emptyList()

        val result = arr.mapNotNull { item ->
            val obj = item.jsonObject

            val foodStableId = obj["foodStableId"]?.jsonPrimitive?.contentOrNull
                ?: return@mapNotNull null

            val servings = obj["ingredientServings"]?.jsonPrimitive?.doubleOrNull

            RecipeBundleIngredientDto(
                foodStableId = foodStableId,
                ingredientServings = servings
            )
        }

        MeowLog.d("ParseRecipeBundle> parsed ingredients count=${result.size}")
        return result
    }

    private fun parseFoods(element: JsonElement?): List<RecipeBundleFoodDto> {
        val arr = element?.jsonArray ?: return emptyList()

        val result = arr.mapNotNull { item ->
            val obj = item.jsonObject

            val stableId = obj["stableId"]?.jsonPrimitive?.contentOrNull
                ?: return@mapNotNull null

            val name = obj["name"]?.jsonPrimitive?.contentOrNull
                ?: return@mapNotNull null

            val servingUnit = obj["servingUnit"]?.jsonPrimitive?.contentOrNull
                ?: return@mapNotNull null

            val nutrients = parseNutrients(obj["nutrients"])

            RecipeBundleFoodDto(
                stableId = stableId,
                name = name,
                brand = obj["brand"]?.jsonPrimitive?.contentOrNull,

                servingSize = obj["servingSize"]?.jsonPrimitive?.doubleOrNull ?: 1.0,
                servingUnit = servingUnit,

                gramsPerServingUnit = obj["gramsPerServingUnit"]?.jsonPrimitive?.doubleOrNull,
                mlPerServingUnit = obj["mlPerServingUnit"]?.jsonPrimitive?.doubleOrNull,
                servingsPerPackage = obj["servingsPerPackage"]?.jsonPrimitive?.doubleOrNull,

                isRecipe = obj["isRecipe"]?.jsonPrimitive?.booleanOrNull ?: false,
                isLowSodium = obj["isLowSodium"]?.jsonPrimitive?.booleanOrNull,

                usdaFdcId = obj["usdaFdcId"]?.jsonPrimitive?.longOrNull,
                usdaGtinUpc = obj["usdaGtinUpc"]?.jsonPrimitive?.contentOrNull,
                usdaPublishedDate = obj["usdaPublishedDate"]?.jsonPrimitive?.contentOrNull,
                usdaModifiedDate = obj["usdaModifiedDate"]?.jsonPrimitive?.contentOrNull,
                usdaServingSize = obj["usdaServingSize"]?.jsonPrimitive?.doubleOrNull,
                usdaServingUnit = obj["usdaServingUnit"]?.jsonPrimitive?.contentOrNull,
                householdServingText = obj["householdServingText"]?.jsonPrimitive?.contentOrNull,

                canonicalNutrientBasis = obj["canonicalNutrientBasis"]
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?.let {
                        runCatching {
                            RecipeBundleFoodNutrientBasis.valueOf(it)
                        }.getOrNull()
                    },

                nutrients = nutrients
            )
        }

        MeowLog.d("ParseRecipeBundle> parsed foods count=${result.size}")
        return result
    }

    private fun parseNutrients(element: JsonElement?): List<RecipeBundleFoodNutrientDto> {
        val arr = element?.jsonArray ?: return emptyList()

        return arr.mapNotNull { item ->
            val obj = item.jsonObject

            val code = obj["code"]?.jsonPrimitive?.contentOrNull
                ?: return@mapNotNull null

            val amount = obj["amount"]?.jsonPrimitive?.doubleOrNull
                ?: return@mapNotNull null

            RecipeBundleFoodNutrientDto(
                code = code,
                amount = amount
            )
        }
    }
}