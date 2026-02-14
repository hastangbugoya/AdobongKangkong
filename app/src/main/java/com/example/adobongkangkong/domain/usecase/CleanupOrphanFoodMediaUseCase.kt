package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.domain.repository.FoodRepository
import javax.inject.Inject

class CleanupOrphanFoodMediaUseCase @Inject constructor(
    private val foods: FoodRepository
) {
    suspend operator fun invoke(): Int = foods.cleanupOrphanFoodMedia()
}
