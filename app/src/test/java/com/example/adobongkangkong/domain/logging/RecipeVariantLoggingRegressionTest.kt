package com.example.adobongkangkong.domain.logging

import com.example.adobongkangkong.data.local.db.dao.FoodDao
import com.example.adobongkangkong.data.local.db.dao.FoodIdNameRow
import com.example.adobongkangkong.data.local.db.dao.RecipeDao
import com.example.adobongkangkong.data.local.db.dao.RecipeIngredientDao
import com.example.adobongkangkong.data.local.db.entity.MealSlot
import com.example.adobongkangkong.data.local.db.entity.RecipeEntity
import com.example.adobongkangkong.data.local.db.entity.RecipeIngredientEntity
import com.example.adobongkangkong.data.local.db.entity.RecipeVariantEntity
import com.example.adobongkangkong.data.local.db.entity.RecipeVariantIngredientChangeEntity
import com.example.adobongkangkong.domain.logging.model.AmountInput
import com.example.adobongkangkong.domain.logging.model.BatchSummary
import com.example.adobongkangkong.domain.logging.model.FoodRef
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.model.FoodHardDeleteBlockers
import com.example.adobongkangkong.domain.model.LogEntry
import com.example.adobongkangkong.domain.model.LogUnit
import com.example.adobongkangkong.domain.model.RecipeDraft
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.model.TodayLogItem
import com.example.adobongkangkong.domain.nutrition.ComputeRecipeBatchNutritionUseCase
import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.nutrition.NutrientMap
import com.example.adobongkangkong.domain.recipes.ComputeLoggedRecipeNutritionUseCase
import com.example.adobongkangkong.domain.recipes.FoodNutritionSnapshot
import com.example.adobongkangkong.domain.repository.FoodNutritionSnapshotRepository
import com.example.adobongkangkong.domain.repository.FoodRepository
import com.example.adobongkangkong.domain.repository.FoodStorePricePreview
import com.example.adobongkangkong.domain.repository.LogRepository
import com.example.adobongkangkong.domain.repository.RecipeBatchLookupRepository
import com.example.adobongkangkong.domain.repository.RecipeDraftLookupRepository
import com.example.adobongkangkong.domain.repository.RecipeIngredientLine
import com.example.adobongkangkong.domain.repository.RecipeInstructionStep
import com.example.adobongkangkong.domain.repository.RecipeRepository
import com.example.adobongkangkong.domain.repository.RecipeVariantRepository
import com.example.adobongkangkong.domain.usecase.recipevariant.AssembleRecipeVariantUseCase
import com.example.adobongkangkong.domain.usecase.recipevariant.ComputeRecipeVariantNutritionUseCase
import com.example.adobongkangkong.domain.usage.CheckFoodUsableUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RecipeVariantLoggingRegressionTest {

    @Test
    fun createVariantLog_savesVariantSnapshotAndProvenance() = runBlocking {
        val logRepo = CapturingLogRepository()

        val useCase = CreateLogEntryUseCase(
            foodRepository = SingleFoodRepository(recipeFood()),
            snapshotRepository = EmptySnapshotRepository(),
            logRepository = logRepo,
            checkFoodUsable = CheckFoodUsableUseCase(),
            recipeDraftLookup = EmptyRecipeDraftRepository(),
            recipeBatchLookup = EmptyRecipeBatchRepository(),
            computeRecipeBatchNutritionUseCase = dummyBatchNutrition(),
            computeLoggedRecipeNutrition = ComputeLoggedRecipeNutritionUseCase(),
            computeRecipeVariantNutrition = variantNutritionUseCase(),
        )

        val result = useCase.execute(
            ref = FoodRef.RecipeVariant(
                recipeId = RECIPE_ID,
                variantId = VARIANT_ID,
                stableId = RECIPE_STABLE_ID,
                displayName = VARIANT_DISPLAY_NAME,
                servingsYieldDefault = 6.0,
            ),
            timestamp = Instant.parse("2026-06-15T12:00:00Z"),
            amountInput = AmountInput.ByServings(1.0),
            mealSlot = MealSlot.BREAKFAST,
            logDateIso = "2026-06-15",
        )

        assertTrue(result is CreateLogEntryUseCase.Result.Success)

        val inserted = logRepo.inserted
        assertNotNull(inserted)
        inserted!!

        assertEquals(RECIPE_STABLE_ID, inserted.foodStableId)
        assertEquals(VARIANT_DISPLAY_NAME, inserted.itemName)
        assertEquals(VARIANT_ID, inserted.recipeVariantId)
        assertEquals(null, inserted.recipeBatchId)
        assertEquals(1.0, inserted.amount, 0.0001)
        assertEquals(LogUnit.SERVING, inserted.unit)
        assertEquals(MealSlot.BREAKFAST, inserted.mealSlot)

        assertEquals(877.0, inserted.nutrients[NutrientKey.CALORIES_KCAL], 0.0001)
        assertEquals(68.15, inserted.nutrients[NutrientKey.PROTEIN_G], 0.0001)
    }

    @Test
    fun updateVariantLog_twoServingsUsesVariantMathAndKeepsVariantIdentity() = runBlocking {
        val existing = LogEntry(
            id = 99L,
            stableId = "log-variant-99",
            createdAt = Instant.parse("2026-06-15T12:00:00Z"),
            modifiedAt = Instant.parse("2026-06-15T12:00:00Z"),
            timestamp = Instant.parse("2026-06-15T12:00:00Z"),
            logDateIso = "2026-06-15",
            itemName = VARIANT_DISPLAY_NAME,
            foodStableId = RECIPE_STABLE_ID,
            nutrients = macros(
                calories = 877.0,
                protein = 68.15,
            ),
            amount = 1.0,
            unit = LogUnit.SERVING,
            recipeBatchId = null,
            recipeVariantId = VARIANT_ID,
            gramsPerServingCooked = null,
            mealSlot = MealSlot.BREAKFAST,
        )

        val logRepo = CapturingLogRepository(existing)

        val useCase = UpdateLogEntryUseCase(
            foodRepository = SingleFoodRepository(recipeFood()),
            snapshotRepository = EmptySnapshotRepository(),
            logRepository = logRepo,
            checkFoodUsable = CheckFoodUsableUseCase(),
            recipeDraftLookup = EmptyRecipeDraftRepository(),
            recipeBatchLookup = EmptyRecipeBatchRepository(),
            computeRecipeBatchNutritionUseCase = dummyBatchNutrition(),
            computeLoggedRecipeNutrition = ComputeLoggedRecipeNutritionUseCase(),
            recipeDao = EmptyRecipeDao(),
            computeRecipeVariantNutrition = variantNutritionUseCase(),
        )

        val result = useCase.execute(
            logId = existing.id,
            amountInput = AmountInput.ByServings(2.0),
            mealSlot = MealSlot.BREAKFAST,
        )

        assertTrue(result is UpdateLogEntryUseCase.Result.Success)

        val updated = logRepo.updated
        assertNotNull(updated)
        updated!!

        assertEquals(existing.id, updated.id)
        assertEquals(RECIPE_STABLE_ID, updated.foodStableId)
        assertEquals(VARIANT_DISPLAY_NAME, updated.itemName)
        assertEquals(VARIANT_ID, updated.recipeVariantId)
        assertEquals(null, updated.recipeBatchId)
        assertEquals(2.0, updated.amount, 0.0001)
        assertEquals(LogUnit.SERVING, updated.unit)
        assertEquals(MealSlot.BREAKFAST, updated.mealSlot)

        assertEquals(1754.0, updated.nutrients[NutrientKey.CALORIES_KCAL], 0.0001)
        assertEquals(136.3, updated.nutrients[NutrientKey.PROTEIN_G], 0.0001)
    }

    private companion object {
        const val RECIPE_ID = 200L
        const val RECIPE_FOOD_ID = 100L
        const val VARIANT_ID = 400L
        const val INGREDIENT_FOOD_ID = 10L
        const val RECIPE_STABLE_ID = "recipe-food-100"
        const val VARIANT_DISPLAY_NAME = "Chicken Gochugang • Test"
    }

    private fun recipeFood(): Food =
        Food(
            id = RECIPE_FOOD_ID,
            stableId = RECIPE_STABLE_ID,
            name = "Chicken Gochugang",
            brand = null,
            servingSize = 1.0,
            servingUnit = ServingUnit.SERVING,
            gramsPerServingUnit = null,
            mlPerServingUnit = null,
            servingsPerPackage = null,
            isRecipe = true,
        )

    private fun variantNutritionUseCase(): ComputeRecipeVariantNutritionUseCase {
        val recipeFoodEntity = com.example.adobongkangkong.data.local.db.entity.FoodEntity(
            id = RECIPE_FOOD_ID,
            stableId = RECIPE_STABLE_ID,
            name = "Chicken Gochugang",
            brand = null,
            servingSize = 1.0,
            servingUnit = ServingUnit.SERVING,
            gramsPerServingUnit = null,
            mlPerServingUnit = null,
            servingsPerPackage = null,
            isRecipe = true,
        )

        val ingredientFoodEntity = com.example.adobongkangkong.data.local.db.entity.FoodEntity(
            id = INGREDIENT_FOOD_ID,
            stableId = "ingredient-food-$INGREDIENT_FOOD_ID",
            name = "Variant Ingredient",
            brand = null,
            servingSize = 1.0,
            servingUnit = ServingUnit.SERVING,
            gramsPerServingUnit = null,
            mlPerServingUnit = null,
            servingsPerPackage = null,
            isRecipe = false,
        )

        val recipe = RecipeEntity(
            id = RECIPE_ID,
            stableId = "recipe-$RECIPE_ID",
            foodId = RECIPE_FOOD_ID,
            name = "Chicken Gochugang",
            servingsYield = 6.0,
            totalYieldGrams = null,
            isDeleted = false,
            deletedAtEpochMs = null,
            createdAt = Instant.EPOCH,
        )

        val ingredient = RecipeIngredientEntity(
            id = 300L,
            recipeId = RECIPE_ID,
            foodId = INGREDIENT_FOOD_ID,
            amountServings = null,
            amountGrams = 5262.0,
            sortOrder = 0,
        )

        val variant = RecipeVariantEntity(
            id = VARIANT_ID,
            recipeFoodId = RECIPE_FOOD_ID,
            name = "Test",
            notes = null,
            isArchived = false,
            servingsYieldOverride = 6.0,
            totalYieldGramsOverride = null,
            nutrientsJsonSnapshot = null,
            createdAtEpochMillis = 0L,
            updatedAtEpochMillis = 0L,
        )

        val assembleRecipeVariant = AssembleRecipeVariantUseCase(
            recipeVariantRepository = FakeRecipeVariantRepository(variant = variant),
            recipeDao = FakeRecipeDao(recipe),
            recipeIngredientDao = FakeRecipeIngredientDao(listOf(ingredient)),
            foodDao = FakeFoodDao(listOf(recipeFoodEntity, ingredientFoodEntity)),
        )

        return ComputeRecipeVariantNutritionUseCase(
            assembleRecipeVariant = assembleRecipeVariant,
            snapshotRepository = SnapshotRepository(
                listOf(
                    FoodNutritionSnapshot(
                        foodId = INGREDIENT_FOOD_ID,
                        gramsPerServingUnit = null,
                        mlPerServingUnit = null,
                        nutrientsPerGram = macros(
                            calories = 1.0,
                            protein = 408.9 / 5262.0,
                        ),
                        nutrientsPerMilliliter = null,
                    )
                )
            ),
        )
    }

    private fun macros(
        calories: Double,
        protein: Double,
    ): NutrientMap =
        NutrientMap(
            mapOf(
                NutrientKey.CALORIES_KCAL to calories,
                NutrientKey.PROTEIN_G to protein,
            )
        )

    private class CapturingLogRepository(
        private val existing: LogEntry? = null,
    ) : LogRepository {
        var inserted: LogEntry? = null
        var updated: LogEntry? = null

        override suspend fun insert(entry: LogEntry): Long {
            inserted = entry
            return 123L
        }

        override suspend fun update(entry: LogEntry) {
            updated = entry
        }

        override suspend fun getById(logId: Long): LogEntry? =
            existing?.takeIf { it.id == logId }

        override fun observeDay(logDateIso: String): Flow<List<LogEntry>> {
            throw UnsupportedOperationException("Unused in RecipeVariantLoggingRegressionTest.")
        }

        override fun observeRangeByDateIso(
            startDateIsoInclusive: String,
            endDateIsoInclusive: String,
        ): Flow<List<LogEntry>> {
            throw UnsupportedOperationException("Unused in RecipeVariantLoggingRegressionTest.")
        }

        override fun observeTodayItems(logDateIso: String): Flow<List<TodayLogItem>> {
            throw UnsupportedOperationException("Unused in RecipeVariantLoggingRegressionTest.")
        }

        override suspend fun deleteById(logId: Long) = Unit
    }

    private class SingleFoodRepository(
        private val food: Food,
    ) : FoodRepository {
        override fun search(query: String, limit: Int): Flow<List<Food>> {
            throw UnsupportedOperationException("Unused in RecipeVariantLoggingRegressionTest.")
        }

        override suspend fun getStorePricePreviewsForFood(foodId: Long): List<FoodStorePricePreview> =
            emptyList()

        override suspend fun getById(id: Long): Food? =
            food.takeIf { it.id == id }

        override suspend fun getByStableId(stableId: String): Food? =
            food.takeIf { it.stableId == stableId }

        override suspend fun upsert(food: Food): Long {
            throw UnsupportedOperationException("Unused in RecipeVariantLoggingRegressionTest.")
        }

        override suspend fun getFoodRefForLogging(foodId: Long): FoodRef.Food? {
            throw UnsupportedOperationException("Unused in RecipeVariantLoggingRegressionTest.")
        }

        override suspend fun isFoodsEmpty(): Boolean {
            throw UnsupportedOperationException("Unused in RecipeVariantLoggingRegressionTest.")
        }

        override suspend fun deleteFood(foodId: Long): Boolean {
            throw UnsupportedOperationException("Unused in RecipeVariantLoggingRegressionTest.")
        }

        override suspend fun softDeleteFood(foodId: Long) {
            throw UnsupportedOperationException("Unused in RecipeVariantLoggingRegressionTest.")
        }

        override suspend fun getFoodHardDeleteBlockers(foodId: Long): FoodHardDeleteBlockers {
            throw UnsupportedOperationException("Unused in RecipeVariantLoggingRegressionTest.")
        }

        override suspend fun hardDeleteFood(foodId: Long) {
            throw UnsupportedOperationException("Unused in RecipeVariantLoggingRegressionTest.")
        }

        override suspend fun cleanupOrphanFoodMedia(): Int {
            throw UnsupportedOperationException("Unused in RecipeVariantLoggingRegressionTest.")
        }

        override suspend fun upsertFoodStorePrice(
            foodId: Long,
            storeId: Long,
            pricePer100g: Double?,
            pricePer100ml: Double?,
            updatedAtEpochMs: Long,
        ): Long {
            throw UnsupportedOperationException("Unused in RecipeVariantLoggingRegressionTest.")
        }

        override suspend fun deleteFoodStorePrice(foodId: Long, storeId: Long) {
            throw UnsupportedOperationException("Unused in RecipeVariantLoggingRegressionTest.")
        }

        override suspend fun getAveragePricePer100gForFood(foodId: Long): Double? {
            throw UnsupportedOperationException("Unused in RecipeVariantLoggingRegressionTest.")
        }

        override fun observeAveragePricePer100gForFood(foodId: Long): Flow<Double?> {
            throw UnsupportedOperationException("Unused in RecipeVariantLoggingRegressionTest.")
        }

        override suspend fun getAveragePricePer100mlForFood(foodId: Long): Double? {
            throw UnsupportedOperationException("Unused in RecipeVariantLoggingRegressionTest.")
        }

        override fun observeAveragePricePer100mlForFood(foodId: Long): Flow<Double?> {
            throw UnsupportedOperationException("Unused in RecipeVariantLoggingRegressionTest.")
        }

        override suspend fun getAveragePricePer100gForFoodAtStore(
            foodId: Long,
            storeId: Long,
        ): Double? {
            throw UnsupportedOperationException("Unused in RecipeVariantLoggingRegressionTest.")
        }

        override fun observeAveragePricePer100gForFoodAtStore(
            foodId: Long,
            storeId: Long,
        ): Flow<Double?> {
            throw UnsupportedOperationException("Unused in RecipeVariantLoggingRegressionTest.")
        }

        override suspend fun getAveragePricePer100mlForFoodAtStore(
            foodId: Long,
            storeId: Long,
        ): Double? {
            throw UnsupportedOperationException("Unused in RecipeVariantLoggingRegressionTest.")
        }

        override fun observeAveragePricePer100mlForFoodAtStore(
            foodId: Long,
            storeId: Long,
        ): Flow<Double?> {
            throw UnsupportedOperationException("Unused in RecipeVariantLoggingRegressionTest.")
        }
    }

    private class EmptySnapshotRepository : FoodNutritionSnapshotRepository {
        override suspend fun getSnapshot(foodId: Long): FoodNutritionSnapshot? = null
        override suspend fun getSnapshots(foodIds: Set<Long>): Map<Long, FoodNutritionSnapshot> =
            emptyMap()
    }

    private class SnapshotRepository(
        snapshots: List<FoodNutritionSnapshot>,
    ) : FoodNutritionSnapshotRepository {
        private val byFoodId = snapshots.associateBy { it.foodId }

        override suspend fun getSnapshot(foodId: Long): FoodNutritionSnapshot? =
            byFoodId[foodId]

        override suspend fun getSnapshots(foodIds: Set<Long>): Map<Long, FoodNutritionSnapshot> =
            byFoodId.filterKeys { it in foodIds }
    }

    private class EmptyRecipeDraftRepository : RecipeDraftLookupRepository {
        override suspend fun getRecipeDraft(recipeId: Long): RecipeDraft? = null
    }

    private class EmptyRecipeBatchRepository : RecipeBatchLookupRepository {
        override suspend fun getBatchById(batchId: Long): BatchSummary? = null
        override suspend fun getBatchesForRecipe(recipeId: Long): List<BatchSummary> = emptyList()
        override suspend fun getBatchFoodIds(batchIds: Set<Long>): Map<Long, Long> = emptyMap()
    }

    private class EmptyRecipeDao : RecipeDao {
        override suspend fun insert(recipe: RecipeEntity): Long = recipe.id
        override fun observeAll(): Flow<List<RecipeEntity>> = flowOf(emptyList())
        override suspend fun getById(recipeId: Long): RecipeEntity? = null
        override suspend fun getByIds(recipeIds: List<Long>): List<RecipeEntity> = emptyList()
        override suspend fun getByFoodId(foodId: Long): RecipeEntity? = null
        override suspend fun getByFoodIds(foodIds: List<Long>): List<RecipeEntity> = emptyList()
        override suspend fun getAll(): List<RecipeEntity> = emptyList()
        override suspend fun getIdByStableId(stableId: String): Long? = null
        override suspend fun updateCore(id: Long, foodId: Long, name: String, servingsYield: Double) = Unit
        override suspend fun softDeleteById(recipeId: Long, deletedAtEpochMs: Long) = Unit
        override suspend fun softDeleteByFoodId(foodId: Long, deletedAtEpochMs: Long) = Unit
        override suspend fun countRecipes(): Int = 0
        override fun observeByFoodId(foodId: Long): Flow<RecipeEntity?> = flowOf(null)
    }

    private class FakeRecipeVariantRepository(
        private val variant: RecipeVariantEntity,
        private val changes: List<RecipeVariantIngredientChangeEntity> = emptyList(),
    ) : RecipeVariantRepository {

        override fun observeVariantsForRecipe(recipeFoodId: Long): Flow<List<RecipeVariantEntity>> =
            flowOf(if (recipeFoodId == variant.recipeFoodId) listOf(variant) else emptyList())

        override fun observeActiveVariantsForRecipe(recipeFoodId: Long): Flow<List<RecipeVariantEntity>> =
            flowOf(
                if (recipeFoodId == variant.recipeFoodId && !variant.isArchived) {
                    listOf(variant)
                } else {
                    emptyList()
                }
            )

        override suspend fun getVariantById(variantId: Long): RecipeVariantEntity? =
            variant.takeIf { it.id == variantId }

        override suspend fun createVariant(
            recipeFoodId: Long,
            name: String,
            notes: String?,
            nowEpochMillis: Long,
        ): Long {
            throw UnsupportedOperationException("Unused in RecipeVariantLoggingRegressionTest.")
        }

        override suspend fun updateVariant(variant: RecipeVariantEntity) {
            throw UnsupportedOperationException("Unused in RecipeVariantLoggingRegressionTest.")
        }

        override suspend fun archiveVariant(variantId: Long, nowEpochMillis: Long) {
            throw UnsupportedOperationException("Unused in RecipeVariantLoggingRegressionTest.")
        }

        override suspend fun restoreVariant(variantId: Long, nowEpochMillis: Long) {
            throw UnsupportedOperationException("Unused in RecipeVariantLoggingRegressionTest.")
        }

        override suspend fun deleteArchivedVariant(variantId: Long) {
            throw UnsupportedOperationException("Unused in RecipeVariantLoggingRegressionTest.")
        }

        override suspend fun getChangesForVariant(variantId: Long): List<RecipeVariantIngredientChangeEntity> =
            if (variantId == variant.id) changes else emptyList()

        override suspend fun replaceChangesForVariant(
            variantId: Long,
            changes: List<RecipeVariantIngredientChangeEntity>,
        ) {
            throw UnsupportedOperationException("Unused in RecipeVariantLoggingRegressionTest.")
        }

        override suspend fun updateVariantNutritionSnapshot(
            variantId: Long,
            nutrientsJsonSnapshot: String?,
            nowEpochMillis: Long,
        ) {
            throw UnsupportedOperationException("Unused in RecipeVariantLoggingRegressionTest.")
        }
    }

    private class FakeRecipeDao(
        private val recipe: RecipeEntity,
    ) : RecipeDao {
        override suspend fun insert(recipe: RecipeEntity): Long = recipe.id
        override fun observeAll(): Flow<List<RecipeEntity>> = flowOf(listOf(recipe))
        override suspend fun getAll(): List<RecipeEntity> = listOf(recipe)
        override suspend fun getById(recipeId: Long): RecipeEntity? = recipe.takeIf { it.id == recipeId }
        override suspend fun getByIds(recipeIds: List<Long>): List<RecipeEntity> = listOf(recipe).filter { it.id in recipeIds }
        override suspend fun getByFoodId(foodId: Long): RecipeEntity? = recipe.takeIf { it.foodId == foodId }
        override suspend fun getByFoodIds(foodIds: List<Long>): List<RecipeEntity> = listOf(recipe).filter { it.foodId in foodIds }
        override suspend fun getIdByStableId(stableId: String): Long? = recipe.id.takeIf { recipe.stableId == stableId }
        override suspend fun updateCore(id: Long, foodId: Long, name: String, servingsYield: Double) {
            throw UnsupportedOperationException("Unused in RecipeVariantLoggingRegressionTest.")
        }
        override suspend fun softDeleteById(recipeId: Long, deletedAtEpochMs: Long) {
            throw UnsupportedOperationException("Unused in RecipeVariantLoggingRegressionTest.")
        }
        override suspend fun softDeleteByFoodId(foodId: Long, deletedAtEpochMs: Long) {
            throw UnsupportedOperationException("Unused in RecipeVariantLoggingRegressionTest.")
        }
        override suspend fun countRecipes(): Int = 1
        override fun observeByFoodId(foodId: Long): Flow<RecipeEntity?> =
            flowOf(recipe.takeIf { it.foodId == foodId })
    }

    private class FakeRecipeIngredientDao(
        private val ingredients: List<RecipeIngredientEntity>,
    ) : RecipeIngredientDao {
        override suspend fun insertAll(items: List<RecipeIngredientEntity>) {
            throw UnsupportedOperationException("Unused in RecipeVariantLoggingRegressionTest.")
        }

        override suspend fun getForRecipe(recipeId: Long): List<RecipeIngredientEntity> =
            ingredients
                .filter { it.recipeId == recipeId }
                .sortedBy { it.sortOrder }

        override suspend fun deleteForRecipe(recipeId: Long) {
            throw UnsupportedOperationException("Unused in RecipeVariantLoggingRegressionTest.")
        }

        override suspend fun deleteByIds(ingredientIds: List<Long>) {
            throw UnsupportedOperationException("Unused in RecipeVariantLoggingRegressionTest.")
        }

        override suspend fun countRecipesUsingFood(foodId: Long): Int =
            ingredients.count { it.foodId == foodId }

        override suspend fun setAmountGrams(ingredientId: Long, grams: Double?) {
            throw UnsupportedOperationException("Unused in RecipeVariantLoggingRegressionTest.")
        }

        override suspend fun setAmountServings(ingredientId: Long, servings: Double?) {
            throw UnsupportedOperationException("Unused in RecipeVariantLoggingRegressionTest.")
        }
    }

    private class FakeFoodDao(
        foods: List<com.example.adobongkangkong.data.local.db.entity.FoodEntity>,
    ) : FoodDao {
        private val byId = foods.associateBy { it.id }
        private val byStableId = foods.associateBy { it.stableId }

        override suspend fun insert(entity: com.example.adobongkangkong.data.local.db.entity.FoodEntity): Long =
            entity.id

        override suspend fun getByStableId(stableId: String): com.example.adobongkangkong.data.local.db.entity.FoodEntity? =
            byStableId[stableId]

        override suspend fun upsertAll(items: List<com.example.adobongkangkong.data.local.db.entity.FoodEntity>) {
            throw UnsupportedOperationException("Unused in RecipeVariantLoggingRegressionTest.")
        }

        override suspend fun upsert(item: com.example.adobongkangkong.data.local.db.entity.FoodEntity) {
            throw UnsupportedOperationException("Unused in RecipeVariantLoggingRegressionTest.")
        }

        override suspend fun getById(id: Long): com.example.adobongkangkong.data.local.db.entity.FoodEntity? =
            byId[id]

        override suspend fun deleteById(id: Long) {
            throw UnsupportedOperationException("Unused in RecipeVariantLoggingRegressionTest.")
        }

        override suspend fun getByIds(ids: List<Long>): List<com.example.adobongkangkong.data.local.db.entity.FoodEntity> =
            ids.mapNotNull { byId[it] }

        override fun search(query: String, limit: Int): Flow<List<com.example.adobongkangkong.data.local.db.entity.FoodEntity>> =
            flowOf(
                byId.values
                    .filter { it.name.contains(query, ignoreCase = true) }
                    .take(limit)
            )

        override suspend fun countFoods(): Int = byId.size

        override suspend fun getAll(): List<com.example.adobongkangkong.data.local.db.entity.FoodEntity> =
            byId.values.toList()

        override suspend fun getIdByStableId(stableId: String): Long? =
            byStableId[stableId]?.id

        override suspend fun getByUsdaFdcId(fdcId: Long): com.example.adobongkangkong.data.local.db.entity.FoodEntity? =
            null

        override suspend fun updateCore(
            id: Long,
            name: String,
            brand: String?,
            servingSize: Double,
            servingUnit: String,
            gramsPerServingUnit: Double?,
            isRecipe: Boolean,
        ) {
            throw UnsupportedOperationException("Unused in RecipeVariantLoggingRegressionTest.")
        }

        override suspend fun updateGramsPerServingUnit(id: Long, gramsPerServingUnit: Double?) {
            throw UnsupportedOperationException("Unused in RecipeVariantLoggingRegressionTest.")
        }

        override suspend fun softDeleteById(id: Long, deletedAtEpochMs: Long) {
            throw UnsupportedOperationException("Unused in RecipeVariantLoggingRegressionTest.")
        }

        override suspend fun getStableIdById(id: Long): String? =
            byId[id]?.stableId

        override suspend fun getExistingFoodIds(ids: List<Long>): List<Long> =
            ids.filter { it in byId }

        override suspend fun getNamesByIds(ids: List<Long>): List<FoodIdNameRow> =
            ids.mapNotNull { id ->
                byId[id]?.let { food ->
                    FoodIdNameRow(id = food.id, name = food.name)
                }
            }
    }

    private fun dummyBatchNutrition(): ComputeRecipeBatchNutritionUseCase {
        return ComputeRecipeBatchNutritionUseCase(
            recipeRepo = object : RecipeRepository {
                override suspend fun createRecipe(draft: RecipeDraft): Long {
                    throw UnsupportedOperationException()
                }

                override suspend fun updateRecipeByFoodId(
                    foodId: Long,
                    servingsYield: Double,
                    totalYieldGrams: Double?,
                    ingredients: List<RecipeIngredientLine>,
                ) {
                    throw UnsupportedOperationException()
                }

                override suspend fun getRecipeByFoodId(foodId: Long) = null
                override suspend fun getIngredients(recipeId: Long): List<RecipeIngredientLine> = emptyList()
                override suspend fun getHeaderByRecipeId(recipeId: Long) = null
                override suspend fun getFoodIdsByRecipeIds(recipeIds: Set<Long>): Map<Long, Long> = emptyMap()
                override suspend fun getRecipeIdsByFoodIds(foodIds: Set<Long>): Map<Long, Long> = emptyMap()
                override suspend fun getInstructionSteps(recipeId: Long): List<RecipeInstructionStep> = emptyList()
                override suspend fun insertInstructionStep(recipeId: Long, position: Int, text: String): Long {
                    throw UnsupportedOperationException()
                }
                override suspend fun updateInstructionStepText(stepId: Long, text: String) {
                    throw UnsupportedOperationException()
                }
                override suspend fun updateInstructionStepPosition(stepId: Long, position: Int) {
                    throw UnsupportedOperationException()
                }
                override suspend fun setInstructionStepImage(stepId: Long, imagePath: String?) {
                    throw UnsupportedOperationException()
                }
                override suspend fun deleteInstructionStep(stepId: Long) {
                    throw UnsupportedOperationException()
                }
                override suspend fun deleteInstructionStepsForRecipe(recipeId: Long) {
                    throw UnsupportedOperationException()
                }
                override suspend fun reorderInstructionSteps(recipeId: Long, orderedStepIds: List<Long>) {
                    throw UnsupportedOperationException()
                }
                override suspend fun moveInstructionStepUp(recipeId: Long, stepId: Long) {
                    throw UnsupportedOperationException()
                }
                override suspend fun moveInstructionStepDown(recipeId: Long, stepId: Long) {
                    throw UnsupportedOperationException()
                }
                override suspend fun softDeleteRecipeByFoodId(foodId: Long) {
                    throw UnsupportedOperationException()
                }
            },
            snapshotRepo = EmptySnapshotRepository(),
        )
    }
}
