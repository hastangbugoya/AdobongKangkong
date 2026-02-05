package com.example.adobongkangkong.di

import com.example.adobongkangkong.BuildConfig
import com.example.adobongkangkong.data.usda.UsdaFoodsSearchService
import com.example.adobongkangkong.data.usda.UsdaFoodsSearchServiceImpl
import com.example.adobongkangkong.data.usda.api.UsdaApiKeyInterceptor
import com.example.adobongkangkong.data.usda.api.UsdaFoodsApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UsdaNetworkModule {

    private const val BASE_URL = "https://api.nal.usda.gov/"

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val apiKey = BuildConfig.USDA_API_KEY

        return OkHttpClient.Builder()
            .addInterceptor(UsdaApiKeyInterceptor(apiKey))
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideUsdaRetrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideUsdaFoodsApi(retrofit: Retrofit): UsdaFoodsApi =
        retrofit.create(UsdaFoodsApi::class.java)

    @Provides
    @Singleton
    fun provideUsdaFoodsSearchService(api: UsdaFoodsApi): UsdaFoodsSearchService =
        UsdaFoodsSearchServiceImpl(api)
}
