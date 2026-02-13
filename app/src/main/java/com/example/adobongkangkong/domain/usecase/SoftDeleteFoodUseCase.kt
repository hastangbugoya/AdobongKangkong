package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.domain.repository.FoodRepository
import javax.inject.Inject

class SoftDeleteFoodUseCase @Inject constructor(
    private val foodRepository: FoodRepository
) {
    sealed class Result {
        data object Success : Result()
        data class NotFound(val foodId: Long) : Result()
    }

    suspend operator fun invoke(foodId: Long): Result {
        val existing = foodRepository.getById(foodId) ?: return Result.NotFound(foodId)
        foodRepository.softDeleteFood(existing.id)
        return Result.Success
    }
}
