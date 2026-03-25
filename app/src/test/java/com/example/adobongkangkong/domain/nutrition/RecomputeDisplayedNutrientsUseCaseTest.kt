package com.example.adobongkangkong.domain.nutrition

import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.domain.model.ServingUnit
import org.junit.Assert.*
import org.junit.Test

class RecomputeDisplayedNutrientsUseCaseTest {

    private val recompute = RecomputeDisplayedNutrientsUseCase()
    private val resolve = ResolveServingGroundingUseCase()

    @Test
    fun `per 100g recompute scales correctly`() {
        val canonical = mapOf(
            NutrientKey.CALORIES_KCAL to 200.0,
            NutrientKey.PROTEIN_G to 10.0
        )

        val resolution = resolve.execute(
            servingSize = 50.0,
            servingUnit = ServingUnit.G,
            gramsPerServingUnit = null,
            millilitersPerServingUnit = null
        )

        val result = recompute.execute(
            canonicalNutrients = canonical,
            basisType = BasisType.PER_100G,
            resolution = resolution
        )

        val success = result as RecomputeDisplayedNutrientsUseCase.Result.Success

        assertEquals(100.0, success.nutrients[NutrientKey.CALORIES_KCAL]!!, 0.0001)
        assertEquals(5.0, success.nutrients[NutrientKey.PROTEIN_G]!!, 0.0001)
    }

    @Test
    fun `per 100ml recompute scales correctly`() {
        val canonical = mapOf(
            NutrientKey.CALORIES_KCAL to 50.0
        )

        val resolution = resolve.execute(
            servingSize = 200.0,
            servingUnit = ServingUnit.ML,
            gramsPerServingUnit = null,
            millilitersPerServingUnit = null
        )

        val result = recompute.execute(
            canonicalNutrients = canonical,
            basisType = BasisType.PER_100ML,
            resolution = resolution
        )

        val success = result as RecomputeDisplayedNutrientsUseCase.Result.Success

        assertEquals(100.0, success.nutrients[NutrientKey.CALORIES_KCAL]!!, 0.0001)
    }

    @Test
    fun `per 100g blocks when no gram path`() {
        val canonical = mapOf(
            NutrientKey.CALORIES_KCAL to 100.0
        )

        val resolution = resolve.execute(
            servingSize = 1.0,
            servingUnit = ServingUnit.CUP_US,
            gramsPerServingUnit = null,
            millilitersPerServingUnit = null
        )

        val result = recompute.execute(
            canonicalNutrients = canonical,
            basisType = BasisType.PER_100G,
            resolution = resolution
        )

        val blocked = result as RecomputeDisplayedNutrientsUseCase.Result.Blocked

        assertEquals(
            RecomputeDisplayedNutrientsUseCase.BlockReason.NO_GRAM_PATH,
            blocked.reason
        )
    }

    @Test
    fun `per 100ml blocks when no ml path`() {
        val canonical = mapOf(
            NutrientKey.CALORIES_KCAL to 100.0
        )

        val resolution = resolve.execute(
            servingSize = 1.0,
            servingUnit = ServingUnit.LB,
            gramsPerServingUnit = null,
            millilitersPerServingUnit = null
        )

        val result = recompute.execute(
            canonicalNutrients = canonical,
            basisType = BasisType.PER_100ML,
            resolution = resolution
        )

        val blocked = result as RecomputeDisplayedNutrientsUseCase.Result.Blocked

        assertEquals(
            RecomputeDisplayedNutrientsUseCase.BlockReason.NO_ML_PATH,
            blocked.reason
        )
    }

    @Test
    fun `empty nutrients blocks`() {
        val resolution = resolve.execute(
            servingSize = 100.0,
            servingUnit = ServingUnit.G,
            gramsPerServingUnit = null,
            millilitersPerServingUnit = null
        )

        val result = recompute.execute(
            canonicalNutrients = emptyMap(),
            basisType = BasisType.PER_100G,
            resolution = resolution
        )

        val blocked = result as RecomputeDisplayedNutrientsUseCase.Result.Blocked

        assertEquals(
            RecomputeDisplayedNutrientsUseCase.BlockReason.NO_NUTRIENTS,
            blocked.reason
        )
    }

    @Test
    fun `cup with grams bridge allows per 100g recompute`() {
        val canonical = mapOf(
            NutrientKey.CALORIES_KCAL to 200.0
        )

        val resolution = resolve.execute(
            servingSize = 1.0,
            servingUnit = ServingUnit.CUP_US,
            gramsPerServingUnit = 180.0,
            millilitersPerServingUnit = null
        )

        val result = recompute.execute(
            canonicalNutrients = canonical,
            basisType = BasisType.PER_100G,
            resolution = resolution
        )

        val success = result as RecomputeDisplayedNutrientsUseCase.Result.Success

        assertEquals(360.0, success.nutrients[NutrientKey.CALORIES_KCAL]!!, 0.0001)
    }
}