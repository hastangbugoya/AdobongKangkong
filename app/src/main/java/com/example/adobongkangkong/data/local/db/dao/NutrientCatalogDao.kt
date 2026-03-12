package com.example.adobongkangkong.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.adobongkangkong.data.local.db.entity.NutrientEntity

@Dao
interface NutrientCatalogDao {

    @Query("SELECT COUNT(*) FROM nutrients")
    suspend fun countAll(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnoreExisting(rows: List<NutrientEntity>): List<Long>
}