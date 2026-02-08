package com.example.adobongkangkong.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.adobongkangkong.data.local.db.entity.MealTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MealTemplateDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: MealTemplateEntity): Long

    @Update
    suspend fun update(entity: MealTemplateEntity)

    @Delete
    suspend fun delete(entity: MealTemplateEntity)

    @Query("SELECT * FROM meal_templates WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): MealTemplateEntity?

    @Query("""
        SELECT *
        FROM meal_templates
        ORDER BY name COLLATE NOCASE ASC, id ASC
    """)
    fun observeAll(): Flow<List<MealTemplateEntity>>
}
