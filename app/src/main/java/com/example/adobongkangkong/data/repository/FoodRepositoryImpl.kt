package com.example.adobongkangkong.data.repository

import android.util.Log
import com.example.adobongkangkong.data.local.db.dao.FoodDao
import com.example.adobongkangkong.data.local.db.dao.FoodGoalFlagsDao
import com.example.adobongkangkong.data.local.db.dao.FoodNutrientDao
import com.example.adobongkangkong.data.local.db.dao.LogEntryDao
import com.example.adobongkangkong.data.local.db.dao.PlannedItemDao
import com.example.adobongkangkong.data.local.db.dao.RecipeBatchDao
import com.example.adobongkangkong.data.local.db.dao.RecipeIngredientDao
import com.example.adobongkangkong.data.local.db.mapper.toDomain
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.data.local.db.mapper.toEntity
import com.example.adobongkangkong.domain.logging.model.FoodRef
import com.example.adobongkangkong.domain.model.FoodHardDeleteBlockers
import com.example.adobongkangkong.domain.planner.model.PlannedItemSource
import com.example.adobongkangkong.domain.repository.FoodRepository
import com.example.adobongkangkong.feature.camera.FoodImageStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

class FoodRepositoryImpl @Inject constructor(
    private val foodDao: FoodDao,
    private val foodNutrientDao: FoodNutrientDao,
    private val foodGoalFlagsDao: FoodGoalFlagsDao,
    private val recipeIngredientDao: RecipeIngredientDao,
    private val logEntryDao: LogEntryDao,
    private val plannedItemDao: PlannedItemDao,
    private val recipeBatchDao: RecipeBatchDao,
    private val foodImageStorage: FoodImageStorage
) : FoodRepository {

    override fun search(query: String, limit: Int): Flow<List<Food>> =
        foodDao.search(query = query, limit = limit).map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: Long): Food? =
        foodDao.getById(id)?.toDomain()

    override suspend fun upsert(food: Food): Long {
        val entity = food.toEntity()
        Log.d("Meow","UPSERT > Food: ${food.name} >stableId=${entity.stableId} gtin=${entity.usdaGtinUpc} fdc=${entity.usdaFdcId} pub=${entity.usdaPublishedDate} mod=${entity.usdaModifiedDate}")

        foodDao.upsert(entity)

        return foodDao.getIdByStableId(entity.stableId)
            ?: error("Upsert failed: no row found for stableId='${entity.stableId}'")
    }

    override suspend fun getFoodRefForLogging(foodId: Long): FoodRef.Food? {
        val entity = foodDao.getById(foodId) ?: return null
        return FoodRef.Food(foodId = entity.id)
    }

    /**
     * Default delete behavior: SOFT delete.
     *
     * Kept for compatibility with existing callers.
     */
    override suspend fun deleteFood(foodId: Long): Boolean {
        val now = System.currentTimeMillis()
        foodDao.softDeleteById(id = foodId, deletedAtEpochMs = now)
        return true
    }

    override suspend fun softDeleteFood(foodId: Long) {
        // Idempotent: if missing, no-op.
        val now = Instant.now().toEpochMilli()
        foodDao.softDeleteById(id = foodId, deletedAtEpochMs = now)
    }

    override suspend fun getFoodHardDeleteBlockers(foodId: Long): FoodHardDeleteBlockers {
        val entity = foodDao.getById(foodId)
            ?: return FoodHardDeleteBlockers(
                isRecipeFood = false,
                logsUsingStableId = 0,
                plannedItemsUsingFoodId = 0,
                recipeIngredientsUsingFoodId = 0,
                recipeBatchesUsingBatchFoodId = 0
            )

        val stableId = entity.stableId

        // Logs reference stableId (not foodId)
        val logsCount = logEntryDao.countByFoodStableId(stableId)

        // Planner references (FOOD refId is foodId)
        val plannedCount = plannedItemDao.countByTypeAndRefId(
            type = PlannedItemSource.FOOD,
            refId = foodId
        )

        // Food used as ingredient
        val ingredientCount = recipeIngredientDao.countRecipesUsingFood(foodId)

        // Food used as a batch snapshot
        val batchSnapshotCount = recipeBatchDao.countByBatchFoodId(foodId)

        // IMPORTANT: Hard delete for recipe-foods needs RecipeDao cleanup too.
        // We refuse hard delete here rather than guessing.
        val isRecipeFood = entity.isRecipe

        return FoodHardDeleteBlockers(
            isRecipeFood = isRecipeFood,
            logsUsingStableId = logsCount,
            plannedItemsUsingFoodId = plannedCount,
            recipeIngredientsUsingFoodId = ingredientCount,
            recipeBatchesUsingBatchFoodId = batchSnapshotCount
        )
    }

    override suspend fun hardDeleteFood(foodId: Long) {
        // Execute-only. Policy enforced by use case.

        // Delete owned rows first to avoid orphans (and to work even without FK cascades).
        foodNutrientDao.deleteForFood(foodId)
        foodGoalFlagsDao.clear(foodId)

        // Delete food row
        foodDao.deleteById(foodId)

        // Media cleanup (best-effort)
        foodImageStorage.deleteBanner(foodId)
    }

    override suspend fun isFoodsEmpty(): Boolean =
        foodDao.countFoods() == 0

    override suspend fun cleanupOrphanFoodMedia(): Int {
        var deletedCount = 0

        // 1) Clean cache blur derivatives (safe to wipe)
        deletedCount += foodImageStorage.deleteAllBlurCache()

        // 2) Clean orphan banners in filesDir (only if food id no longer exists)
        val orphanIds = foodImageStorage.findFoodIdsWithBannerInFilesDir()
        if (orphanIds.isEmpty()) return deletedCount

        val existing = foodDao.getExistingFoodIds(orphanIds).toSet()
        val trulyOrphan = orphanIds.filter { it !in existing }

        trulyOrphan.forEach { id ->
            deletedCount += foodImageStorage.deleteAllFoodMediaDirs(id)
        }

        return deletedCount
    }

}

