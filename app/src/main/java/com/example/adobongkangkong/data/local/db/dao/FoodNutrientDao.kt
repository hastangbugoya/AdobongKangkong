package com.example.adobongkangkong.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.adobongkangkong.data.local.db.entity.FoodNutrientEntity

@Dao
interface FoodNutrientDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<FoodNutrientEntity>)

    @Query("SELECT * FROM food_nutrients WHERE foodId = :foodId")
    suspend fun getForFood(foodId: Long): List<FoodNutrientEntity>


    @Query("DELETE FROM food_nutrients WHERE foodId = :foodId")
    suspend fun deleteForFood(foodId: Long)
}
