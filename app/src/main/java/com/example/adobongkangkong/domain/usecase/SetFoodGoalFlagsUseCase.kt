package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.domain.repository.FoodGoalFlagsRepository
import javax.inject.Inject

class SetFoodGoalFlagsUseCase @Inject constructor(
    private val repo: FoodGoalFlagsRepository
) {
    suspend operator fun invoke(foodId: Long, eatMore: Boolean, limit: Boolean, favorite: Boolean) {
        repo.setFlags(foodId, eatMore, limit, favorite)
    }
}