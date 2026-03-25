package com.example.adobongkangkong.domain.nutrition

import com.example.adobongkangkong.domain.model.ServingUnit

/**
 * Fully resolved serving grounding model.
 *
 * This is the single source of truth for:
 * - whether a serving can be grounded in grams
 * - whether a serving can be grounded in milliliters
 * - what evidence supports each grounding path
 * - what is blocked and why
 *
 * IMPORTANT:
 * - No density guessing
 * - No implicit conversions between grams and mL
 * - Deterministic + explicit bridges only
 */
data class ServingResolution(

    // -------------------------
    // Raw input snapshot (for debugging + testability)
    // -------------------------
    val servingSize: Double,
    val servingUnit: ServingUnit,
    val gramsPerServingUnit: Double?,
    val millilitersPerServingUnit: Double?,

    // -------------------------
    // Resolved outputs
    // -------------------------
    val gramsPerServing: Double?,
    val millilitersPerServing: Double?,

    // -------------------------
    // Evidence (HOW each path was resolved)
    // -------------------------
    val gramsEvidence: ResolutionEvidence?,
    val millilitersEvidence: ResolutionEvidence?,

    // -------------------------
    // Block reasons (WHY something is unavailable)
    // -------------------------
    val gramsBlockReason: BlockReason?,
    val millilitersBlockReason: BlockReason?
) {

    // -------------------------
    // Capabilities
    // -------------------------

    val supportsGramLogging: Boolean = gramsPerServing != null
    val supportsMilliliterLogging: Boolean = millilitersPerServing != null

    /**
     * User can always choose "servings", but nutrition computation may not be possible.
     */
    val supportsServingSelection: Boolean = servingSize > 0.0

    val supportsPer100GComputation: Boolean = supportsGramLogging
    val supportsPer100MLComputation: Boolean = supportsMilliliterLogging

    val isDualGrounded: Boolean =
        supportsGramLogging && supportsMilliliterLogging

    val hasAnyGrounding: Boolean =
        supportsGramLogging || supportsMilliliterLogging

    // -------------------------
    // Helper queries
    // -------------------------

    fun canComputeForPer100G(): Boolean = supportsPer100GComputation

    fun canComputeForPer100ML(): Boolean = supportsPer100MLComputation

    fun hasBlockingIssue(): Boolean =
        gramsBlockReason != null || millilitersBlockReason != null
}

/**
 * How a grounding path was derived.
 */
enum class ResolutionEvidence {
    DIRECT_MASS_UNIT,
    DIRECT_VOLUME_UNIT,
    GRAMS_BRIDGE,
    ML_BRIDGE
}

/**
 * Why a grounding path could not be resolved.
 */
enum class BlockReason {
    INVALID_SERVING_SIZE,
    NO_MASS_PATH,
    NO_VOLUME_PATH,
    INVALID_GRAMS_BRIDGE,
    INVALID_ML_BRIDGE
}