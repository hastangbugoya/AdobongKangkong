package com.example.adobongkangkong.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.adobongkangkong.data.local.db.entity.RecipeEntity
import com.example.adobongkangkong.data.local.db.entity.RecipeIngredientEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recipe: RecipeEntity): Long

    @Query("SELECT * FROM recipes ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<RecipeEntity>>

    // Edit Recipe

    @Query("SELECT * FROM recipes WHERE id = :recipeId LIMIT 1")
    suspend fun getById(recipeId: Long): RecipeEntity?

    @Query("SELECT * FROM recipes WHERE foodId = :foodId LIMIT 1")
    suspend fun getByFoodId(foodId: Long): RecipeEntity?
}
