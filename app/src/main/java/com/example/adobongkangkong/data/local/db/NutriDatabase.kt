package com.example.adobongkangkong.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
import com.example.adobongkangkong.data.local.db.entity.CalendarSuccessNutrientEntity
import com.example.adobongkangkong.data.local.db.entity.FoodBarcodeEntity
import com.example.adobongkangkong.data.local.db.entity.FoodCategoryCrossRefEntity
import com.example.adobongkangkong.data.local.db.entity.FoodCategoryEntity
import com.example.adobongkangkong.data.local.db.entity.FoodEntity
import com.example.adobongkangkong.data.local.db.entity.FoodGoalFlagsEntity
import com.example.adobongkangkong.data.local.db.entity.FoodNutrientEntity
import com.example.adobongkangkong.data.local.db.entity.FoodStorePriceEntity
import com.example.adobongkangkong.data.local.db.entity.ImportIssueEntity
import com.example.adobongkangkong.data.local.db.entity.ImportRunEntity
import com.example.adobongkangkong.data.local.db.entity.IouEntity
import com.example.adobongkangkong.data.local.db.entity.LogEntryEntity
import com.example.adobongkangkong.data.local.db.entity.MealTemplateEntity
import com.example.adobongkangkong.data.local.db.entity.MealTemplateItemEntity
import com.example.adobongkangkong.data.local.db.entity.MealTemplatePrefsEntity
import com.example.adobongkangkong.data.local.db.entity.NutrientAliasEntity
import com.example.adobongkangkong.data.local.db.entity.NutrientEntity
import com.example.adobongkangkong.data.local.db.entity.PlannedItemEntity
import com.example.adobongkangkong.data.local.db.entity.PlannedMealEntity
import com.example.adobongkangkong.data.local.db.entity.PlannedSeriesEntity
import com.example.adobongkangkong.data.local.db.entity.PlannedSeriesItemEntity
import com.example.adobongkangkong.data.local.db.entity.PlannedSeriesSlotRuleEntity
import com.example.adobongkangkong.data.local.db.entity.RecipeBatchEntity
import com.example.adobongkangkong.data.local.db.entity.RecipeCategoryCrossRefEntity
import com.example.adobongkangkong.data.local.db.entity.RecipeEntity
import com.example.adobongkangkong.data.local.db.entity.RecipeIngredientEntity
import com.example.adobongkangkong.data.local.db.entity.RecipeInstructionStepEntity
import com.example.adobongkangkong.data.local.db.entity.StoreEntity
import com.example.adobongkangkong.data.local.db.entity.UserNutrientTargetEntity
import com.example.adobongkangkong.data.local.db.entity.UserPinnedNutrientEntity

/**
 * Primary Room database for AdobongKangkong.
 *
 * Database stability rule:
 * - Table names and persisted column names are part of the long-term storage contract.
 * - Adding new tables is normal.
 * - Adding new columns is normal.
 * - Renaming existing tables or persisted columns is strongly discouraged.
 *
 * Why this matters:
 * - Existing installs depend on stable identifiers for reliable migration.
 * - Renames create avoidable migration risk and increase long-term maintenance burden.
 * - Future schema growth should prefer additive changes over identifier churn.
 *
 * Practical guidance:
 * - Prefer additive migrations.
 * - Only rename a table/column if there is a truly strong reason and the migration has
 *   been reviewed carefully end-to-end.
 * - Before changing any existing DB identifier name, assume "do not rename" unless there
 *   is an explicitly documented exception.
 */
