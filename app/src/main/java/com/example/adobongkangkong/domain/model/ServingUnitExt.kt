package com.example.adobongkangkong.domain.model

/**
 * Units where "1 serving" implies volume-ish or container-ish, and must be backed by gramsPerServing
 * to be safely converted to weight-based nutrition.
 */
fun ServingUnit.requiresGramsPerServing(): Boolean = when (this) {
    ServingUnit.ML,
    ServingUnit.TBSP,
    ServingUnit.TSP,
    ServingUnit.CUP,
    ServingUnit.RCCUP,
    ServingUnit.SCOOP,
    ServingUnit.BOTTLE,
    ServingUnit.JAR,
    ServingUnit.CAN,
    ServingUnit.BOX,
    ServingUnit.BAG,
    ServingUnit.PACKET,
    ServingUnit.PACK,
    ServingUnit.SERVING,
    ServingUnit.OTHER,
    ServingUnit.PIECE,
    ServingUnit.SLICE,
    ServingUnit.QUART,
    ServingUnit.BUNCH -> true

    // Weight-ish
    ServingUnit.G,
    ServingUnit.MG,
    ServingUnit.OZ,
    ServingUnit.LB -> false
}

