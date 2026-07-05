package com.example.adobongkangkong.domain.recipes

import com.example.adobongkangkong.data.local.db.dao.RecipeMeasuredYieldDao
import com.example.adobongkangkong.data.local.db.entity.RecipeMeasuredYieldEntity
import javax.inject.Inject

/**
 * Sets the active measured cooked yield for a base recipe or recipe variant.
 *
 * This is not cooked-batch tracking. It only stores the current yield assumption
 * used to convert gram-based recipe logs into serving-equivalent nutrition.
 */
class SetActiveRecipeMeasuredYieldUseCase @Inject constructor(
    private val dao: RecipeMeasuredYieldDao
) {
    suspend fun execute(
        recipeId: Long,
        variantId: Long?,
        yieldGrams: Double,
        note: String? = null,
        updatedAtEpochMs: Long = System.currentTimeMillis()
    ): Result {
        if (recipeId <= 0L) {
            return Result.Blocked("Recipe is missing.")
        }

        if (yieldGrams <= 0.0) {
            return Result.Blocked("Measured yield must be greater than 0 g.")
        }

        val id = dao.replaceActiveYield(
            RecipeMeasuredYieldEntity(
                recipeId = recipeId,
                variantId = variantId,
                yieldGrams = yieldGrams,
                updatedAtEpochMs = updatedAtEpochMs,
                note = note?.trim()?.takeIf { it.isNotBlank() },
                isActive = true
            )
        )

        return Result.Success(id)
    }

    sealed class Result {
        data class Success(val id: Long) : Result()
        data class Blocked(val message: String) : Result()
    }
}