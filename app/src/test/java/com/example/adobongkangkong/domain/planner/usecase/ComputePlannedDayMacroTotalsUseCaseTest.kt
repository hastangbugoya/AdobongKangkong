package com.example.adobongkangkong.domain.planner.usecase

import com.example.adobongkangkong.data.local.db.entity.MealSlot
import com.example.adobongkangkong.domain.logging.model.BatchSummary
import com.example.adobongkangkong.domain.model.MacroTotals
import com.example.adobongkangkong.domain.nutrition.MacroKeys
import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.nutrition.NutrientMap
import com.example.adobongkangkong.domain.planner.model.PlannedItem
import com.example.adobongkangkong.domain.planner.model.PlannedItemSource
import com.example.adobongkangkong.domain.planner.model.PlannedMeal
import com.example.adobongkangkong.domain.recipes.FoodNutritionSnapshot
import com.example.adobongkangkong.domain.repository.FoodNutritionSnapshotRepository
import com.example.adobongkangkong.domain.repository.RecipeBatchLookupRepository
import com.example.adobongkangkong.domain.repository.RecipeHeader
import com.example.adobongkangkong.domain.repository.RecipeIngredientLine
import com.example.adobongkangkong.domain.repository.RecipeInstructionStep
import com.example.adobongkangkong.domain.repository.RecipeRepository
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ComputePlannedDayMacroTotalsUseCaseTest {

    private val kcal = MacroKeys.CALORIES
    private val protein = MacroKeys.PROTEIN
    private val carbs = MacroKeys.CARBS
    private val fat = MacroKeys.FAT

    private fun nutrientsOf(vararg pairs: Pair<NutrientKey, Double>): NutrientMap =
        NutrientMap(pairs.toMap())

    private fun gramsSnapshot(
        foodId: Long,
        gramsPerServingUnit: Double,
        perGram: NutrientMap
    ) = FoodNutritionSnapshot(
        foodId = foodId,
        gramsPerServingUnit = gramsPerServingUnit,
        mlPerServingUnit = null,
        nutrientsPerGram = perGram,
        nutrientsPerMilliliter = null
    )

    private fun mlSnapshot(
        foodId: Long,
        mlPerServingUnit: Double,
        perMl: NutrientMap
    ) = FoodNutritionSnapshot(
        foodId = foodId,
        gramsPerServingUnit = null,
        mlPerServingUnit = mlPerServingUnit,
        nutrientsPerGram = null,
        nutrientsPerMilliliter = perMl
    )

    private fun meal(
        id: Long,
        items: List<PlannedItem>
    ) = PlannedMeal(
        id = id,
        date = LocalDate.of(2026, 3, 12),
        slot = MealSlot.BREAKFAST,
        title = null,
        items = items,
        seriesId = null
    )

    private fun item(
        id: Long,
        sourceType: PlannedItemSource,
        sourceId: Long,
        qtyGrams: Double? = null,
        qtyServings: Double? = null
    ) = PlannedItem(
        id = id,
        sourceType = sourceType,
        sourceId = sourceId,
        qtyGrams = qtyGrams,
        qtyServings = qtyServings,
        title = null
    )

    @Test
    fun planner_invariant_100g_equals_2_servings_of_50g() = runBlocking {
        val useCase = ComputePlannedDayMacroTotalsUseCase(
            foodSnapshots = FakeFoodSnapshotRepository(
                mapOf(
                    1L to gramsSnapshot(
                        foodId = 1L,
                        gramsPerServingUnit = 50.0,
                        perGram = nutrientsOf(
                            kcal to 2.0,
                            protein to 0.1,
                            carbs to 0.2,
                            fat to 0.05
                        )
                    )
                )
            ),
            recipes = FakeRecipeRepository(),
            recipeBatches = FakeRecipeBatchLookupRepository()
        )

        val gramsResult = useCase(
            listOf(
                meal(
                    id = 1L,
                    items = listOf(
                        item(
                            id = 1L,
                            sourceType = PlannedItemSource.FOOD,
                            sourceId = 1L,
                            qtyGrams = 100.0
                        )
                    )
                )
            )
        )

        val servingsResult = useCase(
            listOf(
                meal(
                    id = 2L,
                    items = listOf(
                        item(
                            id = 2L,
                            sourceType = PlannedItemSource.FOOD,
                            sourceId = 1L,
                            qtyServings = 2.0
                        )
                    )
                )
            )
        )

        assertEquals(gramsResult.dayTotals.caloriesKcal, servingsResult.dayTotals.caloriesKcal, 1e-9)
        assertEquals(gramsResult.dayTotals.proteinG, servingsResult.dayTotals.proteinG, 1e-9)
        assertEquals(gramsResult.dayTotals.carbsG, servingsResult.dayTotals.carbsG, 1e-9)
        assertEquals(gramsResult.dayTotals.fatG, servingsResult.dayTotals.fatG, 1e-9)
    }

    @Test
    fun planner_invariant_combined_items_equal_sum_of_individual_items() = runBlocking {
        val useCase = ComputePlannedDayMacroTotalsUseCase(
            foodSnapshots = FakeFoodSnapshotRepository(
                mapOf(
                    1L to gramsSnapshot(
                        foodId = 1L,
                        gramsPerServingUnit = 50.0,
                        perGram = nutrientsOf(
                            kcal to 2.0,
                            protein to 0.1,
                            carbs to 0.2,
                            fat to 0.05
                        )
                    )
                )
            ),
            recipes = FakeRecipeRepository(),
            recipeBatches = FakeRecipeBatchLookupRepository()
        )

        val itemA = item(
            id = 1L,
            sourceType = PlannedItemSource.FOOD,
            sourceId = 1L,
            qtyGrams = 40.0
        )
        val itemB = item(
            id = 2L,
            sourceType = PlannedItemSource.FOOD,
            sourceId = 1L,
            qtyGrams = 60.0
        )

        val together = useCase(
            listOf(
                meal(
                    id = 1L,
                    items = listOf(itemA, itemB)
                )
            )
        )

        val separateA = useCase(
            listOf(
                meal(
                    id = 2L,
                    items = listOf(itemA)
                )
            )
        )

        val separateB = useCase(
            listOf(
                meal(
                    id = 3L,
                    items = listOf(itemB)
                )
            )
        )

        assertEquals(
            separateA.dayTotals.caloriesKcal + separateB.dayTotals.caloriesKcal,
            together.dayTotals.caloriesKcal,
            1e-9
        )
        assertEquals(
            separateA.dayTotals.proteinG + separateB.dayTotals.proteinG,
            together.dayTotals.proteinG,
            1e-9
        )
        assertEquals(
            separateA.dayTotals.carbsG + separateB.dayTotals.carbsG,
            together.dayTotals.carbsG,
            1e-9
        )
        assertEquals(
            separateA.dayTotals.fatG + separateB.dayTotals.fatG,
            together.dayTotals.fatG,
            1e-9
        )
    }

    @Test
    fun computes_meal_and_day_totals_for_food_item_by_grams() = runBlocking {
        val useCase = ComputePlannedDayMacroTotalsUseCase(
            foodSnapshots = FakeFoodSnapshotRepository(
                mapOf(
                    1L to gramsSnapshot(
                        foodId = 1L,
                        gramsPerServingUnit = 50.0,
                        perGram = nutrientsOf(
                            kcal to 2.0,
                            protein to 0.10,
                            carbs to 0.20,
                            fat to 0.05
                        )
                    )
                )
            ),
            recipes = FakeRecipeRepository(),
            recipeBatches = FakeRecipeBatchLookupRepository()
        )

        val meals = listOf(
            meal(
                id = 10L,
                items = listOf(
                    item(
                        id = 100L,
                        sourceType = PlannedItemSource.FOOD,
                        sourceId = 1L,
                        qtyGrams = 150.0
                    )
                )
            )
        )

        val result = useCase(meals)
        val mealTotal = assertNotNull(result.mealTotals[10L])

        assertEquals(300.0, mealTotal.caloriesKcal, 1e-9)
        assertEquals(15.0, mealTotal.proteinG, 1e-9)
        assertEquals(30.0, mealTotal.carbsG, 1e-9)
        assertEquals(7.5, mealTotal.fatG, 1e-9)

        assertEquals(300.0, result.dayTotals.caloriesKcal, 1e-9)
        assertEquals(15.0, result.dayTotals.proteinG, 1e-9)
        assertEquals(30.0, result.dayTotals.carbsG, 1e-9)
        assertEquals(7.5, result.dayTotals.fatG, 1e-9)
    }

    @Test
    fun computes_meal_and_day_totals_for_food_item_by_servings_using_grams_bridge() = runBlocking {
        val useCase = ComputePlannedDayMacroTotalsUseCase(
            foodSnapshots = FakeFoodSnapshotRepository(
                mapOf(
                    1L to gramsSnapshot(
                        foodId = 1L,
                        gramsPerServingUnit = 40.0,
                        perGram = nutrientsOf(
                            kcal to 2.0,
                            protein to 0.10,
                            carbs to 0.25,
                            fat to 0.05
                        )
                    )
                )
            ),
            recipes = FakeRecipeRepository(),
            recipeBatches = FakeRecipeBatchLookupRepository()
        )

        val meals = listOf(
            meal(
                id = 10L,
                items = listOf(
                    item(
                        id = 100L,
                        sourceType = PlannedItemSource.FOOD,
                        sourceId = 1L,
                        qtyServings = 2.5
                    )
                )
            )
        )

        val result = useCase(meals)
        val mealTotal = assertNotNull(result.mealTotals[10L])

        // 2.5 servings * 40g = 100g
        assertEquals(200.0, mealTotal.caloriesKcal, 1e-9)
        assertEquals(10.0, mealTotal.proteinG, 1e-9)
        assertEquals(25.0, mealTotal.carbsG, 1e-9)
        assertEquals(5.0, mealTotal.fatG, 1e-9)
    }

    @Test
    fun computes_meal_and_day_totals_for_food_item_by_servings_using_volume_fallback() = runBlocking {
        val useCase = ComputePlannedDayMacroTotalsUseCase(
            foodSnapshots = FakeFoodSnapshotRepository(
                mapOf(
                    1L to mlSnapshot(
                        foodId = 1L,
                        mlPerServingUnit = 30.0,
                        perMl = nutrientsOf(
                            kcal to 0.5,
                            protein to 0.01,
                            carbs to 0.02,
                            fat to 0.03
                        )
                    )
                )
            ),
            recipes = FakeRecipeRepository(),
            recipeBatches = FakeRecipeBatchLookupRepository()
        )

        val meals = listOf(
            meal(
                id = 10L,
                items = listOf(
                    item(
                        id = 100L,
                        sourceType = PlannedItemSource.FOOD,
                        sourceId = 1L,
                        qtyServings = 2.0
                    )
                )
            )
        )

        val result = useCase(meals)
        val mealTotal = assertNotNull(result.mealTotals[10L])

        // 2 servings * 30mL = 60mL
        assertEquals(30.0, mealTotal.caloriesKcal, 1e-9)
        assertEquals(0.6, mealTotal.proteinG, 1e-9)
        assertEquals(1.2, mealTotal.carbsG, 1e-9)
        assertEquals(1.8, mealTotal.fatG, 1e-9)
    }

    @Test
    fun prefers_grams_bridge_over_volume_fallback_when_both_exist() = runBlocking {
        val useCase = ComputePlannedDayMacroTotalsUseCase(
            foodSnapshots = FakeFoodSnapshotRepository(
                mapOf(
                    1L to FoodNutritionSnapshot(
                        foodId = 1L,
                        gramsPerServingUnit = 50.0,
                        mlPerServingUnit = 100.0,
                        nutrientsPerGram = nutrientsOf(
                            kcal to 2.0,
                            protein to 0.1,
                            carbs to 0.0,
                            fat to 0.0
                        ),
                        nutrientsPerMilliliter = nutrientsOf(
                            kcal to 9.0,
                            protein to 9.0,
                            carbs to 9.0,
                            fat to 9.0
                        )
                    )
                )
            ),
            recipes = FakeRecipeRepository(),
            recipeBatches = FakeRecipeBatchLookupRepository()
        )

        val meals = listOf(
            meal(
                id = 10L,
                items = listOf(
                    item(
                        id = 100L,
                        sourceType = PlannedItemSource.FOOD,
                        sourceId = 1L,
                        qtyServings = 2.0
                    )
                )
            )
        )

        val result = useCase(meals)
        val mealTotal = assertNotNull(result.mealTotals[10L])

        // Must use grams path:
        // 2 servings * 50g = 100g
        // kcal = 100 * 2 = 200
        assertEquals(200.0, mealTotal.caloriesKcal, 1e-9)
        assertEquals(10.0, mealTotal.proteinG, 1e-9)
        assertEquals(0.0, mealTotal.carbsG, 1e-9)
        assertEquals(0.0, mealTotal.fatG, 1e-9)
    }

    @Test
    fun resolves_recipe_and_batch_sources() = runBlocking {
        val useCase = ComputePlannedDayMacroTotalsUseCase(
            foodSnapshots = FakeFoodSnapshotRepository(
                mapOf(
                    101L to gramsSnapshot(
                        foodId = 101L,
                        gramsPerServingUnit = 50.0,
                        perGram = nutrientsOf(kcal to 2.0, protein to 0.10, carbs to 0.0, fat to 0.0)
                    ),
                    202L to gramsSnapshot(
                        foodId = 202L,
                        gramsPerServingUnit = 20.0,
                        perGram = nutrientsOf(kcal to 1.0, protein to 0.05, carbs to 0.10, fat to 0.02)
                    )
                )
            ),
            recipes = FakeRecipeRepository(
                foodIdsByRecipeIds = mapOf(11L to 101L)
            ),
            recipeBatches = FakeRecipeBatchLookupRepository(
                batchFoodIds = mapOf(22L to 202L)
            )
        )

        val meals = listOf(
            meal(
                id = 10L,
                items = listOf(
                    item(
                        id = 100L,
                        sourceType = PlannedItemSource.RECIPE,
                        sourceId = 11L,
                        qtyServings = 2.0
                    ),
                    item(
                        id = 101L,
                        sourceType = PlannedItemSource.RECIPE_BATCH,
                        sourceId = 22L,
                        qtyServings = 3.0
                    )
                )
            )
        )

        val result = useCase(meals)
        val mealTotal = assertNotNull(result.mealTotals[10L])

        // Recipe -> foodId 101:
        // 2 * 50g = 100g => 200 kcal, 10 protein
        //
        // Batch -> foodId 202:
        // 3 * 20g = 60g => 60 kcal, 3 protein, 6 carbs, 1.2 fat
        assertEquals(260.0, mealTotal.caloriesKcal, 1e-9)
        assertEquals(13.0, mealTotal.proteinG, 1e-9)
        assertEquals(6.0, mealTotal.carbsG, 1e-9)
        assertEquals(1.2, mealTotal.fatG, 1e-9)
    }

    @Test
    fun missing_snapshot_and_missing_mappings_contribute_zero() = runBlocking {
        val useCase = ComputePlannedDayMacroTotalsUseCase(
            foodSnapshots = FakeFoodSnapshotRepository(emptyMap()),
            recipes = FakeRecipeRepository(
                foodIdsByRecipeIds = emptyMap()
            ),
            recipeBatches = FakeRecipeBatchLookupRepository(
                batchFoodIds = emptyMap()
            )
        )

        val meals = listOf(
            meal(
                id = 10L,
                items = listOf(
                    item(
                        id = 100L,
                        sourceType = PlannedItemSource.FOOD,
                        sourceId = 1L,
                        qtyGrams = 100.0
                    ),
                    item(
                        id = 101L,
                        sourceType = PlannedItemSource.RECIPE,
                        sourceId = 11L,
                        qtyServings = 1.0
                    ),
                    item(
                        id = 102L,
                        sourceType = PlannedItemSource.RECIPE_BATCH,
                        sourceId = 22L,
                        qtyServings = 1.0
                    )
                )
            )
        )

        val result = useCase(meals)
        val mealTotal = assertNotNull(result.mealTotals[10L])

        assertEquals(0.0, mealTotal.caloriesKcal, 0.0)
        assertEquals(0.0, mealTotal.proteinG, 0.0)
        assertEquals(0.0, mealTotal.carbsG, 0.0)
        assertEquals(0.0, mealTotal.fatG, 0.0)

        assertEquals(0.0, result.dayTotals.caloriesKcal, 0.0)
        assertEquals(0.0, result.dayTotals.proteinG, 0.0)
        assertEquals(0.0, result.dayTotals.carbsG, 0.0)
        assertEquals(0.0, result.dayTotals.fatG, 0.0)
    }

    @Test
    fun non_positive_grams_and_servings_contribute_zero() = runBlocking {
        val useCase = ComputePlannedDayMacroTotalsUseCase(
            foodSnapshots = FakeFoodSnapshotRepository(
                mapOf(
                    1L to gramsSnapshot(
                        foodId = 1L,
                        gramsPerServingUnit = 50.0,
                        perGram = nutrientsOf(
                            kcal to 2.0,
                            protein to 0.10,
                            carbs to 0.20,
                            fat to 0.05
                        )
                    )
                )
            ),
            recipes = FakeRecipeRepository(),
            recipeBatches = FakeRecipeBatchLookupRepository()
        )

        val meals = listOf(
            meal(
                id = 10L,
                items = listOf(
                    item(id = 100L, sourceType = PlannedItemSource.FOOD, sourceId = 1L, qtyGrams = 0.0),
                    item(id = 101L, sourceType = PlannedItemSource.FOOD, sourceId = 1L, qtyGrams = -10.0),
                    item(id = 102L, sourceType = PlannedItemSource.FOOD, sourceId = 1L, qtyServings = 0.0),
                    item(id = 103L, sourceType = PlannedItemSource.FOOD, sourceId = 1L, qtyServings = -1.0)
                )
            )
        )

        val result = useCase(meals)
        val mealTotal = assertNotNull(result.mealTotals[10L])

        assertEquals(0.0, mealTotal.caloriesKcal, 0.0)
        assertEquals(0.0, mealTotal.proteinG, 0.0)
        assertEquals(0.0, mealTotal.carbsG, 0.0)
        assertEquals(0.0, mealTotal.fatG, 0.0)
    }

    @Test
    fun all_meals_get_zero_entry_even_when_they_have_no_usable_items() = runBlocking {
        val useCase = ComputePlannedDayMacroTotalsUseCase(
            foodSnapshots = FakeFoodSnapshotRepository(emptyMap()),
            recipes = FakeRecipeRepository(),
            recipeBatches = FakeRecipeBatchLookupRepository()
        )

        val meals = listOf(
            meal(id = 10L, items = emptyList()),
            meal(
                id = 11L,
                items = listOf(
                    item(
                        id = 100L,
                        sourceType = PlannedItemSource.FOOD,
                        sourceId = 1L,
                        qtyGrams = 100.0
                    )
                )
            )
        )

        val result = useCase(meals)

        assertTrue(result.mealTotals.containsKey(10L))
        assertTrue(result.mealTotals.containsKey(11L))

        assertEquals(0.0, result.mealTotals.getValue(10L).caloriesKcal, 0.0)
        assertEquals(0.0, result.mealTotals.getValue(11L).caloriesKcal, 0.0)
        assertEquals(0.0, result.dayTotals.caloriesKcal, 0.0)
    }

    @Test
    fun empty_meals_returns_empty_meal_totals_and_zero_day_totals() = runBlocking {
        val useCase = ComputePlannedDayMacroTotalsUseCase(
            foodSnapshots = FakeFoodSnapshotRepository(emptyMap()),
            recipes = FakeRecipeRepository(),
            recipeBatches = FakeRecipeBatchLookupRepository()
        )

        val result = useCase(emptyList())

        assertTrue(result.mealTotals.isEmpty())
        assertEquals(0.0, result.dayTotals.caloriesKcal, 0.0)
        assertEquals(0.0, result.dayTotals.proteinG, 0.0)
        assertEquals(0.0, result.dayTotals.carbsG, 0.0)
        assertEquals(0.0, result.dayTotals.fatG, 0.0)
    }

    @Test
    fun invariant_day_totals_equal_sum_of_meal_totals() = runBlocking {
        val useCase = ComputePlannedDayMacroTotalsUseCase(
            foodSnapshots = FakeFoodSnapshotRepository(
                mapOf(
                    1L to gramsSnapshot(
                        foodId = 1L,
                        gramsPerServingUnit = 50.0,
                        perGram = nutrientsOf(
                            kcal to 2.0,
                            protein to 0.10,
                            carbs to 0.20,
                            fat to 0.05
                        )
                    ),
                    2L to mlSnapshot(
                        foodId = 2L,
                        mlPerServingUnit = 30.0,
                        perMl = nutrientsOf(
                            kcal to 0.5,
                            protein to 0.01,
                            carbs to 0.02,
                            fat to 0.03
                        )
                    )
                )
            ),
            recipes = FakeRecipeRepository(),
            recipeBatches = FakeRecipeBatchLookupRepository()
        )

        val meals = listOf(
            meal(
                id = 10L,
                items = listOf(
                    item(
                        id = 100L,
                        sourceType = PlannedItemSource.FOOD,
                        sourceId = 1L,
                        qtyGrams = 100.0
                    )
                )
            ),
            meal(
                id = 11L,
                items = listOf(
                    item(
                        id = 101L,
                        sourceType = PlannedItemSource.FOOD,
                        sourceId = 2L,
                        qtyServings = 2.0
                    )
                )
            )
        )

        val result = useCase(meals)

        val summed = result.mealTotals.values.fold(MacroTotals()) { acc, m ->
            MacroTotals(
                caloriesKcal = acc.caloriesKcal + m.caloriesKcal,
                proteinG = acc.proteinG + m.proteinG,
                carbsG = acc.carbsG + m.carbsG,
                fatG = acc.fatG + m.fatG
            )
        }

        assertEquals(summed.caloriesKcal, result.dayTotals.caloriesKcal, 1e-9)
        assertEquals(summed.proteinG, result.dayTotals.proteinG, 1e-9)
        assertEquals(summed.carbsG, result.dayTotals.carbsG, 1e-9)
        assertEquals(summed.fatG, result.dayTotals.fatG, 1e-9)
    }

    private class FakeFoodSnapshotRepository(
        private val snapshots: Map<Long, FoodNutritionSnapshot>
    ) : FoodNutritionSnapshotRepository {
        override suspend fun getSnapshot(foodId: Long): FoodNutritionSnapshot? = snapshots[foodId]

        override suspend fun getSnapshots(foodIds: Set<Long>): Map<Long, FoodNutritionSnapshot> =
            snapshots.filterKeys { it in foodIds }
    }

    private class FakeRecipeRepository(
        private val foodIdsByRecipeIds: Map<Long, Long> = emptyMap()
    ) : RecipeRepository {
        override suspend fun getFoodIdsByRecipeIds(recipeIds: Set<Long>): Map<Long, Long> =
            foodIdsByRecipeIds.filterKeys { it in recipeIds }

        override suspend fun getRecipeIdsByFoodIds(foodIds: Set<Long>): Map<Long, Long> = emptyMap()

        override suspend fun getRecipeByFoodId(foodId: Long): RecipeHeader? = error("Not needed in this test")
        override suspend fun getIngredients(recipeId: Long): List<RecipeIngredientLine> = error("Not needed in this test")
        override suspend fun getHeaderByRecipeId(recipeId: Long): RecipeHeader? = error("Not needed in this test")
        override suspend fun softDeleteRecipeByFoodId(foodId: Long) {
            error("Not needed in this test")
        }

        override suspend fun createRecipe(draft: com.example.adobongkangkong.domain.model.RecipeDraft): Long =
            error("Not needed in this test")

        override suspend fun updateRecipeByFoodId(
            foodId: Long,
            servingsYield: Double,
            totalYieldGrams: Double?,
            ingredients: List<RecipeIngredientLine>
        ) = error("Not needed in this test")

        override suspend fun getInstructionSteps(recipeId: Long): List<RecipeInstructionStep> =
            emptyList()

        override suspend fun insertInstructionStep(
            recipeId: Long,
            position: Int,
            text: String
        ): Long = error("Not needed in this test")

        override suspend fun updateInstructionStepText(
            stepId: Long,
            text: String
        ) = error("Not needed in this test")

        override suspend fun updateInstructionStepPosition(
            stepId: Long,
            position: Int
        ) = error("Not needed in this test")

        override suspend fun setInstructionStepImage(
            stepId: Long,
            imagePath: String?
        ) = error("Not needed in this test")

        override suspend fun deleteInstructionStep(stepId: Long) =
            error("Not needed in this test")

        override suspend fun deleteInstructionStepsForRecipe(recipeId: Long) =
            error("Not needed in this test")

        override suspend fun reorderInstructionSteps(
            recipeId: Long,
            orderedStepIds: List<Long>
        ) = error("Not needed in this test")

        override suspend fun moveInstructionStepUp(
            recipeId: Long,
            stepId: Long
        ) = error("Not needed in this test")

        override suspend fun moveInstructionStepDown(
            recipeId: Long,
            stepId: Long
        ) = error("Not needed in this test")
    }

    private class FakeRecipeBatchLookupRepository(
        private val batchFoodIds: Map<Long, Long> = emptyMap()
    ) : RecipeBatchLookupRepository {
        override suspend fun getBatchFoodIds(batchIds: Set<Long>): Map<Long, Long> =
            batchFoodIds.filterKeys { it in batchIds }

        override suspend fun getBatchById(batchId: Long): BatchSummary? = error("Not needed in this test")
        override suspend fun getBatchesForRecipe(recipeId: Long): List<BatchSummary> = error("Not needed in this test")
    }

    @Test
    fun grams_overrides_servings_when_both_present() = runBlocking {

        val useCase = ComputePlannedDayMacroTotalsUseCase(
            foodSnapshots = FakeFoodSnapshotRepository(
                mapOf(
                    1L to gramsSnapshot(
                        foodId = 1L,
                        gramsPerServingUnit = 50.0,
                        perGram = nutrientsOf(
                            kcal to 2.0,
                            protein to 0.1,
                            carbs to 0.0,
                            fat to 0.0
                        )
                    )
                )
            ),
            recipes = FakeRecipeRepository(),
            recipeBatches = FakeRecipeBatchLookupRepository()
        )

        val meals = listOf(
            meal(
                id = 1,
                items = listOf(
                    item(
                        id = 1,
                        sourceType = PlannedItemSource.FOOD,
                        sourceId = 1,
                        qtyGrams = 100.0,
                        qtyServings = 3.0
                    )
                )
            )
        )

        val result = useCase(meals)

        val mealTotal = result.mealTotals[1]!!

        // must use grams path only
        assertEquals(200.0, mealTotal.caloriesKcal, 1e-9)
    }

    @Test
    fun floating_point_stability_large_item_count() = runBlocking {

        val snap = gramsSnapshot(
            foodId = 1,
            gramsPerServingUnit = 50.0,
            perGram = nutrientsOf(
                kcal to 2.0,
                protein to 0.1,
                carbs to 0.2,
                fat to 0.05
            )
        )

        val meals = listOf(
            meal(
                id = 1,
                items = (1..100).map {
                    item(
                        id = it.toLong(),
                        sourceType = PlannedItemSource.FOOD,
                        sourceId = 1,
                        qtyGrams = 10.0
                    )
                }
            )
        )

        val useCase = ComputePlannedDayMacroTotalsUseCase(
            FakeFoodSnapshotRepository(mapOf(1L to snap)),
            FakeRecipeRepository(),
            FakeRecipeBatchLookupRepository()
        )

        val result = useCase(meals)

        assertEquals(2000.0, result.dayTotals.caloriesKcal, 1e-6)
    }
}