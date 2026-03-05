package com.example.adobongkangkong.di

import android.content.Context
import androidx.room.Room
import com.example.adobongkangkong.data.local.db.NutriDatabase
import com.example.adobongkangkong.data.local.db.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NutriDatabase {
        return Room.databaseBuilder(
            context,
            NutriDatabase::class.java,
            "nutri.db"
        )
            .addMigrations(
                NutriDatabase.MIGRATION_3_4,
                NutriDatabase.MIGRATION_5_6,
                NutriDatabase.MIGRATION_6_7,
                NutriDatabase.MIGRATION_7_8,
                NutriDatabase.MIGRATION_8_9,
                NutriDatabase.MIGRATION_9_10,
                NutriDatabase.MIGRATION_11_12,
            )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides fun provideFoodDao(db: NutriDatabase): FoodDao = db.foodDao()
    @Provides fun provideNutrientDao(db: NutriDatabase): NutrientDao = db.nutrientDao()
    @Provides fun provideFoodNutrientDao(db: NutriDatabase): FoodNutrientDao = db.foodNutrientDao()
    @Provides fun provideLogEntryDao(db: NutriDatabase): LogEntryDao = db.logEntryDao()
    @Provides fun provideRecipeDao(db: NutriDatabase): RecipeDao = db.recipeDao()
    @Provides fun provideSummaryDao(db: NutriDatabase): SummaryDao = db.summaryDao()
    @Provides fun provideRecipeIngredientDao(db: NutriDatabase): RecipeIngredientDao = db.recipeIngredientDao()

    @Provides fun provideNutrientAliasDao(db: NutriDatabase): NutrientAliasDao = db.nutrientAliasDao()
    @Provides fun provideRecipeBatchDao(db: NutriDatabase): RecipeBatchDao = db.recipeBatchDao()

    @Provides fun provideFoodGoalFlagsDao(db: NutriDatabase): FoodGoalFlagsDao = db.foodGoalFlagsDao()

    @Provides
    fun provideUserNutrientTargetDao(db: NutriDatabase): UserNutrientTargetDao =
        db.userNutrientTargetDao()

    @Provides fun provideUserPinnedNutrientDao(db: NutriDatabase): UserPinnedNutrientDao = db.userPinnedNutrientDao()

    @Provides fun providePlannedMealDao(db: NutriDatabase): PlannedMealDao = db.plannedMealDao()

    @Provides fun providePlannedItemDao(db: NutriDatabase): PlannedItemDao = db.plannedItemDao()

    @Provides fun provideMealTemplateDao(db: NutriDatabase): MealTemplateDao = db.mealTemplateDao()

    @Provides fun provideMealTemplateItemDao(db: NutriDatabase): MealTemplateItemDao = db.mealTemplateItemDao()

    @Provides fun provideMealTemplatePrefsDao(db: NutriDatabase): MealTemplatePrefsDao = db.mealTemplatePrefsDao()

    @Provides fun providesFoodBarcodeDao(db: NutriDatabase): FoodBarcodeDao = db.foodBarcodeEntityDao()

    @Provides fun providePlannedSeriesDao(db: NutriDatabase): PlannedSeriesDao = db.plannedSeriesDao()
    @Provides
    fun providePlannedSeriesItemDao(db: NutriDatabase): PlannedSeriesItemDao =
        db.plannedSeriesItemDao()

    @Provides
    fun provideIouDao(db: NutriDatabase): IouDao =
        db.iouDao()
}
