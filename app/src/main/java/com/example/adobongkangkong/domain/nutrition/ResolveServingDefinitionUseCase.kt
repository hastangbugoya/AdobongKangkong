//package com.example.adobongkangkong.domain.nutrition
//
//import com.example.adobongkangkong.domain.model.ServingUnit
//import com.example.adobongkangkong.domain.model.toGrams
//import com.example.adobongkangkong.domain.model.toMilliliters
//
///**
// * Represents the fully resolved serving definition for a food.
// *
// * This is the single source of truth for:
// * - whether a serving can be converted to grams
// * - whether a serving can be converted to milliliters
// * - whether nutrition math can be performed against PER_100G or PER_100ML
// *
// * IMPORTANT:
// * - No density guessing is performed
// * - Only deterministic conversions or explicit bridges are used
// */
//data class ServingResolution(
//    val gramsPerServing: Double?,
//    val millilitersPerServing: Double?,
//    val source: ResolutionSource,
//    val blockReason: BlockReason?
//) {
//
//    val supportsPer100G: Boolean = gramsPerServing != null
//    val supportsPer100ML: Boolean = millilitersPerServing != null
//
//    enum class ResolutionSource {
//        DIRECT_MASS_UNIT,
//        DIRECT_VOLUME_UNIT,
//        GRAMS_BRIDGE,
//        ML_BRIDGE,
//        NONE
//    }
//
//    enum class BlockReason {
//        NO_CONVERSION_PATH
//    }
//}
//
///**
// * Resolves serving definition into deterministic mass and/or volume.
// */
//class ResolveServingDefinitionUseCase {
//
//    fun execute(
//        servingSize: Double,
//        servingUnit: ServingUnit,
//        gramsPerServingUnit: Double?,
//        mlPerServingUnit: Double?
//    ): ServingResolution {
//
//        if (servingSize <= 0.0) {
//            return ServingResolution(
//                gramsPerServing = null,
//                millilitersPerServing = null,
//                source = ServingResolution.ResolutionSource.NONE,
//                blockReason = ServingResolution.BlockReason.NO_CONVERSION_PATH
//            )
//        }
//
//        // 1. Direct mass conversion
//        val gramsDirect = servingUnit.toGrams(servingSize)
//        if (gramsDirect != null) {
//            return ServingResolution(
//                gramsPerServing = gramsDirect,
//                millilitersPerServing = null,
//                source = ServingResolution.ResolutionSource.DIRECT_MASS_UNIT,
//                blockReason = null
//            )
//        }
//
//        // 2. Direct volume conversion
//        val mlDirect = servingUnit.toMilliliters(servingSize)
//        if (mlDirect != null) {
//            return ServingResolution(
//                gramsPerServing = null,
//                millilitersPerServing = mlDirect,
//                source = ServingResolution.ResolutionSource.DIRECT_VOLUME_UNIT,
//                blockReason = null
//            )
//        }
//
//        // 3. Grams bridge
//        val gramsBridge = gramsPerServingUnit?.takeIf { it > 0.0 }?.let {
//            servingSize * it
//        }
//        if (gramsBridge != null) {
//            return ServingResolution(
//                gramsPerServing = gramsBridge,
//                millilitersPerServing = null,
//                source = ServingResolution.ResolutionSource.GRAMS_BRIDGE,
//                blockReason = null
//            )
//        }
//
//        // 4. mL bridge
//        val mlBridge = mlPerServingUnit?.takeIf { it > 0.0 }?.let {
//            servingSize * it
//        }
//        if (mlBridge != null) {
//            return ServingResolution(
//                gramsPerServing = null,
//                millilitersPerServing = mlBridge,
//                source = ServingResolution.ResolutionSource.ML_BRIDGE,
//                blockReason = null
//            )
//        }
//
//        // 5. No valid path
//        return ServingResolution(
//            gramsPerServing = null,
//            millilitersPerServing = null,
//            source = ServingResolution.ResolutionSource.NONE,
//            blockReason = ServingResolution.BlockReason.NO_CONVERSION_PATH
//        )
//    }
//}