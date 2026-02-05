package com.example.adobongkangkong.data.usda.api

import retrofit2.http.GET
import retrofit2.http.Query

interface UsdaFoodsApi {

    // USDA endpoint: /fdc/v1/foods/search?query=gtinUpc:...&pageSize=...
    @GET("fdc/v1/foods/search")
    suspend fun foodsSearch(
        @Query("query") query: String,
        @Query("pageSize") pageSize: Int = 5
    ): String
}
