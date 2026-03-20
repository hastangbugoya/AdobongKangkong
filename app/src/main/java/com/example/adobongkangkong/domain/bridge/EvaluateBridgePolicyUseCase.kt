package com.example.adobongkangkong.domain.bridge

/**
 * Phase 2: Applies flow + action policy.
 */
class EvaluateBridgePolicyUseCase {

    operator fun invoke(input: BridgePolicyInput): BridgeDecision {

        val capability = input.capability

        val confidence = capability.confidence
        val flow = input.flow

        return when (confidence) {

            BridgeConfidence.STRONG -> {
                BridgeDecision(
                    conversionClass = capability.conversionClass,
                    confidence = confidence,
                    source = capability.source,
                    isAllowed = true,
                    isPersistableAsFoodTruth = true,
                    requiresUserAction = false,
                    warningLevel = BridgeWarningLevel.NONE,
                    shouldShowEstimateBadge = false,
                    shouldShowBlockingPrompt = false,
                    shouldPromptForRealBridge = false,
                    fallbackUsed = false,
                    reason = capability.reason
                )
            }

            BridgeConfidence.ESTIMATED -> {
                when (flow) {

                    BridgeFlow.QUICK_ADD,
                    BridgeFlow.FOOD_LOGGING_AD_HOC -> {
                        BridgeDecision(
                            conversionClass = capability.conversionClass,
                            confidence = confidence,
                            source = capability.source,
                            isAllowed = true,
                            isPersistableAsFoodTruth = false,
                            requiresUserAction = false,
                            warningLevel = BridgeWarningLevel.SOFT_WARNING,
                            shouldShowEstimateBadge = true,
                            shouldShowBlockingPrompt = false,
                            shouldPromptForRealBridge = false,
                            fallbackUsed = true,
                            reason = capability.reason
                        )
                    }

                    else -> {
                        BridgeDecision(
                            conversionClass = capability.conversionClass,
                            confidence = confidence,
                            source = capability.source,
                            isAllowed = false,
                            isPersistableAsFoodTruth = false,
                            requiresUserAction = true,
                            warningLevel = BridgeWarningLevel.HARD_BLOCK,
                            shouldShowEstimateBadge = true,
                            shouldShowBlockingPrompt = true,
                            shouldPromptForRealBridge = true,
                            fallbackUsed = true,
                            reason = "blocked_estimated_not_allowed_in_flow"
                        )
                    }
                }
            }

            BridgeConfidence.NONE -> {
                BridgeDecision(
                    conversionClass = capability.conversionClass,
                    confidence = confidence,
                    source = capability.source,
                    isAllowed = false,
                    isPersistableAsFoodTruth = false,
                    requiresUserAction = true,
                    warningLevel = BridgeWarningLevel.HARD_BLOCK,
                    shouldShowEstimateBadge = false,
                    shouldShowBlockingPrompt = true,
                    shouldPromptForRealBridge = true,
                    fallbackUsed = false,
                    reason = "blocked_no_bridge"
                )
            }
        }
    }
}