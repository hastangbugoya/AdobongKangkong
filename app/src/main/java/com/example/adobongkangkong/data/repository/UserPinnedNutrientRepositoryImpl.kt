package com.example.adobongkangkong.data.repository

import com.example.adobongkangkong.data.local.db.dao.UserPinnedNutrientDao
import com.example.adobongkangkong.domain.model.UserNutrientPreference
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

    override fun observePreferences(): Flow<List<UserNutrientPreference>> =
        dao.observeAll().map { entities ->
            entities
                .groupBy { it.nutrientCode }
                .mapNotNull { (nutrientCode, rows) ->
                    val key = nutrientCode.toNutrientKeyOrNull() ?: return@mapNotNull null
                    val pinnedRow = rows
                        .filter { it.position == 0 || it.position == 1 }
                        .minByOrNull { it.position }
                    UserNutrientPreference(
                        key = key,
                        isPinned = pinnedRow != null,
                        isCritical = rows.any { it.isCritical },
                        position = pinnedRow?.position
                    )
                }
                .sortedWith(
                    compareBy<UserNutrientPreference> { it.position ?: Int.MAX_VALUE }
                        .thenBy { it.key.value }
                )
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

    override suspend fun setCritical(key: NutrientKey, isCritical: Boolean) {
        dao.setCritical(
            nutrientCode = key.canonical().value,
            isCritical = isCritical
        )
    }
}

private fun String?.toNutrientKeyOrNull(): NutrientKey? {
    val cleaned = this?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return NutrientKey(cleaned.uppercase())
}

private fun NutrientKey.canonical(): NutrientKey =
    NutrientKey(value.trim().uppercase())
