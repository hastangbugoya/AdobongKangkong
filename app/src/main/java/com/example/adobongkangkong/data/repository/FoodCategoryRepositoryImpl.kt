package com.example.adobongkangkong.data.repository

import com.example.adobongkangkong.data.local.db.dao.FoodCategoryDao
import com.example.adobongkangkong.data.local.db.entity.FoodCategoryCrossRefEntity
import com.example.adobongkangkong.data.local.db.entity.FoodCategoryEntity
import com.example.adobongkangkong.data.local.db.entity.RecipeCategoryCrossRefEntity
import com.example.adobongkangkong.domain.model.FoodCategory
import com.example.adobongkangkong.domain.repository.FoodCategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class FoodCategoryRepositoryImpl @Inject constructor(
    private val dao: FoodCategoryDao
) : FoodCategoryRepository {

    override suspend fun getAll(): List<FoodCategory> =
        dao.getAll().map { it.toDomain() }

    override fun observeAll(): Flow<List<FoodCategory>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeFoodIdsForCategory(categoryId: Long): Flow<Set<Long>> =
        dao.observeFoodIdsForCategory(categoryId).map { it.toSet() }

    override fun observeRecipeIdsForCategory(categoryId: Long): Flow<Set<Long>> =
        dao.observeRecipeIdsForCategory(categoryId).map { it.toSet() }

    override fun observeRecipeFoodIdsForCategory(categoryId: Long): Flow<Set<Long>> =
        dao.observeRecipeFoodIdsForCategory(categoryId).map { it.toSet() }

    override suspend fun getForFood(foodId: Long): List<FoodCategory> =
        dao.getForFood(foodId).map { it.toDomain() }

    override suspend fun getForRecipe(recipeId: Long): List<FoodCategory> =
        dao.getForRecipe(recipeId).map { it.toDomain() }

    override suspend fun getOrCreateByName(name: String): FoodCategory {
        val normalized = name.trim()
        require(normalized.isNotBlank()) { "Category name is required." }

        dao.findByName(normalized)?.let { return it.toDomain() }

        val nextSortOrder = dao.getMaxSortOrder() + 1
        val createdAt = System.currentTimeMillis()

        val id = dao.insertCategory(
            FoodCategoryEntity(
                name = normalized,
                sortOrder = nextSortOrder,
                createdAtEpochMs = createdAt,
                isSystem = false,
            )
        )

        return FoodCategory(
            id = id,
            name = normalized,
            sortOrder = nextSortOrder,
            isSystem = false,
        )
    }

    override suspend fun replaceForFood(foodId: Long, categoryIds: Set<Long>) {
        dao.deleteCrossRefsForFood(foodId)
        if (categoryIds.isEmpty()) return

        dao.insertCrossRefs(
            categoryIds.sorted().map { categoryId ->
                FoodCategoryCrossRefEntity(
                    foodId = foodId,
                    categoryId = categoryId,
                )
            }
        )
    }

    override suspend fun replaceForRecipe(recipeId: Long, categoryIds: Set<Long>) {
        dao.deleteCrossRefsForRecipe(recipeId)
        if (categoryIds.isEmpty()) return

        dao.insertRecipeCrossRefs(
            categoryIds.sorted().map { categoryId ->
                RecipeCategoryCrossRefEntity(
                    recipeId = recipeId,
                    categoryId = categoryId,
                )
            }
        )
    }

    private fun FoodCategoryEntity.toDomain(): FoodCategory =
        FoodCategory(
            id = id,
            name = name,
            sortOrder = sortOrder,
            isSystem = isSystem,
        )
}