package com.example.adobongkangkong.data.repository

import com.example.adobongkangkong.data.local.db.dao.RecipeDao
import com.example.adobongkangkong.data.local.db.dao.RecipeIngredientDao
import com.example.adobongkangkong.data.local.db.entity.RecipeIngredientEntity
import com.example.adobongkangkong.domain.model.RecipeDraft
import com.example.adobongkangkong.domain.model.RecipeIngredientDraft
import com.example.adobongkangkong.domain.repository.RecipeDraftLookupRepository
import javax.inject.Inject

class RecipeDraftLookupRepositoryImpl @Inject constructor(
    private val recipeDao: RecipeDao,
    private val recipeIngredientDao: RecipeIngredientDao
) : RecipeDraftLookupRepository {

    override suspend fun getRecipeDraft(recipeId: Long): RecipeDraft? {
        val recipe = recipeDao.getById(recipeId) ?: return null
        val ingredients = recipeIngredientDao.getForRecipe(recipeId)

        return RecipeDraft(
            name = recipe.name,
            servingsYield = recipe.servingsYield,
            totalYieldGrams = recipe.totalYieldGrams,
            ingredients = ingredients.map { ing ->
                RecipeIngredientDraft(
                    foodId = ing.ingredientFoodId,
                    ingredientServings = ing.ingredientServings
                )
            }
        )
    }
}