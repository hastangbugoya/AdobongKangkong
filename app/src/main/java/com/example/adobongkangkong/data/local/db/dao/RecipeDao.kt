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

    @Query("SELECT * FROM recipes ORDER BY createdAt DESC")
    suspend fun getAll(): List<RecipeEntity>

    @Query("SELECT id FROM recipes WHERE stableId = :stableId LIMIT 1")
    suspend fun getIdByStableId(stableId: String): Long?


    @Query("""
        UPDATE recipes SET
          foodId = :foodId,
          name = :name,
          servingsYield = :servingsYield
        WHERE id = :id
        """)
    suspend fun updateCore(
        id: Long,
        foodId: Long,
        name: String,
        servingsYield: Double
    )
}
