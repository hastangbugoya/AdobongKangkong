package com.example.adobongkangkong.data.repository

import androidx.room.withTransaction
import com.example.adobongkangkong.data.local.db.NutriDatabase
import com.example.adobongkangkong.data.local.db.dao.FoodDao
import com.example.adobongkangkong.data.local.db.dao.FoodNutrientDao
import com.example.adobongkangkong.data.local.db.dao.NutrientDao
import com.example.adobongkangkong.data.local.db.dao.RecipeDao
import com.example.adobongkangkong.data.local.db.dao.RecipeIngredientDao
import com.example.adobongkangkong.data.local.db.dao.RecipeInstructionStepDao
import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.data.local.db.entity.FoodEntity
import com.example.adobongkangkong.data.local.db.entity.FoodNutrientEntity
import com.example.adobongkangkong.data.local.db.entity.RecipeEntity
import com.example.adobongkangkong.data.local.db.entity.RecipeIngredientEntity
import com.example.adobongkangkong.data.local.db.entity.RecipeInstructionStepEntity
import com.example.adobongkangkong.domain.model.RecipeDraft
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.recipes.ComputeRecipeNutritionForSnapshotUseCase
import com.example.adobongkangkong.domain.recipes.Recipe
import com.example.adobongkangkong.domain.recipes.RecipeIngredient
import com.example.adobongkangkong.domain.repository.RecipeHeader
import com.example.adobongkangkong.domain.repository.RecipeIngredientLine
import com.example.adobongkangkong.domain.repository.RecipeInstructionStep
import com.example.adobongkangkong.domain.repository.RecipeRepository
import javax.inject.Inject

