import com.example.adobongkangkong.data.local.db.dao.FoodDao
import com.example.adobongkangkong.data.local.db.mapper.toDomain
import com.example.adobongkangkong.data.local.db.mapper.toEntity
import com.example.adobongkangkong.domain.logging.model.FoodRef
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.model.FoodHardDeleteBlockers
import com.example.adobongkangkong.domain.repository.FoodRepository
import kotlinx.coroutines.flow.Flow

// KEEP EVERYTHING ABOVE UNCHANGED

private class TestFoodRepository(
    private val foodDao: FoodDao
) : FoodRepository {

    // -------------------------
    // Store pricing stubs (NEW)
    // -------------------------

    override suspend fun upsertFoodStorePrice(
        foodId: Long,
        storeId: Long,
        pricePer100g: Double?,
        pricePer100ml: Double?,
        updatedAtEpochMs: Long
    ): Long {
        throw UnsupportedOperationException("Unused in MergeFoodsUseCase instrumentation tests.")
    }

    override suspend fun deleteFoodStorePrice(
        foodId: Long,
        storeId: Long
    ) {
        throw UnsupportedOperationException("Unused in MergeFoodsUseCase instrumentation tests.")
    }

    override suspend fun getAveragePricePer100gForFood(foodId: Long): Double? {
        throw UnsupportedOperationException("Unused in MergeFoodsUseCase instrumentation tests.")
    }

    override fun observeAveragePricePer100gForFood(foodId: Long): Flow<Double?> {
        throw UnsupportedOperationException("Unused in MergeFoodsUseCase instrumentation tests.")
    }

    override suspend fun getAveragePricePer100mlForFood(foodId: Long): Double? {
        throw UnsupportedOperationException("Unused in MergeFoodsUseCase instrumentation tests.")
    }

    override fun observeAveragePricePer100mlForFood(foodId: Long): Flow<Double?> {
        throw UnsupportedOperationException("Unused in MergeFoodsUseCase instrumentation tests.")
    }

    override suspend fun getAveragePricePer100gForFoodAtStore(
        foodId: Long,
        storeId: Long
    ): Double? {
        throw UnsupportedOperationException("Unused in MergeFoodsUseCase instrumentation tests.")
    }

    override fun observeAveragePricePer100gForFoodAtStore(
        foodId: Long,
        storeId: Long
    ): Flow<Double?> {
        throw UnsupportedOperationException("Unused in MergeFoodsUseCase instrumentation tests.")
    }

    override suspend fun getAveragePricePer100mlForFoodAtStore(
        foodId: Long,
        storeId: Long
    ): Double? {
        throw UnsupportedOperationException("Unused in MergeFoodsUseCase instrumentation tests.")
    }

    override fun observeAveragePricePer100mlForFoodAtStore(
        foodId: Long,
        storeId: Long
    ): Flow<Double?> {
        throw UnsupportedOperationException("Unused in MergeFoodsUseCase instrumentation tests.")
    }

    // -------------------------
    // Existing methods (UNCHANGED)
    // -------------------------

    override fun search(query: String, limit: Int): Flow<List<Food>> {
        throw UnsupportedOperationException("Unused in MergeFoodsUseCase instrumentation tests.")
    }

    override suspend fun getById(id: Long): Food? =
        foodDao.getById(id)?.toDomain()

    override suspend fun upsert(food: Food): Long {
        val entity = food.toEntity()
        foodDao.upsert(entity)
        return foodDao.getIdByStableId(entity.stableId)
            ?: error("Upsert failed in test repository for stableId=${entity.stableId}")
    }

    override suspend fun getFoodRefForLogging(foodId: Long): FoodRef.Food? {
        throw UnsupportedOperationException("Unused in MergeFoodsUseCase instrumentation tests.")
    }

    override suspend fun isFoodsEmpty(): Boolean {
        throw UnsupportedOperationException("Unused in MergeFoodsUseCase instrumentation tests.")
    }

    override suspend fun deleteFood(foodId: Long): Boolean {
        throw UnsupportedOperationException("Unused in MergeFoodsUseCase instrumentation tests.")
    }

    override suspend fun softDeleteFood(foodId: Long) {
        throw UnsupportedOperationException("Unused in MergeFoodsUseCase instrumentation tests.")
    }

    override suspend fun getFoodHardDeleteBlockers(foodId: Long): FoodHardDeleteBlockers {
        throw UnsupportedOperationException("Unused in MergeFoodsUseCase instrumentation tests.")
    }

    override suspend fun hardDeleteFood(foodId: Long) {
        throw UnsupportedOperationException("Unused in MergeFoodsUseCase instrumentation tests.")
    }

    override suspend fun cleanupOrphanFoodMedia(): Int {
        throw UnsupportedOperationException("Unused in MergeFoodsUseCase instrumentation tests.")
    }

    override suspend fun getByStableId(stableId: String): Food? =
        foodDao.getByStableId(stableId)?.toDomain()
}