package com.example.adobongkangkong.domain.usecase.recipevariant

import com.example.adobongkangkong.domain.repository.RecipeVariantRepository
import javax.inject.Inject

class ArchiveRecipeVariantUseCase @Inject constructor(
    private val repository: RecipeVariantRepository,
) {
    suspend operator fun invoke(
        variantId: Long,
    ) {
        repository.archiveVariant(
            variantId = variantId,
            nowEpochMillis = System.currentTimeMillis(),
        )
    }
}