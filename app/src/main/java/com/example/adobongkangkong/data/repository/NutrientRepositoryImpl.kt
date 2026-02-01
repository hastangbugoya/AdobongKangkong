package com.example.adobongkangkong.data.repository

import com.example.adobongkangkong.data.local.db.dao.NutrientAliasDao
import com.example.adobongkangkong.data.local.db.dao.NutrientDao
import com.example.adobongkangkong.data.local.db.entity.NutrientEntity
import com.example.adobongkangkong.data.local.db.mapper.toDomain
import com.example.adobongkangkong.domain.model.Nutrient
import com.example.adobongkangkong.domain.repository.NutrientRepository
import com.example.adobongkangkong.domain.util.NutrientSearchScorer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import javax.inject.Inject

class NutrientRepositoryImpl @Inject constructor(
    private val nutrientDao: NutrientDao,
    private val aliasDao: NutrientAliasDao
) : NutrientRepository {

    override fun search(query: String, limit: Int): Flow<List<Nutrient>> {
        val qLike = "%${query.trim().lowercase()}%"

        return nutrientDao.searchWithAliases(qLike, limit = 200)
            .mapLatest { entities ->
                val nutrients = entities.map { it.toDomain() }
                val ids = nutrients.map { it.id }

                val aliasRows =
                    if (ids.isEmpty()) emptyList()
                    else aliasDao.getForNutrients(ids)

                val aliasesById: Map<Long, List<String>> =
                    aliasRows
                        .groupBy { it.nutrientId }
                        .mapValues { (_, list) -> list.map { it.aliasDisplay } }

                val nutrientsWithAliases: List<Nutrient> =
                    nutrients.map { n ->
                        n.copy(aliases = aliasesById[n.id].orEmpty())
                    }

                nutrientsWithAliases
                    .map { n ->
                        val score = NutrientSearchScorer.score(
                            query = query,
                            nutrient = n,
                            aliases = n.aliases
                        )
                        n to score
                    }
                    .filter { (_, score) -> score > 0 }
                    .sortedByDescending { (_, score) -> score }
                    .take(limit)
                    .map { (n, _) -> n }
            }
    }


    override fun observeAllNutrients(): Flow<List<Nutrient>> =
        nutrientDao.observeAllNutrients()
            .map { entities -> entities.map { it.toDomain() } }
}
