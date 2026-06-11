package com.example.adobongkangkong.domain.usecase.recipevariant

import com.example.adobongkangkong.domain.repository.RecipeVariantRepository
import javax.inject.Inject

class CreateRecipeVariantUseCase @Inject constructor(
    private val repository: RecipeVariantRepository,
) {
    suspend operator fun invoke(
        recipeFoodId: Long,
        name: String,
        notes: String?,
    ): Long {
        val cleanedName = name.trim()
        require(cleanedName.isNotBlank()) {
            "Variant name cannot be blank."
        }

        return repository.createVariant(
            recipeFoodId = recipeFoodId,
            name = cleanedName,
            notes = notes,
            nowEpochMillis = System.currentTimeMillis(),
        )
    }
}