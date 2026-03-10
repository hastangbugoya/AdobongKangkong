package com.example.adobongkangkong.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.adobongkangkong.data.local.db.entity.FoodCategoryCrossRefEntity
import com.example.adobongkangkong.data.local.db.entity.FoodCategoryEntity
import com.example.adobongkangkong.data.local.db.entity.RecipeCategoryCrossRefEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodCategoryDao {

    @Query(
        """
        SELECT *
        FROM food_categories
        ORDER BY sortOrder ASC, name COLLATE NOCASE ASC, id ASC
        """
    )
    suspend fun getAll(): List<FoodCategoryEntity>

    @Query(
        """
        SELECT *
        FROM food_categories
        ORDER BY sortOrder ASC, name COLLATE NOCASE ASC, id ASC
        """
    )
    fun observeAll(): Flow<List<FoodCategoryEntity>>

    @Query(
        """
        SELECT foodId
        FROM food_category_cross_refs
        WHERE categoryId = :categoryId
        ORDER BY foodId ASC
        """
    )
    fun observeFoodIdsForCategory(categoryId: Long): Flow<List<Long>>

    @Query(
        """
        SELECT c.*
        FROM food_categories c
        INNER JOIN food_category_cross_refs x ON x.categoryId = c.id
        WHERE x.foodId = :foodId
        ORDER BY c.sortOrder ASC, c.name COLLATE NOCASE ASC, c.id ASC
        """
    )
    suspend fun getForFood(foodId: Long): List<FoodCategoryEntity>

    @Query(
        """
        SELECT recipeId
        FROM recipe_category_cross_refs
        WHERE categoryId = :categoryId
        ORDER BY recipeId ASC
        """
    )
    fun observeRecipeIdsForCategory(categoryId: Long): Flow<List<Long>>

    @Query(
        """
        SELECT r.foodId
        FROM recipe_category_cross_refs x
        INNER JOIN recipes r ON r.id = x.recipeId
        WHERE x.categoryId = :categoryId
        ORDER BY r.foodId ASC
        """
    )
    fun observeRecipeFoodIdsForCategory(categoryId: Long): Flow<List<Long>>

    @Query(
        """
        SELECT c.*
        FROM food_categories c
        INNER JOIN recipe_category_cross_refs x ON x.categoryId = c.id
        WHERE x.recipeId = :recipeId
        ORDER BY c.sortOrder ASC, c.name COLLATE NOCASE ASC, c.id ASC
        """
    )
    suspend fun getForRecipe(recipeId: Long): List<FoodCategoryEntity>

    @Query(
        """
        SELECT *
        FROM food_categories
        WHERE lower(name) = lower(:name)
        LIMIT 1
        """
    )
    suspend fun findByName(name: String): FoodCategoryEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM food_categories")
    suspend fun getMaxSortOrder(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCategory(entity: FoodCategoryEntity): Long

    @Query("DELETE FROM food_category_cross_refs WHERE foodId = :foodId")
    suspend fun deleteCrossRefsForFood(foodId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRefs(entities: List<FoodCategoryCrossRefEntity>)

    @Query("DELETE FROM recipe_category_cross_refs WHERE recipeId = :recipeId")
    suspend fun deleteCrossRefsForRecipe(recipeId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecipeCrossRefs(entities: List<RecipeCategoryCrossRefEntity>)
}