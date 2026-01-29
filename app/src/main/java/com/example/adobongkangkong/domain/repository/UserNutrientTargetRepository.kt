package com.example.adobongkangkong.domain.repository

import com.example.adobongkangkong.domain.model.UserNutrientTarget
import com.example.adobongkangkong.domain.nutrition.NutrientKey
import kotlinx.coroutines.flow.Flow

interface UserNutrientTargetRepository {
    fun observeTargets(): Flow<Map<String, UserNutrientTarget>>
    suspend fun upsert(target: UserNutrientTarget)
    suspend fun upsertAll(targets: List<UserNutrientTarget>)
    suspend fun delete(nutrientCode: String)
    suspend fun hasAnyTargets(): Boolean

}
