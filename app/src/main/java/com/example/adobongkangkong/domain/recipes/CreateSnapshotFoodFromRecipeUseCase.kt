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
 * Creates a **batch snapshot food** from a recipe, representing a specific cooking event that can be logged independently.
 *
 * Purpose
 * - Convert the current recipe definition into a new immutable, loggable Food snapshot representing a cooked batch.
 * - Ensure the snapshot contains stable PER_100G nutrient rows and, when possible, serving-based logging support.
 * - Link the snapshot back to its source recipe via [RecipeBatchEntity].
 *
 * Rationale (why this use case exists)
 * - Recipes remain editable over time, but logged consumption must reference immutable nutrition data.
 * - If recipes were logged directly, later edits would retroactively change historical logs.
 * - Creating a dedicated batch snapshot guarantees:
 *   - reproducible nutrition,
 *   - auditability of cooking events,
 *   - and independence from future recipe edits.
 *
 * Conceptual model
 * - Recipe → editable definition (ingredients, yields).
 * - Batch → immutable snapshot representing one cooking event.
 * - Batch food → a normal FoodEntity (isRecipe=false) with fixed PER_100G nutrients.
 * - RecipeBatchEntity → link table connecting recipeId → batchFoodId.
 *
 * High-level workflow
 * 1) Validate inputs and load persisted recipe + ingredient rows.
 * 2) Compute full nutrition using [ComputeRecipeNutritionForSnapshotUseCase].
 * 3) Convert totals into PER_100G nutrient amounts using [cookedYieldGrams].
 * 4) Insert a new FoodEntity representing the cooked batch snapshot.
 * 5) Insert FoodNutrientEntity rows (PER_100G basis).
 * 6) Insert RecipeBatchEntity linking recipe → snapshot food.
 * 7) Return identifiers for logging and navigation.
 *
 * Persistence guarantees
 * - All writes occur inside a single database transaction ([NutriDatabase.withTransaction]).
 * - Either the entire snapshot is created successfully, or nothing is written.
 * - Every successful execution creates a brand-new Food row (never reused).
 *
 * Parameters
 * - recipeId:
 *   Recipe being cooked. Must exist and contain ingredients.
 *
 * - cookedYieldGrams:
 *   Measured cooked yield used to normalize totals into PER_100G.
 *   Required for correct logging math.
 *
 * - servingsYieldUsed:
 *   Optional actual servings produced during this batch.
 *   When present, enables SERVING-based logging for the batch snapshot.
 *
 * - createdAt:
 *   Timestamp used for:
 *   - batch entity creation,
 *   - snapshot food naming (human readable),
 *   - and chronological ordering.
 *
 * Return
 * - [Result.Success]
 *   Snapshot successfully created.
 *   - batchId → recipe_batches primary key.
 *   - batchFoodId → Food id representing the snapshot (loggable item).
 *
 * - [Result.Blocked]
 *   Expected, user-actionable condition. No DB writes occur.
 *   Examples:
 *   - recipe missing
 *   - no ingredients
 *   - invalid yield
 *   - nutrition warnings
 *   - empty snapshot nutrients
 *
 * - [Result.Error]
 *   Unexpected system failure (DB constraint, mapping failure, etc.).
 *   Safe to retry.
 *
 * Blocking conditions (intentional safeguards)
 * - cookedYieldGrams <= 0
 * - recipe does not exist
 * - recipe has no ingredients
 * - nutrition compute produced warnings
 * - snapshot nutrient set empty
 * - required macro nutrients missing
 *
 * Nutrient snapshot rules
 * - Nutrients are stored strictly as PER_100G ([BasisType.PER_100G]).
 * - Missing nutrient mappings are dropped with diagnostic logging.
 * - Snapshot creation is allowed only if minimum viable macro nutrients exist:
 *   CALORIES_KCAL, PROTEIN_G, CARBS_G, FAT_G
 *
 * Serving-based logging support
 * - If cooked grams per serving can be computed:
 *   servingUnit = SERVING
 *   gramsPerServingUnit = computed value
 *
 * - Otherwise fallback to:
 *   servingUnit = G
 *   servingSize = 100
 *
 * Corner cases and behavior
 * - Multiple batch snapshots may exist for the same recipe.
 * - Snapshot nutrition reflects recipe state at time of cooking.
 * - Missing micronutrients do not block creation if macros exist.
 *
 * Pitfalls / gotchas
 * - RecipeEntity does NOT embed ingredients; they must be loaded explicitly.
 * - Do not attempt to reuse snapshot foods; each batch must be unique.
 * - Returning sentinel IDs (0L) causes downstream constraint failures — always use Result types.
 * - Snapshot foods must never be marked isRecipe=true.
 *
 * Architectural rules
 * - Snapshot foods are immutable representations of cooked batches.
 * - Snapshot logs must not rejoin mutable recipe or food data.
 * - Logging model uses ISO timestamps; this use case provides creation timestamps but does not log consumption.
 * - All persistence must remain transaction-atomic.
 *
 * FUTURE-YOU NOTE (2026-02-20)
 * - Never return sentinel IDs like 0L on failure paths.
 * - Always return Blocked/Error instead.
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
         * Snapshot successfully created.
         *
         * batchId:
         *   Primary key of recipe_batches row.
         *
         * batchFoodId:
         *   Food id of newly created snapshot food.
         *   This is the ID that must be used for logging.
         */
        data class Success(
            val batchId: Long,
            val batchFoodId: Long
        ) : Result

        /**
         * Expected, user-correctable condition.
         *
         * Examples:
         * - missing yield
         * - incomplete recipe
         * - nutrition warnings
         *
         * Caller should display messages and stop.
         */
        data class Blocked(val messages: List<String>) : Result

        /**
         * Unexpected failure.
         *
         * Examples:
         * - DB constraint violation
         * - nutrient mapping inconsistency
         *
         * Caller may allow retry.
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

                val hasServings =
                    gramsPerServingCooked != null &&
                            gramsPerServingCooked > 0.0

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
                require(batchFoodId > 0L)

                val existingRefs = recipeBatchDao.countByBatchFoodId(batchFoodId)
                Log.d(
                    "Meow",
                    "CreateSnapshotFoodFromRecipeUseCase> inserted batch food batchFoodId=$batchFoodId existingBatchRefs=$existingRefs"
                )

                if (existingRefs > 0) {
                    error("Invariant violated: batchFoodId already referenced.")
                }

                var droppedMissingId = 0
                val missingIdKeys = mutableListOf<String>()
                val persistedCodes = LinkedHashSet<String>()

                val nutrientRows: List<FoodNutrientEntity> =
                    per100gSnapshot.entries().mapNotNull { (nutrientKey, amtPer100g) ->

                        val nutrientCode = nutrientKey.value

                        val nutrient = nutrientDao.getByCode(nutrientCode)
                            ?: run {
                                droppedMissingId++
                                if (missingIdKeys.size < 25)
                                    missingIdKeys += nutrientCode
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

                if (nutrientRows.isEmpty()) {
                    error("Unable to snapshot nutrients.")
                }

                val macroCodesRequired = setOf(
                    "CALORIES_KCAL",
                    "PROTEIN_G",
                    "CARBS_G",
                    "FAT_G"
                )

                val presentMacroCodes =
                    persistedCodes.intersect(macroCodesRequired)

                if (presentMacroCodes.size < macroCodesRequired.size) {
                    error("Unable to snapshot nutrients (macros incomplete).")
                }

                if (droppedMissingId > 0) {
                    Log.w(
                        "Meow",
                        "Snapshot nutrient drops: missingId=$droppedMissingId keys=${missingIdKeys.distinct()}"
                    )
                }

                foodNutrientDao.deleteForFood(batchFoodId)
                foodNutrientDao.upsertAll(nutrientRows)

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

                require(batchId > 0L)

                Result.Success(
                    batchId = batchId,
                    batchFoodId = batchFoodId
                )
            }
        }.getOrElse { t ->
            Result.Error(
                t.message ?: "Failed to create batch snapshot."
            )
        }
    }
}

/**
 * ===== Bottom KDoc (for future AI assistant) =====
 *
 * Invariants (must never change)
 * - Each successful execution MUST create a new FoodEntity row (never reuse).
 * - Snapshot nutrients MUST be stored using BasisType.PER_100G.
 * - RecipeBatchEntity MUST link recipeId → batchFoodId.
 * - Entire operation MUST run inside a DB transaction.
 * - Must never return sentinel IDs.
 * - Snapshot food MUST have isRecipe=false.
 *
 * Logging and immutability rules
 * - Snapshot foods represent historical truth.
 * - They must never be recomputed or modified after creation.
 * - Log entries must reference snapshot foods, not recipes.
 *
 * Architectural boundaries
 * - This use case bridges domain and persistence intentionally.
 * - Direct DAO usage is allowed because snapshot creation is a persistence orchestration concern.
 *
 * Migration notes (KMP / time)
 * - Uses java.time.Instant and ZonedDateTime.
 * - If migrating to KMP, replace with platform-agnostic time abstraction.
 *
 * Performance considerations
 * - Snapshot creation is infrequent and user-triggered.
 * - Nutrient lookup uses per-nutrient DAO calls; acceptable given low frequency.
 *
 * Maintenance recommendations
 * - Consider batch nutrient lookup API in NutrientDao to reduce per-row queries.
 * - Consider replacing Log with injected Logger abstraction.
 * - Consider moving timestamp formatting to shared formatter utility.
 */