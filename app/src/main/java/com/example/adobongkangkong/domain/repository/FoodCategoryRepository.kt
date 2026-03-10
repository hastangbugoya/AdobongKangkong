package com.example.adobongkangkong.domain.repository

import com.example.adobongkangkong.domain.model.FoodCategory
import kotlinx.coroutines.flow.Flow

interface FoodCategoryRepository {
    suspend fun getAll(): List<FoodCategory>

    fun observeAll(): Flow<List<FoodCategory>>

    fun observeFoodIdsForCategory(categoryId: Long): Flow<Set<Long>>

    fun observeRecipeIdsForCategory(categoryId: Long): Flow<Set<Long>>

    fun observeRecipeFoodIdsForCategory(categoryId: Long): Flow<Set<Long>>

    suspend fun getForFood(foodId: Long): List<FoodCategory>

    suspend fun getForRecipe(recipeId: Long): List<FoodCategory>

    suspend fun getOrCreateByName(name: String): FoodCategory

    suspend fun replaceForFood(foodId: Long, categoryIds: Set<Long>)

    suspend fun replaceForRecipe(recipeId: Long, categoryIds: Set<Long>)
}