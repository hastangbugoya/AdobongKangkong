package com.example.adobongkangkong.domain.logging

import com.example.adobongkangkong.data.local.db.dao.RecipeDao
import com.example.adobongkangkong.data.local.db.entity.MealSlot
import com.example.adobongkangkong.data.local.db.entity.RecipeEntity
import com.example.adobongkangkong.domain.logging.UpdateLogEntryUseCase.NutritionDecision
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
import com.example.adobongkangkong.domain.repository.LogRepository
import com.example.adobongkangkong.domain.repository.RecipeBatchLookupRepository
import com.example.adobongkangkong.domain.repository.RecipeDraftLookupRepository
import com.example.adobongkangkong.domain.repository.RecipeIngredientLine
import com.example.adobongkangkong.domain.repository.RecipeInstructionStep
import com.example.adobongkangkong.domain.repository.RecipeRepository
import com.example.adobongkangkong.domain.usage.CheckFoodUsableUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class UpdateLogEntryUseCaseTest {

    @Test
    fun noPopup_whenAmountChanges_butBasisSame() = runBlocking {
        val food = testFood()
        val existing = testLog(food, grams = 100.0, proteinPer100g = 10.0)
        val snapshot = snapshot(food.id, proteinPer100g = 10.0)
        val env = buildUseCase(existing, food, snapshot)

        val result = env.useCase.execute(
            logId = existing.id,
            amountInput = AmountInput.ByGrams(200.0),
            mealSlot = MealSlot.BREAKFAST
        )

        assertTrue(result is UpdateLogEntryUseCase.Result.Success)
    }

    @Test
    fun popup_whenSentinelChanged() = runBlocking {
        val food = testFood()
        val existing = testLog(food, grams = 100.0, proteinPer100g = 10.0)
        val snapshot = snapshot(food.id, proteinPer100g = 2.0)
        val env = buildUseCase(existing, food, snapshot)

        val result = env.useCase.execute(
            logId = existing.id,
            amountInput = AmountInput.ByGrams(100.0),
            mealSlot = MealSlot.LUNCH
        )

        assertTrue(result is UpdateLogEntryUseCase.Result.NutritionChoiceRequired)
    }

    @Test
    fun noPopup_whenOnlyNonSentinelChanged() = runBlocking {
        val food = testFood()
        val existing = testLog(food, grams = 100.0, proteinPer100g = 10.0)

        val snapshot = FoodNutritionSnapshot(
            foodId = food.id,
            gramsPerServingUnit = null,
            mlPerServingUnit = null,
            nutrientsPerGram = NutrientMap(
                mapOf(
                    NutrientKey.PROTEIN_G to 0.10,
                    NutrientKey.FIBER_G to 0.50
                )
            ),
            nutrientsPerMilliliter = null
        )

        val env = buildUseCase(existing, food, snapshot)

        val result = env.useCase.execute(
            logId = existing.id,
            amountInput = AmountInput.ByGrams(100.0),
            mealSlot = MealSlot.BREAKFAST
        )

        assertTrue(result is UpdateLogEntryUseCase.Result.Success)
    }

    @Test
    fun keepOriginal_scalesStoredNutrients() = runBlocking {
        val food = testFood()
        val existing = testLog(food, grams = 100.0, proteinPer100g = 10.0)
        val snapshot = snapshot(food.id, proteinPer100g = 8.0)
        val env = buildUseCase(existing, food, snapshot)

        val result = env.useCase.execute(
            logId = existing.id,
            amountInput = AmountInput.ByGrams(200.0),
            mealSlot = MealSlot.BREAKFAST,
            nutritionDecision = NutritionDecision.KEEP_ORIGINAL
        )

        val updated = env.logRepo.updated!!

        assertEquals(20.0, updated.nutrients[NutrientKey.PROTEIN_G], 0.0001)
        assertTrue(result is UpdateLogEntryUseCase.Result.Success)
    }

    @Test
    fun useCurrent_usesRecomputedNutrients() = runBlocking {
        val food = testFood()
        val existing = testLog(food, grams = 100.0, proteinPer100g = 10.0)
        val snapshot = snapshot(food.id, proteinPer100g = 8.0)
        val env = buildUseCase(existing, food, snapshot)

        val result = env.useCase.execute(
            logId = existing.id,
            amountInput = AmountInput.ByGrams(200.0),
            mealSlot = MealSlot.BREAKFAST,
            nutritionDecision = NutritionDecision.USE_CURRENT
        )

        val updated = env.logRepo.updated!!

        assertEquals(16.0, updated.nutrients[NutrientKey.PROTEIN_G], 0.0001)
        assertTrue(result is UpdateLogEntryUseCase.Result.Success)
    }

    private fun testFood(): Food {
        return Food(
            id = 1L,
            stableId = "food1",
            name = "Test Food",
            brand = null,
            servingSize = 1.0,
            servingUnit = ServingUnit.SERVING,
            gramsPerServingUnit = 100.0,
            mlPerServingUnit = null,
            servingsPerPackage = null,
            isRecipe = false
        )
    }

    private fun testLog(
        food: Food,
        grams: Double,
        proteinPer100g: Double
    ): LogEntry {
        val totalProtein = proteinPer100g * (grams / 100.0)
        val now = Instant.now()

        return LogEntry(
            id = 1L,
            stableId = "log-1",
            createdAt = now,
            modifiedAt = now,
            timestamp = now,
            logDateIso = "2026-01-01",
            itemName = food.name,
            foodStableId = food.stableId,
            nutrients = NutrientMap(
                mapOf(NutrientKey.PROTEIN_G to totalProtein)
            ),
            amount = grams,
            unit = LogUnit.GRAM_COOKED,
            recipeBatchId = null,
            gramsPerServingCooked = null,
            mealSlot = MealSlot.BREAKFAST
        )
    }

    private fun snapshot(
        foodId: Long,
        proteinPer100g: Double
    ): FoodNutritionSnapshot {
        return FoodNutritionSnapshot(
            foodId = foodId,
            gramsPerServingUnit = null,
            mlPerServingUnit = null,
            nutrientsPerGram = NutrientMap(
                mapOf(NutrientKey.PROTEIN_G to proteinPer100g / 100.0)
            ),
            nutrientsPerMilliliter = null
        )
    }

    private data class TestEnv(
        val useCase: UpdateLogEntryUseCase,
        val logRepo: TestLogRepo
    )

    private fun buildUseCase(
        existing: LogEntry,
        food: Food,
        snapshot: FoodNutritionSnapshot
    ): TestEnv {
        val logRepo = TestLogRepo(existing)

        val useCase = UpdateLogEntryUseCase(
            foodRepository = TestFoodRepo(food),
            snapshotRepository = TestSnapshotRepo(snapshot),
            logRepository = logRepo,
            checkFoodUsable = CheckFoodUsableUseCase(),
            recipeDraftLookup = EmptyRecipeDraftRepo(),
            recipeBatchLookup = EmptyRecipeBatchRepo(),
            computeRecipeBatchNutritionUseCase = dummyBatchNutrition(),
            computeLoggedRecipeNutrition = ComputeLoggedRecipeNutritionUseCase(),
            recipeDao = EmptyRecipeDao()
        )

        return TestEnv(
            useCase = useCase,
            logRepo = logRepo
        )
    }

    private class TestLogRepo(
        private val existing: LogEntry
    ) : LogRepository {
        var updated: LogEntry? = null

        override suspend fun insert(entry: LogEntry): Long = entry.id

        override suspend fun update(entry: LogEntry) {
            updated = entry
        }

        override suspend fun getById(logId: Long): LogEntry? = existing

        override fun observeDay(logDateIso: String): Flow<List<LogEntry>> {
            throw UnsupportedOperationException("Unused in UpdateLogEntryUseCaseTest.")
        }

        override fun observeRangeByDateIso(
            startDateIsoInclusive: String,
            endDateIsoInclusive: String
        ): Flow<List<LogEntry>> {
            throw UnsupportedOperationException("Unused in UpdateLogEntryUseCaseTest.")
        }

        override fun observeTodayItems(logDateIso: String): Flow<List<TodayLogItem>> {
            throw UnsupportedOperationException("Unused in UpdateLogEntryUseCaseTest.")
        }

        override suspend fun deleteById(logId: Long) = Unit
    }

    private class TestFoodRepo(
        private val food: Food
    ) : FoodRepository {
        override fun search(query: String, limit: Int): Flow<List<Food>> {
            throw UnsupportedOperationException("Unused in UpdateLogEntryUseCaseTest.")
        }

        override suspend fun getById(id: Long): Food? = if (id == food.id) food else null

        override suspend fun upsert(food: Food): Long {
            throw UnsupportedOperationException("Unused in UpdateLogEntryUseCaseTest.")
        }

        override suspend fun getFoodRefForLogging(foodId: Long): FoodRef.Food? {
            throw UnsupportedOperationException("Unused in UpdateLogEntryUseCaseTest.")
        }

        override suspend fun isFoodsEmpty(): Boolean {
            throw UnsupportedOperationException("Unused in UpdateLogEntryUseCaseTest.")
        }

        override suspend fun deleteFood(foodId: Long): Boolean {
            throw UnsupportedOperationException("Unused in UpdateLogEntryUseCaseTest.")
        }

        override suspend fun softDeleteFood(foodId: Long) {
            throw UnsupportedOperationException("Unused in UpdateLogEntryUseCaseTest.")
        }

        override suspend fun getFoodHardDeleteBlockers(foodId: Long): FoodHardDeleteBlockers {
            throw UnsupportedOperationException("Unused in UpdateLogEntryUseCaseTest.")
        }

        override suspend fun hardDeleteFood(foodId: Long) {
            throw UnsupportedOperationException("Unused in UpdateLogEntryUseCaseTest.")
        }

        override suspend fun cleanupOrphanFoodMedia(): Int {
            throw UnsupportedOperationException("Unused in UpdateLogEntryUseCaseTest.")
        }

        override suspend fun getByStableId(stableId: String): Food? =
            if (stableId == food.stableId) food else null

        override suspend fun upsertFoodStorePrice(
            foodId: Long,
            storeId: Long,
            pricePer100g: Double?,
            pricePer100ml: Double?,
            updatedAtEpochMs: Long
        ): Long {
            throw UnsupportedOperationException("Unused in UpdateLogEntryUseCaseTest.")
        }

        override suspend fun deleteFoodStorePrice(foodId: Long, storeId: Long) {
            throw UnsupportedOperationException("Unused in UpdateLogEntryUseCaseTest.")
        }

        override suspend fun getAveragePricePer100gForFood(foodId: Long): Double? {
            throw UnsupportedOperationException("Unused in UpdateLogEntryUseCaseTest.")
        }

        override fun observeAveragePricePer100gForFood(foodId: Long): Flow<Double?> {
            throw UnsupportedOperationException("Unused in UpdateLogEntryUseCaseTest.")
        }

        override suspend fun getAveragePricePer100mlForFood(foodId: Long): Double? {
            throw UnsupportedOperationException("Unused in UpdateLogEntryUseCaseTest.")
        }

        override fun observeAveragePricePer100mlForFood(foodId: Long): Flow<Double?> {
            throw UnsupportedOperationException("Unused in UpdateLogEntryUseCaseTest.")
        }

        override suspend fun getAveragePricePer100gForFoodAtStore(
            foodId: Long,
            storeId: Long
        ): Double? {
            throw UnsupportedOperationException("Unused in UpdateLogEntryUseCaseTest.")
        }

        override fun observeAveragePricePer100gForFoodAtStore(
            foodId: Long,
            storeId: Long
        ): Flow<Double?> {
            throw UnsupportedOperationException("Unused in UpdateLogEntryUseCaseTest.")
        }

        override suspend fun getAveragePricePer100mlForFoodAtStore(
            foodId: Long,
            storeId: Long
        ): Double? {
            throw UnsupportedOperationException("Unused in UpdateLogEntryUseCaseTest.")
        }

        override fun observeAveragePricePer100mlForFoodAtStore(
            foodId: Long,
            storeId: Long
        ): Flow<Double?> {
            throw UnsupportedOperationException("Unused in UpdateLogEntryUseCaseTest.")
        }
    }

    private class TestSnapshotRepo(
        private val snapshot: FoodNutritionSnapshot
    ) : FoodNutritionSnapshotRepository {
        override suspend fun getSnapshot(foodId: Long): FoodNutritionSnapshot? =
            if (foodId == snapshot.foodId) snapshot else null

        override suspend fun getSnapshots(foodIds: Set<Long>): Map<Long, FoodNutritionSnapshot> =
            if (snapshot.foodId in foodIds) {
                mapOf(snapshot.foodId to snapshot)
            } else {
                emptyMap()
            }
    }

    private class EmptyRecipeDraftRepo : RecipeDraftLookupRepository {
        override suspend fun getRecipeDraft(recipeId: Long): RecipeDraft? = null
    }

    private class EmptyRecipeBatchRepo : RecipeBatchLookupRepository {
        override suspend fun getBatchById(batchId: Long): BatchSummary? = null

        override suspend fun getBatchesForRecipe(recipeId: Long): List<BatchSummary> = emptyList()

        override suspend fun getBatchFoodIds(batchIds: Set<Long>): Map<Long, Long> = emptyMap()
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
                    ingredients: List<RecipeIngredientLine>
                ) {
                    throw UnsupportedOperationException()
                }

                override suspend fun getRecipeByFoodId(foodId: Long) = null

                override suspend fun getIngredients(recipeId: Long): List<RecipeIngredientLine> =
                    emptyList()

                override suspend fun getHeaderByRecipeId(recipeId: Long) = null

                override suspend fun getFoodIdsByRecipeIds(recipeIds: Set<Long>): Map<Long, Long> =
                    emptyMap()

                override suspend fun getRecipeIdsByFoodIds(foodIds: Set<Long>): Map<Long, Long> =
                    emptyMap()

                override suspend fun getInstructionSteps(recipeId: Long): List<RecipeInstructionStep> =
                    emptyList()

                override suspend fun insertInstructionStep(
                    recipeId: Long,
                    position: Int,
                    text: String
                ): Long {
                    throw UnsupportedOperationException()
                }

                override suspend fun updateInstructionStepText(
                    stepId: Long,
                    text: String
                ) {
                    throw UnsupportedOperationException()
                }

                override suspend fun updateInstructionStepPosition(
                    stepId: Long,
                    position: Int
                ) {
                    throw UnsupportedOperationException()
                }

                override suspend fun setInstructionStepImage(
                    stepId: Long,
                    imagePath: String?
                ) {
                    throw UnsupportedOperationException()
                }

                override suspend fun deleteInstructionStep(stepId: Long) {
                    throw UnsupportedOperationException()
                }

                override suspend fun deleteInstructionStepsForRecipe(recipeId: Long) {
                    throw UnsupportedOperationException()
                }

                override suspend fun reorderInstructionSteps(
                    recipeId: Long,
                    orderedStepIds: List<Long>
                ) {
                    throw UnsupportedOperationException()
                }

                override suspend fun moveInstructionStepUp(
                    recipeId: Long,
                    stepId: Long
                ) {
                    throw UnsupportedOperationException()
                }

                override suspend fun moveInstructionStepDown(
                    recipeId: Long,
                    stepId: Long
                ) {
                    throw UnsupportedOperationException()
                }

                override suspend fun softDeleteRecipeByFoodId(foodId: Long) {
                    throw UnsupportedOperationException()
                }
            },
            snapshotRepo = TestSnapshotRepo(
                FoodNutritionSnapshot(
                    foodId = 0L,
                    gramsPerServingUnit = null,
                    mlPerServingUnit = null,
                    nutrientsPerGram = null,
                    nutrientsPerMilliliter = null
                )
            )
        )
    }

    private class EmptyRecipeDao : RecipeDao {
        override suspend fun insert(recipe: RecipeEntity): Long = 0L

        override fun observeAll() =
            throw UnsupportedOperationException("Unused in UpdateLogEntryUseCaseTest.")

        override suspend fun getById(recipeId: Long): RecipeEntity? = null

        override suspend fun getByIds(recipeIds: List<Long>): List<RecipeEntity> = emptyList()

        override suspend fun getByFoodId(foodId: Long): RecipeEntity? = null

        override suspend fun getByFoodIds(foodIds: List<Long>): List<RecipeEntity> = emptyList()

        override suspend fun getAll(): List<RecipeEntity> = emptyList()

        override suspend fun getIdByStableId(stableId: String): Long? = null

        override suspend fun updateCore(
            id: Long,
            foodId: Long,
            name: String,
            servingsYield: Double
        ) = Unit

        override suspend fun softDeleteById(
            recipeId: Long,
            deletedAtEpochMs: Long
        ) = Unit

        override suspend fun softDeleteByFoodId(
            foodId: Long,
            deletedAtEpochMs: Long
        ) = Unit

        override suspend fun countRecipes(): Int = 0
    }
}