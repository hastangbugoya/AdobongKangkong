package com.example.adobongkangkong.domain.repository

import com.example.adobongkangkong.data.local.db.entity.PlannerIouEntity
import kotlinx.coroutines.flow.Flow

interface PlannerIouRepository {

    fun observeForDate(dateIso: String): Flow<List<PlannerIouEntity>>

    fun observeInRange(startDateIso: String, endDateIso: String): Flow<List<PlannerIouEntity>>

    suspend fun getById(id: Long): PlannerIouEntity?

    suspend fun insert(entity: PlannerIouEntity): Long

    suspend fun update(entity: PlannerIouEntity)

    suspend fun deleteById(id: Long)

    suspend fun deleteForDate(dateIso: String)
}
