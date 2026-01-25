package com.example.adobongkangkong.domain.logging

import com.example.adobongkangkong.domain.logging.model.AmountInput
import com.example.adobongkangkong.domain.model.LogEntry
import com.example.adobongkangkong.domain.repository.FoodRepository
import com.example.adobongkangkong.domain.repository.LogRepository
import com.example.adobongkangkong.domain.usage.CheckFoodUsableUseCase
import com.example.adobongkangkong.domain.usage.FoodUsageCheck
import com.example.adobongkangkong.domain.usage.UsageContext
import java.time.Instant
import javax.inject.Inject

class CreateLogEntryUseCase @Inject constructor(
    private val foodRepository: FoodRepository,
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

        // Block serving-based usage for volume/container units missing gramsPerServing
        when (val check = checkFoodUsable.execute(
            servingUnit = food.servingUnit,
            gramsPerServing = food.gramsPerServing,
            amountInput = amountInput,
            context = UsageContext.LOGGING
        )) {
            FoodUsageCheck.Ok -> Unit
            is FoodUsageCheck.Blocked -> return Result.Blocked(check.message)
        }

        val servingsToPersist = when (amountInput) {
            is AmountInput.ByServings -> amountInput.servings
            is AmountInput.ByGrams -> {
                when (food.servingUnit) {
                    com.example.adobongkangkong.domain.model.ServingUnit.G -> amountInput.grams
                    else -> {
                        val gps = food.gramsPerServing
                            ?: return Result.Blocked("Set grams-per-serving before logging by grams for this unit.")
                        amountInput.grams / gps
                    }
                }
            }
        }

        val entry = LogEntry(
            id = 0L, // assuming Room autogen; adjust if your domain model differs
            foodId = foodId,
            servings = servingsToPersist,
            timestamp = timestamp
        )

        // Your LogRepository.insert returns Unit today :contentReference[oaicite:3]{index=3},
        // so we can’t return the new row id without adding it.
        logRepository.insert(entry)

        return Result.Success(id = 0L)
    }
}
