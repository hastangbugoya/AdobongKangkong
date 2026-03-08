package com.example.adobongkangkong.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.adobongkangkong.data.local.db.entity.UserPinnedNutrientEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserPinnedNutrientDao {

    @Query("SELECT * FROM user_pinned_nutrients WHERE position IN (0, 1) ORDER BY position ASC")
    fun observePinned(): Flow<List<UserPinnedNutrientEntity>>

    @Query("SELECT * FROM user_pinned_nutrients ORDER BY position ASC")
    fun observeAll(): Flow<List<UserPinnedNutrientEntity>>

    @Query("SELECT * FROM user_pinned_nutrients ORDER BY position ASC")
    suspend fun getAllOnce(): List<UserPinnedNutrientEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: UserPinnedNutrientEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<UserPinnedNutrientEntity>)

    @Query("DELETE FROM user_pinned_nutrients WHERE position = :position")
    suspend fun clearPosition(position: Int)

    @Query("DELETE FROM user_pinned_nutrients WHERE position IN (0, 1)")
    suspend fun clearPinnedSlots()

    @Query("DELETE FROM user_pinned_nutrients WHERE position NOT IN (0, 1) AND nutrientCode = :nutrientCode")
    suspend fun deletePreferenceOnlyRowsForCode(nutrientCode: String)

    @Query("DELETE FROM user_pinned_nutrients WHERE nutrientCode = :nutrientCode")
    suspend fun deleteAllRowsForCode(nutrientCode: String)

    @Query("DELETE FROM user_pinned_nutrients")
    suspend fun clearAll()

    @Transaction
    suspend fun setPinned(position: Int, nutrientCode: String?) {
        require(position == 0 || position == 1) { "position must be 0 or 1" }

        val existing = getAllOnce()
        val currentPinnedAtPosition = existing.firstOrNull { it.position == position }
        val criticalCodes = existing.filter { it.isCritical }.map { it.nutrientCode }.toSet()

        if (currentPinnedAtPosition != null) {
            clearPosition(position)
            if (currentPinnedAtPosition.isCritical && currentPinnedAtPosition.nutrientCode != nutrientCode) {
                upsert(
                    UserPinnedNutrientEntity(
                        position = nextPreferenceOnlyPosition(existing, excludeCode = currentPinnedAtPosition.nutrientCode),
                        nutrientCode = currentPinnedAtPosition.nutrientCode,
                        isCritical = true
                    )
                )
            }
        }

        if (nutrientCode != null) {
            deletePreferenceOnlyRowsForCode(nutrientCode)
            upsert(
                UserPinnedNutrientEntity(
                    position = position,
                    nutrientCode = nutrientCode,
                    isCritical = nutrientCode in criticalCodes
                )
            )
        }
    }

    @Transaction
    suspend fun setPinnedPositions(position0Code: String?, position1Code: String?) {
        val existing = getAllOnce()
        val criticalCodes = existing.filter { it.isCritical }.map { it.nutrientCode }.toSet()
        val previousPinnedCodes = existing
            .filter { it.position == 0 || it.position == 1 }
            .map { it.nutrientCode }
            .toSet()

        val newPinnedCodes = linkedSetOf<String>().apply {
            position0Code?.let(::add)
            position1Code?.let(::add)
        }

        clearPinnedSlots()

        var nextPreferencePosition = nextPreferenceOnlyPosition(existing)
        (previousPinnedCodes - newPinnedCodes)
            .filter { it in criticalCodes }
            .forEach { code ->
                deletePreferenceOnlyRowsForCode(code)
                upsert(
                    UserPinnedNutrientEntity(
                        position = nextPreferencePosition--,
                        nutrientCode = code,
                        isCritical = true
                    )
                )
            }

        position0Code?.let { code ->
            deletePreferenceOnlyRowsForCode(code)
            upsert(
                UserPinnedNutrientEntity(
                    position = 0,
                    nutrientCode = code,
                    isCritical = code in criticalCodes
                )
            )
        }
        position1Code?.let { code ->
            deletePreferenceOnlyRowsForCode(code)
            upsert(
                UserPinnedNutrientEntity(
                    position = 1,
                    nutrientCode = code,
                    isCritical = code in criticalCodes
                )
            )
        }
    }

    @Transaction
    suspend fun setCritical(nutrientCode: String, isCritical: Boolean) {
        val existing = getAllOnce()
        val rowsForCode = existing.filter { it.nutrientCode == nutrientCode }
        val pinnedRow = rowsForCode.firstOrNull { it.position == 0 || it.position == 1 }

        if (pinnedRow != null) {
            upsert(pinnedRow.copy(isCritical = isCritical))
            if (!isCritical) {
                deletePreferenceOnlyRowsForCode(nutrientCode)
            }
            return
        }

        if (isCritical) {
            val preferenceOnlyRow = rowsForCode.firstOrNull()
            val position = preferenceOnlyRow?.position ?: nextPreferenceOnlyPosition(existing)
            upsert(
                UserPinnedNutrientEntity(
                    position = position,
                    nutrientCode = nutrientCode,
                    isCritical = true
                )
            )
        } else {
            deleteAllRowsForCode(nutrientCode)
        }
    }
}

private fun nextPreferenceOnlyPosition(
    existing: List<UserPinnedNutrientEntity>,
    excludeCode: String? = null
): Int {
    val usedPositions = existing
        .asSequence()
        .filter { it.nutrientCode != excludeCode }
        .map { it.position }
        .toSet()

    var candidate = -1
    while (candidate in usedPositions) {
        candidate -= 1
    }
    return candidate
}
