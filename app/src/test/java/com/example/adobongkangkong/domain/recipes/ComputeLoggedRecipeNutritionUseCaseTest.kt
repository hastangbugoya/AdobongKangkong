package com.example.adobongkangkong.domain.recipes

import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.nutrition.NutrientMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComputeLoggedRecipeNutritionUseCaseTest {

    private val useCase = ComputeLoggedRecipeNutritionUseCase()

    @Test
    fun byServings_oneServing_usesPerServingTotals() {
        val recipeNutrition = recipeNutrition(
            totalCalories = 5262.0,
            servingsYield = 6.0,
            totalYieldGrams = null,
        )

        val result = useCase(
            recipeNutrition = recipeNutrition,
            input = RecipeLogInput.ByServings(1.0),
        )

        assertTrue(result.isAllowed)
        assertEquals(877.0, result.totals[NutrientKey.CALORIES_KCAL], 0.0001)
        assertEquals(68.15, result.totals[NutrientKey.PROTEIN_G], 0.0001)
        assertEquals(18.45, result.totals[NutrientKey.CARBS_G], 0.0001)
        assertEquals(57.9833333333, result.totals[NutrientKey.FAT_G], 0.0001)
    }

    @Test
    fun byServings_twoServings_doublesPerServingTotals() {
        val recipeNutrition = recipeNutrition(
            totalCalories = 5262.0,
            servingsYield = 6.0,
            totalYieldGrams = null,
        )

        val result = useCase(
            recipeNutrition = recipeNutrition,
            input = RecipeLogInput.ByServings(2.0),
        )

        assertTrue(result.isAllowed)
        assertEquals(1754.0, result.totals[NutrientKey.CALORIES_KCAL], 0.0001)
        assertEquals(136.3, result.totals[NutrientKey.PROTEIN_G], 0.0001)
        assertEquals(36.9, result.totals[NutrientKey.CARBS_G], 0.0001)
        assertEquals(115.9666666667, result.totals[NutrientKey.FAT_G], 0.0001)
    }

    @Test
    fun byCookedGrams_usesPerCookedGramTotals() {
        val recipeNutrition = recipeNutrition(
            totalCalories = 1200.0,
            servingsYield = 4.0,
            totalYieldGrams = 600.0,
        )

        val result = useCase(
            recipeNutrition = recipeNutrition,
            input = RecipeLogInput.ByCookedGrams(150.0),
        )

        assertTrue(result.isAllowed)
        assertEquals(300.0, result.totals[NutrientKey.CALORIES_KCAL], 0.0001)
        assertEquals(15.0, result.totals[NutrientKey.PROTEIN_G], 0.0001)
        assertEquals(45.0, result.totals[NutrientKey.CARBS_G], 0.0001)
        assertEquals(10.0, result.totals[NutrientKey.FAT_G], 0.0001)
    }

    @Test
    fun byServings_missingPerServing_blocksAndReturnsEmptyTotals() {
        val recipeNutrition = RecipeNutritionResult(
            totals = macros(
                calories = 5262.0,
                protein = 408.9,
                carbs = 110.7,
                fat = 347.9,
            ),
            perServing = null,
            perCookedGram = null,
            gramsPerServingCooked = null,
            warnings = emptyList(),
        )

        val result = useCase(
            recipeNutrition = recipeNutrition,
            input = RecipeLogInput.ByServings(1.0),
        )

        assertFalse(result.isAllowed)
        assertTrue(result.totals.isEmpty())
        assertTrue(result.warnings.any { it == RecipeNutritionWarning.MissingServingsYield })
    }

    @Test
    fun byCookedGrams_missingPerCookedGram_blocksAndReturnsEmptyTotals() {
        val recipeNutrition = recipeNutrition(
            totalCalories = 5262.0,
            servingsYield = 6.0,
            totalYieldGrams = null,
        )

        val result = useCase(
            recipeNutrition = recipeNutrition,
            input = RecipeLogInput.ByCookedGrams(100.0),
        )

        assertFalse(result.isAllowed)
        assertTrue(result.totals.isEmpty())
        assertTrue(result.warnings.any { it == RecipeNutritionWarning.MissingTotalYieldGrams })
    }

    @Test
    fun nonPositiveServings_blocksWithInvalidServingsWarning() {
        val recipeNutrition = recipeNutrition(
            totalCalories = 5262.0,
            servingsYield = 6.0,
            totalYieldGrams = null,
        )

        val result = useCase(
            recipeNutrition = recipeNutrition,
            input = RecipeLogInput.ByServings(0.0),
        )

        assertFalse(result.isAllowed)
        assertTrue(result.totals.isEmpty())
        assertTrue(
            result.warnings.any {
                it is RecipeNutritionWarning.InvalidServingsYield && it.value == 0.0
            }
        )
    }

    @Test
    fun nonPositiveCookedGrams_blocksWithInvalidTotalYieldWarning() {
        val recipeNutrition = recipeNutrition(
            totalCalories = 1200.0,
            servingsYield = 4.0,
            totalYieldGrams = 600.0,
        )

        val result = useCase(
            recipeNutrition = recipeNutrition,
            input = RecipeLogInput.ByCookedGrams(-1.0),
        )

        assertFalse(result.isAllowed)
        assertTrue(result.totals.isEmpty())
        assertTrue(
            result.warnings.any {
                it is RecipeNutritionWarning.InvalidTotalYieldGrams && it.value == -1.0
            }
        )
    }

    @Test
    fun upstreamWarnings_arePreservedWhenAllowed() {
        val upstreamWarning = RecipeNutritionWarning.MissingFood(foodId = 99L)

        val recipeNutrition = recipeNutrition(
            totalCalories = 5262.0,
            servingsYield = 6.0,
            totalYieldGrams = null,
            warnings = listOf(upstreamWarning),
        )

        val result = useCase(
            recipeNutrition = recipeNutrition,
            input = RecipeLogInput.ByServings(1.0),
        )

        assertTrue(result.isAllowed)
        assertEquals(877.0, result.totals[NutrientKey.CALORIES_KCAL], 0.0001)
        assertTrue(result.warnings.contains(upstreamWarning))
    }

    private fun recipeNutrition(
        totalCalories: Double,
        servingsYield: Double,
        totalYieldGrams: Double?,
        warnings: List<RecipeNutritionWarning> = emptyList(),
    ): RecipeNutritionResult {
        val totals = when (totalCalories) {
            5262.0 -> macros(
                calories = 5262.0,
                protein = 408.9,
                carbs = 110.7,
                fat = 347.9,
            )

            1200.0 -> macros(
                calories = 1200.0,
                protein = 60.0,
                carbs = 180.0,
                fat = 40.0,
            )

            else -> macros(
                calories = totalCalories,
                protein = 0.0,
                carbs = 0.0,
                fat = 0.0,
            )
        }

        val perServing = if (servingsYield > 0.0) {
            totals.scaledBy(1.0 / servingsYield)
        } else {
            null
        }

        val perCookedGram = totalYieldGrams
            ?.takeIf { it > 0.0 }
            ?.let { totals.scaledBy(1.0 / it) }

        val gramsPerServingCooked =
            if (servingsYield > 0.0 && totalYieldGrams != null && totalYieldGrams > 0.0) {
                totalYieldGrams / servingsYield
            } else {
                null
            }

        return RecipeNutritionResult(
            totals = totals,
            perServing = perServing,
            perCookedGram = perCookedGram,
            gramsPerServingCooked = gramsPerServingCooked,
            warnings = warnings,
        )
    }

    private fun macros(
        calories: Double,
        protein: Double,
        carbs: Double,
        fat: Double,
    ): NutrientMap =
        NutrientMap(
            mapOf(
                NutrientKey.CALORIES_KCAL to calories,
                NutrientKey.PROTEIN_G to protein,
                NutrientKey.CARBS_G to carbs,
                NutrientKey.FAT_G to fat,
            )
        )
}
