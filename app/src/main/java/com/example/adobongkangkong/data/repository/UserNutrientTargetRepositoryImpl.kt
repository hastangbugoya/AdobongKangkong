package com.example.adobongkangkong.data.repository

import com.example.adobongkangkong.data.local.db.dao.UserNutrientTargetDao
import com.example.adobongkangkong.data.local.db.entity.UserNutrientTargetEntity
import com.example.adobongkangkong.domain.repository.UserNutrientTargetRepository
import com.example.adobongkangkong.domain.model.UserNutrientTarget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class UserNutrientTargetRepositoryImpl @Inject constructor(
    private val dao: UserNutrientTargetDao
) : UserNutrientTargetRepository {

    override fun observeTargets(): Flow<Map<String, UserNutrientTarget>> =
        dao.observeAll().map { list ->
            list.associate { it.nutrientCode to it.toDomain() }
        }

    override suspend fun upsert(target: UserNutrientTarget) {
        dao.upsert(target.toEntity())
    }

    override suspend fun upsertAll(targets: List<UserNutrientTarget>) {
        dao.upsertAll(targets.map { it.toEntity() })
    }

    override suspend fun delete(nutrientCode: String) {
        dao.delete(nutrientCode)
    }

    override suspend fun hasAnyTargets(): Boolean = dao.hasAny()
}

private fun UserNutrientTargetEntity.toDomain() = UserNutrientTarget(
    nutrientCode = nutrientCode,
    minPerDay = minPerDay,
    targetPerDay = targetPerDay,
    maxPerDay = maxPerDay
)

private fun UserNutrientTarget.toEntity() = UserNutrientTargetEntity(
    nutrientCode = nutrientCode,
    minPerDay = minPerDay,
    targetPerDay = targetPerDay,
    maxPerDay = maxPerDay
)
