package com.example.adobongkangkong.data.repository

import androidx.room.withTransaction
import com.example.adobongkangkong.data.local.db.NutriDatabase
import com.example.adobongkangkong.data.local.db.dao.FoodNutrientDao
import com.example.adobongkangkong.data.local.db.dao.RecipeDao
import com.example.adobongkangkong.data.local.db.dao.RecipeIngredientDao
import com.example.adobongkangkong.data.local.db.entity.FoodEntity
import com.example.adobongkangkong.data.local.db.entity.FoodNutrientEntity
import com.example.adobongkangkong.data.local.db.entity.RecipeEntity
import com.example.adobongkangkong.data.local.db.entity.RecipeIngredientEntity
import com.example.adobongkangkong.domain.model.RecipeDraft
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.recipes.ComputeRecipeNutritionForSnapshotUseCase
import com.example.adobongkangkong.domain.repository.RecipeHeader
import com.example.adobongkangkong.domain.repository.RecipeIngredientLine
import com.example.adobongkangkong.domain.repository.RecipeRepository
import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.domain.nutrition.NutrientMap
import com.example.adobongkangkong.data.local.db.dao.NutrientDao
import com.example.adobongkangkong.domain.recipes.Recipe
import com.example.adobongkangkong.domain.recipes.RecipeIngredient
import javax.inject.Inject


