package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.domain.logging.CreateLogEntryUseCase
import com.example.adobongkangkong.domain.logging.model.AmountInput
import com.example.adobongkangkong.domain.logging.model.FoodRef
import java.time.Instant
import javax.inject.Inject

/**
 * Wrapper for logging that keeps UI call sites simple.
 *
 * Important:
 * - [FoodRef.Food] is an ID-only reference in this project.
 * - [CreateLogEntryUseCase] is responsible for:
 *   1) Loading the Food by id
 *   2) Enforcing "grams-per-serving required when logging by servings"
 *   3) Loading the per-gram snapshot and scaling nutrients
 *   4) Persisting an immutable [LogEntry]
 *
 * So this wrapper should NOT attempt to "build" a richer FoodRef.
 */
class LogFoodUseCase @Inject constructor(
    private val createLogEntry: CreateLogEntryUseCase
) {
    suspend fun logFoodByServings(
        foodId: Long,
        servings: Double,
        timestamp: Instant = Instant.now()
    ): CreateLogEntryUseCase.Result {
        return createLogEntry.execute(
            ref = FoodRef.Food(foodId),
            timestamp = timestamp,
            amountInput = AmountInput.ByServings(servings)
        )
    }

    suspend fun logFoodByGrams(
        foodId: Long,
        grams: Double,
        timestamp: Instant = Instant.now()
    ): CreateLogEntryUseCase.Result {
        return createLogEntry.execute(
            ref = FoodRef.Food(foodId),
            timestamp = timestamp,
            amountInput = AmountInput.ByGrams(grams)
        )
    }

    suspend fun logRecipeByServings(
        recipeRef: FoodRef.Recipe,
        servings: Double,
        recipeBatchId: Long,
        timestamp: Instant = Instant.now()
    ): CreateLogEntryUseCase.Result {
        return createLogEntry.execute(
            ref = recipeRef,
            timestamp = timestamp,
            amountInput = AmountInput.ByServings(servings),
            recipeBatchId = recipeBatchId
        )
    }

    suspend fun logRecipeByGrams(
        recipeRef: FoodRef.Recipe,
        grams: Double,
        recipeBatchId: Long,
        timestamp: Instant = Instant.now()
    ): CreateLogEntryUseCase.Result {
        return createLogEntry.execute(
            ref = recipeRef,
            timestamp = timestamp,
            amountInput = AmountInput.ByGrams(grams),
            recipeBatchId = recipeBatchId
        )
    }
}
