package com.example.adobongkangkong.domain.bridge

/**
 * Phase 2: Applies flow + action policy to a previously evaluated capability result.
 *
 * This use case decides:
 * - whether the current flow may use the capability
 * - whether the result may be persisted as food truth
 * - whether warnings/prompts are required
 *
 * Invariants enforced here:
 * - ESTIMATED may never persist as food truth
 * - DISPLAY_ONLY must not hard-block like an editing/saving flow
 * - persistence depends on action + source, not merely STRONG confidence
 */
class EvaluateBridgePolicyUseCase {

    operator fun invoke(input: BridgePolicyInput): BridgeDecision {
        val capability = input.capability
        val flow = input.flow
        val action = input.action

        if (flow == BridgeFlow.DISPLAY_ONLY) {
            return handleDisplayOnly(capability = capability)
        }

        return when (capability.confidence) {
            BridgeConfidence.STRONG -> strongDecision(
                capability = capability,
                action = action
            )

            BridgeConfidence.ESTIMATED -> estimatedDecision(
                capability = capability,
                flow = flow
            )

            BridgeConfidence.NONE -> noneDecision(capability = capability)
        }
    }

    private fun strongDecision(
        capability: BridgeCapabilityResult,
        action: BridgeAction
    ): BridgeDecision {
        val persistableSources = setOf(
            BridgeSource.EXPLICIT_GRAMS_PER_SERVING_UNIT,
            BridgeSource.EXPLICIT_ML_PER_SERVING_UNIT,
            BridgeSource.EXPLICIT_MATCHED_SERVING_MASS_AND_VOLUME,
            BridgeSource.DERIVED_DENSITY_FROM_MATCHED_SERVING_DATA,
            BridgeSource.USER_CONFIRMED_BRIDGE
        )

        val isPersistableAsFoodTruth =
            action == BridgeAction.SAVE_FOOD_METADATA &&
                    capability.source in persistableSources

        return BridgeDecision(
            conversionClass = capability.conversionClass,
            confidence = capability.confidence,
            source = capability.source,
            isAllowed = true,
            isPersistableAsFoodTruth = isPersistableAsFoodTruth,
            requiresUserAction = false,
            warningLevel = BridgeWarningLevel.NONE,
            shouldShowEstimateBadge = false,
            shouldShowBlockingPrompt = false,
            shouldPromptForRealBridge = false,
            fallbackUsed = capability.fallbackUsed,
            reason = capability.reason
        )
    }

    private fun estimatedDecision(
        capability: BridgeCapabilityResult,
        flow: BridgeFlow
    ): BridgeDecision {
        return when (flow) {
            BridgeFlow.QUICK_ADD,
            BridgeFlow.FOOD_LOGGING_AD_HOC -> {
                BridgeDecision(
                    conversionClass = capability.conversionClass,
                    confidence = capability.confidence,
                    source = capability.source,
                    isAllowed = true,
                    isPersistableAsFoodTruth = false,
                    requiresUserAction = false,
                    warningLevel = BridgeWarningLevel.SOFT_WARNING,
                    shouldShowEstimateBadge = true,
                    shouldShowBlockingPrompt = false,
                    shouldPromptForRealBridge = false,
                    fallbackUsed = capability.fallbackUsed,
                    reason = capability.reason
                )
            }

            BridgeFlow.FOOD_EDITOR_VIEW -> {
                BridgeDecision(
                    conversionClass = capability.conversionClass,
                    confidence = capability.confidence,
                    source = capability.source,
                    isAllowed = true,
                    isPersistableAsFoodTruth = false,
                    requiresUserAction = false,
                    warningLevel = BridgeWarningLevel.INFO,
                    shouldShowEstimateBadge = true,
                    shouldShowBlockingPrompt = false,
                    shouldPromptForRealBridge = false,
                    fallbackUsed = capability.fallbackUsed,
                    reason = capability.reason
                )
            }

            else -> {
                BridgeDecision(
                    conversionClass = capability.conversionClass,
                    confidence = capability.confidence,
                    source = capability.source,
                    isAllowed = false,
                    isPersistableAsFoodTruth = false,
                    requiresUserAction = true,
                    warningLevel = BridgeWarningLevel.HARD_BLOCK,
                    shouldShowEstimateBadge = true,
                    shouldShowBlockingPrompt = true,
                    shouldPromptForRealBridge = true,
                    fallbackUsed = capability.fallbackUsed,
                    reason = "blocked_estimated_not_allowed_in_flow"
                )
            }
        }
    }

    private fun noneDecision(
        capability: BridgeCapabilityResult
    ): BridgeDecision {
        return BridgeDecision(
            conversionClass = capability.conversionClass,
            confidence = capability.confidence,
            source = capability.source,
            isAllowed = false,
            isPersistableAsFoodTruth = false,
            requiresUserAction = true,
            warningLevel = BridgeWarningLevel.HARD_BLOCK,
            shouldShowEstimateBadge = false,
            shouldShowBlockingPrompt = true,
            shouldPromptForRealBridge = true,
            fallbackUsed = capability.fallbackUsed,
            reason = "blocked_no_bridge"
        )
    }

    private fun handleDisplayOnly(
        capability: BridgeCapabilityResult
    ): BridgeDecision {
        return when (capability.confidence) {
            BridgeConfidence.STRONG -> BridgeDecision(
                conversionClass = capability.conversionClass,
                confidence = capability.confidence,
                source = capability.source,
                isAllowed = true,
                isPersistableAsFoodTruth = false,
                requiresUserAction = false,
                warningLevel = BridgeWarningLevel.NONE,
                shouldShowEstimateBadge = false,
                shouldShowBlockingPrompt = false,
                shouldPromptForRealBridge = false,
                fallbackUsed = capability.fallbackUsed,
                reason = capability.reason
            )

            BridgeConfidence.ESTIMATED -> BridgeDecision(
                conversionClass = capability.conversionClass,
                confidence = capability.confidence,
                source = capability.source,
                isAllowed = true,
                isPersistableAsFoodTruth = false,
                requiresUserAction = false,
                warningLevel = BridgeWarningLevel.INFO,
                shouldShowEstimateBadge = true,
                shouldShowBlockingPrompt = false,
                shouldPromptForRealBridge = false,
                fallbackUsed = capability.fallbackUsed,
                reason = capability.reason
            )

            BridgeConfidence.NONE -> BridgeDecision(
                conversionClass = capability.conversionClass,
                confidence = capability.confidence,
                source = capability.source,
                isAllowed = false,
                isPersistableAsFoodTruth = false,
                requiresUserAction = false,
                warningLevel = BridgeWarningLevel.INFO,
                shouldShowEstimateBadge = false,
                shouldShowBlockingPrompt = false,
                shouldPromptForRealBridge = false,
                fallbackUsed = capability.fallbackUsed,
                reason = "display_only_no_bridge"
            )
        }
    }
}