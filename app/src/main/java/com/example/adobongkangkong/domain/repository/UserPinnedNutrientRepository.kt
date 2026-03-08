package com.example.adobongkangkong.domain.repository

import com.example.adobongkangkong.domain.model.UserNutrientPreference
import com.example.adobongkangkong.domain.nutrition.NutrientKey
import kotlinx.coroutines.flow.Flow

interface UserPinnedNutrientRepository {

    /** Slot order: [slot0, slot1] (missing slots omitted). */
    fun observePinnedKeys(): Flow<List<NutrientKey>>
    fun observePreferences(): Flow<List<UserNutrientPreference>>

    suspend fun setPinned(position: Int, key: NutrientKey?)
    suspend fun setPinnedPositions(slot0: NutrientKey?, slot1: NutrientKey?)
    suspend fun setCritical(key: NutrientKey, isCritical: Boolean)
}
