package com.example.adobongkangkong.domain.usecase.recipevariant

import com.example.adobongkangkong.data.local.db.entity.RecipeVariantIngredientChangeEntity
import com.example.adobongkangkong.data.local.db.entity.RecipeVariantIngredientChangeType
import com.example.adobongkangkong.domain.repository.RecipeVariantRepository
import javax.inject.Inject

class SaveRecipeVariantChangesUseCase @Inject constructor(
    private val repository: RecipeVariantRepository,
) {
    suspend operator fun invoke(
        variantId: Long,
        changes: List<RecipeVariantIngredientChangeEntity>,
    ) {
        require(variantId > 0L) {
            "Variant id is required."
        }

        val now = System.currentTimeMillis()

        val cleanedChanges = changes.mapIndexed { index, change ->
            validateChange(change)

            change.copy(
                id = 0L,
                variantId = variantId,
                note = change.note?.trim()?.takeIf { it.isNotBlank() },
                sortOrder = index,
                createdAtEpochMillis = if (change.createdAtEpochMillis > 0L) {
                    change.createdAtEpochMillis
                } else {
                    now
                },
                updatedAtEpochMillis = now,
            )
        }

        repository.replaceChangesForVariant(
            variantId = variantId,
            changes = cleanedChanges,
        )

        repository.updateVariantNutritionSnapshot(
            variantId = variantId,
            nutrientsJsonSnapshot = null,
            nowEpochMillis = now,
        )
    }

    private fun validateChange(
        change: RecipeVariantIngredientChangeEntity,
    ) {
        require(change.changeType in RecipeVariantIngredientChangeType.validValues) {
            "Invalid variant ingredient change type."
        }

        when (change.changeType) {
            RecipeVariantIngredientChangeType.ADD -> {
                require(change.foodId != null && change.foodId > 0L) {
                    "Added ingredient must have a food id."
                }

                require(change.baseRecipeIngredientId == null) {
                    "Added ingredient cannot point to a base recipe ingredient."
                }

                require(hasExactlyOneAmount(change)) {
                    "Added ingredient must use either servings or grams."
                }
            }

            RecipeVariantIngredientChangeType.REMOVE -> {
                require(change.baseRecipeIngredientId != null && change.baseRecipeIngredientId > 0L) {
                    "Removed ingredient must point to a base recipe ingredient."
                }

                require(change.foodId == null) {
                    "Removed ingredient should not have a food id."
                }

                require(change.servings == null && change.grams == null) {
                    "Removed ingredient should not have an amount."
                }
            }

            RecipeVariantIngredientChangeType.ADJUST -> {
                require(change.baseRecipeIngredientId != null && change.baseRecipeIngredientId > 0L) {
                    "Adjusted ingredient must point to a base recipe ingredient."
                }

                require(change.foodId == null) {
                    "Adjusted ingredient should not have a food id."
                }

                require(hasExactlyOneAmount(change)) {
                    "Adjusted ingredient must use either servings or grams."
                }
            }
        }
    }

    private fun hasExactlyOneAmount(
        change: RecipeVariantIngredientChangeEntity,
    ): Boolean {
        val hasServings = change.servings != null
        val hasGrams = change.grams != null

        if (hasServings == hasGrams) return false

        change.servings?.let { servings ->
            if (servings <= 0.0) return false
        }

        change.grams?.let { grams ->
            if (grams <= 0.0) return false
        }

        return true
    }
}
