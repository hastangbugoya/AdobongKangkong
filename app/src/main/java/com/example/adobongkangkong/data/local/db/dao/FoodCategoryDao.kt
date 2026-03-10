package com.example.adobongkangkong.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.adobongkangkong.data.local.db.entity.FoodCategoryCrossRefEntity
import com.example.adobongkangkong.data.local.db.entity.FoodCategoryEntity

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
}
