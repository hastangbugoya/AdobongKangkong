package com.example.adobongkangkong.data.repository

import com.example.adobongkangkong.data.local.db.dao.FoodDao
import com.example.adobongkangkong.data.local.db.entity.FoodEntity
import com.example.adobongkangkong.data.local.db.mapper.toDomain
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.data.local.db.mapper.toEntity
import com.example.adobongkangkong.domain.logging.model.FoodRef
import com.example.adobongkangkong.domain.repository.FoodRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class FoodRepositoryImpl @Inject constructor(
    private val foodDao: FoodDao
) : FoodRepository {

    override fun search(query: String, limit: Int): Flow<List<Food>> =
        foodDao.search(query = query, limit = limit).map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: Long): Food? =
        foodDao.getById(id)?.toDomain()

    override suspend fun upsert(food: Food): Long {
        val entity = food.toEntity()
        foodDao.upsert(entity)
        return entity.id
    }

    override suspend fun getFoodRefForLogging(foodId: Long): FoodRef.Food? {
        val entity = foodDao.getById(foodId) ?: return null

        return FoodRef.Food(foodId = entity.id)
    }

    override suspend fun isFoodsEmpty(): Boolean =
        foodDao.countFoods() == 0

}
