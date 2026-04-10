package com.example.adobongkangkong.domain.planner.usecase

import androidx.room.withTransaction
import com.example.adobongkangkong.data.local.db.NutriDatabase
import javax.inject.Inject

/**
 * Resets planner-domain data only.
 *
 * =============================================================================
 * PURPOSE
 * =============================================================================
 *
 * This use case exists as a recovery valve for planner/prep development.
 *
 * Planner data is currently more experimental and more likely to become inconsistent than
 * core user data like:
 *
 * - foods
 * - recipes
 * - logged history
 *
 * This use case allows the app to wipe planner-related data without destroying the more
 * valuable and more stable domains.
 *
 *
 * =============================================================================
 * WHAT THIS RESETS
 * =============================================================================
 *
 * Planner-domain rows only:
 *
 * - planned_items
 * - planned_meals
 * - planned_series_items
 * - planned_series_slot_rules
 * - planned_series
 * - ious
 *
 *
 * =============================================================================
 * WHAT THIS PRESERVES
 * =============================================================================
 *
 * Intentionally preserved:
 *
 * - foods
 * - food nutrients
 * - recipes
 * - recipe ingredients / instructions
 * - recipe batches
 * - logs / day history
 * - barcode mappings
 * - store / price data
 * - nutrient targets / pinned nutrients
 * - other non-planner user data
 *
 *
 * =============================================================================
 * WHY THIS IS A USE CASE
 * =============================================================================
 *
 * This is domain-safe destructive behavior:
 *
 * - scoped to one domain
 * - intentional
 * - transactional
 * - not just “call a DAO from the UI”
 *
 * Keeping this as a use case ensures planner reset behavior stays centralized and easy to
 * reason about.
 *
 *
 * =============================================================================
 * TRANSACTION / SAFETY
 * =============================================================================
 *
 * The reset runs inside a single database transaction so callers never end up in a half-wiped
 * planner state.
 *
 * Delete order is delegated to DebugResetDao.clearAllPlannerData(), which is expected to
 * delete planner children before planner parents.
 *
 *
 * =============================================================================
 * IMPORTANT PRODUCT RULE
 * =============================================================================
 *
 * This is a planner-domain reset, NOT a full app reset.
 *
 * Do not expand this use case to delete foods, recipes, or logs unless a future product
 * requirement explicitly calls for a broader destructive reset.
 */
class ResetPlannerDataUseCase @Inject constructor(
    private val db: NutriDatabase
) {

    /**
     * Wipes planner-domain data only, preserving core food/recipe/logging data.
     */
    suspend operator fun invoke() {
        db.withTransaction {
            db.debugResetDao().clearAllPlannerData()
        }
    }
}

/**
 * =============================================================================
 * FOR FUTURE AI / FUTURE DEVELOPER — reset scope and invariants
 * =============================================================================
 *
 * CRITICAL SCOPE RULE
 *
 * This use case is intentionally limited to planner/prep state recovery.
 *
 * It must NOT delete:
 * - foods
 * - recipes
 * - logs
 * - pricing data
 * - barcode data
 * - nutrient/profile-like user settings
 *
 *
 * Why this exists
 *
 * Planner state is currently more volatile and easier to rebuild than the user’s historical
 * nutrition data. This gives the app a safe “start planner over” button without causing
 * catastrophic data loss.
 *
 *
 * Transaction rule
 *
 * Always keep this operation wrapped in a single Room transaction.
 *
 *
 * Delete-order rule
 *
 * Child planner rows must be cleared before parent planner rows.
 * The DAO owns the physical delete order.
 *
 *
 * If planner templates should later be included
 *
 * Do NOT silently add them here.
 * Make that an explicit product decision first, because templates may become user-authored
 * assets worth preserving.
 */