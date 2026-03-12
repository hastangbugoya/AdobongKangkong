package com.example.adobongkangkong.data.repository

import com.example.adobongkangkong.data.csvimport.CsvNutrientCatalog
import com.example.adobongkangkong.data.local.db.dao.NutrientCatalogDao
import com.example.adobongkangkong.data.local.db.entity.NutrientEntity
import com.example.adobongkangkong.domain.model.NutrientCategory
import com.example.adobongkangkong.domain.model.NutrientUnit
import com.example.adobongkangkong.domain.repository.NutrientCatalogBootstrapRepository
import javax.inject.Inject

class NutrientCatalogBootstrapRepositoryImpl @Inject constructor(
    private val dao: NutrientCatalogDao
) : NutrientCatalogBootstrapRepository {

    override suspend fun nutrientCount(): Int = dao.countAll()

    override suspend fun seedCsvCatalogIfMissing() {
        val rows = CsvNutrientCatalog.defs.map { def ->
            NutrientEntity(
                id = 0L,
                code = def.code,
                displayName = def.displayName,
                unit = NutrientUnit.fromDb(def.unit),
                category = NutrientCategory.fromDb(def.categoryDbValue)
            )
        }

        dao.insertAllIgnoreExisting(rows)
    }
}