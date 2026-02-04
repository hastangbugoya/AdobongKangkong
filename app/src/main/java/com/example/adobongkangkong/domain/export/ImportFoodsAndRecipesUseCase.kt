package com.example.adobongkangkong.domain.export

import androidx.room.withTransaction
import com.example.adobongkangkong.data.local.db.NutriDatabase
import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.data.local.db.entity.FoodEntity
import com.example.adobongkangkong.data.local.db.entity.FoodNutrientEntity
import com.example.adobongkangkong.data.local.db.entity.RecipeEntity
import com.example.adobongkangkong.data.local.db.entity.RecipeIngredientEntity
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.nutrition.SyncNutrientCatalogUseCase
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject

/**
 * Imports a ZIP created by [ExportFoodsAndRecipesUseCase].
 *
 * Strategy:
 * 1) Read foods.json + recipes.json
 * 2) Upsert foods by stableId
 * 3) Upsert food nutrients by nutrient code
 * 4) Upsert recipes by stableId and replace their ingredients
 *
 * Lax behavior (dev-friendly):
 * - If a recipe references a missing food stableId, we auto-create that food row.
 * - If a nutrient code is unknown, we skip that nutrient with a warning.
 */
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
        // 0) Parse ZIP entries
        val (foodsJson, recipesJson) = readZipEntries(inputStream)

        val foods = json.decodeFromString<List<FoodExport>>(foodsJson)
        val recipes = json.decodeFromString<List<RecipeExport>>(recipesJson)

        // 1) Ensure catalog nutrients exist before we resolve codes -> ids
        // (safe even if you already synced)
        syncNutrientCatalog()

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

        db.withTransaction {
            // A) Upsert foods by stableId
            val stableIdToFoodId = LinkedHashMap<String, Long>(foods.size)

            for (f in foods) {
                val existingId = foodDao.getIdByStableId(f.stableId)

                val servingUnit = runCatching { ServingUnit.valueOf(f.servingUnit) }
                    .getOrElse {
                        warnings += "Food '${f.name}' has unknown servingUnit='${f.servingUnit}'. Defaulted to G."
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

            // B) Upsert food nutrients (replace per-food for simplicity)
            for (f in foods) {
                val foodId = stableIdToFoodId[f.stableId] ?: continue

                // Replace nutrients for this food (keeps import deterministic)
                foodNutrientDao.deleteForFood(foodId)

                val gramsPerServingUnit = f.gramsPerServingUnit?.takeIf { it > 0 }

                val basisType = when {
                    // Legacy meaning: nutrients were stored "per serving unit" when gramsPerServingUnit existed
                    gramsPerServingUnit != null -> BasisType.USDA_REPORTED_SERVING
                    else -> BasisType.PER_100G
                }

// Optional warning (preserves your lax import philosophy)
                if (f.servingUnit != null && gramsPerServingUnit == null) {
                    warnings +=
                        "Food '${f.name}' uses serving unit '${f.servingUnit}' but has no grams-per-serving; assuming PER_100G."
                }

                val rows = mutableListOf<FoodNutrientEntity>()
                for ((code, amount) in f.nutrientsByCode) {

                    val nutrientId = nutrientDao.getIdByCode(code)
                    if (nutrientId == null) {
                        warnings += "Food '${f.name}' has unknown nutrient code '$code' (skipped)."
                        continue
                    }

                    // NEW: unit is required by FoodNutrientEntity now.
                    // Add this DAO method if you don't have it yet:
                    // @Query("SELECT unit FROM nutrients WHERE code = :code LIMIT 1")
                    // suspend fun getUnitByCode(code: String): NutrientUnit?
                    val unit = nutrientDao.getUnitByCode(code)
                    if (unit == null) {
                        warnings += "Food '${f.name}' nutrient code '$code' has no unit in catalog (skipped)."
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
            }


            // C) Upsert recipes, then replace ingredients
            for (r in recipes) {
                // Ensure the recipe's "food row" exists (so recipes can be logged as foods)
                val recipeFoodId: Long =
                    stableIdToFoodId[r.foodStableId]
                        ?: run {
                            // Auto-create missing recipe food row
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
                            warnings += "Recipe '${r.name}' referenced missing foodStableId='${r.foodStableId}'. Created it."
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

                // Replace ingredients for this recipe
                recipeIngredientDao.deleteForRecipe(recipeId)

                val ingredientRows = r.ingredients.mapNotNull { ing ->
                    val ingredientFoodId = stableIdToFoodId[ing.ingredientFoodStableId]
                        ?: run {
                            warnings += "Recipe '${r.name}' references missing ingredientFoodStableId='${ing.ingredientFoodStableId}' (skipped ingredient)."
                            null
                        }

                    RecipeIngredientEntity(
                        recipeId = recipeId,
                        foodId = ingredientFoodId!!,
                        amountServings = ing.ingredientServings,
                        amountGrams = null
                    )
                }


                if (ingredientRows.isNotEmpty()) {
                    recipeIngredientDao.insertAll(ingredientRows)
                    ingredientsInserted += ingredientRows.size
                }
            }
        }

        return Result(
            foodsInserted = foodsInserted,
            foodsUpdated = foodsUpdated,
            foodNutrientsUpserted = foodNutrientsUpserted,
            recipesInserted = recipesInserted,
            recipesUpdated = recipesUpdated,
            ingredientsInserted = ingredientsInserted,
            warnings = warnings
        )
    }

    private fun readZipEntries(input: InputStream): Pair<String, String> {
        var foodsJson: String? = null
        var recipesJson: String? = null

        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name

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