@Database(
    entities = [
        FoodEntity::class,
        NutrientEntity::class,
        FoodNutrientEntity::class,
        LogEntryEntity::class,
        RecipeIngredientEntity::class,
        RecipeEntity::class,
        NutrientAliasEntity::class,
        ImportIssueEntity::class,
        ImportRunEntity::class,
        RecipeBatchEntity::class,
        FoodGoalFlagsEntity::class,
        UserNutrientTargetEntity::class,
        UserPinnedNutrientEntity::class,
        PlannedMealEntity::class,
        PlannedItemEntity::class,
        MealTemplateEntity::class,
        MealTemplateItemEntity::class,
        MealTemplatePrefsEntity::class,
        FoodBarcodeEntity::class,
        PlannedSeriesEntity::class,
        PlannedSeriesSlotRuleEntity::class,
        PlannedSeriesItemEntity::class,
        IouEntity::class,
        FoodCategoryEntity::class,
        FoodCategoryCrossRefEntity::class,
        RecipeCategoryCrossRefEntity::class,

        // Existing schema
        CalendarSuccessNutrientEntity::class,
        RecipeInstructionStepEntity::class,

        // v2 additions
        StoreEntity::class,
        FoodStorePriceEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(DbTypeConverters::class)
abstract class NutriDatabase : RoomDatabase() {

    abstract fun foodDao(): FoodDao
    abstract fun nutrientDao(): NutrientDao
    abstract fun foodNutrientDao(): FoodNutrientDao
    abstract fun logEntryDao(): LogEntryDao
    abstract fun recipeDao(): RecipeDao
    abstract fun summaryDao(): SummaryDao
    abstract fun recipeIngredientDao(): RecipeIngredientDao
    abstract fun nutrientAliasDao(): NutrientAliasDao
    abstract fun importIssueDao(): ImportIssueDao
    abstract fun importRunDao(): ImportRunDao
    abstract fun recipeBatchDao(): RecipeBatchDao
    abstract fun foodGoalFlagsDao(): FoodGoalFlagsDao
    abstract fun userNutrientTargetDao(): UserNutrientTargetDao
    abstract fun userPinnedNutrientDao(): UserPinnedNutrientDao
    abstract fun plannedMealDao(): PlannedMealDao
    abstract fun plannedItemDao(): PlannedItemDao
    abstract fun mealTemplateDao(): MealTemplateDao
    abstract fun mealTemplateItemDao(): MealTemplateItemDao
    abstract fun mealTemplatePrefsDao(): MealTemplatePrefsDao
    abstract fun foodBarcodeEntityDao(): FoodBarcodeDao
    abstract fun debugResetDao(): DebugResetDao
    abstract fun plannedSeriesDao(): PlannedSeriesDao
    abstract fun plannedSeriesItemDao(): PlannedSeriesItemDao
    abstract fun iouDao(): IouDao
    abstract fun foodCategoryDao(): FoodCategoryDao
    abstract fun nutrientCatalogDao(): NutrientCatalogDao
    abstract fun calendarSuccessNutrientDao(): CalendarSuccessNutrientDao
    abstract fun recipeInstructionStepDao(): RecipeInstructionStepDao
    abstract fun storeDao(): StoreDao
    abstract fun foodStorePriceDao(): FoodStorePriceDao

    companion object {

        /**
         * Migration 1 -> 2
         *
         * Adds:
         * - stores
         * - food_store_prices
         *
         * This is an additive migration only.
         * No existing table or persisted column identifiers are renamed here.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS stores (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS index_stores_name
                    ON stores(name)
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS food_store_prices (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        foodId INTEGER NOT NULL,
                        storeId INTEGER NOT NULL,
                        estimatedPrice REAL NOT NULL,
                        FOREIGN KEY(foodId) REFERENCES foods(id) ON DELETE CASCADE,
                        FOREIGN KEY(storeId) REFERENCES stores(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS index_food_store_prices_foodId
                    ON food_store_prices(foodId)
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS index_food_store_prices_storeId
                    ON food_store_prices(storeId)
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS index_food_store_prices_foodId_storeId
                    ON food_store_prices(foodId, storeId)
                    """.trimIndent()
                )
            }
        }
    }
}

/**
 * =============================================================================
 * FUTURE-YOU / FUTURE AI NOTES — DO NOT DELETE
 * =============================================================================
 *
 * Database identifier stability reminder
 * - Strongly discourage renaming existing DB tables and persisted columns.
 * - Adding tables is expected.
 * - Adding fields is expected.
 * - Identifier renames should be treated as exceptional, not routine.
 *
 * Migration testing reminder
 * - Always test new migrations on a test device/emulator with throwaway data first.
 * - Only after that passes should migration testing happen on longer-term test devices
 *   that contain real retained app data.
 *
 * Builder reminder
 * - Ensure the Room database builder registers MIGRATION_1_2 explicitly.
 * - Do not rely on destructive migration for long-term user data.
 */