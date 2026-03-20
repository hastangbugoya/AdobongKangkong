package com.example.adobongkangkong.domain.bridge

/**
 * Facade: combines capability + policy.
 */
class MakeBridgeDecisionUseCase(
    private val capability: EvaluateBridgeCapabilityUseCase,
    private val policy: EvaluateBridgePolicyUseCase,
    private val basisResolver: BridgeBasisResolver
) {

    operator fun invoke(
        fromUnit: com.example.adobongkangkong.domain.model.ServingUnit,
        toUnit: com.example.adobongkangkong.domain.model.ServingUnit,
        gramsPerServingUnit: Double?,
        mlPerServingUnit: Double?,
        hasMatchedServingMeaning: Boolean,
        hasUserConfirmedBridge: Boolean,
        flow: BridgeFlow,
        action: BridgeAction
    ): BridgeDecision {

        val capabilityResult = capability(
            BridgeCapabilityInput(
                fromUnit = fromUnit,
                toUnit = toUnit,
                fromBasis = basisResolver.basisOf(fromUnit),
                toBasis = basisResolver.basisOf(toUnit),
                gramsPerServingUnit = gramsPerServingUnit,
                mlPerServingUnit = mlPerServingUnit,
                hasMatchedServingMeaning = hasMatchedServingMeaning,
                hasUserConfirmedBridge = hasUserConfirmedBridge
            )
        )

        return policy(
            BridgePolicyInput(
                flow = flow,
                action = action,
                capability = capabilityResult
            )
        )
    }
}