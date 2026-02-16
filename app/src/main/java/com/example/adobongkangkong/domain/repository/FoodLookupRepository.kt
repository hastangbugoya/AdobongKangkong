package com.example.adobongkangkong.domain.repository

interface FoodLookupRepository {
    suspend fun getFoodNamesByIds(ids: List<Long>): Map<Long, String>
}