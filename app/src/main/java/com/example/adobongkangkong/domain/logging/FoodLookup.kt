package com.example.adobongkangkong.domain.logging

import com.example.adobongkangkong.domain.model.ServingUnit

/**
 * Minimal read surface the logging use case needs.
 * Implemented by data layer (repo).
 */
interface FoodLookup {
    suspend fun getFoodSnapshotById(foodId: Long): FoodSnapshot?
}

data class FoodSnapshot(
    val id: Long,
    val servingUnit: ServingUnit,
    val gramsPerServing: Double?
)
