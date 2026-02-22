package com.example.adobongkangkong.domain.recipes

import android.util.Log
import androidx.room.withTransaction
import com.example.adobongkangkong.data.local.db.NutriDatabase
import com.example.adobongkangkong.data.local.db.dao.FoodDao
import com.example.adobongkangkong.data.local.db.dao.FoodNutrientDao
import com.example.adobongkangkong.data.local.db.dao.NutrientDao
import com.example.adobongkangkong.data.local.db.dao.RecipeBatchDao
import com.example.adobongkangkong.data.local.db.dao.RecipeDao
import com.example.adobongkangkong.data.local.db.dao.RecipeIngredientDao
import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.data.local.db.entity.FoodEntity
import com.example.adobongkangkong.data.local.db.entity.FoodNutrientEntity
import com.example.adobongkangkong.data.local.db.entity.RecipeBatchEntity
import com.example.adobongkangkong.domain.model.ServingUnit
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Creates a cooked batch snapshot from a recipe.
 *
 * ## Concept
 * A "recipe" stays editable. A "batch" is a **loggable snapshot** representing a specific cooking event.
 *
 * This use case:
 * 1) Computes recipe totals from the current persisted recipe definition.
 * 2) Converts totals into PER_100G amounts using the measured [cookedYieldGrams].
 * 3) Inserts a new non-recipe [FoodEntity] representing the batch snapshot (loggable item).
 * 4) Writes nutrient rows for that batch food (PER_100G).
 * 5) Inserts a [RecipeBatchEntity] linking (recipeId -> batchFoodId).
 *
 * ## Important invariants
 * - Each call that succeeds must create a **new** batch food row (new id).
 * - The returned [Success.batchFoodId] is the Food id that should be logged.
 * - If the recipe is invalid / missing data, we return [Blocked] and **do not write anything**.
 *
 * ## Corner cases and behavior
 * - cookedYieldGrams <= 0: [Blocked]
 * - recipe not found: [Blocked]
 * - recipe has no ingredients (or ingredients not loaded): [Blocked]
 * - nutrition compute warnings: [Blocked] (caller should show messages)
 * - if nutrient snapshot ends up empty (no rows): [Blocked] (prevents "Food nutrition incomplete")
 * - if some nutrients cannot be mapped to ids/units, they are dropped; we log counts for diagnosis.
 *
 * FUTURE-YOU NOTE (2026-02-20):
 * - Do NOT return sentinel ids like 0L on blocked/error paths.
 *   That pattern caused UNIQUE constraint failures when callers inserted RecipeBatchEntity(batchFoodId=0).
 */
