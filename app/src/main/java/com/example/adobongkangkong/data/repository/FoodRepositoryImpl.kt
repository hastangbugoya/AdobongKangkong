package com.example.adobongkangkong.data.repository

import android.util.Log
import com.example.adobongkangkong.data.local.db.dao.FoodDao
import com.example.adobongkangkong.data.local.db.dao.FoodGoalFlagsDao
import com.example.adobongkangkong.data.local.db.dao.FoodNutrientDao
import com.example.adobongkangkong.data.local.db.dao.FoodStorePriceDao
import com.example.adobongkangkong.data.local.db.dao.LogEntryDao
import com.example.adobongkangkong.data.local.db.dao.PlannedItemDao
import com.example.adobongkangkong.data.local.db.dao.RecipeBatchDao
import com.example.adobongkangkong.data.local.db.dao.RecipeIngredientDao
import com.example.adobongkangkong.data.local.db.entity.FoodStorePriceEntity
import com.example.adobongkangkong.data.local.db.mapper.toDomain
import com.example.adobongkangkong.data.local.db.mapper.toEntity
import com.example.adobongkangkong.domain.logging.model.FoodRef
import com.example.adobongkangkong.domain.model.Food
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
    private val foodStorePriceDao: FoodStorePriceDao,
    private val foodImageStorage: FoodImageStorage
) : FoodRepository {

    override fun search(query: String, limit: Int): Flow<List<Food>> =
        foodDao.search(query = query, limit = limit).map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: Long): Food? =
        foodDao.getById(id)?.toDomain()

    override suspend fun getByStableId(stableId: String): Food? {
        return foodDao.getByStableId(stableId)?.toDomain()
    }

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

    override suspend fun deleteFood(foodId: Long): Boolean {
        val now = System.currentTimeMillis()
        foodDao.softDeleteById(id = foodId, deletedAtEpochMs = now)
        return true
    }

    override suspend fun softDeleteFood(foodId: Long) {
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

        val logsCount = logEntryDao.countByFoodStableId(stableId)

        val plannedCount = plannedItemDao.countByTypeAndRefId(
            type = PlannedItemSource.FOOD,
            refId = foodId
        )

        val ingredientCount = recipeIngredientDao.countRecipesUsingFood(foodId)

        val batchSnapshotCount = recipeBatchDao.countByBatchFoodId(foodId)

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
        foodNutrientDao.deleteForFood(foodId)
        foodGoalFlagsDao.clear(foodId)
        foodDao.deleteById(foodId)
        foodImageStorage.deleteBanner(foodId)
    }

    override suspend fun isFoodsEmpty(): Boolean =
        foodDao.countFoods() == 0

    override suspend fun cleanupOrphanFoodMedia(): Int {
        var deletedCount = 0

        deletedCount += foodImageStorage.deleteAllBlurCache()

        val orphanIds = foodImageStorage.findFoodIdsWithBannerInFilesDir()
        if (orphanIds.isEmpty()) return deletedCount

        val existing = foodDao.getExistingFoodIds(orphanIds).toSet()
        val trulyOrphan = orphanIds.filter { it !in existing }

        trulyOrphan.forEach { id ->
            deletedCount += foodImageStorage.deleteAllFoodMediaDirs(id)
        }

        return deletedCount
    }

    // -------------------------
    // 🔥 NEW: pricing
    // -------------------------

    override suspend fun upsertFoodStorePrice(
        foodId: Long,
        storeId: Long,
        estimatedPrice: Double
    ): Long {
        return foodStorePriceDao.upsertFoodStorePrice(
            FoodStorePriceEntity(
                foodId = foodId,
                storeId = storeId,
                estimatedPrice = estimatedPrice
            )
        )
    }

    override suspend fun deleteFoodStorePrice(
        foodId: Long,
        storeId: Long
    ) {
        foodStorePriceDao.deleteByFoodIdAndStoreId(foodId, storeId)
    }

    override suspend fun getAveragePriceForFood(foodId: Long): Double? {
        return foodStorePriceDao.getAveragePriceForFood(foodId)
    }

    override fun observeAveragePriceForFood(foodId: Long): Flow<Double?> {
        return foodStorePriceDao.observeAveragePriceForFood(foodId)
    }

    override suspend fun getAveragePriceForFoodAtStore(
        foodId: Long,
        storeId: Long
    ): Double? {
        return foodStorePriceDao.getAveragePriceForFoodAtStore(foodId, storeId)
    }

    override fun observeAveragePriceForFoodAtStore(
        foodId: Long,
        storeId: Long
    ): Flow<Double?> {
        return foodStorePriceDao.observeAveragePriceForFoodAtStore(foodId, storeId)
    }
}