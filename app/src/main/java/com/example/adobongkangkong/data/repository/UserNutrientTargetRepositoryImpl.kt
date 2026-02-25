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
            list
                .map { it.toDomain().normalizeCode() }
                .associateBy { it.nutrientCode }
        }

    override suspend fun upsert(target: UserNutrientTarget) {
        dao.upsert(target.normalizeCode().toEntity())
    }

    override suspend fun upsertAll(targets: List<UserNutrientTarget>) {
        dao.upsertAll(targets.map { it.normalizeCode().toEntity() })
    }

    override suspend fun delete(nutrientCode: String) {
        dao.delete(nutrientCode.trim().uppercase())
    }

    override suspend fun hasAnyTargets(): Boolean = dao.hasAny()

}




internal fun UserNutrientTargetEntity.toDomain() = UserNutrientTarget(
    nutrientCode = nutrientCode,
    minPerDay = minPerDay,
    targetPerDay = targetPerDay,
    maxPerDay = maxPerDay
)

internal fun UserNutrientTarget.toEntity() = UserNutrientTargetEntity(
    nutrientCode = nutrientCode,
    minPerDay = minPerDay,
    targetPerDay = targetPerDay,
    maxPerDay = maxPerDay
)

private fun UserNutrientTarget.normalizeCode(): UserNutrientTarget =
    copy(nutrientCode = nutrientCode.trim().uppercase())
