package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.domain.logging.CreateLogEntryUseCase
import com.example.adobongkangkong.domain.logging.model.AmountInput
import java.time.Instant
import javax.inject.Inject

/**
 * Legacy-friendly wrapper for logging a food by servings.
 *
 * Uses [CreateLogEntryUseCase] so logs are persisted as immutable snapshot totals
 * (name + stableId + nutrients), not recomputed later.
 */
class LogFoodUseCase @Inject constructor(
    private val createLogEntry: CreateLogEntryUseCase
) {

    suspend operator fun invoke(
        foodId: Long,
        servings: Double,
        timestamp: Instant = Instant.now()
    ) {
        createLogEntry.execute(
            foodId = foodId,
            timestamp = timestamp,
            amountInput = AmountInput.ByServings(servings)
        )
        // If you want to surface warnings/errors, change return type and bubble up Result.
    }
}
