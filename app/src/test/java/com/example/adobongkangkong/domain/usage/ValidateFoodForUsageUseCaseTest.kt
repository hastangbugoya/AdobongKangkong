package com.example.adobongkangkong.domain.usage

import com.example.adobongkangkong.domain.logging.model.AmountInput
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.nutrition.NutrientMap
import com.example.adobongkangkong.domain.recipes.FoodNutritionSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidateFoodForUsageUseCasePersistedTest {

    private val useCase = ValidateFoodForUsageUseCase()

    // -------------------------------------------------------------------------
    // Snapshot / nutrient basis gates
    // -------------------------------------------------------------------------

    @Test
    fun `persisted - snapshot null blocks as MissingSnapshot (LOGGING)`() {
        val r = useCase.execute(
            input(
                servingUnit = ServingUnit.SERVING,
                amountInput = AmountInput.ByServings(1.0),
                context = UsageContext.LOGGING,
                snapshot = null
            )
        )

        assertBlocked(r, FoodValidationResult.Reason.MissingSnapshot)
        assertMessageContains(r, "cannot be logged")
    }

    @Test
    fun `persisted - snapshot null blocks as MissingSnapshot (RECIPE)`() {
        val r = useCase.execute(
            input(
                servingUnit = ServingUnit.SERVING,
                amountInput = AmountInput.ByServings(1.0),
                context = UsageContext.RECIPE,
                snapshot = null
            )
        )

        assertBlocked(r, FoodValidationResult.Reason.MissingSnapshot)
        assertMessageContains(r, "recipes")
    }

    @Test
    fun `persisted - snapshot exists but both bases null blocks as MissingNutrients`() {
        val r = useCase.execute(
            input(
                servingUnit = ServingUnit.SERVING,
                amountInput = AmountInput.ByServings(1.0),
                snapshot = FoodNutritionSnapshot(
                    foodId = 1L,
                    gramsPerServingUnit = null,
                    mlPerServingUnit = null,
                    nutrientsPerGram = null,
                    nutrientsPerMilliliter = null
                )
            )
        )
        assertBlocked(r, FoodValidationResult.Reason.MissingNutrients)
    }

    @Test
    fun `persisted - snapshot bases present but empty maps blocks as MissingNutrients`() {
        val r = useCase.execute(
            input(
                servingUnit = ServingUnit.G,
                amountInput = AmountInput.ByGrams(10.0),
                snapshot = FoodNutritionSnapshot(
                    foodId = 1L,
                    gramsPerServingUnit = null,
                    mlPerServingUnit = null,
                    nutrientsPerGram = NutrientMap.EMPTY,       // empty
                    nutrientsPerMilliliter = NutrientMap.EMPTY  // empty
                )
            )
        )
        assertBlocked(r, FoodValidationResult.Reason.MissingNutrients)
    }

    @Test
    fun `persisted - mass basis present but all values zero blocks as AllNutrientsZero`() {
        val r = useCase.execute(
            input(
                servingUnit = ServingUnit.G,
                amountInput = AmountInput.ByGrams(10.0),
                snapshot = FoodNutritionSnapshot(
                    foodId = 1L,
                    gramsPerServingUnit = null,
                    mlPerServingUnit = null,
                    nutrientsPerGram = nutrientMapOf("TEST" to 0.0),
                    nutrientsPerMilliliter = null
                )
            )
        )
        assertBlocked(r, FoodValidationResult.Reason.AllNutrientsZero)
    }

    @Test
    fun `persisted - volume basis present but all values zero blocks as AllNutrientsZero`() {
        val r = useCase.execute(
            input(
                servingUnit = ServingUnit.ML,
                amountInput = AmountInput.ByServings(1.0),
                snapshot = FoodNutritionSnapshot(
                    foodId = 2L,
                    gramsPerServingUnit = null,
                    mlPerServingUnit = null,
                    nutrientsPerGram = null,
                    nutrientsPerMilliliter = nutrientMapOf("TEST" to 0.0)
                )
            )
        )
        assertBlocked(r, FoodValidationResult.Reason.AllNutrientsZero)
    }

    @Test
    fun `persisted - mass basis nonzero passes`() {
        val r = useCase.execute(
            input(
                servingUnit = ServingUnit.G,
                amountInput = AmountInput.ByGrams(1.0),
                snapshot = snapshotMassOnlyNonZero()
            )
        )
        assertEquals(FoodValidationResult.Ok, r)
    }

    @Test
    fun `persisted - volume basis nonzero passes`() {
        val r = useCase.execute(
            input(
                servingUnit = ServingUnit.ML,
                amountInput = AmountInput.ByServings(1.0),
                snapshot = snapshotVolOnlyNonZero()
            )
        )
        assertEquals(FoodValidationResult.Ok, r)
    }

    // -------------------------------------------------------------------------
    // Serving grounding gate (MissingGramsPerServing)
    // -------------------------------------------------------------------------

    @Test
    fun `persisted - servings + containerish unit + missing gpsu + not volume grounded blocks MissingGramsPerServing`() {
        val r = useCase.execute(
            input(
                servingUnit = ServingUnit.SERVING, // likely requires grams bridge
                gramsPerServingUnit = null,
                mlPerServingUnit = null,
                amountInput = AmountInput.ByServings(1.0),
                snapshot = snapshotMassOnlyNonZero()
            )
        )
        assertBlocked(r, FoodValidationResult.Reason.MissingGramsPerServing)
    }

    @Test
    fun `persisted - servings + containerish unit + gpsu less than or equal to 0 blocks MissingGramsPerServing`() {
        val r = useCase.execute(
            input(
                servingUnit = ServingUnit.SERVING,
                gramsPerServingUnit = 0.0,
                mlPerServingUnit = null,
                amountInput = AmountInput.ByServings(1.0),
                snapshot = snapshotMassOnlyNonZero()
            )
        )
        assertBlocked(r, FoodValidationResult.Reason.MissingGramsPerServing)
    }

    @Test
    fun `persisted - servings + containerish unit + ml bridge present avoids MissingGramsPerServing`() {
        // isVolumeGrounded=true should bypass MissingGramsPerServing
        val r = useCase.execute(
            input(
                servingUnit = ServingUnit.SERVING,
                gramsPerServingUnit = null,
                mlPerServingUnit = 240.0,
                amountInput = AmountInput.ByServings(1.0),
                snapshot = snapshotVolOnlyNonZero()
            )
        )
        // For volume-only + servings, we also require mL computable; ml bridge satisfies that too.
        assertEquals(FoodValidationResult.Ok, r)
    }

    @Test
    fun `persisted - servings + containerish unit + ml bridge less than or equal to 0 does NOT count as volume grounded`() {
        val r = useCase.execute(
            input(
                servingUnit = ServingUnit.SERVING,
                gramsPerServingUnit = null,
                mlPerServingUnit = 0.0,
                amountInput = AmountInput.ByServings(1.0),
                snapshot = snapshotMassOnlyNonZero()
            )
        )
        assertBlocked(r, FoodValidationResult.Reason.MissingGramsPerServing)
    }

    // -------------------------------------------------------------------------
    // Volume-only basis compatibility
    // -------------------------------------------------------------------------

    @Test
    fun `persisted - volume-only nutrients + grams input blocks BasisMismatchVolumeNeedsServings`() {
        val r = useCase.execute(
            input(
                servingUnit = ServingUnit.ML,
                amountInput = AmountInput.ByGrams(100.0),
                snapshot = snapshotVolOnlyNonZero()
            )
        )
        assertBlocked(r, FoodValidationResult.Reason.BasisMismatchVolumeNeedsServings)
    }

    @Test
    fun `persisted - volume-only nutrients + servings input but cannot compute mL blocks MissingMlPerServing`() {
        // To reach MissingMlPerServing, we must avoid being blocked earlier by MissingGramsPerServing.
        // So we provide a gpsu to pass serving-grounding, but no ml bridge and non-volume serving unit.
        val r = useCase.execute(
            input(
                servingUnit = ServingUnit.SERVING,   // not deterministic volume
                gramsPerServingUnit = 10.0,          // pass grounding
                mlPerServingUnit = null,             // still cannot compute mL
                amountInput = AmountInput.ByServings(1.0),
                snapshot = snapshotVolOnlyNonZero()
            )
        )
        assertBlocked(r, FoodValidationResult.Reason.MissingMlPerServing)
    }

    @Test
    fun `persisted - volume-only nutrients + servings input with deterministic volume unit passes`() {
        val r = useCase.execute(
            input(
                servingUnit = ServingUnit.ML,
                amountInput = AmountInput.ByServings(1.0),
                snapshot = snapshotVolOnlyNonZero()
            )
        )
        assertEquals(FoodValidationResult.Ok, r)
    }

    @Test
    fun `persisted - volume-only nutrients + servings input with mlPerServingUnit bridge passes`() {
        val r = useCase.execute(
            input(
                servingUnit = ServingUnit.SERVING,
                gramsPerServingUnit = null,
                mlPerServingUnit = 355.0,
                amountInput = AmountInput.ByServings(1.0),
                snapshot = snapshotVolOnlyNonZero()
            )
        )
        assertEquals(FoodValidationResult.Ok, r)
    }

    // -------------------------------------------------------------------------
    // Mass-only basis compatibility
    // -------------------------------------------------------------------------

    @Test
    fun `persisted - mass-only nutrients + grams input passes`() {
        val r = useCase.execute(
            input(
                servingUnit = ServingUnit.G,
                amountInput = AmountInput.ByGrams(50.0),
                snapshot = snapshotMassOnlyNonZero()
            )
        )
        assertEquals(FoodValidationResult.Ok, r)
    }

    @Test
    fun `persisted - mass-only nutrients + servings input with servingUnit G and gpsu null passes`() {
        // validateBasisCompatibility() explicitly allows servingUnit == G as computable
        val r = useCase.execute(
            input(
                servingUnit = ServingUnit.G,
                gramsPerServingUnit = null,
                mlPerServingUnit = null,
                amountInput = AmountInput.ByServings(1.0),
                snapshot = snapshotMassOnlyNonZero()
            )
        )
        assertEquals(FoodValidationResult.Ok, r)
    }

    @Test
    fun `persisted - mass-only nutrients + servings input with deterministic mass unit passes`() {
        // Should pass because canComputeGrams includes servingUnit.isMassUnit().
        // Pick a mass unit you already use in app.
        val r = useCase.execute(
            input(
                servingUnit = ServingUnit.OZ,
                gramsPerServingUnit = null,
                mlPerServingUnit = null,
                amountInput = AmountInput.ByServings(1.0),
                snapshot = snapshotMassOnlyNonZero()
            )
        )
        assertEquals(FoodValidationResult.Ok, r)
    }

    @Test
    fun `persisted - mass-only nutrients + servings input but servingUnit is volume and gpsu missing blocks BasisMismatchMassNeedsGrams`() {
        // This configuration avoids MissingGramsPerServing because a deterministic volume unit
        // typically does not "require grams per serving", so it reaches basis compatibility.
        val r = useCase.execute(
            input(
                servingUnit = ServingUnit.ML,
                gramsPerServingUnit = null,
                mlPerServingUnit = null,
                amountInput = AmountInput.ByServings(1.0),
                snapshot = snapshotMassOnlyNonZero()
            )
        )
        assertBlocked(r, FoodValidationResult.Reason.BasisMismatchMassNeedsGrams)
    }

    // -------------------------------------------------------------------------
    // Both bases present
    // -------------------------------------------------------------------------

    @Test
    fun `persisted - both bases present passes for grams`() {
        val r = useCase.execute(
            input(
                servingUnit = ServingUnit.G,
                amountInput = AmountInput.ByGrams(25.0),
                snapshot = snapshotBothNonZero()
            )
        )
        assertEquals(FoodValidationResult.Ok, r)
    }

    @Test
    fun `persisted - both bases present passes for servings when grounding satisfied`() {
        val r = useCase.execute(
            input(
                servingUnit = ServingUnit.SERVING,
                gramsPerServingUnit = 30.0,
                mlPerServingUnit = null,
                amountInput = AmountInput.ByServings(1.0),
                snapshot = snapshotBothNonZero()
            )
        )
        assertEquals(FoodValidationResult.Ok, r)
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun input(
        servingUnit: ServingUnit,
        gramsPerServingUnit: Double? = null,
        mlPerServingUnit: Double? = null,
        amountInput: AmountInput,
        context: UsageContext = UsageContext.LOGGING,
        snapshot: FoodNutritionSnapshot?
    ): ValidateFoodForUsageUseCase.PersistedInput =
        ValidateFoodForUsageUseCase.PersistedInput(
            servingUnit = servingUnit,
            gramsPerServingUnit = gramsPerServingUnit,
            mlPerServingUnit = mlPerServingUnit,
            amountInput = amountInput,
            context = context,
            snapshot = snapshot
        )

    private fun snapshotMassOnlyNonZero(): FoodNutritionSnapshot =
        FoodNutritionSnapshot(
            foodId = 1L,
            gramsPerServingUnit = null,
            mlPerServingUnit = null,
            nutrientsPerGram = nutrientMapOf("TEST" to 1.0),
            nutrientsPerMilliliter = null
        )

    private fun snapshotVolOnlyNonZero(): FoodNutritionSnapshot =
        FoodNutritionSnapshot(
            foodId = 2L,
            gramsPerServingUnit = null,
            mlPerServingUnit = null,
            nutrientsPerGram = null,
            nutrientsPerMilliliter = nutrientMapOf("TEST" to 1.0)
        )

    private fun snapshotBothNonZero(): FoodNutritionSnapshot =
        FoodNutritionSnapshot(
            foodId = 3L,
            gramsPerServingUnit = null,
            mlPerServingUnit = null,
            nutrientsPerGram = nutrientMapOf("TEST" to 1.0),
            nutrientsPerMilliliter = nutrientMapOf("TEST" to 1.0)
        )

    private fun nutrientMapOf(vararg pairs: Pair<String, Double>): NutrientMap =
        NutrientMap(pairs.associate { (code, v) -> NutrientKey(code) to v })

    private fun assertBlocked(result: FoodValidationResult, reason: FoodValidationResult.Reason) {
        assertTrue("Expected Blocked but was $result", result is FoodValidationResult.Blocked)
        assertEquals(reason, (result as FoodValidationResult.Blocked).reason)
    }

    private fun assertMessageContains(result: FoodValidationResult, needle: String) {
        assertTrue("Expected Blocked but was $result", result is FoodValidationResult.Blocked)
        val msg = (result as FoodValidationResult.Blocked).message
        assertTrue("Expected message to contain '$needle' but was '$msg'", msg.contains(needle, ignoreCase = true))
    }
}