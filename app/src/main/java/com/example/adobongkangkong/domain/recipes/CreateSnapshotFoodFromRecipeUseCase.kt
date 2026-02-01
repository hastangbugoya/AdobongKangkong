package com.example.adobongkangkong.domain.recipes

import com.example.adobongkangkong.data.local.db.dao.FoodDao
import com.example.adobongkangkong.data.local.db.dao.FoodNutrientDao
import com.example.adobongkangkong.data.local.db.dao.NutrientDao
import com.example.adobongkangkong.data.local.db.dao.RecipeBatchDao
import com.example.adobongkangkong.data.local.db.dao.RecipeDao
import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.data.local.db.entity.FoodEntity
import com.example.adobongkangkong.data.local.db.entity.FoodNutrientEntity
import com.example.adobongkangkong.data.local.db.entity.RecipeBatchEntity
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.nutrition.NutrientCatalog
import java.time.Instant
import javax.inject.Inject

/**
 * Creates a cooked batch snapshot from a recipe.
 *
 * Result:
 * - Inserts a new FoodEntity (isRecipe=false) representing the cooked batch.
 * - Writes the batch Food's nutrients as PER_100G.
 * - Inserts RecipeBatchEntity linking recipe -> batchFoodId.
 *
 * This enforces the app rule:
 * - Recipe remains recipe (editable).
 * - Batch becomes Food (loggable snapshot).
 */
class CreateSnapshotFoodFromRecipeUseCase @Inject constructor(
    private val recipeDao: RecipeDao,
    private val nutrientDao: NutrientDao, // kept to avoid changing wiring; OK if unused
    private val foodDao: FoodDao,
    private val foodNutrientDao: FoodNutrientDao,
    private val recipeBatchDao: RecipeBatchDao,
    private val computeRecipeNutritionForSnapshotUseCase: ComputeRecipeNutritionForSnapshotUseCase
) {

    data class Result(
        val batchId: Long,
        val batchFoodId: Long,
        val blockedMessages: List<String>
    )

    suspend fun execute(
        recipeId: Long,
        cookedYieldGrams: Double,
        servingsYieldUsed: Double? = null,
        createdAt: Instant = Instant.now()
    ): Result {

        if (cookedYieldGrams <= 0.0) {
            return Result(
                batchId = 0L,
                batchFoodId = 0L,
                blockedMessages = listOf("Cooked yield must be > 0g.")
            )
        }

        val recipeEntity = recipeDao.getById(recipeId)
            ?: return Result(
                batchId = 0L,
                batchFoodId = 0L,
                blockedMessages = listOf("Recipe not found.")
            )

        // Compute from DOMAIN recipe
        val computed = computeRecipeNutritionForSnapshotUseCase.invoke(
            recipeEntity.toDomainRecipe()
        )

        if (computed.warnings.isNotEmpty()) {
            return Result(
                batchId = 0L,
                batchFoodId = 0L,
                blockedMessages = computed.warnings.map { it.toString() }
            )
        }

        // Snapshot basis: cooked yield (final mass)
        // computed.totals is keyed by NutrientKey; we persist as PER_100G
        val per100gSnapshot: Map<String, Double> =
            computed.totals.entries().associate { (nutrientKey, totalAmt) ->
                nutrientKey.value to (totalAmt / cookedYieldGrams) * 100.0
            }

        // Create the loggable batch food snapshot
        val batchFood = FoodEntity(
            name = "${recipeEntity.name} (Batch)",
            brand = "From recipe", // provenance; or null if you prefer
            servingSize = 1.0,
            servingUnit = ServingUnit.G,
            gramsPerServing = null,
            servingsPerPackage = null,
            isRecipe = false,
            isLowSodium = null
        )

        val batchFoodId = foodDao.insert(batchFood)

        // Build nutrient rows AFTER we have batchFoodId
        val nutrientRows: List<FoodNutrientEntity> =
            per100gSnapshot.mapNotNull { (nutrientKeyValue, amtPer100g) ->
                val nutrientId = NutrientCatalog.idOfValue(nutrientKeyValue)
                    ?: return@mapNotNull null

                FoodNutrientEntity(
                    foodId = batchFoodId,
                    nutrientId = nutrientId,
                    nutrientAmountPerBasis = amtPer100g,
                    basisType = BasisType.PER_100G
                )
            }

        // Write nutrient rows for the batch food snapshot
        foodNutrientDao.deleteForFood(batchFoodId)
        foodNutrientDao.upsertAll(nutrientRows)

        // Persist the batch link (recipe -> batchFoodId)
        val batchId = recipeBatchDao.insert(
            RecipeBatchEntity(
                id = 0L,
                recipeId = recipeId,
                batchFoodId = batchFoodId,
                cookedYieldGrams = cookedYieldGrams,
                servingsYieldUsed = servingsYieldUsed,
                createdAt = createdAt
            )
        )

        return Result(
            batchId = batchId,
            batchFoodId = batchFoodId,
            blockedMessages = emptyList()
        )
    }
}
