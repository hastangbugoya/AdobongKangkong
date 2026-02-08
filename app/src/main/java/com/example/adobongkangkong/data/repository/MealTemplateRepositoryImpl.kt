package com.example.adobongkangkong.data.repository

import com.example.adobongkangkong.data.local.db.dao.MealTemplateDao
import com.example.adobongkangkong.data.local.db.entity.MealTemplateEntity
import com.example.adobongkangkong.domain.repository.MealTemplateRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class MealTemplateRepositoryImpl @Inject constructor(
    private val dao: MealTemplateDao
) : MealTemplateRepository {
    override fun observeAll(): Flow<List<MealTemplateEntity>> = dao.observeAll()
    override suspend fun getById(id: Long): MealTemplateEntity? = dao.getById(id)
    override suspend fun insert(entity: MealTemplateEntity): Long = dao.insert(entity)
    override suspend fun update(entity: MealTemplateEntity) = dao.update(entity)
    override suspend fun delete(entity: MealTemplateEntity) = dao.delete(entity)
}
