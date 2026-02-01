package com.example.adobongkangkong.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import com.example.adobongkangkong.data.local.db.entity.FoodGoalFlagsEntity

@Dao
interface FoodGoalFlagsDao {

    @Query("SELECT * FROM food_goal_flags WHERE foodId = :foodId LIMIT 1")
    suspend fun get(foodId: Long): FoodGoalFlagsEntity?

    @Query("SELECT * FROM food_goal_flags WHERE foodId = :foodId LIMIT 1")
    fun observe(foodId: Long): Flow<FoodGoalFlagsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FoodGoalFlagsEntity)

    @Query("SELECT * FROM food_goal_flags")
    fun observeAll(): Flow<List<FoodGoalFlagsEntity>>

    @Query("DELETE FROM food_goal_flags WHERE foodId = :foodId")
    suspend fun clear(foodId: Long)
}
