package com.example.adobongkangkong.domain.repository

import com.example.adobongkangkong.data.local.db.entity.IouEntity
import kotlinx.coroutines.flow.Flow

interface IouRepository {

    fun observeForDate(dateIso: String): Flow<List<IouEntity>>

    fun observeInRange(startDateIso: String, endDateIso: String): Flow<List<IouEntity>>

    suspend fun getById(id: Long): IouEntity?

    suspend fun insert(entity: IouEntity): Long

    suspend fun update(entity: IouEntity)

    suspend fun deleteById(id: Long)

    suspend fun deleteForDate(dateIso: String)
}
