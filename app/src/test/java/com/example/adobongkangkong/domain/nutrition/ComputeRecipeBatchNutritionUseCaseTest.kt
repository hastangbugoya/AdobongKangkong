package com.example.adobongkangkong.domain.nutrition

import com.example.adobongkangkong.domain.model.RecipeDraft
import com.example.adobongkangkong.domain.recipes.FoodNutritionSnapshot
import com.example.adobongkangkong.domain.recipes.Recipe
import com.example.adobongkangkong.domain.recipes.RecipeIngredient
import com.example.adobongkangkong.domain.recipes.RecipeNutritionWarning
import com.example.adobongkangkong.domain.repository.FoodNutritionSnapshotRepository
import com.example.adobongkangkong.domain.repository.RecipeHeader
import com.example.adobongkangkong.domain.repository.RecipeIngredientLine
import com.example.adobongkangkong.domain.repository.RecipeRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ComputeRecipeBatchNutritionUseCaseTest {
    private class FakeRecipeRepo(
        private val headerByFoodId: Map<Long, RecipeHeader>,
        private val ingredientsByRecipeId: Map<Long, List<RecipeIngredientLine>>
    ) : RecipeRepository {

        override suspend fun createRecipe(draft: RecipeDraft): Long {
            throw UnsupportedOperationException("Not needed for these tests")
        }

        override suspend fun getRecipeByFoodId(foodId: Long): RecipeHeader? = headerByFoodId[foodId]

        override suspend fun getIngredients(recipeId: Long): List<RecipeIngredientLine> =
            ingredientsByRecipeId[recipeId].orEmpty()

        override suspend fun updateRecipeByFoodId(
            foodId: Long,
            servingsYield: Double,
            totalYieldGrams: Double?,
            ingredients: List<RecipeIngredientLine>
        ) {
            throw UnsupportedOperationException("Not needed for these tests")
        }
    }

    private class FakeSnapshotRepo(
        private val snapshots: Map<Long, FoodNutritionSnapshot>
    ) : FoodNutritionSnapshotRepository {

        override suspend fun getSnapshot(foodId: Long): FoodNutritionSnapshot? = snapshots[foodId]

        override suspend fun getSnapshots(foodIds: Set<Long>): Map<Long, FoodNutritionSnapshot> =
            snapshots.filterKeys { it in foodIds }
    }

    @Test
    fun totals_and_perServing_and_perCookedGram_are_computed_correctly() {
        // Food A: 50g per serving, 1 kcal per gram, 0.1g protein per gram
        val foodA = FoodNutritionSnapshot(
            foodId = 101L,
            gramsPerServingUnit = 50.0,
            mlPerServingUnit = null,
            nutrientsPerGram = NutrientMap.fromCodeMap(
                mapOf(
                    "kcal" to 1.0,
                    "protein_g" to 0.1
                )
            ),
            nutrientsPerMilliliter = null
        )

        // Food B: 25g per serving, 2 kcal per gram, 0.0 protein per gram
        val foodB = FoodNutritionSnapshot(
            foodId = 202L,
            gramsPerServingUnit = 25.0,
            mlPerServingUnit = null,
            nutrientsPerGram = NutrientMap.fromCodeMap(
                mapOf(
                    "kcal" to 2.0,
                    "protein_g" to 0.0
                )
            ),
            nutrientsPerMilliliter = null
        )

        // Ingredient lines are servings-based:
        // A: 2 servings => grams = 2 * 50 = 100 => kcal=100, protein=10
        // B: 1 serving  => grams = 1 * 25 = 25  => kcal=50,  protein=0
        // Totals: kcal=150, protein=10
        val recipeFoodId = 999L
        val recipeId = 42L

        val repo = FakeRecipeRepo(
            headerByFoodId = mapOf(
                recipeFoodId to RecipeHeader(
                    recipeId = recipeId,
                    foodId = recipeFoodId,
                    servingsYield = 5.0,
                    totalYieldGrams = 200.0
                )
            ),
            ingredientsByRecipeId = mapOf(
                recipeId to listOf(
                    RecipeIngredientLine(ingredientFoodId = 101L, ingredientServings = 2.0),
                    RecipeIngredientLine(ingredientFoodId = 202L, ingredientServings = 1.0)
                )
            )
        )

        val snapshotRepo = FakeSnapshotRepo(
            snapshots = mapOf(
                101L to foodA,
                202L to foodB
            )
        )

        val useCase = ComputeRecipeBatchNutritionUseCase(repo, snapshotRepo)
        val result = runBlocking { useCase.execute(recipeFoodId) }

        // totals
        assertEquals(150.0, result.totals[NutrientKey("kcal")], 1e-9)
        assertEquals(10.0, result.totals[NutrientKey("protein_g")], 1e-9)

        // perServing = totals / 5
        val perServing = result.perServing
        assertNotNull(perServing)
        assertEquals(30.0, perServing[NutrientKey("kcal")], 1e-9)
        assertEquals(2.0, perServing[NutrientKey("protein_g")], 1e-9)

        // perCookedGram = totals / 200
        val perCookedGram = result.perCookedGram
        assertNotNull(perCookedGram)
        assertEquals(0.75, perCookedGram[NutrientKey("kcal")], 1e-9)
        assertEquals(0.05, perCookedGram[NutrientKey("protein_g")], 1e-9)

        // gramsPerServingCooked = 200 / 5 = 40
        assertNotNull(result.gramsPerServingCooked)
        assertEquals(40.0, result.gramsPerServingCooked, 1e-9)

        // no warnings expected
        assertTrue(result.warnings.isEmpty(), "Expected no warnings, got: ${result.warnings}")
    }

    @Test
    fun missing_totalYieldGrams_produces_warning_and_null_perCookedGram() {
        val food = FoodNutritionSnapshot(
            foodId = 1L,
            gramsPerServingUnit = 10.0,
            mlPerServingUnit = null,
            nutrientsPerGram = NutrientMap.fromCodeMap(mapOf("kcal" to 1.0)),
            nutrientsPerMilliliter = null
        )

        val recipeFoodId = 10L
        val recipeId = 11L

        val repo = FakeRecipeRepo(
            headerByFoodId = mapOf(
                recipeFoodId to RecipeHeader(
                    recipeId = recipeId,
                    foodId = recipeFoodId,
                    servingsYield = 2.0,
                    totalYieldGrams = null
                )
            ),
            ingredientsByRecipeId = mapOf(
                recipeId to listOf(
                    RecipeIngredientLine(ingredientFoodId = 1L, ingredientServings = 1.0)
                )
            )
        )

        val snapshotRepo = FakeSnapshotRepo(mapOf(1L to food))
        val useCase = ComputeRecipeBatchNutritionUseCase(repo, snapshotRepo)

        val result = runBlocking { useCase.execute(recipeFoodId) }

        assertNotNull(result.perServing) // servingsYield valid
        assertNull(result.perCookedGram)
        assertTrue(result.warnings.any { it is RecipeNutritionWarning.MissingTotalYieldGrams })
    }

    @Test
    fun execute_recipe_with_null_servingsYield_emits_missingServingsYield_and_null_perServing() {
        val food = FoodNutritionSnapshot(
            foodId = 7L,
            gramsPerServingUnit = 100.0,
            mlPerServingUnit = null,
            nutrientsPerGram = NutrientMap.fromCodeMap(mapOf("kcal" to 1.0)),
            nutrientsPerMilliliter = null
        )

        val snapshotRepo = FakeSnapshotRepo(mapOf(7L to food))
        val repo = FakeRecipeRepo(emptyMap(), emptyMap())

        val useCase = ComputeRecipeBatchNutritionUseCase(repo, snapshotRepo)

        val recipe = Recipe(
            id = 123L,
            name = "Test",
            ingredients = listOf(
                RecipeIngredient(foodId = 7L, servings = 1.0)
            ),
            servingsYield = null,          // <-- key
            totalYieldGrams = 300.0
        )

        val result = runBlocking { useCase.execute(recipe) }

        assertNull(result.perServing)
        assertNotNull(result.perCookedGram)
        assertTrue(result.warnings.any { it is RecipeNutritionWarning.MissingServingsYield })
    }

    @Test
    fun missing_gramsPerServing_skips_ingredient_and_warns() {
        val foodMissingGpsu = FoodNutritionSnapshot(
            foodId = 1L,
            gramsPerServingUnit = null, // <-- key
            mlPerServingUnit = null,
            nutrientsPerGram = NutrientMap.fromCodeMap(mapOf("kcal" to 10.0)),
            nutrientsPerMilliliter = null
        )

        val recipeFoodId = 55L
        val recipeId = 56L

        val repo = FakeRecipeRepo(
            headerByFoodId = mapOf(
                recipeFoodId to RecipeHeader(
                    recipeId = recipeId,
                    foodId = recipeFoodId,
                    servingsYield = 1.0,
                    totalYieldGrams = 100.0
                )
            ),
            ingredientsByRecipeId = mapOf(
                recipeId to listOf(
                    RecipeIngredientLine(ingredientFoodId = 1L, ingredientServings = 1.0)
                )
            )
        )

        val snapshotRepo = FakeSnapshotRepo(mapOf(1L to foodMissingGpsu))
        val useCase = ComputeRecipeBatchNutritionUseCase(repo, snapshotRepo)

        val result = runBlocking { useCase.execute(recipeFoodId) }

        // Ingredient skipped -> totals are zero
        assertEquals(0.0, result.totals[NutrientKey("kcal")], 0.0)
        assertTrue(result.warnings.any { it is RecipeNutritionWarning.MissingGramsPerServing })
    }
}

/**
 * AI NOTE — READ BEFORE REFACTORING (2026-02-06)
 *
 * These tests intentionally construct FoodNutritionSnapshot directly.
 * Snapshot constructor params are now required even for grams-only tests:
 * - mlPerServingUnit
 * - nutrientsPerMilliliters
 *
 * If I add more snapshot fields later, I must update every test construction site.
 * For grams-only tests, keep the new volume params = null; do NOT invent density or conversions.
 */
