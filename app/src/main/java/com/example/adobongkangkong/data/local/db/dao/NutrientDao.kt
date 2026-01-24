package com.example.adobongkangkong.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.adobongkangkong.data.local.db.entity.NutrientEntity

@Dao
interface NutrientDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<NutrientEntity>)

    @Query("SELECT * FROM nutrients ORDER BY category ASC, displayName ASC")
    suspend fun getAll(): List<NutrientEntity>

    @Query("SELECT id FROM nutrients WHERE code = :code LIMIT 1")
    suspend fun getIdByCode(code: String): Long?
}
