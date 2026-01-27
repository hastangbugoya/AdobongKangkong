package com.example.adobongkangkong.domain.logging

import com.example.adobongkangkong.domain.logging.model.AmountInput
import com.example.adobongkangkong.domain.model.LogEntry
import com.example.adobongkangkong.domain.repository.FoodNutritionSnapshotRepository
import com.example.adobongkangkong.domain.repository.FoodRepository
import com.example.adobongkangkong.domain.repository.LogRepository
import com.example.adobongkangkong.domain.usage.CheckFoodUsableUseCase
import com.example.adobongkangkong.domain.usage.FoodUsageCheck
import com.example.adobongkangkong.domain.usage.UsageContext
import java.time.Instant
import javax.inject.Inject

class CreateLogEntryUseCase @Inject constructor(
    private val foodRepository: FoodRepository,
    private val snapshotRepository: FoodNutritionSnapshotRepository,
    private val logRepository: LogRepository,
    private val checkFoodUsable: CheckFoodUsableUseCase
) {

    sealed interface Result {
        data class Success(val id: Long) : Result
        data class Blocked(val message: String) : Result
        data class Error(val message: String) : Result
    }

    suspend fun execute(
        foodId: Long,
        timestamp: Instant,
        amountInput: AmountInput
    ): Result {
        val food = foodRepository.getById(foodId)
            ?: return Result.Error("Food not found")

        // Enforce logging rules (volume → grams-per-serving)
        when (val check = checkFoodUsable.execute(
            servingUnit = food.servingUnit,
            gramsPerServing = food.gramsPerServing,
            amountInput = amountInput,
            context = UsageContext.LOGGING
        )) {
            FoodUsageCheck.Ok -> Unit
            is FoodUsageCheck.Blocked -> return Result.Blocked(check.message)
        }

        // Resolve grams eaten
        val grams = when (amountInput) {
            is AmountInput.ByGrams -> amountInput.grams
            is AmountInput.ByServings -> {
                val gps = food.gramsPerServing
                    ?: return Result.Blocked("Set grams-per-serving before logging by servings.")
                amountInput.servings * gps
            }
        }

        // Load snapshot (per-gram nutrition)
        val snapshot = snapshotRepository.getSnapshot(food.id)
            ?: return Result.Error("Nutrition snapshot unavailable")

        // Scale snapshot → actual totals
        val nutrients = snapshot.nutrientsPerGram
            ?.scaledBy(grams)
            ?: return Result.Error("Food nutrition incomplete")

        // Build immutable log entry
        val entry = LogEntry(
            timestamp = timestamp,
            foodStableId = food.stableId,
            itemName = food.name,
            nutrients = nutrients
        )

        logRepository.insert(entry)

        return Result.Success(id = 0L)
    }
}
