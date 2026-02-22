package com.example.adobongkangkong.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query

@Dao
interface DebugResetDao {

    // Keep: foods, food_nutrients, nutrients, nutrient_aliases, recipes, recipe_ingredient
    @Query("DELETE FROM recipe_batches")
    suspend fun clearRecipeBatches()

    @Query("DELETE FROM planned_items")
    suspend fun clearPlannedItems()

    @Query("DELETE FROM planned_meals")
    suspend fun clearPlannedMeals()

    @Query("DELETE FROM log_entries")
    suspend fun clearLogEntries()

    @Query("DELETE FROM meal_template_items")
    suspend fun clearMealTemplateItems()

    @Query("DELETE FROM meal_template_prefs")
    suspend fun clearMealTemplatePrefs()

    @Query("DELETE FROM meal_templates")
    suspend fun clearMealTemplates()

    @Query("DELETE FROM food_barcodes")
    suspend fun clearFoodBarcodes()

    @Query("DELETE FROM food_goal_flags")
    suspend fun clearFoodGoalFlags()

    @Query("DELETE FROM user_nutrient_targets")
    suspend fun clearUserNutrientTargets()

    @Query("DELETE FROM user_pinned_nutrients")
    suspend fun clearUserPinnedNutrients()

    @Query("DELETE FROM import_issues")
    suspend fun clearImportIssues()

    @Query("DELETE FROM import_runs")
    suspend fun clearImportRuns()
}