package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.domain.model.RecipeDraft
import com.example.adobongkangkong.domain.repository.RecipeRepository
import javax.inject.Inject

class CreateRecipeUseCase @Inject constructor(
    private val repo: RecipeRepository
) {
    suspend operator fun invoke(draft: RecipeDraft): Long = repo.createRecipe(draft)
}