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
        IouEntity::class,
        FoodCategoryEntity::class,
        FoodCategoryCrossRefEntity::class,
        RecipeCategoryCrossRefEntity::class,

        // NEW (v20)
        CalendarSuccessNutrientEntity::class,

        // NEW (v21)
        RecipeInstructionStepEntity::class
    ],
    version = 21,
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
    abstract fun iouDao(): IouDao
    abstract fun foodCategoryDao(): FoodCategoryDao
    abstract fun nutrientCatalogDao(): NutrientCatalogDao

    // NEW
    abstract fun calendarSuccessNutrientDao(): CalendarSuccessNutrientDao

    // NEW (v21)
    abstract fun recipeInstructionStepDao(): RecipeInstructionStepDao

    companion object {

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE foods ADD COLUMN mlPerServingUnit REAL")
            }
        }

        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS food_barcodes (
                        barcode TEXT NOT NULL,
                        foodId INTEGER NOT NULL,
                        PRIMARY KEY(barcode),
                        FOREIGN KEY(foodId) REFERENCES foods(id) ON DELETE CASCADE
                    )
                    """
                )

                db.execSQL(
                    "ALTER TABLE foods ADD COLUMN usdaModifiedDate TEXT"
                )
            }
        }

        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE foods ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE foods ADD COLUMN deletedAtEpochMs INTEGER")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_foods_usdaFdcId_unique ON foods(usdaFdcId)"
                )
            }
        }

        val MIGRATION_7_8: Migration = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE food_barcodes ADD COLUMN usdaModifiedDateIso TEXT")
            }
        }

        val MIGRATION_8_9: Migration = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE planned_meals ADD COLUMN seriesId INTEGER")
                db.execSQL("ALTER TABLE planned_meals ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE'")
            }
        }

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

        val MIGRATION_11_12: Migration = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
            CREATE TABLE IF NOT EXISTS ious (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                dateIso TEXT NOT NULL,
                description TEXT NOT NULL,
                createdAtEpochMs INTEGER NOT NULL,
                updatedAtEpochMs INTEGER NOT NULL
            )
            """.trimIndent()
                )

                db.execSQL("CREATE INDEX IF NOT EXISTS index_ious_dateIso ON ious(dateIso)")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_ious_dateIso_createdAtEpochMs ON ious(dateIso, createdAtEpochMs)"
                )
            }
        }

        val MIGRATION_13_14: Migration = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE user_pinned_nutrients ADD COLUMN isCritical INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        val MIGRATION_15_16: Migration = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS food_categories (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        createdAtEpochMs INTEGER NOT NULL,
                        isSystem INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_food_categories_name ON food_categories(name)"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS food_category_cross_refs (
                        foodId INTEGER NOT NULL,
                        categoryId INTEGER NOT NULL,
                        PRIMARY KEY(foodId, categoryId),
                        FOREIGN KEY(foodId) REFERENCES foods(id) ON DELETE CASCADE,
                        FOREIGN KEY(categoryId) REFERENCES food_categories(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_food_category_cross_refs_foodId ON food_category_cross_refs(foodId)"
                )

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_food_category_cross_refs_categoryId ON food_category_cross_refs(categoryId)"
                )
            }
        }

        val MIGRATION_16_17: Migration = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS recipe_category_cross_refs (
                        recipeId INTEGER NOT NULL,
                        categoryId INTEGER NOT NULL,
                        PRIMARY KEY(recipeId, categoryId),
                        FOREIGN KEY(recipeId) REFERENCES recipes(id) ON DELETE CASCADE,
                        FOREIGN KEY(categoryId) REFERENCES food_categories(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_recipe_category_cross_refs_recipeId ON recipe_category_cross_refs(recipeId)"
                )

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_recipe_category_cross_refs_categoryId ON recipe_category_cross_refs(categoryId)"
                )
            }
        }

        val MIGRATION_17_18: Migration = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE food_barcodes ADD COLUMN overrideServingsPerPackage REAL")
                db.execSQL("ALTER TABLE food_barcodes ADD COLUMN overrideHouseholdServingText TEXT")
                db.execSQL("ALTER TABLE food_barcodes ADD COLUMN overrideServingSize REAL")
                db.execSQL("ALTER TABLE food_barcodes ADD COLUMN overrideServingUnit TEXT")

                db.execSQL("ALTER TABLE foods ADD COLUMN mergedIntoFoodId INTEGER")
                db.execSQL("ALTER TABLE foods ADD COLUMN mergedAtEpochMs INTEGER")

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_foods_mergedIntoFoodId ON foods(mergedIntoFoodId)"
                )
            }
        }

        val MIGRATION_18_19: Migration = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE foods ADD COLUMN mergeChildCount INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * v20
         * - Add calendar_success_nutrients table
         *
         * Why:
         * - Stores user-selected nutrient codes used for monthly calendar success evaluation
         * - Fully isolated from dashboard pinned/critical preferences
         */
        val MIGRATION_19_20: Migration = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {

                // Create table
                db.execSQL(
                    """
            CREATE TABLE IF NOT EXISTS calendar_success_nutrients (
                nutrientCode TEXT NOT NULL,
                PRIMARY KEY(nutrientCode)
            )
            """.trimIndent()
                )

                // Required index (Room expects this)
                db.execSQL(
                    """
            CREATE INDEX IF NOT EXISTS index_calendar_success_nutrients_nutrientCode
            ON calendar_success_nutrients(nutrientCode)
            """.trimIndent()
                )
            }
        }

        /**
         * v21
         * - Add recipe_instruction_steps table
         *
         * Why:
         * - Stores ordered instruction / notes rows for each recipe
         * - Supports one optional app-owned image reference per step
         * - Keeps instruction text and image references out of the recipes table so future
         *   editing, reordering, and step-level media remain flexible
         *
         * Notes:
         * - imagePath stores a nullable app-owned internal relative path; image bytes do not live
         *   in Room
         * - stableId is included for future export/import reconciliation
         * - ON DELETE CASCADE ensures step rows are removed when the parent recipe is deleted
         */
        val MIGRATION_20_21: Migration = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS recipe_instruction_steps (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        stableId TEXT NOT NULL,
                        recipeId INTEGER NOT NULL,
                        position INTEGER NOT NULL,
                        text TEXT NOT NULL,
                        imagePath TEXT,
                        FOREIGN KEY(recipeId) REFERENCES recipes(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS index_recipe_instruction_steps_stableId
                    ON recipe_instruction_steps(stableId)
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS index_recipe_instruction_steps_recipeId
                    ON recipe_instruction_steps(recipeId)
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS index_recipe_instruction_steps_recipeId_position
                    ON recipe_instruction_steps(recipeId, position)
                    """.trimIndent()
                )
            }
        }
    }
}