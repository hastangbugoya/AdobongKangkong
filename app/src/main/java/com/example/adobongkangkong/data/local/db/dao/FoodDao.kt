package com.example.adobongkangkong.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.adobongkangkong.data.local.db.entity.FoodEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodDao {

    @Insert
    suspend fun insert(entity: FoodEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<FoodEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: FoodEntity)

    @Query("SELECT * FROM foods WHERE id = :id")
    suspend fun getById(id: Long): FoodEntity?

    @Query("SELECT * FROM foods WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<FoodEntity>

    @Query("""
                SELECT * FROM foods
                WHERE
                  LOWER(name) LIKE '%' || LOWER(:query) || '%'
                  OR LOWER(COALESCE(brand, '')) LIKE '%' || LOWER(:query) || '%'
                ORDER BY isRecipe DESC, name ASC
                LIMIT :limit
            """)
    fun search(query: String, limit: Int = 50): Flow<List<FoodEntity>>




    @Query("SELECT COUNT(*) FROM foods")
    suspend fun countFoods(): Int

    @Query("SELECT * FROM foods ORDER BY isRecipe DESC, name ASC")
    suspend fun getAll(): List<FoodEntity>

    // Import
    @Query("SELECT id FROM foods WHERE stableId = :stableId LIMIT 1")
    suspend fun getIdByStableId(stableId: String): Long?


    @Query("""
        UPDATE foods SET
          name = :name,
          brand = :brand,
          servingSize = :servingSize,
          servingUnit = :servingUnit,
          gramsPerServingUnit = :gramsPerServingUnit,
          isRecipe = :isRecipe
        WHERE id = :id
        """)
    suspend fun updateCore(
        id: Long,
        name: String,
        brand: String?,
        servingSize: Double,
        servingUnit: String,
        gramsPerServingUnit: Double?,
        isRecipe: Boolean
    )

}
