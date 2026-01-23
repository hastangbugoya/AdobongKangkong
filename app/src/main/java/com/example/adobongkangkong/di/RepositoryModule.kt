package com.example.adobongkangkong.di


import com.example.adobongkangkong.data.repository.FoodRepositoryImpl
import com.example.adobongkangkong.data.repository.LogRepositoryImpl
import com.example.adobongkangkong.domain.repository.FoodRepository
import com.example.adobongkangkong.domain.repository.LogRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindLogRepository(
        impl: LogRepositoryImpl
    ): LogRepository

    @Binds
    @Singleton
    abstract fun bindSummaryRepository(
        impl: com.example.adobongkangkong.data.repository.SummaryRepositoryImpl
    ): com.example.adobongkangkong.domain.repository.SummaryRepository

    @Binds
    @Singleton
    abstract fun bindFoodRepository(
        impl: FoodRepositoryImpl
    ): FoodRepository

}