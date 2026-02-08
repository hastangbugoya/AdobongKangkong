package com.example.adobongkangkong.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.example.adobongkangkong.data.local.db.entity.MealTemplatePrefsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MealTemplatePrefsDao {

    @Upsert
    suspend fun upsert(entity: MealTemplatePrefsEntity)

    @Query("SELECT * FROM meal_template_prefs WHERE templateId = :templateId LIMIT 1")
    suspend fun getForTemplate(templateId: Long): MealTemplatePrefsEntity?

    @Query("SELECT * FROM meal_template_prefs WHERE templateId = :templateId LIMIT 1")
    fun observeForTemplate(templateId: Long): Flow<MealTemplatePrefsEntity?>

    @Query("""
        SELECT templateId
        FROM meal_template_prefs
        WHERE isFavorite = 1
        ORDER BY templateId ASC
    """)
    fun observeFavoriteTemplateIds(): Flow<List<Long>>

    @Query("""
        SELECT templateId
        FROM meal_template_prefs
        WHERE bias = :bias
        ORDER BY templateId ASC
    """)
    fun observeTemplateIdsByBias(bias: String): Flow<List<Long>>

    @Query("DELETE FROM meal_template_prefs WHERE templateId = :templateId")
    suspend fun deleteForTemplate(templateId: Long)
}
