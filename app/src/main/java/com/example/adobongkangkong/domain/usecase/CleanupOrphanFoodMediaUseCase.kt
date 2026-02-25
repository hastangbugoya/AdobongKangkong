package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.domain.repository.FoodRepository
import javax.inject.Inject

/**
 * CleanupOrphanFoodMediaUseCase
 *
 * ## Purpose
 * Deletes food media (e.g., images/files) that no longer has a corresponding Food record.
 *
 * ## Rationale
 * Over time, media can become orphaned due to deletes, failed imports, partial edits, or interrupted
 * workflows. Removing orphaned media prevents storage bloat and keeps the app’s media directory
 * consistent with the database.
 *
 * ## Behavior
 * - Delegates to [FoodRepository.cleanupOrphanFoodMedia].
 * - Returns the number of orphaned media items removed.
 *
 * ## Parameters
 * - None.
 *
 * ## Return
 * - [Int]: count of deleted/cleaned orphan media items.
 *
 * ## Notes
 * - This use case performs a maintenance operation and should typically be triggered:
 *   - manually (e.g., debug tools/settings), or
 *   - on a safe cadence (e.g., after certain delete flows, or via WorkManager).
 * - This use case does not define *how* orphaned media is detected; that logic is owned by the
 *   repository implementation.
 */
class CleanupOrphanFoodMediaUseCase @Inject constructor(
    private val foods: FoodRepository
) {
    suspend operator fun invoke(): Int = foods.cleanupOrphanFoodMedia()
}

/**
 * FUTURE AI ASSISTANT NOTES
 *
 * - Standard format for use cases in this codebase:
 *   1) Top KDoc: purpose, rationale, behavior, params/return, and any rules/invariants.
 *   2) Bottom KDoc: implementation constraints and “do not break” assumptions for automation.
 *
 * - This use case is intentionally tiny and must stay that way:
 *   - No direct file I/O here.
 *   - No Room/DAO references here.
 *   - No path assumptions here.
 *   - All detection + deletion rules live behind FoodRepository.cleanupOrphanFoodMedia().
 *
 * - If changing return semantics:
 *   - Keep it as a simple count of removed items (Int).
 *   - If richer reporting is needed later, add a new data class result WITHOUT breaking callers.
 *
 * - If adding scheduling:
 *   - Do it outside this use case (WorkManager, settings action, debug screen).
 */