class RecipeRepositoryImpl @Inject constructor(
    private val db: NutriDatabase,
    private val recipeDao: RecipeDao,
    private val ingredientDao: RecipeIngredientDao,
    private val instructionStepDao: RecipeInstructionStepDao,
    private val computeRecipeNutritionForSnapshot: ComputeRecipeNutritionForSnapshotUseCase,
    private val foodNutrientDao: FoodNutrientDao,
    private val foodDao: FoodDao,
) : RecipeRepository {

    override suspend fun createRecipe(draft: RecipeDraft): Long = db.withTransaction {
        require(draft.name.isNotBlank())
        require(draft.servingsYield > 0.0)
        require(draft.ingredients.isNotEmpty())

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

        val recipeId = recipeDao.insert(
            RecipeEntity(
                foodId = recipeFoodId,
                name = draft.name,
                servingsYield = draft.servingsYield,
                totalYieldGrams = draft.totalYieldGrams
            )
        )

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

        perCookedGram?.takeIf { !it.isEmpty() }?.let {
            val nutrientDao: NutrientDao = db.nutrientDao()

            foodNutrientDao.deleteForFood(recipeFoodId)

            val rows = perCookedGram.entries().mapNotNull { (key, amountPerGram) ->
                val code = key.value
                val nutrientId = nutrientDao.getIdByCode(code) ?: return@mapNotNull null
                val unit = nutrientDao.getUnitByCode(code) ?: return@mapNotNull null

                FoodNutrientEntity(
                    foodId = recipeFoodId,
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
    ) {
        db.withTransaction {
            val existing = recipeDao.getByFoodId(foodId) ?: return@withTransaction
            val recipeId = existing.id

            recipeDao.insert(
                existing.copy(
                    servingsYield = servingsYield,
                    totalYieldGrams = totalYieldGrams
                )
            )

            val gramsPerServing = totalYieldGrams
                ?.takeIf { it > 0.0 && servingsYield > 0.0 }
                ?.div(servingsYield)
                ?.takeIf { it > 0.0 }

            foodDao.updateGramsPerServingUnit(
                id = foodId,
                gramsPerServingUnit = gramsPerServing
            )

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

            val domainRecipe = Recipe(
                id = recipeId,
                name = existing.name,
                ingredients = ingredients.map { line ->
                    val servingsForCompute =
                        line.ingredientServings
                            ?: line.ingredientGrams?.let { 1.0 }
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

            perCookedGram?.takeIf { !it.isEmpty() }?.let {
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

    override suspend fun getHeaderByRecipeId(recipeId: Long): RecipeHeader? {
        val r = recipeDao.getById(recipeId) ?: return null
        return RecipeHeader(
            recipeId = r.id,
            foodId = r.foodId,
            servingsYield = r.servingsYield,
            totalYieldGrams = r.totalYieldGrams
        )
    }

    override suspend fun softDeleteRecipeByFoodId(foodId: Long) {
        db.withTransaction {
            val now = System.currentTimeMillis()

            // 1. Soft delete recipe
            recipeDao.softDeleteByFoodId(
                foodId = foodId,
                deletedAtEpochMs = now
            )

            // 2. Soft delete backing food (critical for hiding in search/logging)
            val food = foodDao.getById(foodId)
            if (food != null) {
                foodDao.upsert(
                    food.copy(
                        isDeleted = true,
                        deletedAtEpochMs = now
                    )
                )
            }
        }
    }

    override suspend fun getFoodIdsByRecipeIds(recipeIds: Set<Long>): Map<Long, Long> {
        if (recipeIds.isEmpty()) return emptyMap()
        return recipeDao.getByIds(recipeIds.toList())
            .associate { it.id to it.foodId }
    }

    override suspend fun getRecipeIdsByFoodIds(foodIds: Set<Long>): Map<Long, Long> {
        if (foodIds.isEmpty()) return emptyMap()
        return recipeDao.getByFoodIds(foodIds.toList())
            .associate { it.foodId to it.id }
    }

    override suspend fun getInstructionSteps(recipeId: Long): List<RecipeInstructionStep> {
        return instructionStepDao.getForRecipe(recipeId).map { entity ->
            entity.toDomain()
        }
    }

    override suspend fun insertInstructionStep(
        recipeId: Long,
        position: Int,
        text: String
    ): Long = db.withTransaction {
        instructionStepDao.insert(
            RecipeInstructionStepEntity(
                recipeId = recipeId,
                position = position,
                text = text
            )
        )
    }

    override suspend fun updateInstructionStepText(
        stepId: Long,
        text: String
    ) {
        instructionStepDao.updateText(
            stepId = stepId,
            text = text
        )
    }

    override suspend fun updateInstructionStepPosition(
        stepId: Long,
        position: Int
    ) {
        instructionStepDao.updatePosition(
            stepId = stepId,
            position = position
        )
    }

    override suspend fun setInstructionStepImage(
        stepId: Long,
        imagePath: String?
    ) {
        instructionStepDao.updateImagePath(
            stepId = stepId,
            imagePath = imagePath
        )
    }

    override suspend fun deleteInstructionStep(stepId: Long) {
        instructionStepDao.deleteById(stepId)
    }

    override suspend fun deleteInstructionStepsForRecipe(recipeId: Long) {
        instructionStepDao.deleteForRecipe(recipeId)
    }

    override suspend fun reorderInstructionSteps(
        recipeId: Long,
        orderedStepIds: List<Long>
    ) {
        db.withTransaction {
            reorderInstructionStepsInternal(
                recipeId = recipeId,
                orderedStepIds = orderedStepIds
            )
        }
    }

    override suspend fun moveInstructionStepUp(
        recipeId: Long,
        stepId: Long
    ) {
        db.withTransaction {
            val existing = instructionStepDao.getForRecipe(recipeId)
            val currentIndex = existing.indexOfFirst { it.id == stepId }

            require(currentIndex >= 0) {
                "moveInstructionStepUp failed: stepId=$stepId does not belong to recipeId=$recipeId"
            }

            if (currentIndex == 0) return@withTransaction

            val reorderedIds = existing.map { it.id }.toMutableList()
            val movingId = reorderedIds[currentIndex]
            reorderedIds[currentIndex] = reorderedIds[currentIndex - 1]
            reorderedIds[currentIndex - 1] = movingId

            reorderInstructionStepsInternal(
                recipeId = recipeId,
                orderedStepIds = reorderedIds
            )
        }
    }

    override suspend fun moveInstructionStepDown(
        recipeId: Long,
        stepId: Long
    ) {
        db.withTransaction {
            val existing = instructionStepDao.getForRecipe(recipeId)
            val currentIndex = existing.indexOfFirst { it.id == stepId }

            require(currentIndex >= 0) {
                "moveInstructionStepDown failed: stepId=$stepId does not belong to recipeId=$recipeId"
            }

            if (currentIndex == existing.lastIndex) return@withTransaction

            val reorderedIds = existing.map { it.id }.toMutableList()
            val movingId = reorderedIds[currentIndex]
            reorderedIds[currentIndex] = reorderedIds[currentIndex + 1]
            reorderedIds[currentIndex + 1] = movingId

            reorderInstructionStepsInternal(
                recipeId = recipeId,
                orderedStepIds = reorderedIds
            )
        }
    }

    private suspend fun reorderInstructionStepsInternal(
        recipeId: Long,
        orderedStepIds: List<Long>
    ) {
        val existing = instructionStepDao.getForRecipe(recipeId)

        val existingIds = existing.map { it.id }.sorted()
        val providedIds = orderedStepIds.sorted()

        require(existingIds == providedIds) {
            "reorderInstructionSteps strict mode failed: provided IDs do not match existing steps"
        }

        existing.forEachIndexed { index, step ->
            instructionStepDao.updatePosition(
                stepId = step.id,
                position = -1000 - index
            )
        }

        orderedStepIds.forEachIndexed { index, stepId ->
            instructionStepDao.updatePosition(
                stepId = stepId,
                position = index
            )
        }
    }

    private fun RecipeInstructionStepEntity.toDomain(): RecipeInstructionStep {
        return RecipeInstructionStep(
            id = id,
            stableId = stableId,
            recipeId = recipeId,
            position = position,
            text = text,
            imagePath = imagePath
        )
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
 *   surface a better UX later.
 *
 * Why default to 1.0 (for compute only):
 * - Prevents crashes / type mismatches where compute expects a non-null Double.
 * - Avoids misleading “0 servings” display, which looks like valid data but isn’t.
 * - Keeps storage/output semantics intact: null still means “unknown / not provided”.
 */