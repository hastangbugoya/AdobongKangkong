package com.example.adobongkangkong.domain.bridge

/**
 * Phase 1: Pure capability evaluator.
 *
 * This use case answers:
 * - what kind of conversion path exists from the known food facts?
 *
 * This use case does NOT answer:
 * - whether a specific flow is allowed to use that path
 * - whether the result may be persisted as food truth
 * - which UX affordance should be shown
 *
 * Important:
 * - COUNT_OR_CONTAINER units are NOT treated as interchangeable just because they share a basis.
 * - Direct serving/container mapping is only valid when the source unit is COUNT_OR_CONTAINER.
 * - Cross-basis fallback is represented here as ESTIMATED capability.
 *   Policy later decides whether that estimated path is allowed in the current flow.
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
        // Only MASS and VOLUME are safe standard unit systems.
        if (fromBasis == toBasis && fromBasis in setOf(UnitBasis.MASS, UnitBasis.VOLUME)) {
            return BridgeCapabilityResult(
                conversionClass = ConversionClass.INTRA_BASIS_STANDARD,
                confidence = BridgeConfidence.STRONG,
                source = BridgeSource.STANDARD_UNIT_CONVERSION,
                fallbackUsed = false,
                reason = "same_basis_standard_conversion"
            )
        }

        // 3. Direct food-specific serving/container mapping.
        //
        // This is ONLY for source units that are count/container-like, such as:
        // - serving
        // - piece
        // - slice
        // - bottle
        //
        // It must NOT capture true MASS <-> VOLUME requests like CUP -> G or ML -> G.
        if (fromBasis == UnitBasis.COUNT_OR_CONTAINER) {
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
        }

        val isCrossBasisMassVolume =
            (fromBasis == UnitBasis.MASS && toBasis == UnitBasis.VOLUME) ||
                    (fromBasis == UnitBasis.VOLUME && toBasis == UnitBasis.MASS)

        // 4. True cross-basis bridge logic only applies to MASS <-> VOLUME.
        if (isCrossBasisMassVolume) {
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

            if (input.hasUserConfirmedBridge) {
                return BridgeCapabilityResult(
                    conversionClass = ConversionClass.CROSS_BASIS_BRIDGED,
                    confidence = BridgeConfidence.STRONG,
                    source = BridgeSource.USER_CONFIRMED_BRIDGE,
                    fallbackUsed = false,
                    reason = "strong_bridge_from_user_confirmed_bridge"
                )
            }

            return BridgeCapabilityResult(
                conversionClass = ConversionClass.CROSS_BASIS_ESTIMATED,
                confidence = BridgeConfidence.ESTIMATED,
                source = BridgeSource.FALLBACK_1ML_EQ_1G,
                fallbackUsed = true,
                reason = "estimated_cross_basis_via_fallback_1ml_eq_1g"
            )
        }

        // 5. Everything else is unresolvable.
        // This includes COUNT_OR_CONTAINER -> COUNT_OR_CONTAINER with no explicit mapping.
        return BridgeCapabilityResult(
            conversionClass = ConversionClass.UNRESOLVABLE,
            confidence = BridgeConfidence.NONE,
            source = BridgeSource.NO_BRIDGE,
            fallbackUsed = false,
            reason = "cross_basis_unresolvable_no_bridge"
        )
    }
}