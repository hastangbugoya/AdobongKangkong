package com.example.adobongkangkong.data.repository

import com.example.adobongkangkong.data.local.db.dao.UserPinnedNutrientDao
import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.repository.UserPinnedNutrientRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class UserPinnedNutrientRepositoryImpl @Inject constructor(
    private val dao: UserPinnedNutrientDao
) : UserPinnedNutrientRepository {

    override fun observePinnedKeys(): Flow<List<NutrientKey>> =
        dao.observePinned().map { entities ->
            entities
                .sortedBy { it.position }
                .mapNotNull { e -> e.nutrientCode.toNutrientKeyOrNull() }
        }

    override suspend fun setPinned(position: Int, key: NutrientKey?) {
        require(position == 0 || position == 1) { "position must be 0 or 1" }
        dao.setPinned(position, key?.canonical()?.value)
    }

    override suspend fun setPinnedPositions(slot0: NutrientKey?, slot1: NutrientKey?) {
        val c0 = slot0?.canonical()
        var c1 = slot1?.canonical()

        // Deterministic de-dupe: slot1 loses if duplicated.
        if (c0 != null && c0 == c1) c1 = null

        dao.setPinnedPositions(
            position0Code = c0?.value,
            position1Code = c1?.value
        )
    }
}

private fun String?.toNutrientKeyOrNull(): NutrientKey? {
    val cleaned = this?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return NutrientKey(cleaned.uppercase())
}

private fun NutrientKey.canonical(): NutrientKey =
    NutrientKey(value.trim().uppercase())
