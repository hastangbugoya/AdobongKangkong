package com.example.adobongkangkong.data.repository

import androidx.room.withTransaction
import com.example.adobongkangkong.data.local.db.NutriDatabase
import com.example.adobongkangkong.data.local.db.dao.FoodDao
import com.example.adobongkangkong.data.local.db.dao.FoodNutrientDao
import com.example.adobongkangkong.data.local.db.dao.NutrientDao
import com.example.adobongkangkong.data.local.db.dao.RecipeDao
import com.example.adobongkangkong.data.local.db.dao.RecipeIngredientDao
import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.data.local.db.entity.FoodEntity
import com.example.adobongkangkong.data.local.db.entity.FoodNutrientEntity
import com.example.adobongkangkong.data.local.db.entity.RecipeEntity
import com.example.adobongkangkong.data.local.db.entity.RecipeIngredientEntity
import com.example.adobongkangkong.domain.model.RecipeDraft
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.recipes.ComputeRecipeNutritionForSnapshotUseCase
import com.example.adobongkangkong.domain.recipes.Recipe
import com.example.adobongkangkong.domain.recipes.RecipeIngredient
import com.example.adobongkangkong.domain.repository.RecipeHeader
import com.example.adobongkangkong.domain.repository.RecipeIngredientLine
import com.example.adobongkangkong.domain.repository.RecipeRepository
import javax.inject.Inject

