package com.example.adobongkangkong.data.repository

import com.example.adobongkangkong.data.local.db.dao.CalendarSuccessNutrientDao
import com.example.adobongkangkong.data.local.db.entity.CalendarSuccessNutrientEntity
import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.repository.CalendarSuccessNutrientRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class CalendarSuccessNutrientRepositoryImpl @Inject constructor(
    private val dao: CalendarSuccessNutrientDao
) : CalendarSuccessNutrientRepository {

    override fun observeSelectedKeys(): Flow<List<NutrientKey>> =
        dao.observeAll().map { entities ->
            entities
                .mapNotNull { it.nutrientCode.toNutrientKeyOrNull() }
                .sortedBy { it.value }
        }

    override suspend fun setSelectedKeys(keys: List<NutrientKey>) {
        val canonical = keys
            .map { it.canonical() }
            .distinctBy { it.value }

        dao.clearAll()

        if (canonical.isNotEmpty()) {
            dao.upsertAll(
                canonical.map {
                    CalendarSuccessNutrientEntity(
                        nutrientCode = it.value
                    )
                }
            )
        }
    }

    override suspend fun clearSelectedKeys() {
        dao.clearAll()
    }
}

private fun String?.toNutrientKeyOrNull(): NutrientKey? {
    val cleaned = this?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return NutrientKey(cleaned.uppercase())
}

private fun NutrientKey.canonical(): NutrientKey =
    NutrientKey(value.trim().uppercase())