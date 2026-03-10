package com.example.adobongkangkong.data.repository

import com.example.adobongkangkong.data.local.db.dao.FoodCategoryDao
import com.example.adobongkangkong.data.local.db.entity.FoodCategoryCrossRefEntity
import com.example.adobongkangkong.data.local.db.entity.FoodCategoryEntity
import com.example.adobongkangkong.domain.model.FoodCategory
import com.example.adobongkangkong.domain.repository.FoodCategoryRepository
import javax.inject.Inject

class FoodCategoryRepositoryImpl @Inject constructor(
    private val dao: FoodCategoryDao
) : FoodCategoryRepository {

    override suspend fun getAll(): List<FoodCategory> =
        dao.getAll().map { it.toDomain() }

    override suspend fun getForFood(foodId: Long): List<FoodCategory> =
        dao.getForFood(foodId).map { it.toDomain() }

    override suspend fun getOrCreateByName(name: String): FoodCategory {
        val normalized = name.trim()
        require(normalized.isNotBlank()) { "Category name is required." }

        dao.findByName(normalized)?.let { return it.toDomain() }

        val id = dao.insertCategory(
            FoodCategoryEntity(
                name = normalized,
                sortOrder = dao.getMaxSortOrder() + 1,
                createdAtEpochMs = System.currentTimeMillis(),
                isSystem = false,
            )
        )

        return (dao.findByName(normalized)
            ?: FoodCategoryEntity(
                id = id,
                name = normalized,
                sortOrder = dao.getMaxSortOrder(),
                createdAtEpochMs = System.currentTimeMillis(),
                isSystem = false,
            )).toDomain()
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

    private fun FoodCategoryEntity.toDomain(): FoodCategory =
        FoodCategory(
            id = id,
            name = name,
            sortOrder = sortOrder,
            isSystem = isSystem,
        )
}
