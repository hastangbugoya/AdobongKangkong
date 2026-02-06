package com.example.adobongkangkong.data.repository

import com.example.adobongkangkong.data.local.db.dao.FoodDao
import com.example.adobongkangkong.data.local.db.dao.FoodGoalFlagsDao
import com.example.adobongkangkong.data.local.db.dao.FoodNutrientDao
import com.example.adobongkangkong.data.local.db.dao.RecipeIngredientDao
import com.example.adobongkangkong.data.local.db.entity.FoodEntity
import com.example.adobongkangkong.data.local.db.mapper.toDomain
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.data.local.db.mapper.toEntity
import com.example.adobongkangkong.domain.logging.model.FoodRef
import com.example.adobongkangkong.domain.repository.FoodRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class FoodRepositoryImpl @Inject constructor(
    private val foodDao: FoodDao,
    private val foodNutrientDao: FoodNutrientDao,
    private val foodGoalFlagsDao: FoodGoalFlagsDao,
    private val recipeIngredientDao: RecipeIngredientDao
) : FoodRepository {

    override fun search(query: String, limit: Int): Flow<List<Food>> =
        foodDao.search(query = query, limit = limit).map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: Long): Food? =
        foodDao.getById(id)?.toDomain()

    override suspend fun upsert(food: Food): Long {
        val entity = food.toEntity()
        foodDao.upsert(entity)

        return foodDao.getIdByStableId(entity.stableId)
            ?: error("Upsert failed: no row found for stableId='${entity.stableId}'")
    }


    override suspend fun getFoodRefForLogging(foodId: Long): FoodRef.Food? {
        val entity = foodDao.getById(foodId) ?: return null

        return FoodRef.Food(foodId = entity.id)
    }

    override suspend fun deleteFood(foodId: Long): Boolean {
        // Delete owned rows first to avoid orphans (and to work even without FK cascades).
        // 🚫 Block delete if food is used by any recipe
        val usageCount = recipeIngredientDao.countRecipesUsingFood(foodId)
        if (usageCount > 0) {
            return false
        }
        foodNutrientDao.deleteForFood(foodId)
        foodGoalFlagsDao.clear(foodId)
        foodDao.deleteById(foodId)

        return true
    }

    override suspend fun isFoodsEmpty(): Boolean =
        foodDao.countFoods() == 0

}
