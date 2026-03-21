package com.example.adobongkangkong.domain.shopping.usecase

import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.domain.model.ServingUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComputeRecipeShoppingRequirementsUseCaseTest {

    private val useCase = ComputeRecipeShoppingRequirementsUseCase()

    @Test
    fun singleExactBatch_computesExpectedTotals() {
        val result = useCase(
            demandEntries = listOf(
                demand(
                    recipeId = 1L,
                    occurrenceKey = "2026-03-20_lunch",
                    requiredYield = 500.0
                )
            ),
            recipeDefinitions = listOf(
                recipe(
                    recipeId = 1L,
                    recipeName = "Chicken Greens",
                    batchYield = 500.0,
                    ingredients = listOf(
                        ingredient(foodId = 101L, foodName = "Chicken Thigh", amountPerBatch = 400.0, unit = ServingUnit.G),
                        ingredient(foodId = 102L, foodName = "Bok Choy", amountPerBatch = 200.0, unit = ServingUnit.G)
                    )
                )
            )
        )

        assertTrue(result.issues.isEmpty())
        assertEquals(1, result.notTotalled.size)
        assertEquals(1, result.totalled.size)

        val occurrence = result.notTotalled.single()
        assertDoubleEquals(500.0, occurrence.requiredYield)
        assertDoubleEquals(1.0, occurrence.batchesRequired)
        assertDoubleEquals(400.0, occurrence.ingredients.first { it.foodId == 101L }.amountRequired)
        assertDoubleEquals(200.0, occurrence.ingredients.first { it.foodId == 102L }.amountRequired)

        val total = result.totalled.single()
        assertDoubleEquals(500.0, total.totalRequiredYield)
        assertDoubleEquals(1.0, total.batchesRequired)
        assertDoubleEquals(400.0, total.ingredients.first { it.foodId == 101L }.amountRequired)
        assertDoubleEquals(200.0, total.ingredients.first { it.foodId == 102L }.amountRequired)
    }

    @Test
    fun fractionalBatch_scalesIngredientsLinearly() {
        val result = useCase(
            demandEntries = listOf(
                demand(
                    recipeId = 1L,
                    occurrenceKey = "2026-03-20_dinner",
                    requiredYield = 750.0
                )
            ),
            recipeDefinitions = listOf(
                recipe(
                    recipeId = 1L,
                    recipeName = "Chicken Greens",
                    batchYield = 500.0,
                    ingredients = listOf(
                        ingredient(foodId = 101L, foodName = "Chicken Thigh", amountPerBatch = 400.0, unit = ServingUnit.G),
                        ingredient(foodId = 103L, foodName = "Soy Sauce", amountPerBatch = 30.0, unit = ServingUnit.ML)
                    )
                )
            )
        )

        assertTrue(result.issues.isEmpty())

        val total = result.totalled.single()
        assertDoubleEquals(750.0, total.totalRequiredYield)
        assertDoubleEquals(1.5, total.batchesRequired)
        assertDoubleEquals(600.0, total.ingredients.first { it.foodId == 101L }.amountRequired)
        assertDoubleEquals(45.0, total.ingredients.first { it.foodId == 103L }.amountRequired)
    }

    @Test
    fun sameRecipeMultipleOccurrences_totalledAggregates_notTotalledPreservesOccurrences() {
        val result = useCase(
            demandEntries = listOf(
                demand(recipeId = 1L, occurrenceKey = "2026-03-20_lunch", requiredYield = 250.0),
                demand(recipeId = 1L, occurrenceKey = "2026-03-21_lunch", requiredYield = 500.0)
            ),
            recipeDefinitions = listOf(
                recipe(
                    recipeId = 1L,
                    recipeName = "Chicken Greens",
                    batchYield = 500.0,
                    ingredients = listOf(
                        ingredient(foodId = 101L, foodName = "Chicken Thigh", amountPerBatch = 400.0, unit = ServingUnit.G)
                    )
                )
            )
        )

        assertTrue(result.issues.isEmpty())
        assertEquals(2, result.notTotalled.size)

        val firstOccurrence = result.notTotalled.first { it.occurrenceKey == "2026-03-20_lunch" }
        val secondOccurrence = result.notTotalled.first { it.occurrenceKey == "2026-03-21_lunch" }

        assertDoubleEquals(0.5, firstOccurrence.batchesRequired)
        assertDoubleEquals(200.0, firstOccurrence.ingredients.single().amountRequired)

        assertDoubleEquals(1.0, secondOccurrence.batchesRequired)
        assertDoubleEquals(400.0, secondOccurrence.ingredients.single().amountRequired)

        val total = result.totalled.single()
        assertDoubleEquals(750.0, total.totalRequiredYield)
        assertDoubleEquals(1.5, total.batchesRequired)
        assertDoubleEquals(600.0, total.ingredients.single().amountRequired)
    }

    @Test
    fun sameIngredientWithinSameRecipe_mergesWhenFoodIdAndUnitMatch() {
        val result = useCase(
            demandEntries = listOf(
                demand(recipeId = 1L, occurrenceKey = "2026-03-20_lunch", requiredYield = 500.0)
            ),
            recipeDefinitions = listOf(
                recipe(
                    recipeId = 1L,
                    recipeName = "Garlic Chicken",
                    batchYield = 500.0,
                    ingredients = listOf(
                        ingredient(foodId = 201L, foodName = "Garlic", amountPerBatch = 10.0, unit = ServingUnit.G),
                        ingredient(foodId = 201L, foodName = "Garlic", amountPerBatch = 5.0, unit = ServingUnit.G)
                    )
                )
            )
        )

        assertTrue(result.issues.isEmpty())

        val totalIngredients = result.totalled.single().ingredients
        assertEquals(1, totalIngredients.size)
        assertDoubleEquals(15.0, totalIngredients.single().amountRequired)

        val occurrenceIngredients = result.notTotalled.single().ingredients
        assertEquals(1, occurrenceIngredients.size)
        assertDoubleEquals(15.0, occurrenceIngredients.single().amountRequired)
    }

    @Test
    fun sameIngredientWithinSameRecipe_doesNotMergeWhenUnitDiffers() {
        val result = useCase(
            demandEntries = listOf(
                demand(recipeId = 1L, occurrenceKey = "2026-03-20_lunch", requiredYield = 500.0)
            ),
            recipeDefinitions = listOf(
                recipe(
                    recipeId = 1L,
                    recipeName = "Garlic Chicken",
                    batchYield = 500.0,
                    ingredients = listOf(
                        ingredient(foodId = 201L, foodName = "Garlic", amountPerBatch = 10.0, unit = ServingUnit.G),
                        ingredient(foodId = 201L, foodName = "Garlic", amountPerBatch = 2.0, unit = ServingUnit.PIECE)
                    )
                )
            )
        )

        assertTrue(result.issues.isEmpty())

        val totalIngredients = result.totalled.single().ingredients
        assertEquals(2, totalIngredients.size)
        assertTrue(totalIngredients.any { it.unit == ServingUnit.G && almostEqual(it.amountRequired, 10.0) })
        assertTrue(totalIngredients.any { it.unit == ServingUnit.PIECE && almostEqual(it.amountRequired, 2.0) })
    }

    @Test
    fun sameIngredientAcrossRecipes_marksDuplicates_butDoesNotMergeAcrossRecipes() {
        val result = useCase(
            demandEntries = listOf(
                demand(recipeId = 1L, occurrenceKey = "2026-03-20_lunch", requiredYield = 500.0),
                demand(recipeId = 2L, occurrenceKey = "2026-03-20_dinner", requiredYield = 400.0)
            ),
            recipeDefinitions = listOf(
                recipe(
                    recipeId = 1L,
                    recipeName = "Chicken Greens",
                    batchYield = 500.0,
                    ingredients = listOf(
                        ingredient(foodId = 201L, foodName = "Garlic", amountPerBatch = 10.0, unit = ServingUnit.G)
                    )
                ),
                recipe(
                    recipeId = 2L,
                    recipeName = "Beef Stir Fry",
                    batchYield = 400.0,
                    ingredients = listOf(
                        ingredient(foodId = 201L, foodName = "Garlic", amountPerBatch = 8.0, unit = ServingUnit.G)
                    )
                )
            )
        )

        assertTrue(result.issues.isEmpty())
        assertEquals(2, result.totalled.size)

        val recipe1 = result.totalled.first { it.recipeId == 1L }
        val recipe2 = result.totalled.first { it.recipeId == 2L }

        assertEquals(1, recipe1.ingredients.size)
        assertEquals(1, recipe2.ingredients.size)
        assertTrue(recipe1.ingredients.single().isDuplicateAcrossRecipes)
        assertTrue(recipe2.ingredients.single().isDuplicateAcrossRecipes)
        assertDoubleEquals(10.0, recipe1.ingredients.single().amountRequired)
        assertDoubleEquals(8.0, recipe2.ingredients.single().amountRequired)
    }

    @Test
    fun ingredientUsedMultipleTimesInOneRecipeOnly_isNotMarkedDuplicateAcrossRecipes() {
        val result = useCase(
            demandEntries = listOf(
                demand(recipeId = 1L, occurrenceKey = "2026-03-20_lunch", requiredYield = 500.0)
            ),
            recipeDefinitions = listOf(
                recipe(
                    recipeId = 1L,
                    recipeName = "Garlic Chicken",
                    batchYield = 500.0,
                    ingredients = listOf(
                        ingredient(foodId = 201L, foodName = "Garlic", amountPerBatch = 10.0, unit = ServingUnit.G),
                        ingredient(foodId = 201L, foodName = "Garlic", amountPerBatch = 5.0, unit = ServingUnit.G)
                    )
                )
            )
        )

        assertTrue(result.issues.isEmpty())
        assertFalse(result.totalled.single().ingredients.single().isDuplicateAcrossRecipes)
        assertFalse(result.notTotalled.single().ingredients.single().isDuplicateAcrossRecipes)
    }

    @Test
    fun missingRecipeDefinition_reportsIssue_andSkipsOccurrence() {
        val result = useCase(
            demandEntries = listOf(
                demand(recipeId = 999L, occurrenceKey = "2026-03-20_lunch", requiredYield = 500.0)
            ),
            recipeDefinitions = emptyList()
        )

        assertEquals(1, result.issues.size)
        assertTrue(result.issues.single() is ComputeRecipeShoppingRequirementsUseCase.Issue.MissingRecipeDefinition)
        assertTrue(result.totalled.isEmpty())
        assertTrue(result.notTotalled.isEmpty())
    }

    @Test
    fun invalidBatchYield_reportsIssue_andSkipsOccurrence() {
        val result = useCase(
            demandEntries = listOf(
                demand(recipeId = 1L, occurrenceKey = "2026-03-20_lunch", requiredYield = 500.0)
            ),
            recipeDefinitions = listOf(
                recipe(
                    recipeId = 1L,
                    recipeName = "Broken Recipe",
                    batchYield = 0.0,
                    ingredients = listOf(
                        ingredient(foodId = 201L, foodName = "Garlic", amountPerBatch = 10.0, unit = ServingUnit.G)
                    )
                )
            )
        )

        assertEquals(1, result.issues.size)
        assertTrue(result.issues.single() is ComputeRecipeShoppingRequirementsUseCase.Issue.InvalidBatchYield)
        assertTrue(result.totalled.isEmpty())
        assertTrue(result.notTotalled.isEmpty())
    }

    @Test
    fun basisMismatch_reportsIssue_andSkipsOccurrence() {
        val result = useCase(
            demandEntries = listOf(
                demand(
                    recipeId = 1L,
                    occurrenceKey = "2026-03-20_lunch",
                    requiredYield = 500.0,
                    basis = BasisType.PER_100ML
                )
            ),
            recipeDefinitions = listOf(
                recipe(
                    recipeId = 1L,
                    recipeName = "Chicken Greens",
                    batchYield = 500.0,
                    basis = BasisType.PER_100G,
                    ingredients = listOf(
                        ingredient(foodId = 101L, foodName = "Chicken Thigh", amountPerBatch = 400.0, unit = ServingUnit.G)
                    )
                )
            )
        )

        assertEquals(1, result.issues.size)
        assertTrue(result.issues.single() is ComputeRecipeShoppingRequirementsUseCase.Issue.BasisMismatch)
        assertTrue(result.totalled.isEmpty())
        assertTrue(result.notTotalled.isEmpty())
    }

    @Test
    fun invoke_isDeterministic_forEquivalentInput() {
        val demandEntries = listOf(
            demand(recipeId = 2L, occurrenceKey = "2026-03-20_dinner", requiredYield = 400.0),
            demand(recipeId = 1L, occurrenceKey = "2026-03-20_lunch", requiredYield = 500.0)
        )

        val recipeDefinitions = listOf(
            recipe(
                recipeId = 2L,
                recipeName = "Beef Stir Fry",
                batchYield = 400.0,
                ingredients = listOf(
                    ingredient(foodId = 201L, foodName = "Garlic", amountPerBatch = 8.0, unit = ServingUnit.G)
                )
            ),
            recipe(
                recipeId = 1L,
                recipeName = "Chicken Greens",
                batchYield = 500.0,
                ingredients = listOf(
                    ingredient(foodId = 101L, foodName = "Chicken Thigh", amountPerBatch = 400.0, unit = ServingUnit.G)
                )
            )
        )

        val first = useCase(
            demandEntries = demandEntries,
            recipeDefinitions = recipeDefinitions
        )

        val second = useCase(
            demandEntries = demandEntries,
            recipeDefinitions = recipeDefinitions
        )

        assertEquals(first, second)
    }

    private fun demand(
        recipeId: Long,
        occurrenceKey: String,
        requiredYield: Double,
        basis: BasisType = BasisType.PER_100G
    ) = ComputeRecipeShoppingRequirementsUseCase.RecipeDemandEntry(
        recipeId = recipeId,
        occurrenceKey = occurrenceKey,
        requiredYield = requiredYield,
        basis = basis
    )

    private fun recipe(
        recipeId: Long,
        recipeName: String,
        batchYield: Double,
        basis: BasisType = BasisType.PER_100G,
        ingredients: List<ComputeRecipeShoppingRequirementsUseCase.RecipeIngredientDefinition>
    ) = ComputeRecipeShoppingRequirementsUseCase.RecipeDefinition(
        recipeId = recipeId,
        recipeName = recipeName,
        batchYield = batchYield,
        basis = basis,
        ingredients = ingredients
    )

    private fun ingredient(
        foodId: Long,
        foodName: String,
        amountPerBatch: Double,
        unit: ServingUnit
    ) = ComputeRecipeShoppingRequirementsUseCase.RecipeIngredientDefinition(
        foodId = foodId,
        foodName = foodName,
        amountPerBatch = amountPerBatch,
        unit = unit
    )

    private fun assertDoubleEquals(expected: Double, actual: Double, delta: Double = 0.000001) {
        assertEquals(expected, actual, delta)
    }

    private fun almostEqual(a: Double, b: Double, delta: Double = 0.000001): Boolean {
        return kotlin.math.abs(a - b) <= delta
    }
}