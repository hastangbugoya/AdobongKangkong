package com.example.adobongkangkong.di

import com.example.adobongkangkong.data.backup.AppBackupRepository
import com.example.adobongkangkong.data.backup.BackupRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BackupModule {

    @Binds
    @Singleton
    abstract fun bindBackupRepository(
        impl: AppBackupRepository
    ): BackupRepository
}