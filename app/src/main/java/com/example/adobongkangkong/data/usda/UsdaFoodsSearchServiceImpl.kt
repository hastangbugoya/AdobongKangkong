package com.example.adobongkangkong.data.usda

import com.example.adobongkangkong.data.usda.api.UsdaFoodsApi
import javax.inject.Inject

class UsdaFoodsSearchServiceImpl @Inject constructor(
    private val api: UsdaFoodsApi
) : UsdaFoodsSearchService {

    override suspend fun searchByBarcode(gtinUpc: String): String {
        // USDA supports structured queries like: gtinUpc:009800895007
        return api.foodsSearch(query = "gtinUpc:${gtinUpc.trim()}")
    }
}
