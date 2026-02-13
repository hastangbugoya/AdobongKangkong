package com.example.adobongkangkong.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.adobongkangkong.data.local.db.entity.*
import com.example.adobongkangkong.data.local.db.dao.*


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
        FoodBarcodeEntity::class
    ],
    version = 6,
    exportSchema = true,
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

    companion object {
        /**
         * Adds foods.mlPerServingUnit as a first-class volume bridge.
         *
         * FUTURE-YOU NOTE (2026-02-06):
         * - This column enables volume-grounded foods (e.g., CAN/BOTTLE) to canonicalize nutrients to PER_100ML
         *   without ever requiring grams (no density guessing).
         */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE foods ADD COLUMN mlPerServingUnit REAL")
            }
        }
        /**
         * DATABASE VERSION NOTES
         *
         * v5
         * - Meal prep planner iteration
         *   - planned_meals
         *   - planned_items
         * - Meal templates
         *   - meal_templates
         *   - meal_template_items
         */

        /**
         * DB VERSION REMINDER
         *
         * Pending (NOT REGISTERED YET):
         * - MealTemplatePrefsEntity (favorite + bias)
         *
         * Register + bump DB version to 6 together.
         */
        /**
         * v6
         * - Add food_barcodes mapping table (barcode -> food)
         * - Add foods.usdaModifiedDate for USDA revision tracking
         */
        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1) Add foods.usdaModifiedDate
                db.execSQL("ALTER TABLE foods ADD COLUMN usdaModifiedDate TEXT")

                // 2) Create food_barcodes table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS food_barcodes (
                        barcode TEXT NOT NULL,
                        foodId INTEGER NOT NULL,
                        source TEXT NOT NULL,
                        usdaFdcId INTEGER,
                        usdaPublishedDateIso TEXT,
                        assignedAtEpochMs INTEGER NOT NULL,
                        lastSeenAtEpochMs INTEGER NOT NULL,
                        PRIMARY KEY(barcode),
                        FOREIGN KEY(foodId) REFERENCES foods(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )

                // 3) Indexes
                db.execSQL("CREATE INDEX IF NOT EXISTS index_food_barcodes_foodId ON food_barcodes(foodId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_food_barcodes_usdaFdcId ON food_barcodes(usdaFdcId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_food_barcodes_source ON food_barcodes(source)")
            }
        }
        /**
         * DB v6
         * - Adds persistent barcode identity via new mapping table `food_barcodes`.
         *   - barcode is UNIQUE and resolves to exactly one foodId at a time.
         *   - mapping source supports USDA vs USER_ASSIGNED, with USDA taking precedence on scan.
         *   - stores USDA metadata (fdcId + publishedDate ISO) on the mapping for version gating.
         * - Adds `foods.usdaModifiedDate` (ISO yyyy-MM-dd) to track USDA refresh metadata.
         *
         * Migration: 5 -> 6
         * - ALTER TABLE foods ADD COLUMN usdaModifiedDate TEXT
         * - CREATE TABLE food_barcodes (...)
         * - CREATE INDEX ... (foodId, usdaFdcId, source)
         */
        /**
         * v7
         * - Soft delete support for foods
         *   - foods.isDeleted (INTEGER NOT NULL DEFAULT 0)
         *   - foods.deletedAtEpochMs (INTEGER NULL)
         */
        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE foods ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE foods ADD COLUMN deletedAtEpochMs INTEGER")
            }
        }
    }
}
