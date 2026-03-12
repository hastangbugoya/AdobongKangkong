package com.example.adobongkangkong.domain.nutrition

import com.example.adobongkangkong.domain.recipes.FoodNutritionSnapshot
import com.example.adobongkangkong.domain.recipes.RecipeNutritionWarning
import com.example.adobongkangkong.domain.repository.FoodNutritionSnapshotRepository
import com.example.adobongkangkong.domain.repository.RecipeHeader
import com.example.adobongkangkong.domain.repository.RecipeIngredientLine
import com.example.adobongkangkong.domain.repository.RecipeRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class ComputeRecipeBatchNutritionUseCaseTest {

    private val protein = NutrientKey.PROTEIN_G
    private val kcal = NutrientKey.CALORIES_KCAL

    private fun nutrientsOf(vararg pairs: Pair<NutrientKey, Double>) =
        NutrientMap(pairs.toMap())

    private fun snapshotGrams(
        foodId: Long,
        gramsPerServingUnit: Double,
        nutrientsPerGram: NutrientMap
    ) = FoodNutritionSnapshot(
        foodId = foodId,
        gramsPerServingUnit = gramsPerServingUnit,
        mlPerServingUnit = null,
        nutrientsPerGram = nutrientsPerGram,
        nutrientsPerMilliliter = null
    )

    private fun snapshotMl(
        foodId: Long,
        mlPerServingUnit: Double,
        nutrientsPerMilliliter: NutrientMap
    ) = FoodNutritionSnapshot(
        foodId = foodId,
        gramsPerServingUnit = null,
        mlPerServingUnit = mlPerServingUnit,
        nutrientsPerGram = null,
        nutrientsPerMilliliter = nutrientsPerMilliliter
    )

    @Test
    fun computes_totals_and_derived_values_for_valid_recipe() = runBlocking {

        val recipeRepo = FakeRecipeRepository(
            header = RecipeHeader(
                recipeId = 10,
                foodId = 100,
                servingsYield = 4.0,
                totalYieldGrams = 400.0
            ),
            ingredients = listOf(
                RecipeIngredientLine(1L, 2.0),
                RecipeIngredientLine(2L, 1.0)
            )
        )

        val snapshotRepo = FakeFoodSnapshotRepo(
            mapOf(
                1L to snapshotGrams(
                    1,
                    gramsPerServingUnit = 50.0,
                    nutrientsPerGram = nutrientsOf(
                        protein to 0.1,
                        kcal to 2.0
                    )
                ),
                2L to snapshotGrams(
                    2,
                    gramsPerServingUnit = 100.0,
                    nutrientsPerGram = nutrientsOf(
                        protein to 0.2,
                        kcal to 1.5
                    )
                )
            )
        )

        val useCase = ComputeRecipeBatchNutritionUseCase(recipeRepo, snapshotRepo)

        val result = useCase.execute(100)

        assertEquals(30.0, result.totals[protein], 1e-9)
        assertEquals(350.0, result.totals[kcal], 1e-9)

        val perServing = assertNotNull(result.perServing)
        val perCookedGram = assertNotNull(result.perCookedGram)
        val gramsPerServingCooked = assertNotNull(result.gramsPerServingCooked)

        assertEquals(7.5, perServing[protein], 1e-9)
        assertEquals(87.5, perServing[kcal], 1e-9)

        assertEquals(0.075, perCookedGram[protein], 1e-9)
        assertEquals(100.0, gramsPerServingCooked, 1e-9)

        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun null_servings_defaults_to_one() = runBlocking {

        val recipeRepo = FakeRecipeRepository(
            RecipeHeader(10, 100, 2.0, 200.0),
            listOf(RecipeIngredientLine(1L, null))
        )

        val snapshotRepo = FakeFoodSnapshotRepo(
            mapOf(
                1L to snapshotGrams(
                    1,
                    80.0,
                    nutrientsOf(protein to 0.25)
                )
            )
        )

        val useCase = ComputeRecipeBatchNutritionUseCase(recipeRepo, snapshotRepo)

        val result = useCase.execute(100)

        assertEquals(20.0, result.totals[protein], 1e-9)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun non_positive_servings_skips_ingredient() = runBlocking {

        val recipeRepo = FakeRecipeRepository(
            RecipeHeader(10, 100, 2.0, 200.0),
            listOf(RecipeIngredientLine(1L, 0.0))
        )

        val snapshotRepo = FakeFoodSnapshotRepo(
            mapOf(
                1L to snapshotGrams(
                    1,
                    50.0,
                    nutrientsOf(protein to 0.1)
                )
            )
        )

        val useCase = ComputeRecipeBatchNutritionUseCase(recipeRepo, snapshotRepo)

        val result = useCase.execute(100)

        assertEquals(0.0, result.totals[protein])
        assertTrue(
            result.warnings.any {
                it is RecipeNutritionWarning.IngredientServingsNonPositive
            }
        )
    }

    @Test
    fun ml_based_ingredient_scales_correctly() = runBlocking {

        val recipeRepo = FakeRecipeRepository(
            RecipeHeader(10, 100, 2.0, 300.0),
            listOf(RecipeIngredientLine(1L, 2.0))
        )

        val snapshotRepo = FakeFoodSnapshotRepo(
            mapOf(
                1L to snapshotMl(
                    1,
                    mlPerServingUnit = 30.0,
                    nutrientsPerMilliliter = nutrientsOf(
                        kcal to 0.5
                    )
                )
            )
        )

        val useCase = ComputeRecipeBatchNutritionUseCase(recipeRepo, snapshotRepo)

        val result = useCase.execute(100)

        assertEquals(30.0, result.totals[kcal], 1e-9)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun missing_recipe_returns_empty_result() = runBlocking {

        val recipeRepo = FakeRecipeRepository(null, emptyList())
        val snapshotRepo = FakeFoodSnapshotRepo(emptyMap())

        val useCase = ComputeRecipeBatchNutritionUseCase(recipeRepo, snapshotRepo)

        val result = useCase.execute(100)

        assertTrue(result.totals.isEmpty())
        assertNull(result.perServing)
        assertNull(result.perCookedGram)
        assertTrue(result.warnings.isEmpty())
    }

    // -------------------------
    // Fake Repositories
    // -------------------------

    private class FakeRecipeRepository(
        private val header: RecipeHeader?,
        private val ingredients: List<RecipeIngredientLine>
    ) : RecipeRepository {

        override suspend fun getRecipeByFoodId(foodId: Long) = header

        override suspend fun getIngredients(recipeId: Long) = ingredients

        override suspend fun getHeaderByRecipeId(recipeId: Long) = header

        override suspend fun getFoodIdsByRecipeIds(recipeIds: Set<Long>) = emptyMap<Long, Long>()

        override suspend fun getRecipeIdsByFoodIds(foodIds: Set<Long>) = emptyMap<Long, Long>()

        override suspend fun createRecipe(draft: com.example.adobongkangkong.domain.model.RecipeDraft): Long {
            error("not needed")
        }

        override suspend fun updateRecipeByFoodId(
            foodId: Long,
            servingsYield: Double,
            totalYieldGrams: Double?,
            ingredients: List<RecipeIngredientLine>
        ) {
            error("not needed")
        }
    }

    private class FakeFoodSnapshotRepo(
        private val snapshots: Map<Long, FoodNutritionSnapshot>
    ) : FoodNutritionSnapshotRepository {

        override suspend fun getSnapshot(foodId: Long) = snapshots[foodId]

        override suspend fun getSnapshots(foodIds: Set<Long>) =
            snapshots.filterKeys { it in foodIds }
    }

    @Test
    fun invariant_totals_equals_per_serving_times_servings_yield() = runBlocking {

        val servingsYield = 5.0
        val totalYieldGrams = 500.0

        val recipeRepo = FakeRecipeRepository(
            RecipeHeader(
                recipeId = 10,
                foodId = 100,
                servingsYield = servingsYield,
                totalYieldGrams = totalYieldGrams
            ),
            listOf(
                RecipeIngredientLine(1, 2.0),
                RecipeIngredientLine(2, 3.0)
            )
        )

        val snapshotRepo = FakeFoodSnapshotRepo(
            mapOf(
                1L to snapshotGrams(
                    1,
                    gramsPerServingUnit = 40.0,
                    nutrientsPerGram = nutrientsOf(
                        NutrientKey.PROTEIN_G to 0.2,
                        NutrientKey.CALORIES_KCAL to 1.5
                    )
                ),
                2L to snapshotGrams(
                    2,
                    gramsPerServingUnit = 20.0,
                    nutrientsPerGram = nutrientsOf(
                        NutrientKey.PROTEIN_G to 0.1,
                        NutrientKey.CALORIES_KCAL to 2.0
                    )
                )
            )
        )

        val useCase = ComputeRecipeBatchNutritionUseCase(recipeRepo, snapshotRepo)

        val result = useCase.execute(100)

        val totals = result.totals
        val perServing = result.perServing!!

        val reconstructedTotals = perServing.scaledBy(servingsYield)

        for (key in totals.keys()) {
            assertEquals(
                totals[key],
                reconstructedTotals[key],
                1e-9,
                "Invariant failed for nutrient $key"
            )
        }
    }

    @Test
    fun invariant_totals_equals_per_cooked_gram_times_total_yield_grams() = runBlocking {
        val totalYieldGrams: Double = 500.0

        val recipeRepo = FakeRecipeRepository(
            header = RecipeHeader(
                recipeId = 10L,
                foodId = 100L,
                servingsYield = 5.0,
                totalYieldGrams = totalYieldGrams
            ),
            ingredients = listOf(
                RecipeIngredientLine(
                    ingredientFoodId = 1L,
                    ingredientServings = 2.0
                ),
                RecipeIngredientLine(
                    ingredientFoodId = 2L,
                    ingredientServings = 3.0
                )
            )
        )

        val snapshotRepo = FakeFoodSnapshotRepo(
            mapOf(
                1L to snapshotGrams(
                    foodId = 1L,
                    gramsPerServingUnit = 40.0,
                    nutrientsPerGram = nutrientsOf(
                        NutrientKey.PROTEIN_G to 0.2,
                        NutrientKey.CALORIES_KCAL to 1.5
                    )
                ),
                2L to snapshotGrams(
                    foodId = 2L,
                    gramsPerServingUnit = 20.0,
                    nutrientsPerGram = nutrientsOf(
                        NutrientKey.PROTEIN_G to 0.1,
                        NutrientKey.CALORIES_KCAL to 2.0
                    )
                )
            )
        )

        val useCase = ComputeRecipeBatchNutritionUseCase(recipeRepo, snapshotRepo)

        val result = useCase.execute(100L)

        val totals = result.totals
        val perCookedGram = assertNotNull(result.perCookedGram)

        val reconstructedTotals = perCookedGram.scaledBy(totalYieldGrams)

        for (key in totals.keys()) {
            assertEquals(
                totals[key],
                reconstructedTotals[key],
                1e-9,
                "Invariant failed for nutrient $key"
            )
        }
    }
}