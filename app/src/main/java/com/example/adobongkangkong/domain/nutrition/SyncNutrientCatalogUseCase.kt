package com.example.adobongkangkong.domain.nutrition

import androidx.room.withTransaction
import com.example.adobongkangkong.data.local.db.NutriDatabase
import com.example.adobongkangkong.data.local.db.entity.NutrientAliasEntity
import com.example.adobongkangkong.data.local.db.entity.NutrientEntity
import javax.inject.Inject

/**
 * Syncs [NutrientCatalog] into the database.
 *
 * Goals:
 * - Add missing nutrients
 * - Update existing nutrients by code WITHOUT changing IDs
 * - Ensure built-in aliases exist (for search)
 *
 * This is intended as a dev tool + safety net during early development.
 */
class SyncNutrientCatalogUseCase @Inject constructor(
    private val db: NutriDatabase
) {
    data class Result(
        val inserted: Int,
        val updated: Int,
        val aliasesUpserted: Int
    )

    suspend operator fun invoke(
        overrideDisplayName: Boolean = true,
        overrideUnit: Boolean = true,
        overrideCategory: Boolean = true
    ): Result {
        val nutrientDao = db.nutrientDao()
        val aliasDao = db.nutrientAliasDao()

        var inserted = 0
        var updated = 0
        var aliasesUpserted = 0

        db.withTransaction {
            val existing = nutrientDao.getAll()
            val existingByCode = existing.associateBy { it.code }

            // 1) Insert missing (IGNORE so we don’t replace existing IDs)
            val missing = NutrientCatalog.entries
                .filter { it.code !in existingByCode }
                .map {
                    NutrientEntity(
                        code = it.code,
                        displayName = it.displayName,
                        unit = it.unit,
                        category = it.category
                    )
                }

            if (missing.isNotEmpty()) {
                val results = nutrientDao.insertIgnore(missing)
                inserted += results.count { it != -1L }
            }

            // 2) Update existing rows by code (preserve IDs)
            for (entry in NutrientCatalog.entries) {
                val curr = existingByCode[entry.code] ?: continue

                val newDisplay = if (overrideDisplayName) entry.displayName else curr.displayName
                val newUnit = if (overrideUnit) entry.unit else curr.unit
                val newCat = if (overrideCategory) entry.category else curr.category

                if (newDisplay != curr.displayName || newUnit != curr.unit || newCat != curr.category) {
                    nutrientDao.updateByCode(
                        code = entry.code,
                        displayName = newDisplay,
                        unit = newUnit.name,
                        category = newCat.name
                    )
                    updated++
                }
            }

            // 3) Upsert aliases (need nutrientId; fetch by code)
            val aliasEntities = mutableListOf<NutrientAliasEntity>()
            for (entry in NutrientCatalog.entries) {
                val id = nutrientDao.getIdByCode(entry.code) ?: continue
                for (alias in entry.aliases) {
                    val key = normalizeAliasKey(alias)
                    if (key.isBlank()) continue
                    aliasEntities += NutrientAliasEntity(
                        nutrientId = id,
                        aliasDisplay = alias.trim(),
                        aliasKey = key
                    )
                }
            }

            if (aliasEntities.isNotEmpty()) {
                aliasDao.upsertAll(aliasEntities)
                aliasesUpserted = aliasEntities.size
            }
        }

        return Result(inserted, updated, aliasesUpserted)
    }

    private fun normalizeAliasKey(raw: String): String =
        raw.trim().lowercase()
            .replace(Regex("\\s+"), " ")
}
