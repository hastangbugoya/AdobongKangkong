package com.example.adobongkangkong.domain.food.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.adobongkangkong.data.local.db.NutriDatabase
import com.example.adobongkangkong.data.local.db.dao.FoodBarcodeDao
import com.example.adobongkangkong.data.local.db.dao.FoodDao
import com.example.adobongkangkong.data.local.db.dao.FoodNutrientDao
import com.example.adobongkangkong.data.local.db.entity.BarcodeMappingSource
import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.data.local.db.entity.FoodBarcodeEntity
import com.example.adobongkangkong.data.local.db.entity.FoodEntity
import com.example.adobongkangkong.data.local.db.entity.FoodNutrientEntity
import com.example.adobongkangkong.data.local.db.mapper.toDomain
import com.example.adobongkangkong.data.local.db.mapper.toEntity
import com.example.adobongkangkong.domain.logging.model.FoodRef
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.model.FoodHardDeleteBlockers
import com.example.adobongkangkong.domain.model.NutrientUnit
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.repository.FoodRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.fail

@RunWith(AndroidJUnit4::class)
class MergeFoodsUseCaseInstrumentedTest {

    private lateinit var db: NutriDatabase
    private lateinit var foodDao: FoodDao
    private lateinit var foodNutrientDao: FoodNutrientDao
    private lateinit var foodBarcodeDao: FoodBarcodeDao
    private lateinit var foodRepository: FoodRepository
    private lateinit var useCase: MergeFoodsUseCase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NutriDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()

        foodDao = db.foodDao()
        foodNutrientDao = db.foodNutrientDao()
        foodBarcodeDao = db.foodBarcodeEntityDao()

        foodRepository = TestFoodRepository(foodDao)

