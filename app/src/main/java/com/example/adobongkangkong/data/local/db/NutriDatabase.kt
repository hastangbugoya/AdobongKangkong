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
        FoodBarcodeEntity::class,
        PlannedSeriesEntity::class,
        PlannedSeriesSlotRuleEntity::class,
        PlannedSeriesItemEntity::class,
        PlannerIouEntity::class
    ],
    version = 12,
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
    abstract fun debugResetDao(): DebugResetDao
    abstract fun plannedSeriesDao(): PlannedSeriesDao
    abstract fun plannedSeriesItemDao(): PlannedSeriesItemDao
    abstract fun plannerIouDao(): PlannerIouDao

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
                // ✅ Enforce one Food row per USDA FDC id (NULLs allowed; multiple NULLs are OK in SQLite)
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_foods_usdaFdcId_unique ON foods(usdaFdcId)"
                )
            }
        }
        /**
         * v8
         * - Add food_barcodes.usdaModifiedDateIso for USDA revision tracking on barcode mappings
         */
        val MIGRATION_7_8: Migration = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add food_barcodes.usdaModifiedDateIso (nullable)
                db.execSQL("ALTER TABLE food_barcodes ADD COLUMN usdaModifiedDateIso TEXT")
            }
        }

        /**
         * v9
         * - planned_meals: add seriesId (nullable) + status (TEXT NOT NULL DEFAULT 'ACTIVE')
         */
        val MIGRATION_8_9: Migration = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE planned_meals ADD COLUMN seriesId INTEGER")
                db.execSQL("ALTER TABLE planned_meals ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE'")
            }
        }
        /**
         * v10
         * - Add planned_series + planned_series_slot_rules for recurrence rule layer
         */
        val MIGRATION_9_10: Migration = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
            CREATE TABLE IF NOT EXISTS planned_series (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                effectiveStartDate TEXT NOT NULL,
                effectiveEndDate TEXT,
                endConditionType TEXT NOT NULL,
                endConditionValue TEXT,
                createdAtEpochMs INTEGER NOT NULL,
                updatedAtEpochMs INTEGER NOT NULL
            )
            """.trimIndent()
                )

                db.execSQL("CREATE INDEX IF NOT EXISTS index_planned_series_effectiveStartDate ON planned_series(effectiveStartDate)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_planned_series_effectiveEndDate ON planned_series(effectiveEndDate)")

                db.execSQL(
                    """
            CREATE TABLE IF NOT EXISTS planned_series_slot_rules (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                seriesId INTEGER NOT NULL,
                weekday INTEGER NOT NULL,
                slot TEXT NOT NULL,
                customLabel TEXT,
                createdAtEpochMs INTEGER NOT NULL,
                FOREIGN KEY(seriesId) REFERENCES planned_series(id) ON DELETE CASCADE
            )
            """.trimIndent()
                )

                db.execSQL("CREATE INDEX IF NOT EXISTS index_planned_series_slot_rules_seriesId ON planned_series_slot_rules(seriesId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_planned_series_slot_rules_seriesId_weekday ON planned_series_slot_rules(seriesId, weekday)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_planned_series_slot_rules_seriesId_weekday_slot ON planned_series_slot_rules(seriesId, weekday, slot)")
            }
        }

        /**
         * v12
         * - Add planner_ious table to store IOU narrative placeholders.
         */
        val MIGRATION_11_12: Migration = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS planner_ious (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        dateIso TEXT NOT NULL,
                        description TEXT NOT NULL,
                        createdAtEpochMs INTEGER NOT NULL,
                        updatedAtEpochMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL("CREATE INDEX IF NOT EXISTS index_planner_ious_dateIso ON planner_ious(dateIso)")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_planner_ious_dateIso_createdAtEpochMs ON planner_ious(dateIso, createdAtEpochMs)"
                )
            }
        }
    }
}
