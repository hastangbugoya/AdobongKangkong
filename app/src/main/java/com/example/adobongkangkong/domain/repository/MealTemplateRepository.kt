package com.example.adobongkangkong.domain.repository

import com.example.adobongkangkong.data.local.db.entity.MealTemplateEntity
import kotlinx.coroutines.flow.Flow

interface MealTemplateRepository {
    fun observeAll(): Flow<List<MealTemplateEntity>>
    suspend fun getById(id: Long): MealTemplateEntity?
    suspend fun insert(entity: MealTemplateEntity): Long
    suspend fun update(entity: MealTemplateEntity)
    suspend fun delete(entity: MealTemplateEntity)
}
