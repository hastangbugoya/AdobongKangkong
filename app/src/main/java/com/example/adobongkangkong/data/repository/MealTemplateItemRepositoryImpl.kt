package com.example.adobongkangkong.data.repository

import com.example.adobongkangkong.data.local.db.dao.MealTemplateItemDao
import com.example.adobongkangkong.data.local.db.entity.MealTemplateItemEntity
import com.example.adobongkangkong.domain.repository.MealTemplateItemRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class MealTemplateItemRepositoryImpl @Inject constructor(
    private val dao: MealTemplateItemDao
) : MealTemplateItemRepository {
    override fun observeItemsForTemplate(templateId: Long): Flow<List<MealTemplateItemEntity>> =
        dao.observeItemsForTemplate(templateId)

    override suspend fun getItemsForTemplate(templateId: Long): List<MealTemplateItemEntity> =
        dao.getItemsForTemplate(templateId)

    override suspend fun insert(entity: MealTemplateItemEntity): Long = dao.insert(entity)
    override suspend fun update(entity: MealTemplateItemEntity) = dao.update(entity)
    override suspend fun delete(entity: MealTemplateItemEntity) = dao.delete(entity)
    override suspend fun deleteItemsForTemplate(templateId: Long) = dao.deleteItemsForTemplate(templateId)
}
