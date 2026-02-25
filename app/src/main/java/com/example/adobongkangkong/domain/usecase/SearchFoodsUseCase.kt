package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.repository.FoodRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Searches foods by query string and returns a reactive list of matching [Food] items.
 *
 * ## Purpose
 * Provide a domain-level entry point for food search that delegates to the repository
 * while keeping UI layers decoupled from persistence details.
 *
 * ## Rationale (why this use case exists)
 * Food search is a cross-cutting capability used by:
 * - Food picker screens
 * - Log entry flows
 * - Planner/meal selection
 * - Barcode fallback assignment
 *
 * Centralizing the call through a use case:
 * - Preserves clean architecture boundaries (UI → Domain → Repository),
 * - Allows future introduction of ranking, normalization, or filtering rules
 *   without touching UI call sites,
 * - Keeps repository contracts stable and abstracted.
 *
 * ## Behavior
 * - Delegates the search operation to [FoodRepository.search].
 * - Emits a reactive [Flow] of matching foods.
 * - Applies a default limit of 50 results unless overridden.
 * - Does not modify, sort, or post-process results beyond repository behavior.
 *
 * ## Parameters
 * - `query`: Raw search string entered by the user.
 * - `limit`: Maximum number of results to return (default = 50).
 *
 * ## Return
 * A [Flow] emitting a list of [Food] objects matching the query. The flow updates
 * whenever the underlying repository data changes.
 *
 * ## Edge cases
 * - Blank or whitespace query behavior is defined by the repository.
 * - If no matches are found, emits an empty list.
 * - Large datasets rely on repository-side indexing and query optimization.
 *
 * ## Pitfalls / gotchas
 * - This use case intentionally does not sanitize or normalize the query;
 *   that responsibility belongs in the repository or a future search-normalization layer.
 * - Avoid adding UI-specific filtering here (e.g., hiding archived foods).
 *
 * ## Architectural rules
 * - Pure read operation.
 * - No joins or DB logic in this layer.
 * - No UI state mutation.
 * - No side effects.
 */
class SearchFoodsUseCase @Inject constructor(
    private val repo: FoodRepository
) {
    operator fun invoke(query: String, limit: Int = 50): Flow<List<Food>> =
        repo.search(query, limit)
}

/**
 * FUTURE-AI / MAINTENANCE KDoc (Do not remove)
 *
 * ## Invariants (must not change)
 * - This use case must remain a thin delegation layer over [FoodRepository.search].
 * - Must return a Flow (reactive stream), not a suspend one-shot call.
 * - Must not mutate or transform food data beyond safe projection rules.
 *
 * ## Do not refactor notes
 * - Do not move query normalization logic into UI.
 * - Do not introduce filtering (e.g., by food type, recipe flag, etc.) here unless
 *   it becomes a defined domain rule.
 * - Do not hardcode sorting assumptions unless made explicit in repository contract.
 *
 * ## Architectural boundaries
 * - UI must call this use case instead of the repository directly.
 * - Repository defines search semantics (SQL, FTS, ranking).
 * - Domain layer must not depend on DB/Room APIs.
 *
 * ## Migration notes (KMP / time APIs)
 * - No time or platform-specific APIs used.
 * - If moved to shared KMP domain, ensure repository abstraction remains platform-agnostic.
 *
 * ## Performance considerations
 * - Performance is repository-dependent (indexing, FTS, query plan).
 * - Default limit (50) exists to prevent large result emissions.
 * - Avoid increasing default limit without evaluating UI rendering cost.
 */