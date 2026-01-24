package com.example.adobongkangkong.data.repository

import com.example.adobongkangkong.data.local.db.dao.NutrientAliasDao
import com.example.adobongkangkong.data.local.db.entity.NutrientAliasEntity
import com.example.adobongkangkong.domain.model.AliasAddResult
import com.example.adobongkangkong.domain.repository.NutrientAliasRepository
import com.example.adobongkangkong.domain.util.AliasNormalizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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

    override suspend fun addAlias(nutrientId: Long, alias: String): AliasAddResult {
        val display = AliasNormalizer.display(alias)
        val key = AliasNormalizer.key(alias)

        if (key.isBlank()) return AliasAddResult.IgnoredEmpty

        // Optional duplicate prevention (nice UX):
        val existing = dao.observeAliasKeys(nutrientId).first()
        if (existing.contains(key)) return AliasAddResult.IgnoredDuplicate

        dao.upsert(
            NutrientAliasEntity(
                nutrientId = nutrientId,
                aliasDisplay = display,
                aliasKey = key
            )
        )
        return AliasAddResult.Added
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
        val key = AliasNormalizer.key(alias)
        if (key.isBlank()) return
        dao.deleteAlias(nutrientId, key)
    }
    private fun normalizeAlias(s: String): String =
        s.lowercase()
            .replace("-", "")
            .replace("_", "")
            .replace(" ", "")
}
