package com.example.adobongkangkong.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.adobongkangkong.core.log.MeowLog
import com.example.adobongkangkong.data.local.db.dao.BodyWeightLogDao
import com.example.adobongkangkong.data.local.db.dao.BodyWeightMeasurementDao
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
import com.example.adobongkangkong.data.local.db.dao.LaxRuleDayDao
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
import com.example.adobongkangkong.data.local.db.entity.BodyWeightLogEntity
import com.example.adobongkangkong.data.local.db.entity.BodyWeightMeasurementEntity
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
import com.example.adobongkangkong.data.local.db.entity.LaxRuleDayEntity
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
import com.example.adobongkangkong.data.local.db.dao.RecipeVariantDao
import com.example.adobongkangkong.data.local.db.entity.RecipeVariantEntity
import com.example.adobongkangkong.data.local.db.entity.RecipeVariantIngredientChangeEntity
import com.example.adobongkangkong.data.local.db.dao.RecipeMeasuredYieldDao
import com.example.adobongkangkong.data.local.db.entity.RecipeMeasuredYieldEntity

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
        LaxRuleDayEntity::class,
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
        CalendarSuccessNutrientEntity::class,
        RecipeInstructionStepEntity::class,
        StoreEntity::class,
        FoodStorePriceEntity::class,
        BodyWeightLogEntity::class,
        BodyWeightMeasurementEntity::class,
        RecipeVariantEntity::class,
        RecipeVariantIngredientChangeEntity::class,
        RecipeMeasuredYieldEntity::class,
    ],
    version = 13,
    exportSchema = false
)
@TypeConverters(DbTypeConverters::class)
abstract class NutriDatabase : RoomDatabase() {

    abstract fun foodDao(): FoodDao
    abstract fun nutrientDao(): NutrientDao
    abstract fun foodNutrientDao(): FoodNutrientDao
    abstract fun logEntryDao(): LogEntryDao
    abstract fun laxRuleDayDao(): LaxRuleDayDao
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
    abstract fun bodyWeightLogDao(): BodyWeightLogDao
    abstract fun bodyWeightMeasurementDao(): BodyWeightMeasurementDao
    abstract fun recipeVariantDao(): RecipeVariantDao
    abstract fun recipeMeasuredYieldDao(): RecipeMeasuredYieldDao

