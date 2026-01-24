package com.example.adobongkangkong.data.repository

import com.example.adobongkangkong.data.local.db.dao.NutrientDao
import com.example.adobongkangkong.domain.model.Nutrient
import com.example.adobongkangkong.domain.repository.NutrientRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import kotlinx.coroutines.flow.map

class NutrientRepositoryImpl @Inject constructor(
    private val nutrientDao: NutrientDao
) : NutrientRepository {

    override fun search(query: String, limit: Int): Flow<List<Nutrient>> {
        val q = "%${query.trim().lowercase()}%"
        return nutrientDao.searchWithAliases(q, limit)
            .map { entities ->
                entities.map { e ->
                    Nutrient(
                        id = e.id,
                        code = e.code,
                        displayName = e.displayName,
                        unit = e.unit,
                        category = e.category
                    )
                }
            }
    }
}
