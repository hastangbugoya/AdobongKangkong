package com.example.adobongkangkong.domain.bridge

import com.example.adobongkangkong.domain.model.ServingUnit

/**
 * Bridge system core enums and models.
 *
 * This file contains ONLY data structures.
 * No logic.
 */

enum class UnitBasis {
    MASS,
    VOLUME,
    COUNT_OR_CONTAINER,
    UNKNOWN
}

enum class ConversionClass {
    IDENTITY,
    INTRA_BASIS_STANDARD,
    INTRA_FOOD_SERVING_MAPPING,
    CROSS_BASIS_BRIDGED,
    CROSS_BASIS_ESTIMATED,
    UNRESOLVABLE
}

enum class BridgeConfidence {
    STRONG,
    ESTIMATED,
    NONE
}

enum class BridgeSource {
    IDENTITY,
    STANDARD_UNIT_CONVERSION,
    EXPLICIT_GRAMS_PER_SERVING_UNIT,
    EXPLICIT_ML_PER_SERVING_UNIT,
    EXPLICIT_MATCHED_SERVING_MASS_AND_VOLUME,
    DERIVED_DENSITY_FROM_MATCHED_SERVING_DATA,
    USER_CONFIRMED_BRIDGE,
    FALLBACK_1ML_EQ_1G,
    NO_BRIDGE
}

enum class BridgeFlow {
    QUICK_ADD,
    FOOD_EDITOR_VIEW,
    FOOD_EDITOR_SAVE,
    RECIPE_BUILDER,
    FOOD_LOGGING_SAVED_FOOD,
    FOOD_LOGGING_AD_HOC,
    DISPLAY_ONLY
}

enum class BridgeAction {
    DISPLAY_CONVERTED_AMOUNT,
    COMPUTE_LOG_GRAMS,
    SAVE_FOOD_METADATA,
    SET_PREFERRED_LOGGING_UNIT,
    COMPUTE_RECIPE_INGREDIENT_MASS,
    SHOW_UI_PREVIEW
}

enum class BridgeWarningLevel {
    NONE,
    INFO,
    SOFT_WARNING,
    HARD_BLOCK
}

/**
 * Phase 1 input
 */
data class BridgeCapabilityInput(
    val fromUnit: ServingUnit,
    val toUnit: ServingUnit,
    val fromBasis: UnitBasis,
    val toBasis: UnitBasis,
    val gramsPerServingUnit: Double?,
    val mlPerServingUnit: Double?,
    val hasMatchedServingMeaning: Boolean,
    val hasUserConfirmedBridge: Boolean
)

/**
 * Phase 1 output
 */
data class BridgeCapabilityResult(
    val conversionClass: ConversionClass,
    val confidence: BridgeConfidence,
    val source: BridgeSource,
    val fallbackUsed: Boolean,
    val reason: String
)

/**
 * Phase 2 input
 */
data class BridgePolicyInput(
    val flow: BridgeFlow,
    val action: BridgeAction,
    val capability: BridgeCapabilityResult
)

/**
 * Final decision
 */
data class BridgeDecision(
    val conversionClass: ConversionClass,
    val confidence: BridgeConfidence,
    val source: BridgeSource,
    val isAllowed: Boolean,
    val isPersistableAsFoodTruth: Boolean,
    val requiresUserAction: Boolean,
    val warningLevel: BridgeWarningLevel,
    val shouldShowEstimateBadge: Boolean,
    val shouldShowBlockingPrompt: Boolean,
    val shouldPromptForRealBridge: Boolean,
    val fallbackUsed: Boolean,
    val reason: String
)