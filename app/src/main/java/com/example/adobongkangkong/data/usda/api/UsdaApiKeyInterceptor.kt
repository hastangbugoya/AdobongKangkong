package com.example.adobongkangkong.data.usda.api

import okhttp3.Interceptor
import okhttp3.Response

class UsdaApiKeyInterceptor(
    private val apiKey: String
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val originalUrl = original.url

        val newUrl = originalUrl.newBuilder()
            .addQueryParameter("api_key", apiKey)
            .build()

        val newRequest = original.newBuilder()
            .url(newUrl)
            .build()

        return chain.proceed(newRequest)
    }
}
