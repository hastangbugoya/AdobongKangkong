package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.repository.FoodRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SearchFoodsUseCase @Inject constructor(
    private val repo: FoodRepository
) {
    operator fun invoke(query: String, limit: Int = 50): Flow<List<Food>> =
        repo.search(query, limit)
}