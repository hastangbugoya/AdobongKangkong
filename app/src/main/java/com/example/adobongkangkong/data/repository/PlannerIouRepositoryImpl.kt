package com.example.adobongkangkong.data.repository

import com.example.adobongkangkong.data.local.db.dao.PlannerIouDao
import com.example.adobongkangkong.data.local.db.entity.PlannerIouEntity
import com.example.adobongkangkong.domain.repository.PlannerIouRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class PlannerIouRepositoryImpl @Inject constructor(
    private val dao: PlannerIouDao
) : PlannerIouRepository {

    override fun observeForDate(dateIso: String): Flow<List<PlannerIouEntity>> =
        dao.observeForDate(dateIso)

    override fun observeInRange(startDateIso: String, endDateIso: String): Flow<List<PlannerIouEntity>> =
        dao.observeInRange(startDateIso, endDateIso)

    override suspend fun getById(id: Long): PlannerIouEntity? =
        dao.getById(id)

    override suspend fun insert(entity: PlannerIouEntity): Long =
        dao.insert(entity)

    override suspend fun update(entity: PlannerIouEntity) =
        dao.update(entity)

    override suspend fun deleteById(id: Long) =
        dao.deleteById(id)

    override suspend fun deleteForDate(dateIso: String) =
        dao.deleteForDate(dateIso)
}