    companion object {

        /**
         * Migration Logging Rationale
         *
         * DB migrations are high-risk because they run before most UI recovery paths exist
         * and often only execute once per installed user database. MeowLogs here make upgrade
         * failures diagnosable from a user-sent log file.
         *
         * Rule:
         * - Log START before schema work.
         * - Log SUCCESS after all schema work completes.
         * - Log FAILED with stacktrace, then rethrow so Room correctly aborts the open.
         */

        /**
         * Migration 1 -> 2
         *
         * Adds:
         * - stores
         * - food_store_prices
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MeowLog.d("DB> MIGRATION 1→2 START")

                try {
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

                    MeowLog.d("DB> MIGRATION 1→2 SUCCESS")
                } catch (t: Throwable) {
                    MeowLog.e("DB> MIGRATION 1→2 FAILED", t)
                    throw t
                }
            }
        }

        /**
         * Migration 2 -> 3
         *
         * Changes:
         * - adds nullable address to stores
         * - replaces unitless food_store_prices.estimatedPrice with:
         *   - pricePer100g
         *   - pricePer100ml
         *   - updatedAtEpochMs
         *
         * Existing estimatedPrice rows are intentionally NOT copied forward because they
         * do not encode whether the value was mass-based or volume-based.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MeowLog.d("DB> MIGRATION 2→3 START")

                try {
                    db.execSQL(
                        """
                        ALTER TABLE stores
                        ADD COLUMN address TEXT
                        """.trimIndent()
                    )

                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS food_store_prices_new (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            foodId INTEGER NOT NULL,
                            storeId INTEGER NOT NULL,
                            pricePer100g REAL,
                            pricePer100ml REAL,
                            updatedAtEpochMs INTEGER NOT NULL,
                            FOREIGN KEY(foodId) REFERENCES foods(id) ON DELETE CASCADE,
                            FOREIGN KEY(storeId) REFERENCES stores(id) ON DELETE CASCADE
                        )
                        """.trimIndent()
                    )

                    db.execSQL(
                        """
                        CREATE INDEX IF NOT EXISTS index_food_store_prices_new_foodId
                        ON food_store_prices_new(foodId)
                        """.trimIndent()
                    )

                    db.execSQL(
                        """
                        CREATE INDEX IF NOT EXISTS index_food_store_prices_new_storeId
                        ON food_store_prices_new(storeId)
                        """.trimIndent()
                    )

                    db.execSQL(
                        """
                        CREATE UNIQUE INDEX IF NOT EXISTS index_food_store_prices_new_foodId_storeId
                        ON food_store_prices_new(foodId, storeId)
                        """.trimIndent()
                    )

                    db.execSQL(
                        """
                        DROP TABLE food_store_prices
                        """.trimIndent()
                    )

                    db.execSQL(
                        """
                        ALTER TABLE food_store_prices_new
                        RENAME TO food_store_prices
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

                    MeowLog.d("DB> MIGRATION 2→3 SUCCESS")
                } catch (t: Throwable) {
                    MeowLog.e("DB> MIGRATION 2→3 FAILED", t)
                    throw t
                }
            }
        }

        /**
         * Migration 3 -> 4
         *
         * Safety-test migration for validating:
         * - backup correctness
         * - Room migration wiring
         * - restore correctness after a DB version bump
         *
         * This migration intentionally makes no schema changes.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MeowLog.d("DB> MIGRATION 3→4 START (NO-OP)")

                try {
                    MeowLog.d("DB> MIGRATION 3→4 SUCCESS (NO-OP)")
                } catch (t: Throwable) {
                    MeowLog.e("DB> MIGRATION 3→4 FAILED", t)
                    throw t
                }
            }
        }

        /**
         * Migration 4 -> 5
         *
         * Adds:
         * - recipes.notes
         *
         * This is an additive nullable TEXT column.
         * Existing recipe rows keep null notes by default.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MeowLog.d("DB> MIGRATION 4→5 START")

                try {
                    db.execSQL(
                        """
                        ALTER TABLE recipes
                        ADD COLUMN notes TEXT
                        """.trimIndent()
                    )

                    MeowLog.d("DB> MIGRATION 4→5 SUCCESS")
                } catch (t: Throwable) {
                    MeowLog.e("DB> MIGRATION 4→5 FAILED", t)
                    throw t
                }
            }
        }

        /**
         * Migration 5 -> 6
         *
         * Adds:
         * - body_weight_logs
         *
         * This table stores historical body-weight entries for trend tracking.
         * Reminder mode/interval settings intentionally remain in DataStore preferences.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MeowLog.d("DB> MIGRATION 5→6 START")

                try {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS body_weight_logs (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            dateIso TEXT NOT NULL,
                            weight REAL NOT NULL,
                            unit TEXT NOT NULL,
                            note TEXT,
                            createdAtEpochMs INTEGER NOT NULL,
                            updatedAtEpochMs INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )

                    db.execSQL(
                        """
                        CREATE UNIQUE INDEX IF NOT EXISTS index_body_weight_logs_dateIso
                        ON body_weight_logs(dateIso)
                        """.trimIndent()
                    )

                    MeowLog.d("DB> MIGRATION 5→6 SUCCESS")
                } catch (t: Throwable) {
                    MeowLog.e("DB> MIGRATION 5→6 FAILED", t)
                    throw t
                }
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
            CREATE TABLE IF NOT EXISTS `recipe_variant` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `recipeFoodId` INTEGER NOT NULL,
                `name` TEXT NOT NULL,
                `notes` TEXT,
                `isArchived` INTEGER NOT NULL DEFAULT 0,
                `servingsYieldOverride` REAL,
                `totalYieldGramsOverride` REAL,
                `nutrientsJsonSnapshot` TEXT,
                `createdAtEpochMillis` INTEGER NOT NULL,
                `updatedAtEpochMillis` INTEGER NOT NULL
            )
            """.trimIndent()
                )

                db.execSQL(
                    """
            CREATE INDEX IF NOT EXISTS `index_recipe_variant_recipeFoodId`
            ON `recipe_variant` (`recipeFoodId`)
            """.trimIndent()
                )

                db.execSQL(
                    """
            CREATE UNIQUE INDEX IF NOT EXISTS `index_recipe_variant_recipeFoodId_name`
            ON `recipe_variant` (`recipeFoodId`, `name`)
            """.trimIndent()
                )

                db.execSQL(
                    """
            CREATE TABLE IF NOT EXISTS `recipe_variant_ingredient_change` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `variantId` INTEGER NOT NULL,
                `changeType` TEXT NOT NULL,
                `baseRecipeIngredientId` INTEGER,
                `foodId` INTEGER,
                `servings` REAL,
                `grams` REAL,
                `note` TEXT,
                `sortOrder` INTEGER NOT NULL,
                `createdAtEpochMillis` INTEGER NOT NULL,
                `updatedAtEpochMillis` INTEGER NOT NULL
            )
            """.trimIndent()
                )

                db.execSQL(
                    """
            CREATE INDEX IF NOT EXISTS `index_recipe_variant_ingredient_change_variantId`
            ON `recipe_variant_ingredient_change` (`variantId`)
            """.trimIndent()
                )

                db.execSQL(
                    """
            CREATE INDEX IF NOT EXISTS `index_recipe_variant_ingredient_change_baseRecipeIngredientId`
            ON `recipe_variant_ingredient_change` (`baseRecipeIngredientId`)
            """.trimIndent()
                )

                db.execSQL(
                    """
            CREATE INDEX IF NOT EXISTS `index_recipe_variant_ingredient_change_foodId`
            ON `recipe_variant_ingredient_change` (`foodId`)
            """.trimIndent()
                )
            }
        }


        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MeowLog.d("DB> MIGRATION 7→8 START")

                try {
                    db.execSQL(
                        """
                        ALTER TABLE log_entries
                        ADD COLUMN recipeVariantId INTEGER
                        """.trimIndent()
                    )

                    db.execSQL(
                        """
                        CREATE INDEX IF NOT EXISTS index_log_entries_recipeVariantId
                        ON log_entries(recipeVariantId)
                        """.trimIndent()
                    )

                    MeowLog.d("DB> MIGRATION 7→8 SUCCESS")
                } catch (t: Throwable) {
                    MeowLog.e("DB> MIGRATION 7→8 FAILED", t)
                    throw t
                }
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MeowLog.d("DB> MIGRATION 8→9 START")

                try {
                    db.execSQL(
                        """
                ALTER TABLE planned_items
                ADD COLUMN recipeVariantId INTEGER
                """.trimIndent()
                    )

                    db.execSQL(
                        """
                CREATE INDEX IF NOT EXISTS index_planned_items_recipeVariantId
                ON planned_items(recipeVariantId)
                """.trimIndent()
                    )

                    db.execSQL(
                        """
                ALTER TABLE planned_series_items
                ADD COLUMN recipeVariantId INTEGER
                """.trimIndent()
                    )

                    db.execSQL(
                        """
                CREATE INDEX IF NOT EXISTS index_planned_series_items_recipeVariantId
                ON planned_series_items(recipeVariantId)
                """.trimIndent()
                    )

                    MeowLog.d("DB> MIGRATION 8→9 SUCCESS")
                } catch (t: Throwable) {
                    MeowLog.e("DB> MIGRATION 8→9 FAILED", t)
                    throw t
                }
            }
        }


        /**
         * Migration 9 -> 10
         *
         * Adds:
         * - lax_rule_days
         *
         * This table stores per-date markers for days where the user wants the app to
         * apply relaxed/lax goal rules. It intentionally does not alter, delete, or
         * rewrite meal log entries; it only adds a reporting/scoring marker for the day.
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MeowLog.d("DB> MIGRATION 9→10 START")

                try {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS lax_rule_days (
                            dateEpochDay INTEGER PRIMARY KEY NOT NULL,
                            createdAtEpochMillis INTEGER NOT NULL,
                            updatedAtEpochMillis INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )

                    MeowLog.d("DB> MIGRATION 9→10 SUCCESS")
                } catch (t: Throwable) {
                    MeowLog.e("DB> MIGRATION 9→10 FAILED", t)
                    throw t
                }
            }
        }
        /**
         * Migration 10 -> 11
         *
         * Adds:
         * - recipe_measured_yields
         *
         * This table stores the active measured cooked yield for a base recipe or recipe
         * variant. It supports gram-based recipe logging without reviving cooked-batch
         * inventory/lifetime tracking.
         */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MeowLog.d("DB> MIGRATION 10→11 START")

                try {
                    db.execSQL(
                        """
                CREATE TABLE IF NOT EXISTS recipe_measured_yields (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    recipeId INTEGER NOT NULL,
                    variantId INTEGER,
                    yieldGrams REAL NOT NULL,
                    updatedAtEpochMs INTEGER NOT NULL,
                    note TEXT,
                    isActive INTEGER NOT NULL DEFAULT 1,
                    FOREIGN KEY(recipeId) REFERENCES recipes(id) ON DELETE CASCADE,
                    FOREIGN KEY(variantId) REFERENCES recipe_variant(id) ON DELETE CASCADE
                )
                """.trimIndent()
                    )

                    db.execSQL(
                        """
                CREATE INDEX IF NOT EXISTS index_recipe_measured_yields_recipeId
                ON recipe_measured_yields(recipeId)
                """.trimIndent()
                    )

                    db.execSQL(
                        """
                CREATE INDEX IF NOT EXISTS index_recipe_measured_yields_variantId
                ON recipe_measured_yields(variantId)
                """.trimIndent()
                    )

                    db.execSQL(
                        """
                CREATE INDEX IF NOT EXISTS index_recipe_measured_yields_recipeId_variantId
                ON recipe_measured_yields(recipeId, variantId)
                """.trimIndent()
                    )

                    MeowLog.d("DB> MIGRATION 10→11 SUCCESS")
                } catch (t: Throwable) {
                    MeowLog.e("DB> MIGRATION 10→11 FAILED", t)
                    throw t
                }
            }
        }

        /**
         * Migration 11 -> 12
         *
         * Adds measured-yield audit metadata to log_entries.
         *
         * These nullable columns freeze the yield assumption used for base recipe gram logs
         * that are backed by recipe_measured_yields rather than the legacy cooked-batch path.
         * Existing logs keep null values and remain valid.
         */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MeowLog.d("DB> MIGRATION 11→12 START")

                try {
                    db.execSQL(
                        """
                        ALTER TABLE log_entries
                        ADD COLUMN measuredYieldIdUsed INTEGER
                        """.trimIndent()
                    )

                    db.execSQL(
                        """
                        ALTER TABLE log_entries
                        ADD COLUMN measuredYieldGramsUsed REAL
                        """.trimIndent()
                    )

                    db.execSQL(
                        """
                        ALTER TABLE log_entries
                        ADD COLUMN gramsLogged REAL
                        """.trimIndent()
                    )

                    db.execSQL(
                        """
                        ALTER TABLE log_entries
                        ADD COLUMN servingsEquivalent REAL
                        """.trimIndent()
                    )

                    db.execSQL(
                        """
                        CREATE INDEX IF NOT EXISTS index_log_entries_measuredYieldIdUsed
                        ON log_entries(measuredYieldIdUsed)
                        """.trimIndent()
                    )

                    MeowLog.d("DB> MIGRATION 11→12 SUCCESS")
                } catch (t: Throwable) {
                    MeowLog.e("DB> MIGRATION 11→12 FAILED", t)
                    throw t
                }
            }
        }

        /**
         * Migration 12 -> 13
         *
         * Adds raw body-weight measurement storage while preserving the existing
         * one-row-per-day body_weight_logs table as AK's official daily trend
         * weight table.
         *
         * Adds:
         * - body_weight_measurements
         * - body_weight_logs.selectedMeasurementId
         * - body_weight_logs.trendSelectionMethod
         * - body_weight_logs.isTrendUserOverride
         * - body_weight_logs.trendSelectedAtEpochMs
         *
         * Existing daily weight logs are backfilled into raw measurements as
         * LEGACY_WEIGHT_LOG rows and selected as user-overridden daily trend
         * values so existing charts/reminders remain stable.
         */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MeowLog.d("DB> MIGRATION 12→13 START")

                try {
                    db.execSQL(
                        """
                        ALTER TABLE body_weight_logs
                        ADD COLUMN selectedMeasurementId INTEGER
                        """.trimIndent()
                    )

                    db.execSQL(
                        """
                        ALTER TABLE body_weight_logs
                        ADD COLUMN trendSelectionMethod TEXT
                        """.trimIndent()
                    )

                    db.execSQL(
                        """
                        ALTER TABLE body_weight_logs
                        ADD COLUMN isTrendUserOverride INTEGER NOT NULL DEFAULT 0
                        """.trimIndent()
                    )

                    db.execSQL(
                        """
                        ALTER TABLE body_weight_logs
                        ADD COLUMN trendSelectedAtEpochMs INTEGER
                        """.trimIndent()
                    )

                    db.execSQL(
                        """
                        CREATE INDEX IF NOT EXISTS index_body_weight_logs_selectedMeasurementId
                        ON body_weight_logs(selectedMeasurementId)
                        """.trimIndent()
                    )

                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS body_weight_measurements (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            dateIso TEXT NOT NULL,
                            measuredAtEpochMs INTEGER NOT NULL,
                            weightKg REAL NOT NULL,
                            source TEXT NOT NULL,
                            sourcePackage TEXT,
                            sourceRecordId TEXT,
                            importedAtEpochMs INTEGER,
                            note TEXT,
                            isDeleted INTEGER NOT NULL DEFAULT 0,
                            createdAtEpochMs INTEGER NOT NULL,
                            updatedAtEpochMs INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )

                    db.execSQL(
                        """
                        CREATE INDEX IF NOT EXISTS index_body_weight_measurements_dateIso
                        ON body_weight_measurements(dateIso)
                        """.trimIndent()
                    )

                    db.execSQL(
                        """
                        CREATE INDEX IF NOT EXISTS index_body_weight_measurements_measuredAtEpochMs
                        ON body_weight_measurements(measuredAtEpochMs)
                        """.trimIndent()
                    )

                    db.execSQL(
                        """
                        CREATE INDEX IF NOT EXISTS index_body_weight_measurements_dateIso_measuredAtEpochMs
                        ON body_weight_measurements(dateIso, measuredAtEpochMs)
                        """.trimIndent()
                    )

                    db.execSQL(
                        """
                        CREATE UNIQUE INDEX IF NOT EXISTS index_body_weight_measurements_source_sourceRecordId
                        ON body_weight_measurements(source, sourceRecordId)
                        """.trimIndent()
                    )

                    db.execSQL(
                        """
                        INSERT INTO body_weight_measurements (
                            dateIso,
                            measuredAtEpochMs,
                            weightKg,
                            source,
                            sourcePackage,
                            sourceRecordId,
                            importedAtEpochMs,
                            note,
                            isDeleted,
                            createdAtEpochMs,
                            updatedAtEpochMs
                        )
                        SELECT
                            dateIso,
                            createdAtEpochMs,
                            CASE
                                WHEN unit = 'KG' THEN weight
                                ELSE weight * 0.45359237
                            END,
                            'LEGACY_WEIGHT_LOG',
                            NULL,
                            'legacy:' || id,
                            NULL,
                            note,
                            0,
                            createdAtEpochMs,
                            updatedAtEpochMs
                        FROM body_weight_logs
                        """.trimIndent()
                    )

                    db.execSQL(
                        """
                        UPDATE body_weight_logs
                        SET selectedMeasurementId = (
                                SELECT body_weight_measurements.id
                                FROM body_weight_measurements
                                WHERE body_weight_measurements.source = 'LEGACY_WEIGHT_LOG'
                                  AND body_weight_measurements.sourceRecordId = 'legacy:' || body_weight_logs.id
                                LIMIT 1
                            ),
                            trendSelectionMethod = 'MANUAL_SELECTED',
                            isTrendUserOverride = 1,
                            trendSelectedAtEpochMs = updatedAtEpochMs
                        """.trimIndent()
                    )

                    MeowLog.d("DB> MIGRATION 12→13 SUCCESS")
                } catch (t: Throwable) {
                    MeowLog.e("DB> MIGRATION 12→13 FAILED", t)
                    throw t
                }
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
 * - Ensure the Room database builder registers all migrations explicitly.
 * - Do not rely on destructive migration for long-term user data.
 *
 * Normalized pricing reminder
 * - pricePer100g and pricePer100ml are separate concepts and must stay separate in queries.
 * - Do not invent basis during migration from old unitless estimatedPrice rows.
 */