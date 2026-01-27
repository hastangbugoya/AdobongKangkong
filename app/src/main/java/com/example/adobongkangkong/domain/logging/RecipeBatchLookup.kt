package com.example.adobongkangkong.domain.logging

import com.example.adobongkangkong.domain.logging.model.BatchSummary

/**
 * Minimal read surface for recipe batch context (yield + servings used).
 * Implemented in data layer (Room / repo).
 */
//interface RecipeBatchLookup {
//    suspend fun getBatchById(batchId: Long): BatchSummary?
//    suspend fun getBatchesForRecipe(recipeId: Long): List<BatchSummary>
//}