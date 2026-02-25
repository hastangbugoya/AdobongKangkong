package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.domain.model.Nutrient
import com.example.adobongkangkong.domain.repository.NutrientRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Searches nutrients by query string and returns a reactive list of matching [Nutrient] items.
 *
 * ## Purpose
 * Provide a domain-level abstraction for nutrient search so UI layers can retrieve
 * matching nutrients without depending directly on repository implementation details.
 *
 * ## Rationale (why this use case exists)
 * Nutrient search is used in:
 * - Food editor nutrient selection
 * - Custom nutrient assignment flows
 * - Target configuration screens
 *
 * Centralizing search access through a use case:
 * - Preserves Clean Architecture boundaries (UI → Domain → Repository),
 * - Allows future enhancements such as ranking, alias resolution, pinned nutrients,
 *   or fuzzy matching without modifying UI call sites,
 * - Ensures search semantics remain consistent across the app.
 *
 * ## Behavior
 * - Delegates directly to [NutrientRepository.search].
 * - Emits a reactive [Flow] of matching nutrients.
 * - Applies a default limit of 50 results unless explicitly overridden.
 * - Does not mutate, filter, or post-process repository results.
 *
 * ## Parameters
 * - `query`: Raw user-entered search string.
 * - `limit`: Maximum number of results to return (default = 50).
 *
 * ## Return
 * A [Flow] emitting a list of [Nutrient] objects matching the query. The flow updates
 * whenever the underlying repository data changes.
 *
 * ## Edge cases
 * - Blank or whitespace query behavior is defined by the repository implementation.
 * - If no matches are found, emits an empty list.
 * - Large datasets rely on repository-side indexing and query performance strategies.
 *
 * ## Pitfalls / gotchas
 * - This use case intentionally does not normalize or preprocess the query.
 *   That responsibility belongs in the repository or a dedicated normalization layer.
 * - Avoid adding UI-specific filtering here (e.g., hiding certain nutrient types)
 *   unless such filtering becomes an explicit domain rule.
 *
 * ## Architectural rules
 * - Pure read operation.
 * - No DB or Room logic in this layer.
 * - No UI state mutation.
 * - No side effects.
 */
class SearchNutrientsUseCase @Inject constructor(
    private val repo: NutrientRepository
) {
    operator fun invoke(query: String, limit: Int = 50): Flow<List<Nutrient>> =
        repo.search(query, limit)
}

/**
 * FUTURE-AI / MAINTENANCE KDoc (Do not remove)
 *
 * ## Invariants (must not change)
 * - Must remain a thin delegation layer over [NutrientRepository.search].
 * - Must return a Flow (reactive stream), not a suspend one-shot call.
 * - Must not mutate or transform nutrient data in this layer.
 *
 * ## Do not refactor notes
 * - Do not inline repository access into UI; keep this domain boundary intact.
 * - Do not introduce ranking, filtering, or sorting logic here unless it becomes
 *   a formal domain rule shared across all search call sites.
 * - Do not hardcode assumptions about search semantics; repository defines them.
 *
 * ## Architectural boundaries
 * - UI must call this use case instead of the repository directly.
 * - Repository owns query implementation details (SQL, FTS, indexing, ranking).
 * - Domain layer must not depend on database APIs.
 *
 * ## Migration notes (KMP / time APIs)
 * - No time or platform-specific APIs used.
 * - If migrated to KMP shared domain, ensure repository abstraction remains platform-agnostic.
 *
 * ## Performance considerations
 * - Performance depends on repository implementation and indexing strategy.
 * - Default limit (50) prevents excessive result emissions.
 * - Avoid increasing default limit without evaluating UI rendering and memory impact.
 */