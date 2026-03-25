package com.example.adobongkangkong.domain.nutrition

import com.example.adobongkangkong.domain.model.ServingUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolveServingGroundingUseCaseTest {

    private val useCase = ResolveServingGroundingUseCase()

    @Test
    fun `lb resolves grams directly`() {
        val result = useCase.execute(
            servingSize = 1.0,
            servingUnit = ServingUnit.LB,
            gramsPerServingUnit = null,
            millilitersPerServingUnit = null
        )

        assertEquals(453.59237, result.gramsPerServing!!, 0.000001)
        assertNull(result.millilitersPerServing)

        assertEquals(ResolutionEvidence.DIRECT_MASS_UNIT, result.gramsEvidence)
        assertNull(result.millilitersEvidence)

        assertNull(result.gramsBlockReason)
        assertEquals(BlockReason.NO_VOLUME_PATH, result.millilitersBlockReason)

        assertTrue(result.supportsGramLogging)
        assertFalse(result.supportsMilliliterLogging)
        assertTrue(result.supportsPer100GComputation)
        assertFalse(result.supportsPer100MLComputation)
        assertFalse(result.isDualGrounded)
        assertTrue(result.hasAnyGrounding)
    }

    @Test
    fun `cup resolves milliliters directly`() {
        val result = useCase.execute(
            servingSize = 1.0,
            servingUnit = ServingUnit.CUP_US,
            gramsPerServingUnit = null,
            millilitersPerServingUnit = null
        )

        assertNull(result.gramsPerServing)
        assertEquals(240.0, result.millilitersPerServing!!, 0.000001)

        assertNull(result.gramsEvidence)
        assertEquals(ResolutionEvidence.DIRECT_VOLUME_UNIT, result.millilitersEvidence)

        assertEquals(BlockReason.NO_MASS_PATH, result.gramsBlockReason)
        assertNull(result.millilitersBlockReason)

        assertFalse(result.supportsGramLogging)
        assertTrue(result.supportsMilliliterLogging)
        assertFalse(result.supportsPer100GComputation)
        assertTrue(result.supportsPer100MLComputation)
        assertFalse(result.isDualGrounded)
        assertTrue(result.hasAnyGrounding)
    }

    @Test
    fun `cup with grams bridge resolves both paths`() {
        val result = useCase.execute(
            servingSize = 1.0,
            servingUnit = ServingUnit.CUP_US,
            gramsPerServingUnit = 180.0,
            millilitersPerServingUnit = null
        )

        assertEquals(180.0, result.gramsPerServing!!, 0.000001)
        assertEquals(240.0, result.millilitersPerServing!!, 0.000001)

        assertEquals(ResolutionEvidence.GRAMS_BRIDGE, result.gramsEvidence)
        assertEquals(ResolutionEvidence.DIRECT_VOLUME_UNIT, result.millilitersEvidence)

        assertNull(result.gramsBlockReason)
        assertNull(result.millilitersBlockReason)

        assertTrue(result.supportsGramLogging)
        assertTrue(result.supportsMilliliterLogging)
        assertTrue(result.supportsPer100GComputation)
        assertTrue(result.supportsPer100MLComputation)
        assertTrue(result.isDualGrounded)
        assertTrue(result.hasAnyGrounding)
    }

    @Test
    fun `bottle with milliliters bridge resolves volume via bridge`() {
        val result = useCase.execute(
            servingSize = 1.0,
            servingUnit = ServingUnit.BOTTLE,
            gramsPerServingUnit = null,
            millilitersPerServingUnit = 330.0
        )

        assertNull(result.gramsPerServing)
        assertEquals(330.0, result.millilitersPerServing!!, 0.000001)

        assertNull(result.gramsEvidence)
        assertEquals(ResolutionEvidence.ML_BRIDGE, result.millilitersEvidence)

        assertEquals(BlockReason.NO_MASS_PATH, result.gramsBlockReason)
        assertNull(result.millilitersBlockReason)

        assertFalse(result.supportsGramLogging)
        assertTrue(result.supportsMilliliterLogging)
        assertFalse(result.supportsPer100GComputation)
        assertTrue(result.supportsPer100MLComputation)
        assertFalse(result.isDualGrounded)
        assertTrue(result.hasAnyGrounding)
    }

    @Test
    fun `serving with no bridges blocks both paths`() {
        val result = useCase.execute(
            servingSize = 1.0,
            servingUnit = ServingUnit.SERVING,
            gramsPerServingUnit = null,
            millilitersPerServingUnit = null
        )

        assertNull(result.gramsPerServing)
        assertNull(result.millilitersPerServing)

        assertNull(result.gramsEvidence)
        assertNull(result.millilitersEvidence)

        assertEquals(BlockReason.NO_MASS_PATH, result.gramsBlockReason)
        assertEquals(BlockReason.NO_VOLUME_PATH, result.millilitersBlockReason)

        assertFalse(result.supportsGramLogging)
        assertFalse(result.supportsMillilitersLoggingCompat())
        assertFalse(result.supportsPer100GComputation)
        assertFalse(result.supportsPer100MLComputation)
        assertFalse(result.isDualGrounded)
        assertFalse(result.hasAnyGrounding)
    }

    @Test
    fun `invalid serving size blocks both paths`() {
        val result = useCase.execute(
            servingSize = 0.0,
            servingUnit = ServingUnit.G,
            gramsPerServingUnit = 10.0,
            millilitersPerServingUnit = 10.0
        )

        assertNull(result.gramsPerServing)
        assertNull(result.millilitersPerServing)

        assertNull(result.gramsEvidence)
        assertNull(result.millilitersEvidence)

        assertEquals(BlockReason.INVALID_SERVING_SIZE, result.gramsBlockReason)
        assertEquals(BlockReason.INVALID_SERVING_SIZE, result.millilitersBlockReason)

        assertFalse(result.supportsGramLogging)
        assertFalse(result.supportsMillilitersLoggingCompat())
        assertFalse(result.supportsPer100GComputation)
        assertFalse(result.supportsPer100MLComputation)
        assertFalse(result.isDualGrounded)
        assertFalse(result.hasAnyGrounding)
    }

    @Test
    fun `negative grams bridge produces invalid grams bridge block`() {
        val result = useCase.execute(
            servingSize = 1.0,
            servingUnit = ServingUnit.SERVING,
            gramsPerServingUnit = -25.0,
            millilitersPerServingUnit = null
        )

        assertNull(result.gramsPerServing)
        assertEquals(BlockReason.INVALID_GRAMS_BRIDGE, result.gramsBlockReason)

        assertNull(result.millilitersPerServing)
        assertEquals(BlockReason.NO_VOLUME_PATH, result.millilitersBlockReason)
    }

    @Test
    fun `negative milliliters bridge produces invalid milliliters bridge block`() {
        val result = useCase.execute(
            servingSize = 1.0,
            servingUnit = ServingUnit.SERVING,
            gramsPerServingUnit = null,
            millilitersPerServingUnit = -25.0
        )

        assertNull(result.millilitersPerServing)
        assertEquals(BlockReason.INVALID_ML_BRIDGE, result.millilitersBlockReason)

        assertNull(result.gramsPerServing)
        assertEquals(BlockReason.NO_MASS_PATH, result.gramsBlockReason)
    }

    /**
     * Temporary compatibility helper so the test reads clearly even if the property name changes later.
     */
    private fun ServingResolution.supportsMillilitersLoggingCompat(): Boolean {
        return supportsMilliliterLogging
    }
}