package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.domain.model.RecipeDraft
import com.example.adobongkangkong.domain.repository.RecipeRepository
import javax.inject.Inject

/**
 * CreateRecipeUseCase
 *
 * ## Purpose
 * Creates a new persisted Recipe from a [RecipeDraft].
 *
 * ## Rationale
 * Recipe creation is a core domain operation that should remain:
 * - centralized (single entry point),
 * - testable (pure orchestration over a repository),
 * - and UI-agnostic (no navigation/state here).
 *
 * Keeping recipe creation behind a use case allows the UI layer to depend only on domain APIs
 * and keeps persistence details (Room/DAO/etc.) out of the UI.
 *
 * ## Behavior
 * - Delegates directly to [RecipeRepository.createRecipe].
 * - Returns the newly created recipeId.
 *
 * ## Parameters
 * @param draft The in-memory recipe draft used to create the persisted recipe. Must contain enough
 *              information for [RecipeRepository] to construct:
 *              - a recipe header (identity + yields), and
 *              - ingredient lines (food refs + quantities).
 *
 * ## Return
 * @return [Long] The created recipeId.
 *
 * ## Ordering and edges
 * - This use case does not validate the draft. Validation rules (if any) are expected to be:
 *   - enforced by the caller before invoking, or
 *   - enforced inside the repository layer as a final guard.
 * - This use case performs no edits/updates; it is for creation only.
 */
class CreateRecipeUseCase @Inject constructor(
    private val repo: RecipeRepository
) {

    suspend operator fun invoke(draft: RecipeDraft): Long =
        repo.createRecipe(draft)
}

/**
 * FUTURE AI ASSISTANT NOTES
 *
 * - Standard format for use cases in this codebase:
 *   1) Top KDoc for future devs (purpose/rationale/behavior/params/return/edges).
 *   2) Bottom KDoc for future AI assistant (constraints + invariants).
 *
 * - Keep this use case as a thin orchestration layer:
 *   - No Room/DAO references here.
 *   - No logging side-effects here.
 *   - No navigation/UI concerns here.
 *
 * - If future requirements add validation:
 *   - Prefer adding a separate validation use case (or pure validator) rather than bloating this.
 *   - If you must add validation here, keep it deterministic and domain-only.
 *
 * - If future requirements add “create or update” semantics:
 *   - Create a separate UpsertRecipeUseCase rather than changing this behavior silently.
 */