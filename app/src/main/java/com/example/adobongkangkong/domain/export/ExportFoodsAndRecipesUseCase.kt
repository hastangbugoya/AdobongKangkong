package com.example.adobongkangkong.domain.export

import com.example.adobongkangkong.data.local.db.NutriDatabase
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.OutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject

/**
 * Exports Foods + Recipes into a single ZIP (JSON inside).
 *
 * Design goals:
 * - Lossless enough to restore later (IDs are reconciled via stableId)
 * - Human-readable (JSON)
 * - Versioned (manifest.schemaVersion)
 *
 * Important: this requires FoodEntity.stableId and RecipeEntity.stableId to exist.
 */
class ExportFoodsAndRecipesUseCase @Inject constructor(
    private val db: NutriDatabase
) {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    suspend operator fun invoke(outputStream: OutputStream): ExportResult {
        val warnings = mutableListOf<String>()

        // 1) Load core tables
        val foods = db.foodDao().getAll()
        val recipes = db.recipeDao().getAll()

        // Map DB foodId -> stableId (used to link recipe ingredients)
        val foodStableIdById: Map<Long, String> = foods.associate { it.id to it.stableId }

        // 2) Build FoodExport list (include nutrients)
        val foodNutrientDao = db.foodNutrientDao()

        val foodExports: List<FoodExport> = foods.map { food ->
            val nutrientRows = foodNutrientDao.getForFoodWithMeta(food.id)
            val nutrientsByCode = nutrientRows
                .asSequence()
                .groupBy { it.code }
                .mapValues { (_, rows) ->
                    // If duplicates exist, keep the last one deterministically.
                    rows.last().amount
                }

            FoodExport(
                stableId = food.stableId,
                name = food.name,
                brand = food.brand,
                servingSize = food.servingSize,
                servingUnit = food.servingUnit.name,
                gramsPerServing = food.gramsPerServing,
                isRecipe = food.isRecipe,
                nutrientsByCode = nutrientsByCode
            )
        }

        // 3) Build RecipeExport list (include ingredient references by stableId)
        val recipeIngredientDao = db.recipeIngredientDao()

        val recipeExports: List<RecipeExport> = recipes.map { recipe ->
            val recipeStableId = recipe.stableId
            val recipeFoodStableId =
                foodStableIdById[recipe.foodId]
                    ?: run {
                        warnings += "Recipe '${recipe.name}' (recipeStableId=$recipeStableId) references missing foodId=${recipe.foodId}."
                        "MISSING_FOOD:${recipe.foodId}"
                    }

            val ingredients = recipeIngredientDao.getForRecipe(recipe.id).map { ing ->
                val ingredientStable =
                    foodStableIdById[ing.ingredientFoodId]
                        ?: run {
                            warnings += "Recipe '${recipe.name}' references missing ingredientFoodId=${ing.ingredientFoodId}."
                            "MISSING_INGREDIENT:${ing.ingredientFoodId}"
                        }

                RecipeIngredientExport(
                    ingredientFoodStableId = ingredientStable,
                    ingredientServings = ing.ingredientServings
                )
            }

            RecipeExport(
                stableId = recipeStableId,
                name = recipe.name,
                servingsYield = recipe.servingsYield,
                foodStableId = recipeFoodStableId,
                ingredients = ingredients
            )
        }

        // 4) Write ZIP
        ZipOutputStream(outputStream).use { zip ->
            val createdAt = Instant.now().toString()
            val manifest = ExportManifest(
                schemaVersion = 1,
                createdAtIso = createdAt,
                appName = "AdobongKangkong",
                foodsCount = foodExports.size,
                recipesCount = recipeExports.size,
                notes = warnings.take(50) // keep manifest small
            )

            writeZipJson(zip, "manifest.json", json.encodeToString(manifest))
            writeZipJson(zip, "foods.json", json.encodeToString(foodExports))
            writeZipJson(zip, "recipes.json", json.encodeToString(recipeExports))
        }

        return ExportResult(
            foodsExported = foodExports.size,
            recipesExported = recipeExports.size,
            warnings = warnings
        )
    }

    private fun writeZipJson(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }
}
