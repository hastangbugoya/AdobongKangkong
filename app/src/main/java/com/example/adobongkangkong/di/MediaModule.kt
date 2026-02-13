package com.example.adobongkangkong.di

import android.content.Context
import com.example.adobongkangkong.feature.camera.FoodImageStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MediaModule {
    @Provides
    @Singleton
    fun provideFoodImageStorage(
        @ApplicationContext context: Context
    ): FoodImageStorage = FoodImageStorage(context)
}
