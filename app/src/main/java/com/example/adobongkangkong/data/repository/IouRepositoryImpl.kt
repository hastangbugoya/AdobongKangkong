package com.example.adobongkangkong.data.repository

import com.example.adobongkangkong.data.local.db.dao.IouDao
import com.example.adobongkangkong.data.local.db.entity.IouEntity
import com.example.adobongkangkong.domain.repository.IouRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class IouRepositoryImpl @Inject constructor(
    private val dao: IouDao
) : IouRepository {

    override fun observeForDate(dateIso: String): Flow<List<IouEntity>> =
        dao.observeForDate(dateIso)

    override fun observeInRange(startDateIso: String, endDateIso: String): Flow<List<IouEntity>> =
        dao.observeInRange(startDateIso, endDateIso)

    override suspend fun getById(id: Long): IouEntity? =
        dao.getById(id)

    override suspend fun insert(entity: IouEntity): Long =
        dao.insert(entity)

    override suspend fun update(entity: IouEntity) =
        dao.update(entity)

    override suspend fun deleteById(id: Long) =
        dao.deleteById(id)

    override suspend fun deleteForDate(dateIso: String) =
        dao.deleteForDate(dateIso)
}
