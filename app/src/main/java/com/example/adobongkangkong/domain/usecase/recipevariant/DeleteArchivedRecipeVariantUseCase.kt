package com.example.adobongkangkong.domain.usecase.recipevariant

import com.example.adobongkangkong.domain.repository.RecipeVariantRepository
import javax.inject.Inject

class DeleteArchivedRecipeVariantUseCase @Inject constructor(
    private val repository: RecipeVariantRepository,
) {
    suspend operator fun invoke(
        variantId: Long,
    ) {
        require(variantId > 0L) {
            "Variant id is required."
        }

        repository.deleteArchivedVariant(variantId)
    }
}
