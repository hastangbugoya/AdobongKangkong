package com.example.adobongkangkong.domain.bridge

import com.example.adobongkangkong.domain.model.ServingUnit

/**
 * Resolves unit → basis.
 *
 * Keep this simple and deterministic.
 */
interface BridgeBasisResolver {
    fun basisOf(unit: ServingUnit): UnitBasis
}

/**
 * Default implementation using existing ServingUnit helpers.
 */
class DefaultBridgeBasisResolver : BridgeBasisResolver {

    override fun basisOf(unit: ServingUnit): UnitBasis {
        return when {
            unit.asG != null -> UnitBasis.MASS
            unit.asMl != null -> UnitBasis.VOLUME
            else -> UnitBasis.COUNT_OR_CONTAINER
        }
    }
}