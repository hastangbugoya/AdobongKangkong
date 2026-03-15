package com.example.adobongkangkong.domain.usda

import com.example.adobongkangkong.data.usda.UsdaFoodsSearchService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchUsdaFoodsByBarcodeUseCaseTest {

    @Test
    fun blankBarcode_returnsBlocked_andDoesNotCallService() {
        val service = FakeUsdaFoodsSearchService(
            barcodeResponse = """{"totalHits":0,"foods":[]}"""
        )
        val useCase = SearchUsdaFoodsByBarcodeUseCase(service)

        val result = invoke(useCase, "   ")

        assertTrue(result is SearchUsdaFoodsByBarcodeUseCase.Result.Blocked)
        result as SearchUsdaFoodsByBarcodeUseCase.Result.Blocked
        assertEquals("Blank barcode", result.reason)
        assertEquals(0, service.searchByBarcodeCalls)
    }

    @Test
    fun nullResponse_returnsFailed() {
        val service = FakeUsdaFoodsSearchService(
            barcodeResponse = null
        )
        val useCase = SearchUsdaFoodsByBarcodeUseCase(service)

        val result = invoke(useCase, "009800895007")

        assertTrue(result is SearchUsdaFoodsByBarcodeUseCase.Result.Failed)
        result as SearchUsdaFoodsByBarcodeUseCase.Result.Failed
        assertEquals("USDA search returned null response", result.message)
        assertEquals(1, service.searchByBarcodeCalls)
    }

    @Test
    fun noFoods_returnsBlocked() {
        val service = FakeUsdaFoodsSearchService(
            barcodeResponse = """
                {
                  "totalHits": 0,
                  "foods": []
                }
            """.trimIndent()
        )
        val useCase = SearchUsdaFoodsByBarcodeUseCase(service)

        val result = invoke(useCase, "009800895007")

        assertTrue(result is SearchUsdaFoodsByBarcodeUseCase.Result.Blocked)
        result as SearchUsdaFoodsByBarcodeUseCase.Result.Blocked
        assertEquals("No results for barcode", result.reason)
    }

    @Test
    fun trimsBarcode_only_andPassesTrimmedValueToService() {
        val service = FakeUsdaFoodsSearchService(
            barcodeResponse = """
                {
                  "totalHits": 1,
                  "foods": [
                    {
                      "fdcId": 1,
                      "description": "Test Food",
                      "gtinUpc": "009800895007",
                      "foodNutrients": []
                    }
                  ]
                }
            """.trimIndent()
        )
        val useCase = SearchUsdaFoodsByBarcodeUseCase(service)

        val result = invoke(useCase, " 009800895007 ")

        assertTrue(result is SearchUsdaFoodsByBarcodeUseCase.Result.Success)
        result as SearchUsdaFoodsByBarcodeUseCase.Result.Success

        assertEquals("009800895007", result.scannedBarcode)
        assertEquals("009800895007", service.lastBarcode)
    }

    @Test
    fun exactGtinMatches_arePreferredWhenPresent() {
        val service = FakeUsdaFoodsSearchService(
            barcodeResponse = """
                {
                  "totalHits": 3,
                  "foods": [
                    {
                      "fdcId": 10,
                      "description": "Wrong Variant A",
                      "brandName": "Brand",
                      "gtinUpc": "111111111111",
                      "foodNutrients": []
                    },
                    {
                      "fdcId": 20,
                      "description": "Correct Variant",
                      "brandName": "Brand",
                      "gtinUpc": "009800895007",
                      "foodNutrients": []
                    },
                    {
                      "fdcId": 30,
                      "description": "Wrong Variant B",
                      "brandName": "Brand",
                      "gtinUpc": "222222222222",
                      "foodNutrients": []
                    }
                  ]
                }
            """.trimIndent()
        )
        val useCase = SearchUsdaFoodsByBarcodeUseCase(service)

        val result = invoke(useCase, "009800895007")

        assertTrue(result is SearchUsdaFoodsByBarcodeUseCase.Result.Success)
        val success = result as SearchUsdaFoodsByBarcodeUseCase.Result.Success

        assertEquals(1, success.candidates.size)
        assertEquals(20L, success.candidates.single().fdcId)
        assertEquals("Correct Variant", success.candidates.single().description)
    }

    @Test
    fun whenNoExactGtinMatch_returnsAllFoods() {
        val service = FakeUsdaFoodsSearchService(
            barcodeResponse = """
                {
                  "totalHits": 2,
                  "foods": [
                    {
                      "fdcId": 100,
                      "description": "Variant A",
                      "brandName": "Brand",
                      "gtinUpc": "111111111111",
                      "foodNutrients": []
                    },
                    {
                      "fdcId": 200,
                      "description": "Variant B",
                      "brandName": "Brand",
                      "gtinUpc": "222222222222",
                      "foodNutrients": []
                    }
                  ]
                }
            """.trimIndent()
        )
        val useCase = SearchUsdaFoodsByBarcodeUseCase(service)

        val result = invoke(useCase, "009800895007")

        assertTrue(result is SearchUsdaFoodsByBarcodeUseCase.Result.Success)
        val success = result as SearchUsdaFoodsByBarcodeUseCase.Result.Success

        assertEquals(2, success.candidates.size)
        assertEquals(listOf(100L, 200L), success.candidates.map { it.fdcId })
    }

    @Test
    fun mapsHouseholdServingText_brandAndDates() {
        val service = FakeUsdaFoodsSearchService(
            barcodeResponse = """
                {
                  "totalHits": 1,
                  "foods": [
                    {
                      "fdcId": 12345,
                      "description": "Spicy Napa Cabbage Kimchi",
                      "brandName": "Jongga",
                      "gtinUpc": "852320000495",
                      "publishedDate": "2024-06-01",
                      "modifiedDate": "2025-01-15",
                      "servingSize": 28,
                      "servingSizeUnit": "g",
                      "householdServingFullText": "1 oz",
                      "foodNutrients": []
                    }
                  ]
                }
            """.trimIndent()
        )
        val useCase = SearchUsdaFoodsByBarcodeUseCase(service)

        val result = invoke(useCase, "852320000495")

        assertTrue(result is SearchUsdaFoodsByBarcodeUseCase.Result.Success)
        val item = (result as SearchUsdaFoodsByBarcodeUseCase.Result.Success).candidates.single()

        assertEquals(12345L, item.fdcId)
        assertEquals("Spicy Napa Cabbage Kimchi", item.description)
        assertEquals("Jongga", item.brand)
        assertEquals("1 oz", item.servingText)
        assertEquals("852320000495", item.gtinUpc)
        assertEquals("2024-06-01", item.publishedDateIso)
        assertEquals("2025-01-15", item.modifiedDateIso)
    }

    @Test
    fun fallsBackToServingSizeAndUnit_whenHouseholdServingMissing() {
        val service = FakeUsdaFoodsSearchService(
            barcodeResponse = """
                {
                  "totalHits": 1,
                  "foods": [
                    {
                      "fdcId": 777,
                      "description": "Milk",
                      "brandOwner": "Acme Dairy",
                      "gtinUpc": "012345678901",
                      "servingSize": 240,
                      "servingSizeUnit": "ML",
                      "foodNutrients": []
                    }
                  ]
                }
            """.trimIndent()
        )
        val useCase = SearchUsdaFoodsByBarcodeUseCase(service)

        val result = invoke(useCase, "012345678901")

        assertTrue(result is SearchUsdaFoodsByBarcodeUseCase.Result.Success)
        val item = (result as SearchUsdaFoodsByBarcodeUseCase.Result.Success).candidates.single()

        assertEquals("Acme Dairy", item.brand)
        assertEquals("240.0 ML", item.servingText)
        assertEquals("012345678901", item.gtinUpc)
    }

    @Test
    fun usesEmptyStringsWhenOptionalFieldsMissing() {
        val service = FakeUsdaFoodsSearchService(
            barcodeResponse = """
                {
                  "totalHits": 1,
                  "foods": [
                    {
                      "fdcId": 888,
                      "description": null,
                      "gtinUpc": null,
                      "brandName": null,
                      "brandOwner": null,
                      "servingSize": null,
                      "servingSizeUnit": null,
                      "householdServingFullText": null,
                      "publishedDate": null,
                      "modifiedDate": null,
                      "foodNutrients": []
                    }
                  ]
                }
            """.trimIndent()
        )
        val useCase = SearchUsdaFoodsByBarcodeUseCase(service)

        val result = invoke(useCase, "123")

        assertTrue(result is SearchUsdaFoodsByBarcodeUseCase.Result.Success)
        val item = (result as SearchUsdaFoodsByBarcodeUseCase.Result.Success).candidates.single()

        assertEquals("", item.description)
        assertEquals("", item.brand)
        assertEquals("", item.servingText)
        assertEquals("", item.gtinUpc)
        assertEquals(null, item.publishedDateIso)
        assertEquals(null, item.modifiedDateIso)
    }

    private fun invoke(
        useCase: SearchUsdaFoodsByBarcodeUseCase,
        barcode: String
    ): SearchUsdaFoodsByBarcodeUseCase.Result {
        var result: SearchUsdaFoodsByBarcodeUseCase.Result? = null
        kotlinx.coroutines.runBlocking {
            result = useCase(barcode)
        }
        return result!!
    }

    private class FakeUsdaFoodsSearchService(
        private val barcodeResponse: String?
    ) : UsdaFoodsSearchService {

        var searchByBarcodeCalls: Int = 0
            private set

        var lastBarcode: String? = null
            private set

        override suspend fun searchByBarcode(gtinUpc: String): String? {
            searchByBarcodeCalls++
            lastBarcode = gtinUpc
            return barcodeResponse
        }

        override suspend fun searchByKeywords(
            query: String,
            pageSize: Int,
            pageNumber: Int
        ): String? {
            error("searchByKeywords should not be called in barcode search tests")
        }
    }
}
