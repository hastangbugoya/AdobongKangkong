package com.example.adobongkangkong.di

import com.example.adobongkangkong.data.repository.MealTemplateItemRepositoryImpl
import com.example.adobongkangkong.data.repository.MealTemplateRepositoryImpl
import com.example.adobongkangkong.data.repository.MealTemplateWriterRepositoryImpl
import com.example.adobongkangkong.domain.repository.MealTemplateItemRepository
import com.example.adobongkangkong.domain.repository.MealTemplateRepository
import com.example.adobongkangkong.domain.repository.MealTemplateWriterRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MealTemplateRepositoryModule {

    @Binds @Singleton
    abstract fun bindMealTemplateRepository(
        impl: MealTemplateRepositoryImpl
    ): MealTemplateRepository

    @Binds @Singleton
    abstract fun bindMealTemplateItemRepository(
        impl: MealTemplateItemRepositoryImpl
    ): MealTemplateItemRepository

    @Binds @Singleton
    abstract fun bindMealTemplateWriterRepository(
        impl: MealTemplateWriterRepositoryImpl
    ): MealTemplateWriterRepository
}
