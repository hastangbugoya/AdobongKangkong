package com.example.adobongkangkong.domain.nutrition

import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.domain.model.ServingUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class ApplyEditedNutrientsUseCaseTest {

    private val apply = ApplyEditedNutrientsUseCase()
    private val resolve = ResolveServingGroundingUseCase()

    @Test
    fun `per 100g apply normalizes correctly`() {
        val displayed = mapOf(
            NutrientKey.CALORIES_KCAL to 100.0,
            NutrientKey.PROTEIN_G to 5.0
        )

        val resolution = resolve.execute(
            servingSize = 50.0,
            servingUnit = ServingUnit.G,
            gramsPerServingUnit = null,
            millilitersPerServingUnit = null
        )

        val result = apply.execute(
            displayedNutrients = displayed,
            basisType = BasisType.PER_100G,
            resolution = resolution
        )

        val success = result as ApplyEditedNutrientsUseCase.Result.Success

        assertEquals(200.0, success.canonicalNutrients[NutrientKey.CALORIES_KCAL]!!, 0.0001)
        assertEquals(10.0, success.canonicalNutrients[NutrientKey.PROTEIN_G]!!, 0.0001)
        assertEquals(
            ApplyEditedNutrientsUseCase.ComputationPath.PER_100G,
            success.usedPath
        )
    }

    @Test
    fun `per 100ml apply normalizes correctly`() {
        val displayed = mapOf(
            NutrientKey.CALORIES_KCAL to 100.0
        )

        val resolution = resolve.execute(
            servingSize = 200.0,
            servingUnit = ServingUnit.ML,
            gramsPerServingUnit = null,
            millilitersPerServingUnit = null
        )

        val result = apply.execute(
            displayedNutrients = displayed,
            basisType = BasisType.PER_100ML,
            resolution = resolution
        )

        val success = result as ApplyEditedNutrientsUseCase.Result.Success

        assertEquals(50.0, success.canonicalNutrients[NutrientKey.CALORIES_KCAL]!!, 0.0001)
        assertEquals(
            ApplyEditedNutrientsUseCase.ComputationPath.PER_100ML,
            success.usedPath
        )
    }

    @Test
    fun `per 100g blocks when no gram path`() {
        val displayed = mapOf(
            NutrientKey.CALORIES_KCAL to 100.0
        )

        val resolution = resolve.execute(
            servingSize = 1.0,
            servingUnit = ServingUnit.CUP_US,
            gramsPerServingUnit = null,
            millilitersPerServingUnit = null
        )

        val result = apply.execute(
            displayedNutrients = displayed,
            basisType = BasisType.PER_100G,
            resolution = resolution
        )

        val blocked = result as ApplyEditedNutrientsUseCase.Result.Blocked

        assertEquals(
            ApplyEditedNutrientsUseCase.BlockReason.NO_GRAM_PATH,
            blocked.reason
        )
    }

    @Test
    fun `per 100ml blocks when no ml path`() {
        val displayed = mapOf(
            NutrientKey.CALORIES_KCAL to 100.0
        )

        val resolution = resolve.execute(
            servingSize = 1.0,
            servingUnit = ServingUnit.LB,
            gramsPerServingUnit = null,
            millilitersPerServingUnit = null
        )

        val result = apply.execute(
            displayedNutrients = displayed,
            basisType = BasisType.PER_100ML,
            resolution = resolution
        )

        val blocked = result as ApplyEditedNutrientsUseCase.Result.Blocked

        assertEquals(
            ApplyEditedNutrientsUseCase.BlockReason.NO_ML_PATH,
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

        val result = apply.execute(
            displayedNutrients = emptyMap(),
            basisType = BasisType.PER_100G,
            resolution = resolution
        )

        val blocked = result as ApplyEditedNutrientsUseCase.Result.Blocked

        assertEquals(
            ApplyEditedNutrientsUseCase.BlockReason.NO_NUTRIENTS,
            blocked.reason
        )
    }

    @Test
    fun `cup with grams bridge allows per 100g apply`() {
        val displayed = mapOf(
            NutrientKey.CALORIES_KCAL to 360.0
        )

        val resolution = resolve.execute(
            servingSize = 1.0,
            servingUnit = ServingUnit.CUP_US,
            gramsPerServingUnit = 180.0,
            millilitersPerServingUnit = null
        )

        val result = apply.execute(
            displayedNutrients = displayed,
            basisType = BasisType.PER_100G,
            resolution = resolution
        )

        val success = result as ApplyEditedNutrientsUseCase.Result.Success

        assertEquals(200.0, success.canonicalNutrients[NutrientKey.CALORIES_KCAL]!!, 0.0001)
    }
}