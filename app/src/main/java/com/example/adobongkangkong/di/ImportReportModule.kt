package com.example.adobongkangkong.di

import com.example.adobongkangkong.data.repository.ImportReportRepositoryImpl
import com.example.adobongkangkong.domain.repository.ImportReportRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ImportReportModule {
    @Binds
    @Singleton
    abstract fun bindImportReportRepository(
        impl: ImportReportRepositoryImpl
    ): ImportReportRepository
}
