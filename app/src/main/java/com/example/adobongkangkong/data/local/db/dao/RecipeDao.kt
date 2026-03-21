package com.example.adobongkangkong.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.adobongkangkong.data.local.db.entity.RecipeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recipe: RecipeEntity): Long

    // --------------------------------------------------
    // Queries (exclude soft-deleted by default)
    // --------------------------------------------------

    @Query("SELECT * FROM recipes WHERE isDeleted = 0 ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM recipes WHERE isDeleted = 0 ORDER BY createdAt DESC")
    suspend fun getAll(): List<RecipeEntity>

    @Query("SELECT * FROM recipes WHERE id = :recipeId LIMIT 1")
    suspend fun getById(recipeId: Long): RecipeEntity?

    @Query("SELECT * FROM recipes WHERE id IN (:recipeIds)")
    suspend fun getByIds(recipeIds: List<Long>): List<RecipeEntity>

    @Query("SELECT * FROM recipes WHERE foodId = :foodId LIMIT 1")
    suspend fun getByFoodId(foodId: Long): RecipeEntity?

    @Query("SELECT * FROM recipes WHERE foodId IN (:foodIds)")
    suspend fun getByFoodIds(foodIds: List<Long>): List<RecipeEntity>

    @Query("SELECT id FROM recipes WHERE stableId = :stableId LIMIT 1")
    suspend fun getIdByStableId(stableId: String): Long?

    // --------------------------------------------------
    // Updates
    // --------------------------------------------------

    @Query(
        """
        UPDATE recipes SET
          foodId = :foodId,
          name = :name,
          servingsYield = :servingsYield
        WHERE id = :id
        """
    )
    suspend fun updateCore(
        id: Long,
        foodId: Long,
        name: String,
        servingsYield: Double
    )

    // --------------------------------------------------
    // Soft Delete
    // --------------------------------------------------

    @Query(
        """
        UPDATE recipes
        SET isDeleted = 1,
            deletedAtEpochMs = :deletedAtEpochMs
        WHERE id = :recipeId
        """
    )
    suspend fun softDeleteById(
        recipeId: Long,
        deletedAtEpochMs: Long
    )

    @Query(
        """
        UPDATE recipes
        SET isDeleted = 1,
            deletedAtEpochMs = :deletedAtEpochMs
        WHERE foodId = :foodId
        """
    )
    suspend fun softDeleteByFoodId(
        foodId: Long,
        deletedAtEpochMs: Long
    )

    @Query("SELECT COUNT(*) FROM recipes WHERE isDeleted = 0")
    suspend fun countRecipes(): Int
}