class CreateSnapshotFoodFromRecipeUseCase @Inject constructor(
    private val db: NutriDatabase,
    private val recipeDao: RecipeDao,
    private val recipeIngredientDao: RecipeIngredientDao,
    private val nutrientDao: NutrientDao,
    private val foodDao: FoodDao,
    private val foodNutrientDao: FoodNutrientDao,
    private val recipeBatchDao: RecipeBatchDao,
    private val computeRecipeNutritionForSnapshotUseCase: ComputeRecipeNutritionForSnapshotUseCase
) {

    sealed interface Result {
        /**
         * Success: a new batch was created.
         *
         * @param batchId Row id in recipe_batches.
         * @param batchFoodId Newly created Food id for the cooked batch snapshot (loggable).
         */
        data class Success(
            val batchId: Long,
            val batchFoodId: Long
        ) : Result

        /**
         * Blocked: user/actionable issue (validation, recipe missing data, compute warnings).
         * Caller should show [messages] and stop (no retry without user changes).
         */
        data class Blocked(val messages: List<String>) : Result

        /**
         * Error: unexpected failure (DB, mapping, etc.).
         * Caller should show the message and allow retry.
         */
        data class Error(val message: String) : Result
    }

    suspend fun execute(
        recipeId: Long,
        cookedYieldGrams: Double,
        servingsYieldUsed: Double? = null,
        createdAt: Instant = Instant.now()
    ): Result {
        Log.w("Meow", "CreateSnapshotFoodFromRecipeUseCase.execute() VERSION=2026-02-21A (byCode lookup)")
        if (cookedYieldGrams <= 0.0) {
            return Result.Blocked(listOf("Cooked yield must be > 0g."))
        }

        val recipeEntity = recipeDao.getById(recipeId)
            ?: return Result.Blocked(listOf("Recipe not found."))

        // IMPORTANT:
        // RecipeEntity does NOT embed ingredients. If we forget to load them, snapshot compute
        // will see 0 ingredients and produce 0 totals (misleading "no nutrients" symptoms).
        val ingredientEntities = recipeIngredientDao.getForRecipe(recipeId)
        if (ingredientEntities.isEmpty()) {
            return Result.Blocked(listOf("Recipe has no ingredients."))
        }

        val domainIngredients: List<RecipeIngredient> =
            ingredientEntities.map { ie ->
                RecipeIngredient(
                    foodId = ie.foodId,
                    servings = ie.amountServings,
                    grams = ie.amountGrams
                )
            }

        // Compute from DOMAIN recipe (persisted + ingredients)
        val computed = computeRecipeNutritionForSnapshotUseCase.invoke(
            recipeEntity.toDomainRecipe(ingredients = domainIngredients)
        )

        if (computed.warnings.isNotEmpty()) {
            return Result.Blocked(computed.warnings.map { it.toString() })
        }

        val snapshot = RecipeBatchSnapshotMath.compute(
            totals = computed.totals,
            cookedYieldGrams = cookedYieldGrams,
            servingsYieldUsed = servingsYieldUsed
        )

        val per100gSnapshot = snapshot.per100g
        val gramsPerServingCooked = snapshot.gramsPerServingCooked

        if (per100gSnapshot.isEmpty()) {
            return Result.Blocked(listOf("Recipe has no nutrients to snapshot."))
        }

        return runCatching {
            db.withTransaction {
                val ts = ZonedDateTime.ofInstant(createdAt, ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("MM/dd/yyyy h:mm a"))

                // 1) Create the loggable batch food snapshot
                val hasServings = gramsPerServingCooked != null && gramsPerServingCooked > 0.0

                val batchFood = if (hasServings) {
                    FoodEntity(
                        name = "${recipeEntity.name} ($ts)",
                        brand = "From recipe",
                        servingSize = 1.0,
                        servingUnit = ServingUnit.SERVING,
                        gramsPerServingUnit = gramsPerServingCooked,
                        servingsPerPackage = servingsYieldUsed,
                        isRecipe = false,
                        isLowSodium = null
                    )
                } else {
                    // Fallback:
                    // Store as a 100g base so the food editor + any UI that assumes "servingSize + unit"
                    // aligns with the PER_100G nutrient basis we persist below.
                    FoodEntity(
                        name = "${recipeEntity.name} ($ts)",
                        brand = "From recipe",
                        servingSize = 100.0,
                        servingUnit = ServingUnit.G,
                        gramsPerServingUnit = null,
                        servingsPerPackage = null,
                        isRecipe = false,
                        isLowSodium = null
                    )
                }

                val batchFoodId = foodDao.insert(batchFood)
                require(batchFoodId > 0L) { "Failed to insert batch food." }

                // Debug proof: each batch must reference a fresh snapshot food id.
                val existingRefs = recipeBatchDao.countByBatchFoodId(batchFoodId)
                Log.d(
                    "Meow",
                    "CreateSnapshotFoodFromRecipeUseCase> inserted batch food batchFoodId=$batchFoodId existingBatchRefs=$existingRefs"
                )
                if (existingRefs > 0) {
                    error("Invariant violated: batchFoodId=$batchFoodId is already referenced by recipe_batches ($existingRefs rows).")
                }

                // 2) Build nutrient rows AFTER we have batchFoodId
                var droppedMissingId = 0
                val missingIdKeys = mutableListOf<String>()

                // Track which codes we successfully persisted (for macro seatbelt without extra DAO calls).
                val persistedCodes = LinkedHashSet<String>()

                val nutrientRows: List<FoodNutrientEntity> =
                    per100gSnapshot.entries().mapNotNull { (nutrientKey, amtPer100g) ->
                        val nutrientCode = nutrientKey.value

                        val nutrient = nutrientDao.getByCode(nutrientCode)
                            ?: run {
                                droppedMissingId++
                                if (missingIdKeys.size < 25) missingIdKeys += nutrientCode
                                return@mapNotNull null
                            }

                        persistedCodes += nutrient.code

                        FoodNutrientEntity(
                            foodId = batchFoodId,
                            nutrientId = nutrient.id,
                            nutrientAmountPerBasis = amtPer100g,
                            unit = nutrient.unit,
                            basisType = BasisType.PER_100G
                        )
                    }

                /**
                 * Seatbelt: allow batch creation as long as we have *minimum viable macros*.
                 *
                 * Why:
                 * - Ingredients may contain micronutrient keys that are not currently present in the
                 *   `nutrients` master table (catalog drift / partial seeding).
                 * - Dropping *all* rows would produce an un-loggable snapshot; we must block that.
                 * - Dropping *some* rows is acceptable if the snapshot still contains the core macros
                 *   used throughout the app (Calories/Protein/Carbs/Fat).
                 *
                 * Corner case:
                 * - If even macros cannot be written (e.g., missing CALORIES_KCAL in nutrients table),
                 *   we block to avoid creating a batch food that appears "empty".
                 */
                if (nutrientRows.isEmpty()) {
                    error(
                        "Unable to snapshot nutrients (all rows dropped: " +
                                "missingId=$droppedMissingId keys=$missingIdKeys)."
                    )
                }

                val macroCodesRequired = setOf(
                    "CALORIES_KCAL",
                    "PROTEIN_G",
                    "CARBS_G",
                    "FAT_G"
                )

                val presentMacroCodes = persistedCodes.intersect(macroCodesRequired)
                if (presentMacroCodes.size < macroCodesRequired.size) {
                    error(
                        "Unable to snapshot nutrients (macros incomplete: present=$presentMacroCodes required=$macroCodesRequired). " +
                                "missingId=$droppedMissingId keys=$missingIdKeys"
                    )
                }

                if (droppedMissingId > 0) {
                    Log.w(
                        "Meow",
                        "Snapshot nutrient drops: missingId=$droppedMissingId keys=${missingIdKeys.distinct()}"
                    )
                }

                // 3) Write nutrient rows for the batch food snapshot
                foodNutrientDao.deleteForFood(batchFoodId)
                foodNutrientDao.upsertAll(nutrientRows)

                Log.d(
                    "Meow",
                    "CreateSnapshotFoodFromRecipeUseCase> batchFoodId=$batchFoodId rows=${nutrientRows.size} droppedMissingId=$droppedMissingId"
                )

                // 4) Persist the batch link (recipe -> batchFoodId)
                Log.d(
                    "Meow",
                    "CreateSnapshotFoodFromRecipeUseCase> inserting recipe_batch recipeId=$recipeId batchFoodId=$batchFoodId"
                )

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

                require(batchId > 0L) { "Failed to insert recipe batch." }

                Log.d(
                    "Meow",
                    "CreateSnapshotFoodFromRecipeUseCase> inserted recipe_batch batchId=$batchId recipeId=$recipeId batchFoodId=$batchFoodId"
                )

                Result.Success(
                    batchId = batchId,
                    batchFoodId = batchFoodId
                )
            }
        }.getOrElse { t ->
            Result.Error(t.message ?: "Failed to create batch snapshot.")
        }
    }
}