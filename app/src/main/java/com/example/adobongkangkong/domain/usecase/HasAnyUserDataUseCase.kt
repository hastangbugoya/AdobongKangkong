package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.data.local.db.NutriDatabase
import javax.inject.Inject

/**
 * HasAnyUserDataUseCase
 *
 * ## Purpose
 * Determines whether the database currently contains any user-created core data.
 *
 * ## Rationale
 * Some flows (e.g., onboarding, reset confirmation, export eligibility, destructive actions)
 * need to know whether the user has meaningful content stored.
 *
 * Rather than scattering count checks across UI or repository layers, this use case centralizes
 * the definition of “any user data” into a single decision point.
 *
 * ## Definition of "User Data"
 * Currently defined as:
 * - At least one Food exists, OR
 * - At least one Recipe exists.
 *
 * If either condition is true, the app is considered to contain user data.
 *
 * ## Behavior
 * - Calls `foodDao().countFoods()`
 * - Calls `recipeDao().countRecipes()`
 * - Returns true if either count is > 0.
 *
 * ## Parameters
 * - None.
 *
 * ## Return
 * @return Boolean
 * - true → database contains user-created content
 * - false → database is effectively empty of core user data
 *
 * ## Assumptions / limitations
 * - This does NOT check:
 *   - Logs
 *   - Planned meals
 *   - Series
 *   - Recipe batches
 *   - Barcode mappings
 * - The definition of “user data” is intentionally minimal and centered on core authorable
 *   entities (Food + Recipe).
 *
 * If future flows require a broader definition of user data, extend this use case rather than
 * duplicating logic elsewhere.
 */
class HasAnyUserDataUseCase @Inject constructor(
    private val db: NutriDatabase
) {

    suspend operator fun invoke(): Boolean {
        val foods = db.foodDao().countFoods()
        val recipes = db.recipeDao().countRecipes()
        return foods > 0 || recipes > 0
    }
}

/**
 * FUTURE AI ASSISTANT NOTES
 *
 * - Standard documentation pattern:
 *   1) Top KDoc: dev-facing purpose, definition, assumptions, return semantics.
 *   2) Bottom KDoc: invariants and constraints for automated edits.
 *
 * - This use case intentionally touches the database directly.
 *   It is a lightweight, read-only aggregation check and does not justify a separate repository
 *   abstraction unless KMP migration becomes a priority.
 *
 * - If migrating to KMP:
 *   - Replace NutriDatabase direct access with repository-based counts.
 *   - Avoid Room-specific imports in shared modules.
 *
 * - If the definition of “any user data” expands:
 *   - Update this use case only.
 *   - Do NOT replicate count logic across ViewModels.
 *
 * - Keep this deterministic and side-effect free.
 */