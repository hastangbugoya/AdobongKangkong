package com.example.adobongkangkong.data.repository

import com.example.adobongkangkong.data.local.db.dao.NutrientDao
import com.example.adobongkangkong.domain.model.Nutrient
import com.example.adobongkangkong.domain.repository.NutrientRepository
import javax.inject.Inject

class NutrientRepositoryImpl @Inject constructor(
    private val nutrientDao: NutrientDao
) : NutrientRepository {

//    override suspend fun search(query: String, limit: Int): List<Nutrient> {
//        return nutrientDao.search(query = query, limit = limit).map { e ->
//            Nutrient(
//                id = e.id,
//                code = e.code,
//                displayName = e.displayName,
//                unit = e.unit,       // keep as String for now
//                category = e.category // keep as String for now OR map to enum if you already have it
//            )
//        }
//    }

    override suspend fun search(query: String, limit: Int): List<Nutrient> {
        val q = query.trim().lowercase()
        return nutrientDao.searchWithAliases(q, limit).map { e ->
            Nutrient(
                id = e.id,
                code = e.code,
                displayName = e.displayName,
                unit = e.unit,       // keep as String for now
                category = e.category // keep as String for now OR map to enum if you already have it
            )
        }
    }
}
