package com.example.adobongkangkong.data.repository

import com.example.adobongkangkong.domain.repository.RecipeHeaderLookup
import com.example.adobongkangkong.domain.repository.RecipeHeader
import com.example.adobongkangkong.domain.repository.RecipeRepository
import javax.inject.Inject

class RecipeHeaderLookupImpl @Inject constructor(
    private val recipeRepository: RecipeRepository
) : RecipeHeaderLookup {

    override suspend fun getRecipeHeaderByFoodId(foodId: Long): RecipeHeader? {
        return recipeRepository.getRecipeByFoodId(foodId)
    }
}