package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.domain.logging.CreateLogEntryUseCase
import com.example.adobongkangkong.domain.logging.model.AmountInput
import com.example.adobongkangkong.domain.logging.model.FoodRef
import java.time.Instant
import javax.inject.Inject

/**
 * LogFoodUseCase
 *
 * ## Purpose
 * Provides a small, UI-friendly wrapper around [CreateLogEntryUseCase] for the most common logging
 * actions (food/recipe by grams or servings).
 *
 * ## Rationale
 * Many UI call sites only have:
 * - a foodId (for foods), or
 * - a pre-resolved [FoodRef.Recipe] + recipeBatchId (for recipes),
 * plus a user-entered amount.
 *
 * This wrapper:
 * - standardizes the `AmountInput` creation,
 * - provides sensible defaults (e.g., `timestamp = Instant.now()`),
 * - keeps UI code focused on interaction and not domain wiring.
 *
 * ## Agreed rules / responsibilities
 * This wrapper is intentionally thin and must not duplicate core logging logic.
 *
 * **CreateLogEntryUseCase is the source of truth for:**
 * 1) Loading the Food/Recipe context needed for logging
 * 2) Enforcing logging rules (e.g., grams-per-serving required when logging by servings)
 * 3) Reading nutrition snapshots and scaling totals
 * 4) Persisting an immutable log entry with:
 *    - `logDateIso` for day membership
 *    - `timestamp` for ordering/posterity
 *
 * **This wrapper is responsible only for:**
 * - translating UI inputs into `AmountInput`
 * - passing through `logDateIso`, `timestamp`, and batch context when applicable
 *
 * ## Parameters overview
 * - `foodId` is used for foods because [FoodRef.Food] is ID-only in this project.
 * - `recipeRef` must already be a valid [FoodRef.Recipe] (caller resolves displayName/stableId).
 * - `recipeBatchId` is required for recipe logging paths (batch context is mandatory).
 * - `logDateIso` (yyyy-MM-dd) is REQUIRED and is the authoritative day bucket for Day Log display.
 * - `timestamp` is optional (defaults to now) and is used for ordering/posterity only.
 */
class LogFoodUseCase @Inject constructor(
    private val createLogEntry: CreateLogEntryUseCase
) {

    /**
     * Logs a Food by servings.
     *
     * @param foodId Database id of the food.
     * @param servings Number of servings to log.
     * @param timestamp Optional instant for ordering/posterity (defaults to now).
     * @param logDateIso ISO day string (yyyy-MM-dd) used for day membership.
     */
    suspend fun logFoodByServings(
        foodId: Long,
        servings: Double,
        timestamp: Instant = Instant.now(),
        logDateIso: String,
    ): CreateLogEntryUseCase.Result {
        return createLogEntry.execute(
            ref = FoodRef.Food(foodId),
            timestamp = timestamp,
            amountInput = AmountInput.ByServings(servings),
            logDateIso = logDateIso
        )
    }

    /**
     * Logs a Food by grams.
     *
     * @param foodId Database id of the food.
     * @param grams Grams to log.
     * @param timestamp Optional instant for ordering/posterity (defaults to now).
     * @param logDateIso ISO day string (yyyy-MM-dd) used for day membership.
     */
    suspend fun logFoodByGrams(
        foodId: Long,
        grams: Double,
        timestamp: Instant = Instant.now(),
        logDateIso: String
    ): CreateLogEntryUseCase.Result {
        return createLogEntry.execute(
            ref = FoodRef.Food(foodId),
            timestamp = timestamp,
            amountInput = AmountInput.ByGrams(grams),
            logDateIso = logDateIso
        )
    }

    /**
     * Logs a Recipe (via cooked batch context) by servings.
     *
     * @param recipeRef Pre-resolved recipe reference (includes stableId + displayName).
     * @param servings Number of servings to log.
     * @param recipeBatchId Cooked batch id required to determine cooked-yield context.
     * @param timestamp Optional instant for ordering/posterity (defaults to now).
     * @param logDateIso ISO day string (yyyy-MM-dd) used for day membership.
     */
    suspend fun logRecipeByServings(
        recipeRef: FoodRef.Recipe,
        servings: Double,
        recipeBatchId: Long,
        timestamp: Instant = Instant.now(),
        logDateIso: String
    ): CreateLogEntryUseCase.Result {
        return createLogEntry.execute(
            ref = recipeRef,
            timestamp = timestamp,
            amountInput = AmountInput.ByServings(servings),
            recipeBatchId = recipeBatchId,
            logDateIso = logDateIso
        )
    }

    /**
     * Logs a Recipe (via cooked batch context) by grams.
     *
     * @param recipeRef Pre-resolved recipe reference (includes stableId + displayName).
     * @param grams Grams to log.
     * @param recipeBatchId Cooked batch id required to determine cooked-yield context.
     * @param timestamp Optional instant for ordering/posterity (defaults to now).
     * @param logDateIso ISO day string (yyyy-MM-dd) used for day membership.
     */
    suspend fun logRecipeByGrams(
        recipeRef: FoodRef.Recipe,
        grams: Double,
        recipeBatchId: Long,
        timestamp: Instant = Instant.now(),
        logDateIso: String
    ): CreateLogEntryUseCase.Result {
        return createLogEntry.execute(
            ref = recipeRef,
            timestamp = timestamp,
            amountInput = AmountInput.ByGrams(grams),
            recipeBatchId = recipeBatchId,
            logDateIso = logDateIso
        )
    }
}

/**
 * FUTURE AI ASSISTANT NOTES
 *
 * - This file follows the “two KDocs” standard:
 *   - Top KDoc: dev-facing rationale, responsibilities, and parameter semantics.
 *   - Bottom KDoc: constraints and invariants for automated edits.
 *
 * - Keep this as a thin wrapper.
 *   Do NOT:
 *   - load foods/recipes here,
 *   - compute nutrients here,
 *   - enforce grams-per-serving rules here,
 *   - introduce DB/DAO dependencies here.
 *
 * - `logDateIso` is authoritative for day membership.
 *   Never switch these wrapper calls to timestamp-range based day filtering.
 *
 * - If additional logging variants are added:
 *   - prefer new small wrapper methods here only if they reduce UI boilerplate,
 *   - otherwise call CreateLogEntryUseCase directly from the ViewModel.
 *
 * - If migrating toward KMP:
 *   - `java.time.Instant` is JVM-only. Replace with `kotlinx.datetime.Instant` only when this
 *     use case is moved into shared code.
 */