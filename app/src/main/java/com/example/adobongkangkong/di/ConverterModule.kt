package com.example.adobongkangkong.di

import com.example.adobongkangkong.data.local.db.DbTypeConverters
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ConverterModule {

    @Provides
    @Singleton
    fun provideDbTypeConverters(): DbTypeConverters = DbTypeConverters()
}
