package com.example.adobongkangkong.di

import com.example.adobongkangkong.data.settings.InMemoryUserPreferencesRepository
import com.example.adobongkangkong.domain.settings.UserPreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsModule {

    @Binds
    @Singleton
    abstract fun bindUserPreferencesRepository(
        impl: InMemoryUserPreferencesRepository
    ): UserPreferencesRepository
}
