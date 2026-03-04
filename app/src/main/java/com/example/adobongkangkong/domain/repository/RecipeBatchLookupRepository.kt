package com.example.adobongkangkong.domain.repository

import com.example.adobongkangkong.domain.logging.model.BatchSummary

/**
 * Read-only access to recipe batch context (yield grams, servings used).
 * Implemented by data layer.
 */
interface RecipeBatchLookupRepository {
    suspend fun getBatchById(batchId: Long): BatchSummary?
    suspend fun getBatchesForRecipe(recipeId: Long): List<BatchSummary>

    /** Returns the snapshot Food ID (batchFoodId) for a batch, or null if missing. */
    suspend fun getBatchFoodId(batchId: Long): Long?

    /** Bulk lookup to avoid N+1 callers (missing batches are omitted). */
    suspend fun getBatchFoodIds(batchIds: Set<Long>): Map<Long, Long>
}
