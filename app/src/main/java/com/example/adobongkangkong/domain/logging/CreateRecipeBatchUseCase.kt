package com.example.adobongkangkong.domain.logging

import java.time.Instant
import javax.inject.Inject

class CreateRecipeBatchUseCase @Inject constructor(
    private val recipeBatchWriter: RecipeBatchWriter
) {
    sealed interface Result {
        data class Success(val batchId: Long) : Result
        data class Error(val message: String) : Result
    }

    suspend fun execute(
        recipeId: Long,
        cookedYieldGrams: Double,
        servingsYieldUsed: Double?,
        createdAt: Instant = Instant.now()
    ): Result {
        if (cookedYieldGrams <= 0.0) return Result.Error("Cooked yield must be > 0g")

        return runCatching {
            val id = recipeBatchWriter.createBatch(
                recipeId = recipeId,
                cookedYieldGrams = cookedYieldGrams,
                servingsYieldUsed = servingsYieldUsed,
                createdAt = createdAt
            )
            Result.Success(id)
        }.getOrElse { Result.Error(it.message ?: "Failed to create recipe batch") }
    }
}

/**
 * Minimal write surface for creating a batch + ensuring its nutrition snapshot exists.
 * Implemented by data layer.
 */
interface RecipeBatchWriter {
    suspend fun createBatch(
        recipeId: Long,
        cookedYieldGrams: Double,
        servingsYieldUsed: Double?,
        createdAt: Instant
    ): Long
}
