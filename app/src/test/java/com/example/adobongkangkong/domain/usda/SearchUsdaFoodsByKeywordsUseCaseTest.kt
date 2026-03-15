package com.example.adobongkangkong.domain.usda

import com.example.adobongkangkong.data.usda.UsdaFoodsSearchService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchUsdaFoodsByKeywordsUseCaseTest {

    @Test
    fun `blank query returns blocked and does not call service`() = runTest {
        val service = FakeUsdaFoodsSearchService(
            response = """{"totalHits":0,"foods":[]}"""
        )
        val useCase = SearchUsdaFoodsByKeywordsUseCase(service)

        val result = useCase("   ")

        assertTrue(result is SearchUsdaFoodsByKeywordsUseCase.Result.Blocked)
        assertEquals("Blank search query", (result as SearchUsdaFoodsByKeywordsUseCase.Result.Blocked).reason)
        assertEquals(0, service.searchByKeywordsCalls)
    }

    @Test
    fun `null response returns failed`() = runTest {
        val service = FakeUsdaFoodsSearchService(response = null)
        val useCase = SearchUsdaFoodsByKeywordsUseCase(service)

        val result = useCase("cabbage")

        assertTrue(result is SearchUsdaFoodsByKeywordsUseCase.Result.Failed)
        assertEquals(
            "USDA search returned null response",
            (result as SearchUsdaFoodsByKeywordsUseCase.Result.Failed).message
        )
        assertEquals(1, service.searchByKeywordsCalls)
    }

    @Test
    fun `no foods returns blocked`() = runTest {
        val service = FakeUsdaFoodsSearchService(
            response = """
                {
                  "totalHits": 0,
                  "foods": []
                }
            """.trimIndent()
        )
        val useCase = SearchUsdaFoodsByKeywordsUseCase(service)

        val result = useCase("cabbage")

        assertTrue(result is SearchUsdaFoodsByKeywordsUseCase.Result.Blocked)
        assertEquals("No USDA foods found", (result as SearchUsdaFoodsByKeywordsUseCase.Result.Blocked).reason)
    }

    @Test
    fun `success trims query and uses default paging`() = runTest {
        val service = FakeUsdaFoodsSearchService(
            response = """
                {
                  "totalHits": 1,
                  "foods": [
                    {
                      "fdcId": 2727583,
                      "description": "Cabbage, napa, leaf, destemmed, raw",
                      "dataType": "Foundation",
                      "publishedDate": "2025-04-24",
                      "modifiedDate": "2025-04-24",
                      "householdServingFullText": "100 g",
                      "packageWeight": null,
                      "foodNutrients": []
                    }
                  ]
                }
            """.trimIndent()
        )
        val useCase = SearchUsdaFoodsByKeywordsUseCase(service)

        val result = useCase("  cabbage  ")

        assertTrue(result is SearchUsdaFoodsByKeywordsUseCase.Result.Success)
        result as SearchUsdaFoodsByKeywordsUseCase.Result.Success

        assertEquals("cabbage", result.query)
        assertEquals(1, result.pageNumber)
        assertEquals(1, result.candidates.size)

        assertEquals("cabbage", service.lastQuery)
        assertEquals(30, service.lastPageSize)
        assertEquals(1, service.lastPageNumber)
    }

    @Test
    fun `success maps household serving package weight and metadata`() = runTest {
        val service = FakeUsdaFoodsSearchService(
            response = """
                {
                  "totalHits": 1,
                  "foods": [
                    {
                      "fdcId": 12345,
                      "description": "Spicy Napa Cabbage Kimchi",
                      "brandName": "Jongga",
                      "dataType": "Branded",
                      "gtinUpc": "852320000495",
                      "publishedDate": "2024-06-01",
                      "modifiedDate": "2025-01-15",
                      "servingSize": 28,
                      "servingSizeUnit": "g",
                      "householdServingFullText": "1 oz",
                      "packageWeight": "16 fl oz/1 PT",
                      "foodNutrients": []
                    }
                  ]
                }
            """.trimIndent()
        )
        val useCase = SearchUsdaFoodsByKeywordsUseCase(service)

        val result = useCase("kimchi")

        assertTrue(result is SearchUsdaFoodsByKeywordsUseCase.Result.Success)
        val item = (result as SearchUsdaFoodsByKeywordsUseCase.Result.Success).candidates.single()

        assertEquals(12345L, item.fdcId)
        assertEquals("Spicy Napa Cabbage Kimchi", item.description)
        assertEquals("Jongga", item.brand)
        assertEquals("1 oz", item.servingText)
        assertEquals("1 oz", item.householdServingFullText)
        assertEquals("16 fl oz/1 PT", item.packageWeight)
        assertEquals("Branded", item.dataType)
        assertEquals("852320000495", item.gtinUpc)
        assertEquals("2024-06-01", item.publishedDateIso)
        assertEquals("2025-01-15", item.modifiedDateIso)
    }

    @Test
    fun `success falls back to serving size and unit when household serving text missing`() = runTest {
        val service = FakeUsdaFoodsSearchService(
            response = """
                {
                  "totalHits": 1,
                  "foods": [
                    {
                      "fdcId": 777,
                      "description": "Milk",
                      "brandOwner": "Acme Dairy",
                      "servingSize": 240,
                      "servingSizeUnit": "ML",
                      "foodNutrients": []
                    }
                  ]
                }
            """.trimIndent()
        )
        val useCase = SearchUsdaFoodsByKeywordsUseCase(service)

        val result = useCase("milk")

        assertTrue(result is SearchUsdaFoodsByKeywordsUseCase.Result.Success)
        val item = (result as SearchUsdaFoodsByKeywordsUseCase.Result.Success).candidates.single()

        assertEquals("Acme Dairy", item.brand)
        assertEquals("240.0 ML", item.servingText)
        assertNull(item.householdServingFullText)
        assertNull(item.packageWeight)
        assertNull(item.dataType)
        assertEquals("", item.gtinUpc)
        assertNull(item.publishedDateIso)
        assertNull(item.modifiedDateIso)
    }

    @Test
    fun `success uses empty serving text when neither household nor size unit exist`() = runTest {
        val service = FakeUsdaFoodsSearchService(
            response = """
                {
                  "totalHits": 1,
                  "foods": [
                    {
                      "fdcId": 888,
                      "description": "Mystery Food",
                      "foodNutrients": []
                    }
                  ]
                }
            """.trimIndent()
        )
        val useCase = SearchUsdaFoodsByKeywordsUseCase(service)

        val result = useCase("mystery")

        assertTrue(result is SearchUsdaFoodsByKeywordsUseCase.Result.Success)
        val item = (result as SearchUsdaFoodsByKeywordsUseCase.Result.Success).candidates.single()

        assertEquals("", item.servingText)
        assertNull(item.householdServingFullText)
    }

    @Test
    fun `success honors explicit page size and page number`() = runTest {
        val service = FakeUsdaFoodsSearchService(
            response = """
                {
                  "totalHits": 1,
                  "foods": [
                    {
                      "fdcId": 1,
                      "description": "Egg",
                      "foodNutrients": []
                    }
                  ]
                }
            """.trimIndent()
        )
        val useCase = SearchUsdaFoodsByKeywordsUseCase(service)

        val result = useCase(
            query = "egg",
            pageSize = 75,
            pageNumber = 3
        )

        assertTrue(result is SearchUsdaFoodsByKeywordsUseCase.Result.Success)
        result as SearchUsdaFoodsByKeywordsUseCase.Result.Success

        assertEquals(75, service.lastPageSize)
        assertEquals(3, service.lastPageNumber)
        assertEquals(3, result.pageNumber)
    }

    private class FakeUsdaFoodsSearchService(
        private val response: String?
    ) : UsdaFoodsSearchService {

        var searchByKeywordsCalls: Int = 0
            private set

        var lastQuery: String? = null
            private set

        var lastPageSize: Int? = null
            private set

        var lastPageNumber: Int? = null
            private set

        override suspend fun searchByBarcode(gtinUpc: String): String? {
            error("searchByBarcode should not be called in keyword search tests")
        }

        override suspend fun searchByKeywords(
            query: String,
            pageSize: Int,
            pageNumber: Int
        ): String? {
            searchByKeywordsCalls++
            lastQuery = query
            lastPageSize = pageSize
            lastPageNumber = pageNumber
            return response
        }
    }
}