        useCase = MergeFoodsUseCase(
            appDatabase = db,
            foodRepository = foodRepository,
            foodNutrientDao = foodNutrientDao,
            foodBarcodeDao = foodBarcodeDao
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun mergeFoods_basicMerge_copiesMissingNutrients_movesBarcodes_and_marksOverrideMerged() = runBlocking {
        val overrideFoodId = insertFood(
            name = "Coke 12oz can",
            servingUnit = ServingUnit.CAN
        )
        val canonicalFoodId = insertFood(
            name = "Coke 20oz bottle",
            servingUnit = ServingUnit.BOTTLE
        )

        insertNutrient(
            foodId = overrideFoodId,
            nutrientId = 1001L,
            amount = 100.0
        )
        insertNutrient(
            foodId = overrideFoodId,
            nutrientId = 1002L,
            amount = 200.0
        )
        insertNutrient(
            foodId = canonicalFoodId,
            nutrientId = 1001L,
            amount = 111.0
        )

        insertBarcode(
            barcode = "111111111111",
            foodId = overrideFoodId,
            overrideServingSize = 1.0,
            overrideServingUnit = ServingUnit.CAN,
            overrideHouseholdServingText = "1 can"
        )
        insertBarcode(
            barcode = "222222222222",
            foodId = overrideFoodId,
            overrideServingsPerPackage = 6.0
        )

        useCase.mergeFoods(
            overrideFoodId = overrideFoodId,
            canonicalFoodId = canonicalFoodId
        )

        val canonicalNutrients = foodNutrientDao.getForFood(canonicalFoodId)
        val canonicalIds = canonicalNutrients.map { it.nutrientId }.toSet()

        assertTrue(1001L in canonicalIds)
        assertTrue(1002L in canonicalIds)

        val nutrient1001Rows = canonicalNutrients.filter { it.nutrientId == 1001L }
        assertEquals(1, nutrient1001Rows.size)
        assertEquals(111.0, nutrient1001Rows.single().nutrientAmountPerBasis, 0.0001)

        val reassignedBarcodes = foodBarcodeDao.getAllForFood(canonicalFoodId)
        val overrideBarcodesAfter = foodBarcodeDao.getAllForFood(overrideFoodId)

        assertEquals(2, reassignedBarcodes.size)
        assertTrue(reassignedBarcodes.any { it.barcode == "111111111111" && it.overrideServingUnit == ServingUnit.CAN })
        assertTrue(reassignedBarcodes.any { it.barcode == "222222222222" && it.overrideServingsPerPackage == 6.0 })
        assertTrue(overrideBarcodesAfter.isEmpty())

        val overrideFoodAfter = requireNotNull(foodDao.getById(overrideFoodId))
        assertTrue(overrideFoodAfter.isDeleted)
        assertEquals(canonicalFoodId, overrideFoodAfter.mergedIntoFoodId)
        assertTrue((overrideFoodAfter.mergedAtEpochMs ?: 0L) > 0L)
        assertTrue((overrideFoodAfter.deletedAtEpochMs ?: 0L) > 0L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun mergeFoods_sameIds_throws() = runBlocking {
        val foodId = insertFood(name = "Same")

        useCase.mergeFoods(
            overrideFoodId = foodId,
            canonicalFoodId = foodId
        )
    }

    @Test
    fun mergeFoods_overrideAlreadyMerged_throws_andDoesNotMutateState() = runBlocking {
        val canonicalFoodId = insertFood(name = "Canonical")
        val overrideFoodId = insertFood(
            name = "Already merged",
            mergedIntoFoodId = 999L,
            mergedAtEpochMs = 1234L
        )

        insertBarcode(
            barcode = "333333333333",
            foodId = overrideFoodId
        )

        try {
            useCase.mergeFoods(
                overrideFoodId = overrideFoodId,
                canonicalFoodId = canonicalFoodId
            )
        } catch (_: IllegalStateException) {
        }

        val overrideBarcodes = foodBarcodeDao.getAllForFood(overrideFoodId)
        val canonicalBarcodes = foodBarcodeDao.getAllForFood(canonicalFoodId)
        val overrideFoodAfter = requireNotNull(foodDao.getById(overrideFoodId))

        assertEquals(1, overrideBarcodes.size)
        assertTrue(canonicalBarcodes.isEmpty())
        assertEquals(999L, overrideFoodAfter.mergedIntoFoodId)
        assertEquals(1234L, overrideFoodAfter.mergedAtEpochMs)
        assertFalse(overrideFoodAfter.isDeleted)
        assertNull(overrideFoodAfter.deletedAtEpochMs)
    }

    @Test
    fun mergeFoods_deletedCanonical_throws_andDoesNotSoftDeleteOverride() = runBlocking {
        val overrideFoodId = insertFood(name = "Override")
        val canonicalFoodId = insertFood(
            name = "Deleted canonical",
            isDeleted = true,
            deletedAtEpochMs = 9999L
        )

        insertBarcode(
            barcode = "444444444444",
            foodId = overrideFoodId
        )

        try {
            useCase.mergeFoods(
                overrideFoodId = overrideFoodId,
                canonicalFoodId = canonicalFoodId
            )
        } catch (_: IllegalStateException) {
        }

        val overrideFoodAfter = requireNotNull(foodDao.getById(overrideFoodId))
        val overrideBarcodes = foodBarcodeDao.getAllForFood(overrideFoodId)
        val canonicalBarcodes = foodBarcodeDao.getAllForFood(canonicalFoodId)

        assertFalse(overrideFoodAfter.isDeleted)
        assertNull(overrideFoodAfter.mergedIntoFoodId)
        assertNull(overrideFoodAfter.mergedAtEpochMs)
        assertNull(overrideFoodAfter.deletedAtEpochMs)
        assertEquals(1, overrideBarcodes.size)
        assertTrue(canonicalBarcodes.isEmpty())
    }

    @Test
    fun mergeFoods_canonicalNutrientWins_whenBothFoodsContainSameNutrient() = runBlocking {
        val overrideFoodId = insertFood(name = "Override")
        val canonicalFoodId = insertFood(name = "Canonical")

        insertNutrient(
            foodId = overrideFoodId,
            nutrientId = 2001L,
            amount = 100.0
        )
        insertNutrient(
            foodId = canonicalFoodId,
            nutrientId = 2001L,
            amount = 250.0
        )

        useCase.mergeFoods(
            overrideFoodId = overrideFoodId,
            canonicalFoodId = canonicalFoodId
        )

        val rows = foodNutrientDao.getForFood(canonicalFoodId)
            .filter { it.nutrientId == 2001L }

        assertEquals(1, rows.size)
        assertEquals(250.0, rows.single().nutrientAmountPerBasis, 0.0001)
    }

    @Test
    fun mergeFoods_movesAllBarcodes_and_preservesPackagingOverrides() = runBlocking {
        val overrideFoodId = insertFood(
            name = "Coke 12oz can",
            servingUnit = ServingUnit.CAN
        )
        val canonicalFoodId = insertFood(
            name = "Coke canonical",
            servingUnit = ServingUnit.BOTTLE
        )

        insertBarcode(
            barcode = "111111111111",
            foodId = overrideFoodId,
            overrideServingSize = 1.0,
            overrideServingUnit = ServingUnit.CAN,
            overrideHouseholdServingText = "1 can",
            overrideServingsPerPackage = 1.0
        )
        insertBarcode(
            barcode = "222222222222",
            foodId = overrideFoodId,
            overrideServingSize = 6.0,
            overrideServingUnit = ServingUnit.SERVING,
            overrideHouseholdServingText = "6 pack",
            overrideServingsPerPackage = 6.0
        )
        insertBarcode(
            barcode = "333333333333",
            foodId = overrideFoodId,
            overrideServingSize = 12.0,
            overrideServingUnit = ServingUnit.SERVING,
            overrideHouseholdServingText = "12 pack",
            overrideServingsPerPackage = 12.0
        )

        useCase.mergeFoods(
            overrideFoodId = overrideFoodId,
            canonicalFoodId = canonicalFoodId
        )

        val overrideRows = foodBarcodeDao.getAllForFood(overrideFoodId)
        val canonicalRows = foodBarcodeDao.getAllForFood(canonicalFoodId)

        assertTrue(overrideRows.isEmpty())
        assertEquals(3, canonicalRows.size)

        val canRow = canonicalRows.first { it.barcode == "111111111111" }
        assertEquals(1.0, canRow.overrideServingSize ?: 0.0, 0.0001)
        assertEquals(ServingUnit.CAN, canRow.overrideServingUnit)
        assertEquals("1 can", canRow.overrideHouseholdServingText)
        assertEquals(1.0, canRow.overrideServingsPerPackage ?: 0.0, 0.0001)

        val sixPackRow = canonicalRows.first { it.barcode == "222222222222" }
        assertEquals(6.0, sixPackRow.overrideServingSize ?: 0.0, 0.0001)
        assertEquals(ServingUnit.SERVING, sixPackRow.overrideServingUnit)
        assertEquals("6 pack", sixPackRow.overrideHouseholdServingText)
        assertEquals(6.0, sixPackRow.overrideServingsPerPackage ?: 0.0, 0.0001)

        val twelvePackRow = canonicalRows.first { it.barcode == "333333333333" }
        assertEquals(12.0, twelvePackRow.overrideServingSize ?: 0.0, 0.0001)
        assertEquals(ServingUnit.SERVING, twelvePackRow.overrideServingUnit)
        assertEquals("12 pack", twelvePackRow.overrideHouseholdServingText)
        assertEquals(12.0, twelvePackRow.overrideServingsPerPackage ?: 0.0, 0.0001)
    }

    @Test
    fun mergeFoods_marksOverrideFoodAsMergedAndDeleted() = runBlocking {
        val overrideFoodId = insertFood(name = "Override")
        val canonicalFoodId = insertFood(name = "Canonical")

        useCase.mergeFoods(
            overrideFoodId = overrideFoodId,
            canonicalFoodId = canonicalFoodId
        )

        val overrideAfter = requireNotNull(foodDao.getById(overrideFoodId))
        val canonicalAfter = requireNotNull(foodDao.getById(canonicalFoodId))

        assertTrue(overrideAfter.isDeleted)
        assertEquals(canonicalFoodId, overrideAfter.mergedIntoFoodId)
        assertTrue((overrideAfter.mergedAtEpochMs ?: 0L) > 0L)
        assertTrue((overrideAfter.deletedAtEpochMs ?: 0L) > 0L)

        assertFalse(canonicalAfter.isDeleted)
        assertNull(canonicalAfter.mergedIntoFoodId)
        assertNull(canonicalAfter.mergedAtEpochMs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun mergeFoods_sameIds_throwsIllegalArgumentException() = runBlocking {
        val foodId = insertFood(name = "Same food")

        useCase.mergeFoods(
            overrideFoodId = foodId,
            canonicalFoodId = foodId
        )
    }

    @Test
    fun mergeFoods_deletedCanonical_throws_andLeavesStateUnchanged() = runBlocking {
        val overrideFoodId = insertFood(name = "Override")
        val canonicalFoodId = insertFood(
            name = "Deleted canonical",
            isDeleted = true,
            deletedAtEpochMs = 9999L
        )

        insertNutrient(
            foodId = overrideFoodId,
            nutrientId = 3001L,
            amount = 123.0
        )
        insertBarcode(
            barcode = "444444444444",
            foodId = overrideFoodId,
            overrideServingsPerPackage = 4.0
        )

        try {
            useCase.mergeFoods(
                overrideFoodId = overrideFoodId,
                canonicalFoodId = canonicalFoodId
            )
            fail("Expected IllegalStateException")
        } catch (_: IllegalStateException) {
        }

        val overrideAfter = requireNotNull(foodDao.getById(overrideFoodId))
        val canonicalAfter = requireNotNull(foodDao.getById(canonicalFoodId))
        val overrideNutrients = foodNutrientDao.getForFood(overrideFoodId)
        val canonicalNutrients = foodNutrientDao.getForFood(canonicalFoodId)
        val overrideBarcodes = foodBarcodeDao.getAllForFood(overrideFoodId)
        val canonicalBarcodes = foodBarcodeDao.getAllForFood(canonicalFoodId)

        assertFalse(overrideAfter.isDeleted)
        assertNull(overrideAfter.mergedIntoFoodId)
        assertNull(overrideAfter.mergedAtEpochMs)
        assertNull(overrideAfter.deletedAtEpochMs)

        assertTrue(canonicalAfter.isDeleted)
        assertEquals(9999L, canonicalAfter.deletedAtEpochMs)

        assertEquals(1, overrideNutrients.size)
        assertTrue(canonicalNutrients.isEmpty())

        assertEquals(1, overrideBarcodes.size)
        assertTrue(canonicalBarcodes.isEmpty())
    }

    @Test
    fun mergeFoods_overrideAlreadyMerged_throws_andLeavesStateUnchanged() = runBlocking {
        val overrideFoodId = insertFood(
            name = "Already merged override",
            mergedIntoFoodId = 77L,
            mergedAtEpochMs = 1234L
        )
        val canonicalFoodId = insertFood(name = "Canonical")

        insertBarcode(
            barcode = "555555555555",
            foodId = overrideFoodId,
            overrideHouseholdServingText = "1 bottle"
        )

        try {
            useCase.mergeFoods(
                overrideFoodId = overrideFoodId,
                canonicalFoodId = canonicalFoodId
            )
            fail("Expected IllegalStateException")
        } catch (_: IllegalStateException) {
        }

        val overrideAfter = requireNotNull(foodDao.getById(overrideFoodId))
        val canonicalAfter = requireNotNull(foodDao.getById(canonicalFoodId))
        val overrideBarcodes = foodBarcodeDao.getAllForFood(overrideFoodId)
        val canonicalBarcodes = foodBarcodeDao.getAllForFood(canonicalFoodId)

        assertEquals(77L, overrideAfter.mergedIntoFoodId)
        assertEquals(1234L, overrideAfter.mergedAtEpochMs)
        assertFalse(overrideAfter.isDeleted)
        assertNull(overrideAfter.deletedAtEpochMs)

        assertFalse(canonicalAfter.isDeleted)
        assertNull(canonicalAfter.mergedIntoFoodId)
        assertNull(canonicalAfter.mergedAtEpochMs)

        assertEquals(1, overrideBarcodes.size)
        assertTrue(canonicalBarcodes.isEmpty())
    }

    private suspend fun insertFood(
        name: String,
        servingUnit: ServingUnit = ServingUnit.SERVING,
        isDeleted: Boolean = false,
        deletedAtEpochMs: Long? = null,
        mergedIntoFoodId: Long? = null,
        mergedAtEpochMs: Long? = null
    ): Long {
        return foodDao.insert(
            FoodEntity(
                name = name,
                brand = null,
                servingSize = 1.0,
                servingUnit = servingUnit,
                gramsPerServingUnit = null,
                mlPerServingUnit = null,
                servingsPerPackage = null,
                isRecipe = false,
                isLowSodium = null,
                isDeleted = isDeleted,
                deletedAtEpochMs = deletedAtEpochMs,
                usdaFdcId = null,
                usdaGtinUpc = null,
                usdaPublishedDate = null,
                usdaModifiedDate = null,
                usdaServingSize = null,
                usdaServingUnit = null,
                usdaHouseholdServingText = null,
                mergedIntoFoodId = mergedIntoFoodId,
                mergedAtEpochMs = mergedAtEpochMs
            )
        )
    }

    private suspend fun insertNutrient(
        foodId: Long,
        nutrientId: Long,
        amount: Double,
        basisType: BasisType = BasisType.PER_100G
    ) {
        foodNutrientDao.upsertAll(
            listOf(
                FoodNutrientEntity(
                    foodId = foodId,
                    nutrientId = nutrientId,
                    nutrientAmountPerBasis = amount,
                    unit = NutrientUnit.G,
                    basisType = basisType
                )
            )
        )
    }

    private suspend fun insertBarcode(
        barcode: String,
        foodId: Long,
        overrideServingsPerPackage: Double? = null,
        overrideHouseholdServingText: String? = null,
        overrideServingSize: Double? = null,
        overrideServingUnit: ServingUnit? = null
    ) {
        val now = System.currentTimeMillis()

        foodBarcodeDao.upsert(
            FoodBarcodeEntity(
                barcode = barcode,
                foodId = foodId,
                source = BarcodeMappingSource.USER_ASSIGNED,
                usdaFdcId = null,
                usdaPublishedDateIso = null,
                usdaModifiedDateIso = null,
                overrideServingsPerPackage = overrideServingsPerPackage,
                overrideHouseholdServingText = overrideHouseholdServingText,
                overrideServingSize = overrideServingSize,
                overrideServingUnit = overrideServingUnit,
                assignedAtEpochMs = now,
                lastSeenAtEpochMs = now
            )
        )
    }
}

private class TestFoodRepository(
    private val foodDao: FoodDao
) : FoodRepository {

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