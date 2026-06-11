package com.example.adobongkangkong.domain.usecase.recipevariant

import com.example.adobongkangkong.data.local.db.entity.RecipeVariantEntity
import com.example.adobongkangkong.domain.repository.RecipeVariantRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveRecipeVariantsForRecipeUseCase @Inject constructor(
    private val repository: RecipeVariantRepository,
) {
    operator fun invoke(
        recipeFoodId: Long,
    ): Flow<List<RecipeVariantEntity>> {
        return repository.observeVariantsForRecipe(recipeFoodId)
    }
}