package com.example.adobongkangkong.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.adobongkangkong.data.local.db.dao.FoodDao
import com.example.adobongkangkong.data.local.db.dao.FoodGoalFlagsDao
import com.example.adobongkangkong.data.local.db.dao.FoodNutrientDao
import com.example.adobongkangkong.data.local.db.dao.ImportIssueDao
import com.example.adobongkangkong.data.local.db.dao.ImportRunDao
import com.example.adobongkangkong.data.local.db.dao.LogEntryDao
import com.example.adobongkangkong.data.local.db.dao.NutrientAliasDao
import com.example.adobongkangkong.data.local.db.dao.NutrientDao
import com.example.adobongkangkong.data.local.db.dao.RecipeBatchDao
import com.example.adobongkangkong.data.local.db.dao.RecipeDao
import com.example.adobongkangkong.data.local.db.dao.RecipeIngredientDao
import com.example.adobongkangkong.data.local.db.dao.SummaryDao
import com.example.adobongkangkong.data.local.db.dao.UserNutrientTargetDao
import com.example.adobongkangkong.data.local.db.entity.FoodEntity
import com.example.adobongkangkong.data.local.db.entity.FoodGoalFlagsEntity
import com.example.adobongkangkong.data.local.db.entity.FoodNutrientEntity
import com.example.adobongkangkong.data.local.db.entity.ImportIssueEntity
import com.example.adobongkangkong.data.local.db.entity.ImportRunEntity
import com.example.adobongkangkong.data.local.db.entity.LogEntryEntity
import com.example.adobongkangkong.data.local.db.entity.NutrientEntity
import com.example.adobongkangkong.data.local.db.entity.RecipeEntity
import com.example.adobongkangkong.data.local.db.entity.RecipeIngredientEntity
import com.example.adobongkangkong.data.local.db.entity.NutrientAliasEntity
import com.example.adobongkangkong.data.local.db.entity.RecipeBatchEntity
import com.example.adobongkangkong.data.local.db.entity.UserNutrientTargetEntity

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
        UserNutrientTargetEntity::class
    ],
    version = 2,
    exportSchema = true
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
}
