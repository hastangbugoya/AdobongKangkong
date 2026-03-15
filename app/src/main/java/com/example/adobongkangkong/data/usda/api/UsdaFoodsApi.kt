package com.example.adobongkangkong.data.usda.api

import retrofit2.http.GET
import retrofit2.http.Query

interface UsdaFoodsApi {

    /**
     * USDA endpoint:
     * /fdc/v1/foods/search
     *
     * Supported usage examples:
     * - barcode search:
     *     query=gtinUpc:009800895007
     * - keyword search:
     *     query=chicken breast
     *
     * Paging:
     * - pageSize controls how many results are returned
     * - pageNumber is 1-based
     */
    @GET("fdc/v1/foods/search")
    suspend fun foodsSearch(
        @Query("query") query: String,
        @Query("pageSize") pageSize: Int = 20,
        @Query("pageNumber") pageNumber: Int = 1
    ): String
}
