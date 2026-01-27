package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.domain.logging.CreateLogEntryUseCase
import com.example.adobongkangkong.domain.logging.model.AmountInput
import com.example.adobongkangkong.domain.logging.model.FoodRef
import java.time.Instant
import javax.inject.Inject

/**
 * Legacy-friendly wrapper for logging by servings.
 *
 * Uses [CreateLogEntryUseCase] so logs are persisted as immutable snapshot totals
 * (name + stableId + nutrients), not recomputed later.
 */
class LogFoodUseCase @Inject constructor(
    private val createLogEntry: CreateLogEntryUseCase
) {

    suspend operator fun invoke(
        ref: FoodRef,
        servings: Double,
        timestamp: Instant = Instant.now(),
        recipeBatchId: Long? = null
    ): CreateLogEntryUseCase.Result {
        return createLogEntry.execute(
            ref = ref,
            timestamp = timestamp,
            amountInput = AmountInput.ByServings(servings),
            recipeBatchId = recipeBatchId
        )
    }
}