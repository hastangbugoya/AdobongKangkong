package com.example.adobongkangkong.di


import com.example.adobongkangkong.data.repository.FoodBarcodeRepositoryImpl
import com.example.adobongkangkong.data.repository.FoodGoalFlagsRepositoryImpl
import com.example.adobongkangkong.data.repository.FoodNutrientRepositoryImpl
import com.example.adobongkangkong.data.repository.FoodNutritionSnapshotRepositoryImpl
import com.example.adobongkangkong.data.repository.FoodRepositoryImpl
import com.example.adobongkangkong.data.repository.LogRepositoryImpl
import com.example.adobongkangkong.data.repository.NutrientAliasRepositoryImpl
import com.example.adobongkangkong.data.repository.NutrientRepositoryImpl
import com.example.adobongkangkong.data.repository.PlannedMealRepositoryImpl
import com.example.adobongkangkong.data.repository.RecipeBatchLookupRepositoryImpl
import com.example.adobongkangkong.data.repository.RecipeDraftLookupRepositoryImpl
import com.example.adobongkangkong.data.repository.RecipeRepositoryImpl
import com.example.adobongkangkong.data.repository.UserNutrientTargetRepositoryImpl
import com.example.adobongkangkong.data.repository.UserPinnedNutrientRepositoryImpl
import com.example.adobongkangkong.domain.repository.FoodBarcodeRepository
import com.example.adobongkangkong.domain.repository.FoodGoalFlagsRepository
import com.example.adobongkangkong.domain.repository.FoodNutrientRepository
import com.example.adobongkangkong.domain.repository.FoodNutritionSnapshotRepository
import com.example.adobongkangkong.domain.repository.FoodRepository
import com.example.adobongkangkong.domain.repository.LogRepository
import com.example.adobongkangkong.domain.repository.NutrientAliasRepository
import com.example.adobongkangkong.domain.repository.NutrientRepository
import com.example.adobongkangkong.domain.repository.PlannedMealRepository
import com.example.adobongkangkong.domain.repository.RecipeBatchLookupRepository
import com.example.adobongkangkong.domain.repository.RecipeDraftLookupRepository
import com.example.adobongkangkong.domain.repository.RecipeRepository
import com.example.adobongkangkong.domain.repository.UserNutrientTargetRepository
import com.example.adobongkangkong.domain.repository.UserPinnedNutrientRepository
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

    @Binds
    @Singleton
    abstract fun bindRecipeRepository(
        impl: RecipeRepositoryImpl
    ): RecipeRepository

    @Binds
    @Singleton
    abstract fun bindFoodNutrientRepository(
        impl: FoodNutrientRepositoryImpl
    ): FoodNutrientRepository

    @Binds
    @Singleton
    abstract fun bindNutrientRepository(
        impl: NutrientRepositoryImpl
    ): NutrientRepository

    @Binds abstract fun bindNutrientAliasRepository(
        impl: NutrientAliasRepositoryImpl
    ): NutrientAliasRepository

    @Binds
    @Singleton
    abstract fun bindFoodNutritionSnapshotRepository(
        impl: FoodNutritionSnapshotRepositoryImpl
    ): FoodNutritionSnapshotRepository


    @Binds
    @Singleton
    abstract fun bindRecipeBatchLookupRepository (
        impl: RecipeBatchLookupRepositoryImpl
    ): RecipeBatchLookupRepository

    @Binds
    @Singleton
    abstract fun bindRecipeDraftLookupRepository(
        impl: RecipeDraftLookupRepositoryImpl
    ): RecipeDraftLookupRepository

    @Binds
    abstract fun bindFoodGoalFlagsRepository(
        impl: FoodGoalFlagsRepositoryImpl
    ): FoodGoalFlagsRepository

    @Binds
    abstract fun bindUserNutrientTargetRepository(
        impl: UserNutrientTargetRepositoryImpl
    ): UserNutrientTargetRepository

    @Binds
    abstract fun bindUserPinnedNutrientRepository(
        impl: UserPinnedNutrientRepositoryImpl
    ): UserPinnedNutrientRepository

    @Binds
    @Singleton
    abstract fun bindFoodBarcodeRepository(
        impl: FoodBarcodeRepositoryImpl
    ): FoodBarcodeRepository
}