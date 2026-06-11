package com.example.adobongkangkong.domain.usecase.recipevariant

import com.example.adobongkangkong.domain.repository.RecipeVariantRepository
import javax.inject.Inject

class RestoreRecipeVariantUseCase @Inject constructor(
    private val repository: RecipeVariantRepository,
) {
    suspend operator fun invoke(
        variantId: Long,
    ) {
        repository.restoreVariant(
            variantId = variantId,
            nowEpochMillis = System.currentTimeMillis(),
        )
    }
}