class RecipeRepositoryImpl @Inject constructor(
    private val db: NutriDatabase,
    private val recipeDao: RecipeDao,
    private val ingredientDao: RecipeIngredientDao,
    private val computeRecipeNutritionForSnapshot: ComputeRecipeNutritionForSnapshotUseCase,
    private val foodNutrientDao: FoodNutrientDao,
    private val foodDao: FoodDao,
) : RecipeRepository {

    override suspend fun createRecipe(draft: RecipeDraft): Long = db.withTransaction {
        require(draft.name.isNotBlank())
        require(draft.servingsYield > 0.0)
        require(draft.ingredients.isNotEmpty())

        // 1) Create Food row representing the recipe
        val recipeFoodId = foodDao.insert(
            FoodEntity(
                name = draft.name,
                brand = null,
                servingSize = 1.0,
                servingUnit = ServingUnit.SERVING,
                gramsPerServingUnit = draft.totalYieldGrams
                    ?.takeIf { it > 0.0 }
                    ?.let { total -> total / draft.servingsYield },
                servingsPerPackage = draft.servingsYield,
                isRecipe = true
            )
        )

        // 2) Create recipe meta
        val recipeId = recipeDao.insert(
            RecipeEntity(
                foodId = recipeFoodId,
                name = draft.name,
                servingsYield = draft.servingsYield,
                totalYieldGrams = draft.totalYieldGrams
            )
        )

        // 3) Create ingredients
        ingredientDao.insertAll(
            draft.ingredients.map { ing ->
                // Draft currently uses servings (and defaults to 1 when missing).
                RecipeIngredientEntity(
                    recipeId = recipeId,
                    foodId = ing.foodId,
                    amountServings = ing.ingredientServings,
                    amountGrams = null
                )
            }
        )

        // 4) Persist computed recipe nutrition for snapshot resolution
        val domainRecipe = Recipe(
            id = recipeId,
            name = draft.name,
            ingredients = draft.ingredients.map { ing ->
                RecipeIngredient(
                    foodId = ing.foodId,
                    servings = ing.ingredientServings
                )
            },
            servingsYield = draft.servingsYield,
            totalYieldGrams = draft.totalYieldGrams
        )

        val nutrition = computeRecipeNutritionForSnapshot(recipe = domainRecipe)
        val perCookedGram = nutrition.perCookedGram

        perCookedGram?.takeIf{!it.isEmpty()}?.let() {
            val nutrientDao: NutrientDao = db.nutrientDao()

            // Replace existing nutrients for this recipe foodId.
            foodNutrientDao.deleteForFood(recipeFoodId)

            val rows = perCookedGram.entries().mapNotNull { (key, amountPerGram) ->
                val code = key.value
                val nutrientId = nutrientDao.getIdByCode(code) ?: return@mapNotNull null
                val unit = nutrientDao.getUnitByCode(code) ?: return@mapNotNull null

                FoodNutrientEntity(
                    foodId = recipeFoodId,
                    nutrientId = nutrientId,
                    nutrientAmountPerBasis = amountPerGram * 100.0, // per gram -> per 100g
                    unit = unit,
                    basisType = BasisType.PER_100G
                )
            }

            if (rows.isNotEmpty()) {
                foodNutrientDao.upsertAll(rows)
            }
        }

        recipeId
    }

    override suspend fun getRecipeByFoodId(foodId: Long): RecipeHeader? {
        val r = recipeDao.getByFoodId(foodId) ?: return null
        return RecipeHeader(
            recipeId = r.id,
            foodId = r.foodId,
            servingsYield = r.servingsYield,
            totalYieldGrams = r.totalYieldGrams
        )
    }

    override suspend fun getIngredients(recipeId: Long): List<RecipeIngredientLine> {
        return ingredientDao.getForRecipe(recipeId).map { entity ->
            // ✅ Keep nulls as null (do NOT coerce to 0.0)
            RecipeIngredientLine(
                ingredientFoodId = entity.foodId,
                ingredientServings = entity.amountServings,
                ingredientGrams = entity.amountGrams
            )
        }
    }

    override suspend fun updateRecipeByFoodId(
        foodId: Long,
        servingsYield: Double,
        totalYieldGrams: Double?,
        ingredients: List<RecipeIngredientLine>
    ) { db.withTransaction {
        val existing = recipeDao.getByFoodId(foodId) ?: return@withTransaction
        val recipeId = existing.id

        recipeDao.insert(existing.copy(servingsYield = servingsYield, totalYieldGrams = totalYieldGrams))

        val gramsPerServing = totalYieldGrams
            ?.takeIf { it > 0.0 && servingsYield > 0.0 }
            ?.div(servingsYield)
            ?.takeIf { it > 0.0 }

        foodDao.updateGramsPerServingUnit(
            id = foodId,
            gramsPerServingUnit = gramsPerServing
        )

        // Replace ingredient rows
        ingredientDao.deleteForRecipe(recipeId)
        ingredientDao.insertAll(
            ingredients.map { line ->
                RecipeIngredientEntity(
                    recipeId = recipeId,
                    foodId = line.ingredientFoodId,
                    amountServings = line.ingredientServings,
                    amountGrams = line.ingredientGrams
                )
            }
        )

        // Refresh persisted recipe nutrients after edits
        val domainRecipe = Recipe(
            id = recipeId,
            name = existing.name,
            ingredients = ingredients.map { line ->
                // ✅ Default to 1.0 ONLY for compute path if both null.
                // This matches your “default-to-1” decision without destroying nulls in storage/output.
                val servingsForCompute =
                    line.ingredientServings
                        ?: line.ingredientGrams?.let { _ -> 1.0 }
                        ?: 1.0

                RecipeIngredient(
                    foodId = line.ingredientFoodId,
                    servings = servingsForCompute
                )
            },
            servingsYield = servingsYield,
            totalYieldGrams = totalYieldGrams
        )

        val computed = computeRecipeNutritionForSnapshot(recipe = domainRecipe)
        val perCookedGram = computed.perCookedGram

        perCookedGram?.takeIf{!it.isEmpty()}?.let() {
            val nutrientDao: NutrientDao = db.nutrientDao()

            foodNutrientDao.deleteForFood(foodId)

            val rows = perCookedGram.entries().mapNotNull { (key, amountPerGram) ->
                val code = key.value
                val nutrientId = nutrientDao.getIdByCode(code) ?: return@mapNotNull null
                val unit = nutrientDao.getUnitByCode(code) ?: return@mapNotNull null

                FoodNutrientEntity(
                    foodId = foodId,
                    nutrientId = nutrientId,
                    nutrientAmountPerBasis = amountPerGram * 100.0,
                    unit = unit,
                    basisType = BasisType.PER_100G
                )
            }

            if (rows.isNotEmpty()) {
                foodNutrientDao.upsertAll(rows)
            }
        }
    }}

    override suspend fun getHeaderByRecipeId(recipeId: Long): RecipeHeader? {
        val r = recipeDao.getById(recipeId) ?: return null
        return RecipeHeader(
            recipeId = r.id,
            foodId = r.foodId,
            servingsYield = r.servingsYield,
            totalYieldGrams = r.totalYieldGrams
        )
    }

override suspend fun getFoodIdsByRecipeIds(recipeIds: Set<Long>): Map<Long, Long> {
    if (recipeIds.isEmpty()) return emptyMap()
    return recipeDao.getByIds(recipeIds.toList())
        .associate { it.id to it.foodId }
}
}

/**
 * FOR-FUTURE-ME — Null ingredient quantities and the “default-to-1” decision
 *
 * Source of error:
 * - We changed domain RecipeIngredientLine to support:
 *     ingredientServings: Double? = null
 *     ingredientGrams: Double? = null
 * - Old code coerced null servings to 0.0 (`?: 0.0`), which makes the UI show misleading
 *   “0 servings” and breaks downstream math (0 collapses totals).
 *
 * Current rule:
 * - Repo outputs keep null as null (no `?: 0.0`).
 * - For nutrition compute only (ComputeRecipeNutritionForSnapshotUseCase / builder previews),
 *   we may temporarily default missing quantity to 1.0 so the UI doesn’t crash and we can
 *   surface a better UX later (explicit prompt/validation, or grams-first compute).
 *
 * Why default to 1.0 (for compute only):
 * - Prevents crashes / type mismatches where compute expects a non-null Double.
 * - Avoids misleading “0 servings” display, which looks like valid data but isn’t.
 * - Keeps storage/output semantics intact: null still means “unknown / not provided”.
 */