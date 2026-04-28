package com.example.adobongkangkong.domain.export

import androidx.room.withTransaction
import com.example.adobongkangkong.core.log.MeowLog
import com.example.adobongkangkong.data.local.db.NutriDatabase
import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.data.local.db.entity.FoodEntity
import com.example.adobongkangkong.data.local.db.entity.FoodNutrientEntity
import com.example.adobongkangkong.data.local.db.entity.RecipeEntity
import com.example.adobongkangkong.data.local.db.entity.RecipeIngredientEntity
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.nutrition.SyncNutrientCatalogUseCase
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject

class ImportFoodsAndRecipesUseCase @Inject constructor(
    private val db: NutriDatabase,
    private val syncNutrientCatalog: SyncNutrientCatalogUseCase
) {

    data class Result(
        val foodsInserted: Int,
        val foodsUpdated: Int,
        val foodNutrientsUpserted: Int,
        val recipesInserted: Int,
        val recipesUpdated: Int,
        val ingredientsInserted: Int,
        val warnings: List<String>
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend operator fun invoke(inputStream: InputStream, replaceExisting: Boolean): Result {
        MeowLog.d("ImportFoodsAndRecipesUseCase> START replaceExisting=$replaceExisting")

        try {
            MeowLog.d("ImportFoodsAndRecipesUseCase> readZipEntries START")
            val (foodsJson, recipesJson) = readZipEntries(inputStream)
            MeowLog.d(
                "ImportFoodsAndRecipesUseCase> readZipEntries SUCCESS " +
                        "foodsJson=${foodsJson.length} chars recipesJson=${recipesJson.length} chars"
            )

            MeowLog.d("ImportFoodsAndRecipesUseCase> decode JSON START")
            val foods = json.decodeFromString<List<FoodExport>>(foodsJson)
            val recipes = json.decodeFromString<List<RecipeExport>>(recipesJson)
            MeowLog.d("ImportFoodsAndRecipesUseCase> decode JSON SUCCESS foods=${foods.size} recipes=${recipes.size}")

            MeowLog.d("ImportFoodsAndRecipesUseCase> syncNutrientCatalog START")
            syncNutrientCatalog()
            MeowLog.d("ImportFoodsAndRecipesUseCase> syncNutrientCatalog SUCCESS")

            val warnings = mutableListOf<String>()

            var foodsInserted = 0
            var foodsUpdated = 0
            var foodNutrientsUpserted = 0
            var recipesInserted = 0
            var recipesUpdated = 0
            var ingredientsInserted = 0

            val foodDao = db.foodDao()
            val nutrientDao = db.nutrientDao()
            val foodNutrientDao = db.foodNutrientDao()
            val recipeDao = db.recipeDao()
            val recipeIngredientDao = db.recipeIngredientDao()

            MeowLog.d("ImportFoodsAndRecipesUseCase> DB transaction START")

            db.withTransaction {
                val stableIdToFoodId = LinkedHashMap<String, Long>(foods.size)

                MeowLog.d("ImportFoodsAndRecipesUseCase> upsert foods START count=${foods.size}")

                for (f in foods) {
                    val existingId = foodDao.getIdByStableId(f.stableId)

                    val servingUnit = runCatching { ServingUnit.valueOf(f.servingUnit) }
                        .getOrElse {
                            val msg = "Food '${f.name}' has unknown servingUnit='${f.servingUnit}'. Defaulted to G."
                            warnings += msg
                            MeowLog.d("ImportFoodsAndRecipesUseCase> WARN $msg")
                            ServingUnit.G
                        }

                    if (existingId == null) {
                        val newId = foodDao.insert(
                            FoodEntity(
                                id = 0,
                                stableId = f.stableId,
                                name = f.name,
                                brand = f.brand,
                                servingSize = f.servingSize,
                                servingUnit = servingUnit,
                                gramsPerServingUnit = f.gramsPerServingUnit,
                                isRecipe = f.isRecipe
                            )
                        )
                        foodsInserted++
                        stableIdToFoodId[f.stableId] = newId
                    } else {
                        foodDao.updateCore(
                            id = existingId,
                            name = f.name,
                            brand = f.brand,
                            servingSize = f.servingSize,
                            servingUnit = servingUnit.name,
                            gramsPerServingUnit = f.gramsPerServingUnit,
                            isRecipe = f.isRecipe
                        )
                        foodsUpdated++
                        stableIdToFoodId[f.stableId] = existingId
                    }
                }

                MeowLog.d(
                    "ImportFoodsAndRecipesUseCase> upsert foods SUCCESS " +
                            "inserted=$foodsInserted updated=$foodsUpdated"
                )

                MeowLog.d("ImportFoodsAndRecipesUseCase> upsert nutrients START foodCount=${foods.size}")

                for (f in foods) {
                    val foodId = stableIdToFoodId[f.stableId] ?: continue

                    foodNutrientDao.deleteForFood(foodId)

                    val gramsPerServingUnit = f.gramsPerServingUnit?.takeIf { it > 0 }

                    val basisType = when {
                        gramsPerServingUnit != null -> BasisType.USDA_REPORTED_SERVING
                        else -> BasisType.PER_100G
                    }

                    if (f.servingUnit != null && gramsPerServingUnit == null) {
                        val msg =
                            "Food '${f.name}' uses serving unit '${f.servingUnit}' but has no grams-per-serving; assuming PER_100G."
                        warnings += msg
                        MeowLog.d("ImportFoodsAndRecipesUseCase> WARN $msg")
                    }

                    val rows = mutableListOf<FoodNutrientEntity>()

                    for ((code, amount) in f.nutrientsByCode) {
                        val nutrientId = nutrientDao.getIdByCode(code)
                        if (nutrientId == null) {
                            val msg = "Food '${f.name}' has unknown nutrient code '$code' (skipped)."
                            warnings += msg
                            MeowLog.d("ImportFoodsAndRecipesUseCase> WARN $msg")
                            continue
                        }

                        val unit = nutrientDao.getUnitByCode(code)
                        if (unit == null) {
                            val msg = "Food '${f.name}' nutrient code '$code' has no unit in catalog (skipped)."
                            warnings += msg
                            MeowLog.d("ImportFoodsAndRecipesUseCase> WARN $msg")
                            continue
                        }

                        rows += FoodNutrientEntity(
                            foodId = foodId,
                            nutrientId = nutrientId,
                            nutrientAmountPerBasis = amount,
                            unit = unit,
                            basisType = basisType
                        )
                    }

                    foodNutrientDao.upsertAll(rows)
                    foodNutrientsUpserted += rows.size
                }

                MeowLog.d(
                    "ImportFoodsAndRecipesUseCase> upsert nutrients SUCCESS " +
                            "foodNutrientsUpserted=$foodNutrientsUpserted warnings=${warnings.size}"
                )

                MeowLog.d("ImportFoodsAndRecipesUseCase> upsert recipes START count=${recipes.size}")

                for (r in recipes) {
                    val recipeFoodId: Long =
                        stableIdToFoodId[r.foodStableId]
                            ?: run {
                                val newFoodId = foodDao.insert(
                                    FoodEntity(
                                        id = 0,
                                        stableId = r.foodStableId,
                                        name = r.name,
                                        brand = null,
                                        servingSize = 1.0,
                                        servingUnit = ServingUnit.G,
                                        gramsPerServingUnit = null,
                                        isRecipe = true
                                    )
                                )
                                foodsInserted++
                                stableIdToFoodId[r.foodStableId] = newFoodId

                                val msg = "Recipe '${r.name}' referenced missing foodStableId='${r.foodStableId}'. Created it."
                                warnings += msg
                                MeowLog.d("ImportFoodsAndRecipesUseCase> WARN $msg")

                                newFoodId
                            }

                    val existingRecipeId = recipeDao.getIdByStableId(r.stableId)

                    val recipeId = if (existingRecipeId == null) {
                        val newId = recipeDao.insert(
                            RecipeEntity(
                                id = 0L,
                                stableId = r.stableId,
                                foodId = recipeFoodId,
                                name = r.name,
                                servingsYield = r.servingsYield
                            )
                        )
                        recipesInserted++
                        newId
                    } else {
                        recipeDao.updateCore(
                            id = existingRecipeId,
                            foodId = recipeFoodId,
                            name = r.name,
                            servingsYield = r.servingsYield
                        )
                        recipesUpdated++
                        existingRecipeId
                    }

                    recipeIngredientDao.deleteForRecipe(recipeId)

                    val ingredientRows = r.ingredients.mapNotNull { ing ->
                        val ingredientFoodId = stableIdToFoodId[ing.ingredientFoodStableId]
                        if (ingredientFoodId == null) {
                            val msg =
                                "Recipe '${r.name}' references missing ingredientFoodStableId='${ing.ingredientFoodStableId}' (skipped ingredient)."
                            warnings += msg
                            MeowLog.d("ImportFoodsAndRecipesUseCase> WARN $msg")
                            null
                        } else {
                            RecipeIngredientEntity(
                                recipeId = recipeId,
                                foodId = ingredientFoodId,
                                amountServings = ing.ingredientServings,
                                amountGrams = null
                            )
                        }
                    }

                    if (ingredientRows.isNotEmpty()) {
                        recipeIngredientDao.insertAll(ingredientRows)
                        ingredientsInserted += ingredientRows.size
                    }
                }

                MeowLog.d(
                    "ImportFoodsAndRecipesUseCase> upsert recipes SUCCESS " +
                            "recipesInserted=$recipesInserted recipesUpdated=$recipesUpdated ingredientsInserted=$ingredientsInserted"
                )
            }

            MeowLog.d("ImportFoodsAndRecipesUseCase> DB transaction SUCCESS")

            val result = Result(
                foodsInserted = foodsInserted,
                foodsUpdated = foodsUpdated,
                foodNutrientsUpserted = foodNutrientsUpserted,
                recipesInserted = recipesInserted,
                recipesUpdated = recipesUpdated,
                ingredientsInserted = ingredientsInserted,
                warnings = warnings
            )

            MeowLog.d(
                "ImportFoodsAndRecipesUseCase> SUCCESS " +
                        "foodsInserted=${result.foodsInserted} foodsUpdated=${result.foodsUpdated} " +
                        "foodNutrients=${result.foodNutrientsUpserted} recipesInserted=${result.recipesInserted} " +
                        "recipesUpdated=${result.recipesUpdated} ingredients=${result.ingredientsInserted} " +
                        "warnings=${result.warnings.size}"
            )

            return result
        } catch (t: Throwable) {
            MeowLog.e("ImportFoodsAndRecipesUseCase> FAILED", t)
            throw t
        }
    }

    private fun readZipEntries(input: InputStream): Pair<String, String> {
        var foodsJson: String? = null
        var recipesJson: String? = null

        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name

                MeowLog.d("ImportFoodsAndRecipesUseCase> ZIP entry=$name")

                val text = zip.readBytes().toString(Charsets.UTF_8)

                when (name) {
                    "foods.json" -> foodsJson = text
                    "recipes.json" -> recipesJson = text
                }

                zip.closeEntry()
            }
        }

        requireNotNull(foodsJson) { "Import ZIP is missing foods.json" }
        requireNotNull(recipesJson) { "Import ZIP is missing recipes.json" }

        return foodsJson!! to recipesJson!!
    }
}