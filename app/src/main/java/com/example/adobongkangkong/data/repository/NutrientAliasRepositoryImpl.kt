package com.example.adobongkangkong.data.repository

import com.example.adobongkangkong.data.local.db.dao.NutrientAliasDao
import com.example.adobongkangkong.data.local.db.entity.NutrientAliasEntity
import com.example.adobongkangkong.domain.repository.NutrientAliasRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class NutrientAliasRepositoryImpl @Inject constructor(
    private val dao: NutrientAliasDao
) : NutrientAliasRepository {

    override fun observeAliases(nutrientId: Long): Flow<List<String>> =
        dao.observeAliasesForNutrient(nutrientId)

    override suspend fun replaceAliases(nutrientId: Long, aliases: List<String>) {
        dao.deleteAliasesForNutrient(nutrientId)
        insertNormalized(nutrientId, aliases)
    }

    override suspend fun addAlias(nutrientId: Long, alias: String) {
        insertNormalized(nutrientId, listOf(alias))
    }

    private suspend fun insertNormalized(nutrientId: Long, aliases: List<String>) {
        val entities = aliases
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .map { display ->
                NutrientAliasEntity(
                    nutrientId = nutrientId,
                    aliasDisplay = display,
                    aliasKey = display.lowercase()
                )
            }

        if (entities.isNotEmpty()) {
            dao.insertAliases(entities)
        }
    }

    override suspend fun deleteAlias(nutrientId: Long, alias: String) {
        dao.deleteAlias(nutrientId, alias.trim().lowercase())
    }
}
