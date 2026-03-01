package com.example.adobongkangkong.domain.usage

import com.example.adobongkangkong.domain.model.ServingUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidateFoodForUsageUseCaseDraftTest {

    private val useCase = ValidateFoodForUsageUseCase()

    @Test
    fun `draft - no nutrient rows blocks as MissingNutrients`() {
        val r = useCase.executeDraft(
            ValidateFoodForUsageUseCase.DraftInput(
                servingUnit = ServingUnit.SERVING,
                gramsPerServingUnit = null,
                mlPerServingUnit = null,
                context = UsageContext.LOGGING,
                draft = ValidateFoodForUsageUseCase.DraftNutrients(
                    hasAnyRows = false,
                    hasAnyNumeric = false
                )
            )
        )

        assertBlocked(r, FoodValidationResult.Reason.MissingNutrients)
    }

    @Test
    fun `draft - rows exist but no numeric blocks as BlankNutrients`() {
        val r = useCase.executeDraft(
            ValidateFoodForUsageUseCase.DraftInput(
                servingUnit = ServingUnit.SERVING,
                gramsPerServingUnit = null,
                mlPerServingUnit = null,
                context = UsageContext.LOGGING,
                draft = ValidateFoodForUsageUseCase.DraftNutrients(
                    hasAnyRows = true,
                    hasAnyNumeric = false
                )
            )
        )

        assertBlocked(r, FoodValidationResult.Reason.BlankNutrients)
    }

    @Test
    fun `draft - has numeric but missing grounding blocks MissingGramsPerServing`() {
        val r = useCase.executeDraft(
            ValidateFoodForUsageUseCase.DraftInput(
                servingUnit = ServingUnit.SERVING,
                gramsPerServingUnit = null,
                mlPerServingUnit = null,
                context = UsageContext.LOGGING,
                draft = ValidateFoodForUsageUseCase.DraftNutrients(
                    hasAnyRows = true,
                    hasAnyNumeric = true
                )
            )
        )

        assertBlocked(r, FoodValidationResult.Reason.MissingGramsPerServing)
    }

    @Test
    fun `draft - has numeric and gpsu present passes`() {
        val r = useCase.executeDraft(
            ValidateFoodForUsageUseCase.DraftInput(
                servingUnit = ServingUnit.SERVING,
                gramsPerServingUnit = 30.0,
                mlPerServingUnit = null,
                context = UsageContext.LOGGING,
                draft = ValidateFoodForUsageUseCase.DraftNutrients(
                    hasAnyRows = true,
                    hasAnyNumeric = true
                )
            )
        )

        assertEquals(FoodValidationResult.Ok, r)
    }

    @Test
    fun `draft - has numeric and ml bridge present passes (volume grounded)`() {
        val r = useCase.executeDraft(
            ValidateFoodForUsageUseCase.DraftInput(
                servingUnit = ServingUnit.SERVING,
                gramsPerServingUnit = null,
                mlPerServingUnit = 240.0, // volume grounded bypass
                context = UsageContext.LOGGING,
                draft = ValidateFoodForUsageUseCase.DraftNutrients(
                    hasAnyRows = true,
                    hasAnyNumeric = true
                )
            )
        )

        assertEquals(FoodValidationResult.Ok, r)
    }

    @Test
    fun `draft - deterministic volume unit should not require gpsu`() {
        // Draft validates "servings-based completeness" using AmountInput.ByServings(1.0).
        // Deterministic volume unit should be volume-grounded even without gpsu.
        val r = useCase.executeDraft(
            ValidateFoodForUsageUseCase.DraftInput(
                servingUnit = ServingUnit.ML,
                gramsPerServingUnit = null,
                mlPerServingUnit = null,
                context = UsageContext.LOGGING,
                draft = ValidateFoodForUsageUseCase.DraftNutrients(
                    hasAnyRows = true,
                    hasAnyNumeric = true
                )
            )
        )

        assertEquals(FoodValidationResult.Ok, r)
    }

    private fun assertBlocked(result: FoodValidationResult, reason: FoodValidationResult.Reason) {
        assertTrue("Expected Blocked but was $result", result is FoodValidationResult.Blocked)
        assertEquals(reason, (result as FoodValidationResult.Blocked).reason)
    }
}