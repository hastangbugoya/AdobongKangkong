package com.example.adobongkangkong.domain.nutrition

import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.model.toGrams
import com.example.adobongkangkong.domain.model.toMilliliters

/**
 * Resolves a serving definition into all deterministic grounding capabilities.
 *
 * Rules:
 * - Resolve grams and milliliters independently
 * - Prefer direct deterministic unit grounding over user bridge grounding
 * - Never guess density
 * - Never derive grams from mL or mL from grams unless an explicit bridge exists
 * - Zero/negative bridge values are treated as invalid
 */
class ResolveServingGroundingUseCase {

    fun execute(
        servingSize: Double,
        servingUnit: ServingUnit,
        gramsPerServingUnit: Double?,
        millilitersPerServingUnit: Double?
    ): ServingResolution {
        val normalizedGramsBridge = gramsPerServingUnit?.takeIf { it > 0.0 }
        val normalizedMillilitersBridge = millilitersPerServingUnit?.takeIf { it > 0.0 }

        if (servingSize <= 0.0) {
            return ServingResolution(
                servingSize = servingSize,
                servingUnit = servingUnit,
                gramsPerServingUnit = gramsPerServingUnit,
                millilitersPerServingUnit = millilitersPerServingUnit,
                gramsPerServing = null,
                millilitersPerServing = null,
                gramsEvidence = null,
                millilitersEvidence = null,
                gramsBlockReason = BlockReason.INVALID_SERVING_SIZE,
                millilitersBlockReason = BlockReason.INVALID_SERVING_SIZE
            )
        }

        val gramsResolution = resolveMassPath(
            servingSize = servingSize,
            servingUnit = servingUnit,
            gramsPerServingUnit = gramsPerServingUnit,
            normalizedGramsBridge = normalizedGramsBridge
        )

        val millilitersResolution = resolveVolumePath(
            servingSize = servingSize,
            servingUnit = servingUnit,
            millilitersPerServingUnit = millilitersPerServingUnit,
            normalizedMillilitersBridge = normalizedMillilitersBridge
        )

        return ServingResolution(
            servingSize = servingSize,
            servingUnit = servingUnit,
            gramsPerServingUnit = gramsPerServingUnit,
            millilitersPerServingUnit = millilitersPerServingUnit,
            gramsPerServing = gramsResolution.amount,
            millilitersPerServing = millilitersResolution.amount,
            gramsEvidence = gramsResolution.evidence,
            millilitersEvidence = millilitersResolution.evidence,
            gramsBlockReason = gramsResolution.blockReason,
            millilitersBlockReason = millilitersResolution.blockReason
        )
    }

    private fun resolveMassPath(
        servingSize: Double,
        servingUnit: ServingUnit,
        gramsPerServingUnit: Double?,
        normalizedGramsBridge: Double?
    ): GroundingPathResolution {
        val gramsDirect = servingUnit.toGrams(servingSize)
        if (gramsDirect != null) {
            return GroundingPathResolution(
                amount = gramsDirect,
                evidence = ResolutionEvidence.DIRECT_MASS_UNIT,
                blockReason = null
            )
        }

        if (normalizedGramsBridge != null) {
            return GroundingPathResolution(
                amount = servingSize * normalizedGramsBridge,
                evidence = ResolutionEvidence.GRAMS_BRIDGE,
                blockReason = null
            )
        }

        val blockReason =
            if (gramsPerServingUnit != null && gramsPerServingUnit <= 0.0) {
                BlockReason.INVALID_GRAMS_BRIDGE
            } else {
                BlockReason.NO_MASS_PATH
            }

        return GroundingPathResolution(
            amount = null,
            evidence = null,
            blockReason = blockReason
        )
    }

    private fun resolveVolumePath(
        servingSize: Double,
        servingUnit: ServingUnit,
        millilitersPerServingUnit: Double?,
        normalizedMillilitersBridge: Double?
    ): GroundingPathResolution {
        val millilitersDirect = servingUnit.toMilliliters(servingSize)
        if (millilitersDirect != null) {
            return GroundingPathResolution(
                amount = millilitersDirect,
                evidence = ResolutionEvidence.DIRECT_VOLUME_UNIT,
                blockReason = null
            )
        }

        if (normalizedMillilitersBridge != null) {
            return GroundingPathResolution(
                amount = servingSize * normalizedMillilitersBridge,
                evidence = ResolutionEvidence.ML_BRIDGE,
                blockReason = null
            )
        }

        val blockReason =
            if (millilitersPerServingUnit != null && millilitersPerServingUnit <= 0.0) {
                BlockReason.INVALID_ML_BRIDGE
            } else {
                BlockReason.NO_VOLUME_PATH
            }

        return GroundingPathResolution(
            amount = null,
            evidence = null,
            blockReason = blockReason
        )
    }

    private data class GroundingPathResolution(
        val amount: Double?,
        val evidence: ResolutionEvidence?,
        val blockReason: BlockReason?
    )
}