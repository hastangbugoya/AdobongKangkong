package com.example.adobongkangkong.domain.usda

import com.example.adobongkangkong.data.local.db.entity.BarcodeMappingSource
import com.example.adobongkangkong.data.local.db.entity.FoodBarcodeEntity
import com.example.adobongkangkong.domain.repository.FoodBarcodeRepository
import com.example.adobongkangkong.domain.usda.ResolveBarcodeWithUsdaUseCase.OpenReason
import com.example.adobongkangkong.domain.usda.ResolveBarcodeWithUsdaUseCase.Result
import com.example.adobongkangkong.domain.usda.ResolveBarcodeWithUsdaUseCase.UsdaBarcodeCandidateMeta
import com.example.adobongkangkong.domain.usda.model.CollisionReason
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolveBarcodeWithUsdaUseCaseTest {

    private class FakeFoodBarcodeRepository(
        private val mappingByBarcode: Map<String, FoodBarcodeEntity> = emptyMap()
    ) : FoodBarcodeRepository {
        override suspend fun getByBarcode(normalizedBarcode: String): FoodBarcodeEntity? =
            mappingByBarcode[normalizedBarcode]

        override suspend fun getFoodIdForBarcode(normalizedBarcode: String): Long? =
            mappingByBarcode[normalizedBarcode]?.foodId

        override suspend fun upsert(entity: FoodBarcodeEntity) = Unit
        override suspend fun deleteByBarcode(normalizedBarcode: String) = Unit
        override suspend fun touchLastSeen(normalizedBarcode: String, epochMs: Long) = Unit
        override suspend fun countForFood(foodId: Long): Int = 0
        override suspend fun getAllBySource(source: BarcodeMappingSource): List<FoodBarcodeEntity> = emptyList()
        override suspend fun upsertAndTouch(entity: FoodBarcodeEntity, nowEpochMs: Long) = Unit
        override suspend fun getAllBarcodesForFood(foodId: Long): List<FoodBarcodeEntity> = emptyList()
    }

    private fun incoming(
        fdcId: Long,
        published: String?
    ) = UsdaBarcodeCandidateMeta(
        fdcId = fdcId,
        gtinUpc = "000000000000",
        publishedDateIso = published,
        modifiedDateIso = null,
        description = "Incoming",
        brand = "Brand"
    )

    /**
     * NOTE:
     * Adjust the constructor args here if your FoodBarcodeEntity has extra required fields.
     * The test only relies on:
     * - barcode
     * - foodId
     * - source
     * - usdaFdcId
     * - usdaPublishedDateIso
     */
    private fun existing(
        barcode: String,
        foodId: Long,
        source: BarcodeMappingSource,
        usdaFdcId: Long?,
        published: String?
    ): FoodBarcodeEntity = FoodBarcodeEntity(
        barcode = barcode,
        foodId = foodId,
        source = source,
        usdaFdcId = usdaFdcId,
        usdaPublishedDateIso = published,
        lastSeenAtEpochMs = 0L,
        assignedAtEpochMs = 0L
    )

    @Test
    fun `blank barcode - Blocked`() = runBlocking {
        val repo = FakeFoodBarcodeRepository()
        val useCase = ResolveBarcodeWithUsdaUseCase(repo)

        val r = useCase.resolveCandidateChosen("   ", incoming(fdcId = 123, published = "2024-01-01"))
        assertTrue(r is Result.Blocked)
        assertEquals("Blank barcode", (r as Result.Blocked).reason)
    }

    @Test
    fun `invalid incoming fdcId - Blocked`() = runBlocking {
        val repo = FakeFoodBarcodeRepository()
        val useCase = ResolveBarcodeWithUsdaUseCase(repo)

        val r = useCase.resolveCandidateChosen("0123", incoming(fdcId = 0, published = "2024-01-01"))
        assertTrue(r is Result.Blocked)
        assertTrue((r as Result.Blocked).reason.contains("Invalid incoming fdcId"))
    }

    @Test
    fun `no existing mapping - ProceedToImport`() = runBlocking {
        val repo = FakeFoodBarcodeRepository()
        val useCase = ResolveBarcodeWithUsdaUseCase(repo)

        val r = useCase.resolveCandidateChosen("0123", incoming(fdcId = 111, published = "2024-01-01"))
        assertTrue(r is Result.ProceedToImport)
        r as Result.ProceedToImport
        assertEquals("0123", r.barcode)
        assertEquals(111L, r.chosen.fdcId)
    }

    @Test
    fun `existing USER_ASSIGNED - NeedsCollisionPrompt ExistingUserAssignedMapping`() = runBlocking {
        val barcode = "0123"
        val repo = FakeFoodBarcodeRepository(
            mappingByBarcode = mapOf(
                barcode to existing(
                    barcode = barcode,
                    foodId = 42,
                    source = BarcodeMappingSource.USER_ASSIGNED,
                    usdaFdcId = null,
                    published = null
                )
            )
        )
        val useCase = ResolveBarcodeWithUsdaUseCase(repo)

        val r = useCase.resolveCandidateChosen(barcode, incoming(fdcId = 111, published = "2024-01-01"))
        assertTrue(r is Result.NeedsCollisionPrompt)
        r as Result.NeedsCollisionPrompt
        assertEquals(CollisionReason.ExistingUserAssignedMapping, r.reason)
        assertEquals(42L, r.existingFoodId)
        assertEquals(BarcodeMappingSource.USER_ASSIGNED, r.existingSource)
        assertEquals(111L, r.incoming.fdcId)
    }

    @Test
    fun `existing USDA - null or mismatched usdaFdcId - NeedsCollisionPrompt ExistingUsdaFdcIdMismatch`() = runBlocking {
        val barcode = "0123"
        val repo = FakeFoodBarcodeRepository(
            mappingByBarcode = mapOf(
                barcode to existing(
                    barcode = barcode,
                    foodId = 7,
                    source = BarcodeMappingSource.USDA,
                    usdaFdcId = null, // also covers mismatch path
                    published = "2024-01-01"
                )
            )
        )
        val useCase = ResolveBarcodeWithUsdaUseCase(repo)

        val r = useCase.resolveCandidateChosen(barcode, incoming(fdcId = 111, published = "2024-02-01"))
        assertTrue(r is Result.NeedsCollisionPrompt)
        r as Result.NeedsCollisionPrompt
        assertEquals(CollisionReason.ExistingUsdaFdcIdMismatch, r.reason)
        assertEquals(7L, r.existingFoodId)
    }

    @Test
    fun `existing USDA same fdcId but missing dates - OpenExisting NoDateConservative`() = runBlocking {
        val barcode = "0123"
        val repo = FakeFoodBarcodeRepository(
            mappingByBarcode = mapOf(
                barcode to existing(
                    barcode = barcode,
                    foodId = 9,
                    source = BarcodeMappingSource.USDA,
                    usdaFdcId = 111,
                    published = null // existing missing => conservative
                )
            )
        )
        val useCase = ResolveBarcodeWithUsdaUseCase(repo)

        val r = useCase.resolveCandidateChosen(barcode, incoming(fdcId = 111, published = "2024-02-01"))
        assertTrue(r is Result.OpenExisting)
        r as Result.OpenExisting
        assertEquals(9L, r.foodId)
        assertEquals(OpenReason.ExistingUsdaNoDateConservative, r.reason)
    }

    @Test
    fun `existing USDA same fdcId and incoming not newer - OpenExisting UpToDate`() = runBlocking {
        val barcode = "0123"
        val repo = FakeFoodBarcodeRepository(
            mappingByBarcode = mapOf(
                barcode to existing(
                    barcode = barcode,
                    foodId = 10,
                    source = BarcodeMappingSource.USDA,
                    usdaFdcId = 111,
                    published = "2024-02-01"
                )
            )
        )
        val useCase = ResolveBarcodeWithUsdaUseCase(repo)

        val rSame = useCase.resolveCandidateChosen(barcode, incoming(fdcId = 111, published = "2024-02-01"))
        assertTrue(rSame is Result.OpenExisting)
        assertEquals(OpenReason.ExistingUsdaUpToDate, (rSame as Result.OpenExisting).reason)

        val rOlder = useCase.resolveCandidateChosen(barcode, incoming(fdcId = 111, published = "2024-01-31"))
        assertTrue(rOlder is Result.OpenExisting)
        assertEquals(OpenReason.ExistingUsdaUpToDate, (rOlder as Result.OpenExisting).reason)
    }

    @Test
    fun `existing USDA same fdcId and incoming newer - ProceedToImport`() = runBlocking {
        val barcode = "0123"
        val repo = FakeFoodBarcodeRepository(
            mappingByBarcode = mapOf(
                barcode to existing(
                    barcode = barcode,
                    foodId = 11,
                    source = BarcodeMappingSource.USDA,
                    usdaFdcId = 111,
                    published = "2024-01-01"
                )
            )
        )
        val useCase = ResolveBarcodeWithUsdaUseCase(repo)

        val r = useCase.resolveCandidateChosen(barcode, incoming(fdcId = 111, published = "2024-02-01"))
        assertTrue(r is Result.ProceedToImport)
        r as Result.ProceedToImport
        assertEquals(111L, r.chosen.fdcId)
    }

    @Test
    fun `existing USDA same published but incoming modified newer - ProceedToImport`() = runBlocking {
        val barcode = "0123"
        val repo = FakeFoodBarcodeRepository(
            mappingByBarcode = mapOf(
                barcode to existing(
                    barcode = barcode,
                    foodId = 20,
                    source = BarcodeMappingSource.USDA,
                    usdaFdcId = 111,
                    published = "2024-02-01"
                ).copy(usdaModifiedDateIso = "2024-02-05")
            )
        )
        val useCase = ResolveBarcodeWithUsdaUseCase(repo)

        val incoming = UsdaBarcodeCandidateMeta(
            fdcId = 111,
            gtinUpc = barcode,
            publishedDateIso = "2024-02-01",
            modifiedDateIso = "2024-02-10",
            description = "Incoming",
            brand = "Brand"
        )

        val r = useCase.resolveCandidateChosen(barcode, incoming)
        assertTrue(r is Result.ProceedToImport)
    }

    @Test
    fun `existing USDA same published but incoming modified older - OpenExisting`() = runBlocking {
        val barcode = "0123"
        val repo = FakeFoodBarcodeRepository(
            mappingByBarcode = mapOf(
                barcode to existing(
                    barcode = barcode,
                    foodId = 21,
                    source = BarcodeMappingSource.USDA,
                    usdaFdcId = 111,
                    published = "2024-02-01"
                ).copy(usdaModifiedDateIso = "2024-02-10")
            )
        )
        val useCase = ResolveBarcodeWithUsdaUseCase(repo)

        val incoming = UsdaBarcodeCandidateMeta(
            fdcId = 111,
            gtinUpc = barcode,
            publishedDateIso = "2024-02-01",
            modifiedDateIso = "2024-02-05",
            description = "Incoming",
            brand = "Brand"
        )

        val r = useCase.resolveCandidateChosen(barcode, incoming)
        assertTrue(r is Result.OpenExisting)
    }

    @Test
    fun `existing USDA same published and no modified dates - OpenExisting`() = runBlocking {
        val barcode = "0123"
        val repo = FakeFoodBarcodeRepository(
            mappingByBarcode = mapOf(
                barcode to existing(
                    barcode = barcode,
                    foodId = 22,
                    source = BarcodeMappingSource.USDA,
                    usdaFdcId = 111,
                    published = "2024-02-01"
                )
            )
        )
        val useCase = ResolveBarcodeWithUsdaUseCase(repo)

        val incoming = UsdaBarcodeCandidateMeta(
            fdcId = 111,
            gtinUpc = barcode,
            publishedDateIso = "2024-02-01",
            modifiedDateIso = null,
            description = "Incoming",
            brand = "Brand"
        )

        val r = useCase.resolveCandidateChosen(barcode, incoming)
        assertTrue(r is Result.OpenExisting)
    }
}