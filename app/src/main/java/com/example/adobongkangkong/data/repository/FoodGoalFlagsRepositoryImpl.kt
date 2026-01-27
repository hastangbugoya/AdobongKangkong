package com.example.adobongkangkong.data.repository

import com.example.adobongkangkong.data.local.db.dao.FoodGoalFlagsDao
import com.example.adobongkangkong.data.local.db.entity.FoodGoalFlagsEntity
import com.example.adobongkangkong.domain.model.FoodGoalFlags
import com.example.adobongkangkong.domain.repository.FoodGoalFlagsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class FoodGoalFlagsRepositoryImpl @Inject constructor(
    private val dao: FoodGoalFlagsDao
) : FoodGoalFlagsRepository {

    override suspend fun get(foodId: Long): FoodGoalFlags? =
        dao.get(foodId)?.toDomain()

    override fun observe(foodId: Long): Flow<FoodGoalFlags?> =
        dao.observe(foodId).map { it?.toDomain() }

    override suspend fun setFlags(foodId: Long, eatMore: Boolean, limit: Boolean, favorite: Boolean) {
        dao.upsert(
            FoodGoalFlagsEntity(
                foodId = foodId,
                eatMore = eatMore,
                limit = limit,
                favorite = favorite
            )
        )
    }

    override suspend fun clear(foodId: Long) {
        dao.clear(foodId)
    }
}

private fun FoodGoalFlagsEntity.toDomain() =
    FoodGoalFlags(
        foodId = foodId,
        eatMore = eatMore,
        limit = limit,
        favorite = favorite
    )
