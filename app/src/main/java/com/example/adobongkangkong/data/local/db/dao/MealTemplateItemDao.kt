package com.example.adobongkangkong.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.adobongkangkong.data.local.db.entity.MealTemplateItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MealTemplateItemDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: MealTemplateItemEntity): Long

    @Update
    suspend fun update(entity: MealTemplateItemEntity)

    @Delete
    suspend fun delete(entity: MealTemplateItemEntity)

    @Query("SELECT * FROM meal_template_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): MealTemplateItemEntity?

    @Query("""
        SELECT *
        FROM meal_template_items
        WHERE templateId = :templateId
        ORDER BY sortOrder ASC, id ASC
    """)
    fun observeItemsForTemplate(templateId: Long): Flow<List<MealTemplateItemEntity>>

    @Query("""
        SELECT *
        FROM meal_template_items
        WHERE templateId = :templateId
        ORDER BY sortOrder ASC, id ASC
    """)
    suspend fun getItemsForTemplate(templateId: Long): List<MealTemplateItemEntity>

    @Query("""
        SELECT *
        FROM meal_template_items
        WHERE templateId IN (:templateIds)
        ORDER BY templateId ASC, sortOrder ASC, id ASC
    """)
    suspend fun getItemsForTemplates(templateIds: List<Long>): List<MealTemplateItemEntity>


    @Query("DELETE FROM meal_template_items WHERE templateId = :templateId")
    suspend fun deleteItemsForTemplate(templateId: Long)
}
