package com.example.adobongkangkong.domain.usecase.recipevariant

import com.example.adobongkangkong.domain.repository.RecipeVariantRepository
import javax.inject.Inject

class UpdateRecipeVariantUseCase @Inject constructor(
    private val repository: RecipeVariantRepository,
) {
    suspend operator fun invoke(
        variantId: Long,
        name: String,
        notes: String?,
    ) {
        val cleanedName = name.trim()

        require(cleanedName.isNotBlank()) {
            "Variant name cannot be blank."
        }

        val existing = repository.getVariantById(variantId)
            ?: error("Variant not found.")

        repository.updateVariant(
            existing.copy(
                name = cleanedName,
                notes = notes?.trim()?.takeIf { it.isNotBlank() },
                updatedAtEpochMillis = System.currentTimeMillis(),
            )
        )
    }
}