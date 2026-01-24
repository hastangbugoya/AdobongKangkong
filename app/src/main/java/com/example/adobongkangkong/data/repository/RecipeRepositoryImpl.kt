package com.example.adobongkangkong.data.repository

import androidx.room.withTransaction
import com.example.adobongkangkong.data.local.db.NutriDatabase
import com.example.adobongkangkong.data.local.db.dao.RecipeDao
import com.example.adobongkangkong.data.local.db.dao.RecipeIngredientDao
import com.example.adobongkangkong.data.local.db.entity.FoodEntity
import com.example.adobongkangkong.data.local.db.entity.FoodNutrientEntity
import com.example.adobongkangkong.data.local.db.entity.RecipeEntity
import com.example.adobongkangkong.data.local.db.entity.RecipeIngredientEntity
import com.example.adobongkangkong.domain.model.RecipeDraft
import com.example.adobongkangkong.domain.repository.RecipeRepository
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.repository.RecipeHeader
import com.example.adobongkangkong.domain.repository.RecipeIngredientLine
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
        val foodNutrientDao = db.foodNutrientDao()

        // 1) Create a Food row representing the recipe
        val recipeFoodId = foodDao.insert(
            FoodEntity(
                // id auto? if your FoodEntity uses @PrimaryKey without autoGenerate, switch to autoGenerate
                // If your FoodEntity is NOT auto-generate, tell me and I’ll adjust.
                name = draft.name,
                brand = null,
                servingSize = 1.0,
                servingUnit = ServingUnit.SERVING,
                gramsPerServing = null,
                servingsPerPackage = null,
                isRecipe = true
            )
        )

        // 2) Create recipe meta
        val recipeId = recipeDao.insert(
            RecipeEntity(
                foodId = recipeFoodId,
                name = draft.name,
                servingsYield = draft.servingsYield
            )
        )

        // 3) Create ingredients
        val ingredientEntities = draft.ingredients.map {
            RecipeIngredientEntity(
                recipeId = recipeId,
                ingredientFoodId = it.foodId,
                ingredientServings = it.ingredientServings
            )
        }
        ingredientDao.insertAll(ingredientEntities)

        // 4) Compute derived nutrients per serving for the recipe food
        // Collect all nutrient amounts from ingredients
        val totalByNutrientId = mutableMapOf<Long, Double>()

        for (ing in ingredientEntities) {
            val ingNutrients = foodNutrientDao.getForFood(ing.ingredientFoodId)
            for (n in ingNutrients) {
                val add = n.nutrientAmountPerBasis * ing.ingredientServings
                totalByNutrientId[n.nutrientId] = (totalByNutrientId[n.nutrientId] ?: 0.0) + add
            }
        }

        val perServing = totalByNutrientId.map { (nutrientId, totalAmount) ->
            FoodNutrientEntity(
                foodId = recipeFoodId,
                nutrientId = nutrientId,
                nutrientAmountPerBasis = totalAmount / draft.servingsYield
            )
        }

        foodNutrientDao.upsertAll(perServing)

        recipeId
    }

    override suspend fun getRecipeByFoodId(foodId: Long): RecipeHeader? {
        val r = recipeDao.getByFoodId(foodId) ?: return null
        return RecipeHeader(recipeId = r.id, foodId = r.foodId, servingsYield = r.servingsYield)
    }

    override suspend fun getIngredients(recipeId: Long): List<RecipeIngredientLine> =
        ingredientDao.getForRecipe(recipeId).map {
            RecipeIngredientLine(
                ingredientFoodId = it.ingredientFoodId,
                ingredientServings = it.ingredientServings
            )
        }

    override suspend fun updateRecipeByFoodId(
        foodId: Long,
        servingsYield: Double,
        ingredients: List<RecipeIngredientLine>
    ) {
        val existing = recipeDao.getByFoodId(foodId) ?: return
        val recipeId = existing.id

        recipeDao.insert(existing.copy(servingsYield = servingsYield))
        ingredientDao.deleteForRecipe(recipeId)
        ingredientDao.insertAll(
            ingredients.map { line ->
                RecipeIngredientEntity(
                    recipeId = recipeId,
                    ingredientFoodId = line.ingredientFoodId,
                    ingredientServings = line.ingredientServings
                )
            }
        )
    }
}
