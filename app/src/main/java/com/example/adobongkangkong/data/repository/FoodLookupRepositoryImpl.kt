package com.example.adobongkangkong.data.repository

import com.example.adobongkangkong.data.local.db.dao.FoodDao
import com.example.adobongkangkong.domain.repository.FoodLookupRepository
import javax.inject.Inject

class FoodLookupRepositoryImpl @Inject constructor(
    private val foodDao: FoodDao
) : FoodLookupRepository {

    override suspend fun getFoodNamesByIds(ids: List<Long>): Map<Long, String> {
        if (ids.isEmpty()) return emptyMap()
        return foodDao.getNamesByIds(ids).associate { it.id to it.name }
    }
}