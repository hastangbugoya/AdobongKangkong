package com.example.adobongkangkong.domain.repository

import com.example.adobongkangkong.data.local.db.entity.MealTemplateItemEntity
import kotlinx.coroutines.flow.Flow

interface MealTemplateItemRepository {
    fun observeItemsForTemplate(templateId: Long): Flow<List<MealTemplateItemEntity>>
    suspend fun getItemsForTemplate(templateId: Long): List<MealTemplateItemEntity>

    suspend fun insert(entity: MealTemplateItemEntity): Long
    suspend fun update(entity: MealTemplateItemEntity)
    suspend fun delete(entity: MealTemplateItemEntity)
    suspend fun deleteItemsForTemplate(templateId: Long)
}
