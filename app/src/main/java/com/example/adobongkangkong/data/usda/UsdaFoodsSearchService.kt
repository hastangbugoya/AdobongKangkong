package com.example.adobongkangkong.data.usda

/**
 * Simple abstraction over the USDA /foods/search endpoint.
 * Returns raw JSON response string (the importer owns parsing).
 */
interface UsdaFoodsSearchService {
    suspend fun searchByBarcode(gtinUpc: String): String?
}
