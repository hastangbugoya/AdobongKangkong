package com.example.adobongkangkong.domain.repository

import com.example.adobongkangkong.domain.model.RecipeDraft

/**
 * Loads a recipe as a RecipeDraft (definition + ingredients) for nutrition computation.
 * Implemented by data layer.
 */
interface RecipeDraftLookupRepository {
    suspend fun getRecipeDraft(recipeId: Long): RecipeDraft?
}