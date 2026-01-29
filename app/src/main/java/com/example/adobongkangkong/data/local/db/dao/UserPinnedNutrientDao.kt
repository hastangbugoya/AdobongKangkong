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

    @Query("SELECT * FROM user_pinned_nutrients ORDER BY position ASC")
    fun observePinned(): Flow<List<UserPinnedNutrientEntity>>

    @Query("SELECT * FROM user_pinned_nutrients ORDER BY position ASC")
    suspend fun getPinnedOnce(): List<UserPinnedNutrientEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: UserPinnedNutrientEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<UserPinnedNutrientEntity>)

    @Query("DELETE FROM user_pinned_nutrients WHERE position = :position")
    suspend fun clearPosition(position: Int)

    @Query("DELETE FROM user_pinned_nutrients")
    suspend fun clearAll()

    @Transaction
    suspend fun setPinned(position: Int, nutrientCode: String?) {
        require(position == 0 || position == 1) { "position must be 0 or 1" }
        if (nutrientCode == null) {
            clearPosition(position)
        } else {
            upsert(UserPinnedNutrientEntity(position = position, nutrientCode = nutrientCode))
        }
    }

    @Transaction
    suspend fun setPinnedPositions(position0Code: String?, position1Code: String?) {
        // Make it atomic and deterministic
        clearAll()
        val entities = buildList {
            if (position0Code != null) add(UserPinnedNutrientEntity(position = 0, nutrientCode = position0Code))
            if (position1Code != null) add(UserPinnedNutrientEntity(position = 1, nutrientCode = position1Code))
        }
        if (entities.isNotEmpty()) upsertAll(entities)
    }
}
