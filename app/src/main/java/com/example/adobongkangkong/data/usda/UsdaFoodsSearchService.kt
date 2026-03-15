package com.example.adobongkangkong.data.usda

/**
 * Service wrapper around USDA `/foods/search`.
 *
 * Purpose
 * - Provides a minimal abstraction over the USDA search endpoint.
 * - Keeps network details out of domain use cases.
 *
 * Supported search types
 * - Barcode search (structured query: gtinUpc:...)
 * - Keyword search (plain text query)
 *
 * Notes
 * - Both methods return the raw JSON response.
 * - Parsing is handled by UsdaFoodsSearchParser in domain layer.
 */
interface UsdaFoodsSearchService {

    /**
     * Searches USDA `/foods/search` by barcode.
     *
     * Example query generated:
     *     gtinUpc:009800895007
     */
    suspend fun searchByBarcode(gtinUpc: String): String?

    /**
     * Searches USDA `/foods/search` by free-text keywords.
     *
     * Example query:
     *     "chicken breast"
     *
     * Paging is supported but optional for now.
     */
    suspend fun searchByKeywords(
        query: String,
        pageSize: Int = 20,
        pageNumber: Int = 1
    ): String?
}
