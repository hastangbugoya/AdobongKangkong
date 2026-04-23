package com.example.adobongkangkong.di

import android.content.Context
import androidx.room.Room
import com.example.adobongkangkong.data.local.db.NutriDatabase
import com.example.adobongkangkong.data.local.db.dao.CalendarSuccessNutrientDao
import com.example.adobongkangkong.data.local.db.dao.DebugResetDao
import com.example.adobongkangkong.data.local.db.dao.FoodBarcodeDao
import com.example.adobongkangkong.data.local.db.dao.FoodCategoryDao
import com.example.adobongkangkong.data.local.db.dao.FoodDao
import com.example.adobongkangkong.data.local.db.dao.FoodGoalFlagsDao
import com.example.adobongkangkong.data.local.db.dao.FoodNutrientDao
import com.example.adobongkangkong.data.local.db.dao.FoodStorePriceDao
import com.example.adobongkangkong.data.local.db.dao.ImportIssueDao
import com.example.adobongkangkong.data.local.db.dao.ImportRunDao
import com.example.adobongkangkong.data.local.db.dao.IouDao
import com.example.adobongkangkong.data.local.db.dao.LogEntryDao
import com.example.adobongkangkong.data.local.db.dao.MealTemplateDao
import com.example.adobongkangkong.data.local.db.dao.MealTemplateItemDao
import com.example.adobongkangkong.data.local.db.dao.MealTemplatePrefsDao
import com.example.adobongkangkong.data.local.db.dao.NutrientAliasDao
import com.example.adobongkangkong.data.local.db.dao.NutrientCatalogDao
import com.example.adobongkangkong.data.local.db.dao.NutrientDao
import com.example.adobongkangkong.data.local.db.dao.PlannedItemDao
import com.example.adobongkangkong.data.local.db.dao.PlannedMealDao
import com.example.adobongkangkong.data.local.db.dao.PlannedSeriesDao
import com.example.adobongkangkong.data.local.db.dao.PlannedSeriesItemDao
import com.example.adobongkangkong.data.local.db.dao.RecipeBatchDao
import com.example.adobongkangkong.data.local.db.dao.RecipeDao
import com.example.adobongkangkong.data.local.db.dao.RecipeIngredientDao
import com.example.adobongkangkong.data.local.db.dao.RecipeInstructionStepDao
import com.example.adobongkangkong.data.local.db.dao.StoreDao
import com.example.adobongkangkong.data.local.db.dao.SummaryDao
import com.example.adobongkangkong.data.local.db.dao.UserNutrientTargetDao
import com.example.adobongkangkong.data.local.db.dao.UserPinnedNutrientDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): NutriDatabase {
        return Room.databaseBuilder(
            context,
            NutriDatabase::class.java,
            "nutri.db"
        )
            .addMigrations(NutriDatabase.MIGRATION_1_2)
            .addMigrations(NutriDatabase.MIGRATION_2_3)
            .addMigrations(NutriDatabase.MIGRATION_3_4)
            .build()
    }

    @Provides
    fun provideFoodDao(db: NutriDatabase): FoodDao = db.foodDao()

    @Provides
    fun provideNutrientDao(db: NutriDatabase): NutrientDao = db.nutrientDao()

    @Provides
    fun provideFoodNutrientDao(db: NutriDatabase): FoodNutrientDao = db.foodNutrientDao()

    @Provides
    fun provideLogEntryDao(db: NutriDatabase): LogEntryDao = db.logEntryDao()

    @Provides
    fun provideRecipeDao(db: NutriDatabase): RecipeDao = db.recipeDao()

    @Provides
    fun provideSummaryDao(db: NutriDatabase): SummaryDao = db.summaryDao()

    @Provides
    fun provideRecipeIngredientDao(db: NutriDatabase): RecipeIngredientDao =
        db.recipeIngredientDao()

    @Provides
    fun provideRecipeInstructionStepDao(db: NutriDatabase): RecipeInstructionStepDao =
        db.recipeInstructionStepDao()

    @Provides
    fun provideDebugResetDao(db: NutriDatabase): DebugResetDao =
        db.debugResetDao()

    @Provides
    fun provideNutrientAliasDao(db: NutriDatabase): NutrientAliasDao = db.nutrientAliasDao()

    @Provides
    fun provideRecipeBatchDao(db: NutriDatabase): RecipeBatchDao = db.recipeBatchDao()

    @Provides
    fun provideFoodGoalFlagsDao(db: NutriDatabase): FoodGoalFlagsDao = db.foodGoalFlagsDao()

    @Provides
    fun provideUserNutrientTargetDao(db: NutriDatabase): UserNutrientTargetDao =
        db.userNutrientTargetDao()

    @Provides
    fun provideUserPinnedNutrientDao(db: NutriDatabase): UserPinnedNutrientDao =
        db.userPinnedNutrientDao()

    @Provides
    fun provideCalendarSuccessNutrientDao(db: NutriDatabase): CalendarSuccessNutrientDao =
        db.calendarSuccessNutrientDao()

    @Provides
    fun providePlannedMealDao(db: NutriDatabase): PlannedMealDao = db.plannedMealDao()

    @Provides
    fun providePlannedItemDao(db: NutriDatabase): PlannedItemDao = db.plannedItemDao()

    @Provides
    fun provideMealTemplateDao(db: NutriDatabase): MealTemplateDao = db.mealTemplateDao()

    @Provides
    fun provideMealTemplateItemDao(db: NutriDatabase): MealTemplateItemDao =
        db.mealTemplateItemDao()

    @Provides
    fun provideMealTemplatePrefsDao(db: NutriDatabase): MealTemplatePrefsDao =
        db.mealTemplatePrefsDao()

    @Provides
    fun providesFoodBarcodeDao(db: NutriDatabase): FoodBarcodeDao =
        db.foodBarcodeEntityDao()

    @Provides
    fun providePlannedSeriesDao(db: NutriDatabase): PlannedSeriesDao = db.plannedSeriesDao()

    @Provides
    fun providePlannedSeriesItemDao(db: NutriDatabase): PlannedSeriesItemDao =
        db.plannedSeriesItemDao()

    @Provides
    fun provideIouDao(db: NutriDatabase): IouDao =
        db.iouDao()

    @Provides
    fun provideFoodCategoryDao(db: NutriDatabase): FoodCategoryDao =
        db.foodCategoryDao()

    @Provides
    fun provideNutrientCatalogDao(db: NutriDatabase): NutrientCatalogDao =
        db.nutrientCatalogDao()

    @Provides
    fun provideStoreDao(db: NutriDatabase): StoreDao =
        db.storeDao()

    @Provides
    fun provideFoodStorePriceDao(db: NutriDatabase): FoodStorePriceDao =
        db.foodStorePriceDao()
}