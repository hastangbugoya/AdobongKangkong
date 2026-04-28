package com.example.adobongkangkong.domain.export

import com.example.adobongkangkong.core.log.MeowLog
import com.example.adobongkangkong.data.local.db.NutriDatabase
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.OutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject

class ExportFoodsAndRecipesUseCase @Inject constructor(
    private val db: NutriDatabase
) {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    suspend operator fun invoke(outputStream: OutputStream): ExportResult {
        MeowLog.d("ExportUseCase> START")

        val warnings = mutableListOf<String>()

        try {
            // 1) Load core tables
            MeowLog.d("ExportUseCase> Loading DB tables")
            val foods = db.foodDao().getAll()
            val recipes = db.recipeDao().getAll()
            MeowLog.d("ExportUseCase> Loaded foods=${foods.size} recipes=${recipes.size}")

            val foodStableIdById: Map<Long, String> =
                foods.associate { it.id to it.stableId }

            // 2) Build FoodExport list
            MeowLog.d("ExportUseCase> Building food exports")
            val foodNutrientDao = db.foodNutrientDao()

            val foodExports: List<FoodExport> = foods.map { food ->
                val nutrientRows = foodNutrientDao.getForFoodWithMeta(food.id)

                val nutrientsByCode = nutrientRows
                    .asSequence()
                    .groupBy { it.code }
                    .mapValues { (_, rows) -> rows.last().amount }

                FoodExport(
                    stableId = food.stableId,
                    name = food.name,
                    brand = food.brand,
                    servingSize = food.servingSize,
                    servingUnit = food.servingUnit.name,
                    gramsPerServingUnit = food.gramsPerServingUnit,
                    isRecipe = food.isRecipe,
                    nutrientsByCode = nutrientsByCode
                )
            }

            MeowLog.d("ExportUseCase> Food exports built count=${foodExports.size}")

            // 3) Build RecipeExport list
            MeowLog.d("ExportUseCase> Building recipe exports")
            val recipeIngredientDao = db.recipeIngredientDao()

            val recipeExports: List<RecipeExport> = recipes.map { recipe ->
                val recipeStableId = recipe.stableId

                val recipeFoodStableId =
                    foodStableIdById[recipe.foodId]
                        ?: run {
                            val msg =
                                "Recipe '${recipe.name}' missing foodId=${recipe.foodId}"
                            warnings += msg
                            MeowLog.d("ExportUseCase> WARN $msg")
                            "MISSING_FOOD:${recipe.foodId}"
                        }

                val ingredients = recipeIngredientDao.getForRecipe(recipe.id).map { ing ->
                    val ingredientStable =
                        foodStableIdById[ing.foodId]
                            ?: run {
                                val msg =
                                    "Recipe '${recipe.name}' missing ingredient foodId=${ing.foodId}"
                                warnings += msg
                                MeowLog.d("ExportUseCase> WARN $msg")
                                "MISSING_INGREDIENT:${ing.foodId}"
                            }

                    RecipeIngredientExport(
                        ingredientFoodStableId = ingredientStable,
                        ingredientServings = ing.amountServings ?: 0.0
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

            MeowLog.d("ExportUseCase> Recipe exports built count=${recipeExports.size}")

            // 4) Write ZIP
            MeowLog.d("ExportUseCase> Writing ZIP")

            val zip = ZipOutputStream(outputStream)
            try {
                val createdAt = Instant.now().toString()

                val manifest = ExportManifest(
                    schemaVersion = 1,
                    createdAtIso = createdAt,
                    appName = "AdobongKangkong",
                    foodsCount = foodExports.size,
                    recipesCount = recipeExports.size,
                    notes = warnings.take(50)
                )

                writeZipJson(zip, "manifest.json", json.encodeToString(manifest))
                writeZipJson(zip, "foods.json", json.encodeToString(foodExports))
                writeZipJson(zip, "recipes.json", json.encodeToString(recipeExports))

                zip.finish()
                zip.flush()

                MeowLog.d(
                    "ExportUseCase> ZIP write SUCCESS " +
                            "foods=${foodExports.size} recipes=${recipeExports.size} warnings=${warnings.size}"
                )
            } finally {
                // do not close
            }

            MeowLog.d("ExportUseCase> SUCCESS")

            return ExportResult(
                foodsExported = foodExports.size,
                recipesExported = recipeExports.size,
                warnings = warnings
            )

        } catch (t: Throwable) {
            MeowLog.e("ExportUseCase> FAILED", t)
            throw t
        }
    }

    private fun writeZipJson(zip: ZipOutputStream, name: String, content: String) {
        MeowLog.d("ExportUseCase> write entry=$name size=${content.length}")

        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }
}