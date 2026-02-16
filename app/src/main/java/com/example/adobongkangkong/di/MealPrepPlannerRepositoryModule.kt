package com.example.adobongkangkong.di

import com.example.adobongkangkong.data.repository.PlannedItemRepositoryImpl
import com.example.adobongkangkong.data.repository.PlannedMealRepositoryImpl
import com.example.adobongkangkong.domain.repository.PlannedItemRepository
import com.example.adobongkangkong.domain.repository.PlannedMealRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MealPrepPlannerRepositoryModule {

    @Binds
    @Singleton
    abstract fun bindPlannedMealRepository(
        impl: PlannedMealRepositoryImpl
    ): PlannedMealRepository

    @Binds
    @Singleton
    abstract fun bindPlannedItemRepository(
        impl: PlannedItemRepositoryImpl
    ): PlannedItemRepository

}
