package com.example.adobongkangkong.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.adobongkangkong.data.local.db.entity.CalendarSuccessNutrientEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarSuccessNutrientDao {

    @Query("SELECT * FROM calendar_success_nutrients ORDER BY nutrientCode ASC")
    fun observeAll(): Flow<List<CalendarSuccessNutrientEntity>>

    @Query("SELECT * FROM calendar_success_nutrients ORDER BY nutrientCode ASC")
    suspend fun getAllOnce(): List<CalendarSuccessNutrientEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CalendarSuccessNutrientEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<CalendarSuccessNutrientEntity>)

    @Query("DELETE FROM calendar_success_nutrients WHERE nutrientCode = :nutrientCode")
    suspend fun deleteByCode(nutrientCode: String)

    @Query("DELETE FROM calendar_success_nutrients")
    suspend fun clearAll()
}