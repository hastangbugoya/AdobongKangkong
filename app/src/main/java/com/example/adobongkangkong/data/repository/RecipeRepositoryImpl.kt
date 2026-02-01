package com.example.adobongkangkong.data.repository

import androidx.room.withTransaction
import com.example.adobongkangkong.data.local.db.NutriDatabase
import com.example.adobongkangkong.data.local.db.dao.RecipeDao
import com.example.adobongkangkong.data.local.db.dao.RecipeIngredientDao
import com.example.adobongkangkong.data.local.db.entity.FoodEntity
import com.example.adobongkangkong.data.local.db.entity.RecipeEntity
import com.example.adobongkangkong.data.local.db.entity.RecipeIngredientEntity
import com.example.adobongkangkong.domain.model.RecipeDraft
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.repository.RecipeHeader
import com.example.adobongkangkong.domain.repository.RecipeIngredientLine
import com.example.adobongkangkong.domain.repository.RecipeRepository
import javax.inject.Inject

class RecipeRepositoryImpl @Inject constructor(
    private val db: NutriDatabase,
    private val recipeDao: RecipeDao,
    private val ingredientDao: RecipeIngredientDao
) : RecipeRepository {

    override suspend fun createRecipe(draft: RecipeDraft): Long = db.withTransaction {
        require(draft.name.isNotBlank())
        require(draft.servingsYield > 0.0)
        require(draft.ingredients.isNotEmpty())

        val foodDao = db.foodDao()
        val recipeDao = db.recipeDao()
        val ingredientDao = db.recipeIngredientDao()

        // 1) Create Food row representing the recipe
        val recipeFoodId = foodDao.insert(
            FoodEntity(
                name = draft.name,
                brand = null,
                servingSize = 1.0,
                servingUnit = ServingUnit.SERVING,
                gramsPerServing = null,        // computed dynamically via cooked yield
                servingsPerPackage = null,
                isRecipe = true
            )
        )

        // 2) Create recipe meta
        val recipeId = recipeDao.insert(
            RecipeEntity(
                foodId = recipeFoodId,
                name = draft.name,
                servingsYield = draft.servingsYield,
                totalYieldGrams = draft.totalYieldGrams
            )
        )

        // 3) Create ingredients (servings-based for now)
        ingredientDao.insertAll(
            draft.ingredients.map { ing ->
                RecipeIngredientEntity(
                    recipeId = recipeId,
                    foodId = ing.foodId,
                    amountServings = ing.ingredientServings,
                    amountGrams = null
                )
            }
        )

        recipeId
    }

    override suspend fun getRecipeByFoodId(foodId: Long): RecipeHeader? {
        val r = recipeDao.getByFoodId(foodId) ?: return null
        return RecipeHeader(
            recipeId = r.id,
            foodId = r.foodId,
            servingsYield = r.servingsYield,
            totalYieldGrams = r.totalYieldGrams
        )
    }

    override suspend fun getIngredients(recipeId: Long): List<RecipeIngredientLine> =
        ingredientDao.getForRecipe(recipeId).map { entity ->
            RecipeIngredientLine(
                ingredientFoodId = entity.foodId,
                ingredientServings = entity.amountServings ?: 0.0
            )
        }

    override suspend fun updateRecipeByFoodId(
        foodId: Long,
        servingsYield: Double,
        totalYieldGrams: Double?,
        ingredients: List<RecipeIngredientLine>
    ) = db.withTransaction {
        val existing = recipeDao.getByFoodId(foodId) ?: return@withTransaction
        val recipeId = existing.id

        recipeDao.insert(existing.copy(servingsYield = servingsYield, totalYieldGrams = totalYieldGrams))

        ingredientDao.deleteForRecipe(recipeId)
        ingredientDao.insertAll(
            ingredients.map { line ->
                RecipeIngredientEntity(
                    recipeId = recipeId,
                    foodId = line.ingredientFoodId,
                    amountServings = line.ingredientServings,
                    amountGrams = null
                )
            }
        )
    }
}