class RecipeRepositoryImpl @Inject constructor(
    private val db: NutriDatabase,
    private val recipeDao: RecipeDao,
    private val ingredientDao: RecipeIngredientDao,
    private val computeRecipeNutritionForSnapshot: ComputeRecipeNutritionForSnapshotUseCase,
    private val foodNutrientDao: FoodNutrientDao

) : RecipeRepository {

    override suspend fun createRecipe(draft: RecipeDraft): Long = db.withTransaction {
        require(draft.name.isNotBlank())
        require(draft.servingsYield > 0.0)
        require(draft.ingredients.isNotEmpty())

        val foodDao = db.foodDao()
        val recipeDao = db.recipeDao()
        val ingredientDao = db.recipeIngredientDao()

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

        // 3) Create ingredients (servings-based for now)
        ingredientDao.insertAll(
            draft.ingredients.map { ing ->
                RecipeIngredientEntity(
                    recipeId = recipeId,
                    foodId = ing.foodId,
                    amountServings = ing.ingredientServings,
                    amountGrams = null
                )
            }
        )

// ---- Persist computed recipe nutrition for snapshot resolution ----
        // Persist computed recipe nutrients into food_nutrients so list/snapshots can resolve macros.
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
        if (perCookedGram != null && !perCookedGram.isEmpty()) {
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

// ---- End nutrition persistence ----

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

    override suspend fun getIngredients(recipeId: Long): List<RecipeIngredientLine> =
        ingredientDao.getForRecipe(recipeId).map { entity ->
            RecipeIngredientLine(
                ingredientFoodId = entity.foodId,
                ingredientServings = entity.amountServings ?: 0.0
            )
        }

    override suspend fun updateRecipeByFoodId(
        foodId: Long,
        servingsYield: Double,
        totalYieldGrams: Double?,
        ingredients: List<RecipeIngredientLine>
    ) = db.withTransaction {
        val existing = recipeDao.getByFoodId(foodId) ?: return@withTransaction
        val recipeId = existing.id

        recipeDao.insert(existing.copy(servingsYield = servingsYield, totalYieldGrams = totalYieldGrams))

        ingredientDao.deleteForRecipe(recipeId)
        ingredientDao.insertAll(
            ingredients.map { line ->
                RecipeIngredientEntity(
                    recipeId = recipeId,
                    foodId = line.ingredientFoodId,
                    amountServings = line.ingredientServings,
                    amountGrams = null
                )
            }
        )
        // Refresh persisted recipe nutrients after edits so list/snapshots stay correct.
        val domainRecipe = Recipe(
            id = recipeId,
            name = existing.name,
            ingredients = ingredients.map { line ->
                RecipeIngredient(
                    foodId = line.ingredientFoodId,
                    servings = line.ingredientServings
                )
            },
            servingsYield = servingsYield,
            totalYieldGrams = totalYieldGrams
        )

        val computed = computeRecipeNutritionForSnapshot(recipe = domainRecipe)
        val perCookedGram = computed.perCookedGram

        if (perCookedGram != null && !perCookedGram.isEmpty()) {
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

    }

}
/**
 * ============================================================================
 * FOR-FUTURE-ME NOTES (RecipeRepositoryImpl)
 * ============================================================================
 *
 * WHY THIS EXISTS
 * --------------
 * This repository enforces the project’s “recipe is a food” rule:
 *
 *   - A Recipe has its own metadata (servingsYield, totalYieldGrams, ingredients list)
 *   - BUT the “thing you log/search/display like a food” is a Food row
 *   - Therefore, every recipe must have a FoodEntity row (isRecipe=true)
 *
 * In other words:
 *   RecipeEntity is metadata.
 *   FoodEntity is the canonical identity used across the app (logging, flags, etc.).
 *
 * KEY RELATIONSHIP / INVARIANTS
 * -----------------------------
 * 1) createRecipe() ALWAYS creates a FoodEntity FIRST, then RecipeEntity references it:
 *      val recipeFoodId = insert(FoodEntity(isRecipe=true))
 *      val recipeId     = insert(RecipeEntity(foodId = recipeFoodId, ...))
 *
 * 2) Goal flags (favorite/eatMore/limit) are FOOD-CENTRIC (FoodGoalFlagsEntity PK = foodId).
 *    So recipe flags must attach to the RECIPE’S FOOD ID (recipeFoodId), not recipeId.
 *
 *    This repo currently returns recipeId. That’s fine, but callers who need flags
 *    must resolve recipeFoodId via getByFoodId / getRecipeByFoodId or change the API.
 *
 * 3) This file intentionally does NOT compute nutrition.
 *    Nutrition is derived elsewhere (ingredient composition) and should ultimately
 *    flow into food_nutrients keyed by the recipeFoodId when we implement that fully.
 *
 * TRANSACTION DESIGN
 * ------------------
 * createRecipe() is wrapped in db.withTransaction to guarantee atomicity:
 *   - If ingredient insert fails, we don't leave behind orphaned food/recipe rows.
 *   - If recipe insert fails, we don't leave behind a “recipe food” with no recipe meta.
 *
 * VALIDATION RULES (CURRENT)
 * --------------------------
 * - name must be non-blank
 * - servingsYield must be > 0
 * - ingredients must be non-empty
 *
 * These are “hard requires” because downstream math assumes they’re valid.
 *
 * SERVING MODEL FOR RECIPE FOOD ROW
 * --------------------------------
 * When creating the FoodEntity representing the recipe, we set:
 *   servingSize = 1.0
 *   servingUnit = ServingUnit.SERVING
 *   gramsPerServingUnit = null
 *
 * The intent:
 * - Users will log recipe consumption by grams (or servings) based on cooked yield logic.
 * - gramsPerServingUnit is NOT known at creation time, because it depends on:
 *     totalYieldGrams and servingsYield (and possibly later user edits).
 *
 * The “SERVING” unit here is basically a label that says:
 *   “This Food is a recipe; its serving math is special and derived.”
 *
 * INGREDIENT STORAGE (CURRENT STATE)
 * ---------------------------------
 * We store ingredients as servings-based amounts for now:
 *   RecipeIngredientEntity(amountServings = X, amountGrams = null)
 *
 * This is a conscious simplification: grams-based ingredient amounts can be added later.
 * When we do, we must keep conversions deterministic and avoid storing conflicting sources.
 *
 * UPDATE PATH (updateRecipeByFoodId)
 * ---------------------------------
 * updateRecipeByFoodId() is also transactional and does:
 *   - look up RecipeEntity by foodId
 *   - update servingsYield + totalYieldGrams
 *   - delete all ingredient rows for recipeId
 *   - insert the new list
 *
 * NOTE: This does NOT currently update the FoodEntity name if draft name changes.
 * If name editing is allowed for recipes, we must also update FoodEntity.name to match.
 *
 * IMPORTANT FUTURE TODOs / PITFALLS
 * --------------------------------
 * - FLAGS PERSISTENCE:
 *   The flags table is keyed by foodId, so recipe flags must be written using recipeFoodId.
 *   Since createRecipe() returns recipeId, the caller either:
 *     (A) resolves recipeFoodId after creation, or
 *     (B) changes createRecipe() / CreateRecipeUseCase() to return recipeFoodId too.
 *   DO NOT accidentally store flags under recipeId — it will silently fail conceptually.
 *
 * - NUTRIENT BASIS:
 *   We have multiple BasisType rows (USDA_REPORTED_SERVING / PER_100G / PER_100ML) in DB.
 *   The long-term “canonical” goal is: store only PER_100G or PER_100ML when possible,
 *   and keep serving basis only when the unit is container-ish/ambiguous and not yet grounded.
 *
 * - DELETE/ORPHANS:
 *   FoodEntity is the canonical identity; RecipeEntity references it.
 *   Ensure deletes cascade/are handled consistently (recipe deletion must remove food row,
 *   or at least not leave an isRecipe food with no RecipeEntity).
 *
 * - RETURN VALUES:
 *   Returning recipeId is fine for editing ingredients, but many app features operate on foodId.
 *   Be explicit at call sites which ID you need.
 *
 * END GOAL
 * --------
 * This repository’s job is: keep recipe meta + ingredients consistent with the recipe’s
 * FoodEntity identity, inside transactions, without mixing in nutrient/flags logic.
 * ============================================================================
 */

