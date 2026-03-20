package com.example.adobongkangkong.domain.bridge

/**
 * Phase 1: Pure capability evaluator.
 *
 * NO flow logic here.
 */
class EvaluateBridgeCapabilityUseCase {

    operator fun invoke(input: BridgeCapabilityInput): BridgeCapabilityResult {

        val fromUnit = input.fromUnit
        val toUnit = input.toUnit

        val fromBasis = input.fromBasis
        val toBasis = input.toBasis

        // 1. Identity
        if (fromUnit == toUnit) {
            return BridgeCapabilityResult(
                conversionClass = ConversionClass.IDENTITY,
                confidence = BridgeConfidence.STRONG,
                source = BridgeSource.IDENTITY,
                fallbackUsed = false,
                reason = "identity"
            )
        }

        // 2. Same-basis standard conversion
        if (fromBasis == toBasis) {
            return BridgeCapabilityResult(
                conversionClass = ConversionClass.INTRA_BASIS_STANDARD,
                confidence = BridgeConfidence.STRONG,
                source = BridgeSource.STANDARD_UNIT_CONVERSION,
                fallbackUsed = false,
                reason = "same_basis_standard_conversion"
            )
        }

        // 3. Direct serving mapping
        if (toBasis == UnitBasis.MASS && input.gramsPerServingUnit != null) {
            return BridgeCapabilityResult(
                conversionClass = ConversionClass.INTRA_FOOD_SERVING_MAPPING,
                confidence = BridgeConfidence.STRONG,
                source = BridgeSource.EXPLICIT_GRAMS_PER_SERVING_UNIT,
                fallbackUsed = false,
                reason = "explicit_grams_per_serving_mapping"
            )
        }

        if (toBasis == UnitBasis.VOLUME && input.mlPerServingUnit != null) {
            return BridgeCapabilityResult(
                conversionClass = ConversionClass.INTRA_FOOD_SERVING_MAPPING,
                confidence = BridgeConfidence.STRONG,
                source = BridgeSource.EXPLICIT_ML_PER_SERVING_UNIT,
                fallbackUsed = false,
                reason = "explicit_ml_per_serving_mapping"
            )
        }

        val isCrossBasis = fromBasis != toBasis &&
                (fromBasis == UnitBasis.MASS || fromBasis == UnitBasis.VOLUME) &&
                (toBasis == UnitBasis.MASS || toBasis == UnitBasis.VOLUME)

        if (isCrossBasis) {

            // 4. Strong matched serving bridge
            if (
                input.gramsPerServingUnit != null &&
                input.mlPerServingUnit != null &&
                input.hasMatchedServingMeaning
            ) {
                return BridgeCapabilityResult(
                    conversionClass = ConversionClass.CROSS_BASIS_BRIDGED,
                    confidence = BridgeConfidence.STRONG,
                    source = BridgeSource.EXPLICIT_MATCHED_SERVING_MASS_AND_VOLUME,
                    fallbackUsed = false,
                    reason = "strong_bridge_from_matched_serving_mass_and_volume"
                )
            }

            // 5. User confirmed
            if (input.hasUserConfirmedBridge) {
                return BridgeCapabilityResult(
                    conversionClass = ConversionClass.CROSS_BASIS_BRIDGED,
                    confidence = BridgeConfidence.STRONG,
                    source = BridgeSource.USER_CONFIRMED_BRIDGE,
                    fallbackUsed = false,
                    reason = "strong_bridge_from_user_confirmed_bridge"
                )
            }

            // 6. Estimated fallback
            return BridgeCapabilityResult(
                conversionClass = ConversionClass.CROSS_BASIS_ESTIMATED,
                confidence = BridgeConfidence.ESTIMATED,
                source = BridgeSource.FALLBACK_1ML_EQ_1G,
                fallbackUsed = true,
                reason = "estimated_cross_basis_via_fallback_1ml_eq_1g"
            )
        }

        // 7. None
        return BridgeCapabilityResult(
            conversionClass = ConversionClass.UNRESOLVABLE,
            confidence = BridgeConfidence.NONE,
            source = BridgeSource.NO_BRIDGE,
            fallbackUsed = false,
            reason = "cross_basis_unresolvable_no_bridge"
        )
    }
}