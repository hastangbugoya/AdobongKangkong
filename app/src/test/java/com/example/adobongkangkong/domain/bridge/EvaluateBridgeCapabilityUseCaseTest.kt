package com.example.adobongkangkong.domain.bridge

import com.example.adobongkangkong.domain.model.ServingUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EvaluateBridgeCapabilityUseCaseTest {

    private val subject = EvaluateBridgeCapabilityUseCase()

    @Test
    fun `identity returns strong identity`() {
        val result = subject(
            BridgeCapabilityInput(
                fromUnit = ServingUnit.G,
                toUnit = ServingUnit.G,
                fromBasis = UnitBasis.MASS,
                toBasis = UnitBasis.MASS,
                gramsPerServingUnit = null,
                mlPerServingUnit = null,
                hasMatchedServingMeaning = false,
                hasUserConfirmedBridge = false
            )
        )

        assertEquals(ConversionClass.IDENTITY, result.conversionClass)
        assertEquals(BridgeConfidence.STRONG, result.confidence)
        assertEquals(BridgeSource.IDENTITY, result.source)
        assertFalse(result.fallbackUsed)
        assertEquals("identity", result.reason)
    }

    @Test
    fun `same basis mass conversion returns strong standard conversion`() {
        val result = subject(
            BridgeCapabilityInput(
                fromUnit = ServingUnit.G,
                toUnit = ServingUnit.OZ,
                fromBasis = UnitBasis.MASS,
                toBasis = UnitBasis.MASS,
                gramsPerServingUnit = null,
                mlPerServingUnit = null,
                hasMatchedServingMeaning = false,
                hasUserConfirmedBridge = false
            )
        )

        assertEquals(ConversionClass.INTRA_BASIS_STANDARD, result.conversionClass)
        assertEquals(BridgeConfidence.STRONG, result.confidence)
        assertEquals(BridgeSource.STANDARD_UNIT_CONVERSION, result.source)
        assertFalse(result.fallbackUsed)
        assertEquals("same_basis_standard_conversion", result.reason)
    }

    @Test
    fun `same basis volume conversion returns strong standard conversion`() {
        val result = subject(
            BridgeCapabilityInput(
                fromUnit = ServingUnit.TBSP,
                toUnit = ServingUnit.ML,
                fromBasis = UnitBasis.VOLUME,
                toBasis = UnitBasis.VOLUME,
                gramsPerServingUnit = null,
                mlPerServingUnit = null,
                hasMatchedServingMeaning = false,
                hasUserConfirmedBridge = false
            )
        )

        assertEquals(ConversionClass.INTRA_BASIS_STANDARD, result.conversionClass)
        assertEquals(BridgeConfidence.STRONG, result.confidence)
        assertEquals(BridgeSource.STANDARD_UNIT_CONVERSION, result.source)
        assertFalse(result.fallbackUsed)
        assertEquals("same_basis_standard_conversion", result.reason)
    }

    @Test
    fun `direct serving to mass mapping returns explicit grams serving mapping`() {
        val result = subject(
            BridgeCapabilityInput(
                fromUnit = ServingUnit.SERVING,
                toUnit = ServingUnit.G,
                fromBasis = UnitBasis.COUNT_OR_CONTAINER,
                toBasis = UnitBasis.MASS,
                gramsPerServingUnit = 100.0,
                mlPerServingUnit = null,
                hasMatchedServingMeaning = false,
                hasUserConfirmedBridge = false
            )
        )

        assertEquals(ConversionClass.INTRA_FOOD_SERVING_MAPPING, result.conversionClass)
        assertEquals(BridgeConfidence.STRONG, result.confidence)
        assertEquals(BridgeSource.EXPLICIT_GRAMS_PER_SERVING_UNIT, result.source)
        assertFalse(result.fallbackUsed)
        assertEquals("explicit_grams_per_serving_mapping", result.reason)
    }

    @Test
    fun `direct serving to volume mapping returns explicit ml serving mapping`() {
        val result = subject(
            BridgeCapabilityInput(
                fromUnit = ServingUnit.SERVING,
                toUnit = ServingUnit.ML,
                fromBasis = UnitBasis.COUNT_OR_CONTAINER,
                toBasis = UnitBasis.VOLUME,
                gramsPerServingUnit = null,
                mlPerServingUnit = 240.0,
                hasMatchedServingMeaning = false,
                hasUserConfirmedBridge = false
            )
        )

        assertEquals(ConversionClass.INTRA_FOOD_SERVING_MAPPING, result.conversionClass)
        assertEquals(BridgeConfidence.STRONG, result.confidence)
        assertEquals(BridgeSource.EXPLICIT_ML_PER_SERVING_UNIT, result.source)
        assertFalse(result.fallbackUsed)
        assertEquals("explicit_ml_per_serving_mapping", result.reason)
    }

    @Test
    fun `cross basis with matched serving mass and volume returns strong bridge`() {
        val result = subject(
            BridgeCapabilityInput(
                fromUnit = ServingUnit.CUP_US,
                toUnit = ServingUnit.G,
                fromBasis = UnitBasis.VOLUME,
                toBasis = UnitBasis.MASS,
                gramsPerServingUnit = 250.0,
                mlPerServingUnit = 240.0,
                hasMatchedServingMeaning = true,
                hasUserConfirmedBridge = false
            )
        )

        assertEquals(ConversionClass.CROSS_BASIS_BRIDGED, result.conversionClass)
        assertEquals(BridgeConfidence.STRONG, result.confidence)
        assertEquals(BridgeSource.EXPLICIT_MATCHED_SERVING_MASS_AND_VOLUME, result.source)
        assertFalse(result.fallbackUsed)
        assertEquals("strong_bridge_from_matched_serving_mass_and_volume", result.reason)
    }

    @Test
    fun `cross basis with user confirmed bridge returns strong bridge`() {
        val result = subject(
            BridgeCapabilityInput(
                fromUnit = ServingUnit.ML,
                toUnit = ServingUnit.G,
                fromBasis = UnitBasis.VOLUME,
                toBasis = UnitBasis.MASS,
                gramsPerServingUnit = null,
                mlPerServingUnit = null,
                hasMatchedServingMeaning = false,
                hasUserConfirmedBridge = true
            )
        )

        assertEquals(ConversionClass.CROSS_BASIS_BRIDGED, result.conversionClass)
        assertEquals(BridgeConfidence.STRONG, result.confidence)
        assertEquals(BridgeSource.USER_CONFIRMED_BRIDGE, result.source)
        assertFalse(result.fallbackUsed)
        assertEquals("strong_bridge_from_user_confirmed_bridge", result.reason)
    }

    @Test
    fun `cross basis without strong bridge returns estimated fallback`() {
        val result = subject(
            BridgeCapabilityInput(
                fromUnit = ServingUnit.ML,
                toUnit = ServingUnit.G,
                fromBasis = UnitBasis.VOLUME,
                toBasis = UnitBasis.MASS,
                gramsPerServingUnit = null,
                mlPerServingUnit = null,
                hasMatchedServingMeaning = false,
                hasUserConfirmedBridge = false
            )
        )

        assertEquals(ConversionClass.CROSS_BASIS_ESTIMATED, result.conversionClass)
        assertEquals(BridgeConfidence.ESTIMATED, result.confidence)
        assertEquals(BridgeSource.FALLBACK_1ML_EQ_1G, result.source)
        assertTrue(result.fallbackUsed)
        assertEquals("estimated_cross_basis_via_fallback_1ml_eq_1g", result.reason)
    }

    @Test
    fun `cross basis with mismatched serving meaning does not return strong bridge`() {
        val result = subject(
            BridgeCapabilityInput(
                fromUnit = ServingUnit.CUP_US,
                toUnit = ServingUnit.G,
                fromBasis = UnitBasis.VOLUME,
                toBasis = UnitBasis.MASS,
                gramsPerServingUnit = 200.0,
                mlPerServingUnit = 250.0,
                hasMatchedServingMeaning = false,
                hasUserConfirmedBridge = false
            )
        )

        assertEquals(ConversionClass.CROSS_BASIS_ESTIMATED, result.conversionClass)
        assertEquals(BridgeConfidence.ESTIMATED, result.confidence)
        assertEquals(BridgeSource.FALLBACK_1ML_EQ_1G, result.source)
        assertTrue(result.fallbackUsed)
        assertEquals("estimated_cross_basis_via_fallback_1ml_eq_1g", result.reason)
    }

    @Test
    fun `count to count without direct mapping is unresolvable`() {
        val result = subject(
            BridgeCapabilityInput(
                fromUnit = ServingUnit.SERVING,
                toUnit = ServingUnit.PIECE,
                fromBasis = UnitBasis.COUNT_OR_CONTAINER,
                toBasis = UnitBasis.COUNT_OR_CONTAINER,
                gramsPerServingUnit = null,
                mlPerServingUnit = null,
                hasMatchedServingMeaning = false,
                hasUserConfirmedBridge = false
            )
        )

        assertEquals(ConversionClass.UNRESOLVABLE, result.conversionClass)
        assertEquals(BridgeConfidence.NONE, result.confidence)
        assertEquals(BridgeSource.NO_BRIDGE, result.source)
        assertFalse(result.fallbackUsed)
        assertEquals("cross_basis_unresolvable_no_bridge", result.reason)
    }
}