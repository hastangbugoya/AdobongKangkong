package com.example.adobongkangkong.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query

/**
 * DebugResetDao
 *
 * Purpose:
 * - Provides destructive debug-only cleanup operations for development/testing.
 *
 * Design rules:
 * - Itemized by domain (logs, recipe batches, planner)
 * - Each domain supports:
 *   - clear all
 *   - clear before selected date
 *   - clear after selected date
 *
 * Date semantics:
 * - Logs / planner use ISO day string (YYYY-MM-DD)
 * - Recipe batches use epoch millis because RecipeBatchEntity stores [createdAt] as Instant
 *
 * Safety:
 * - No implicit deletes
 * - No cross-domain coupling
 * - Caller composes operations explicitly
 */
@Dao
interface DebugResetDao {

    /* ============================================================
     * A) LOGS
     * ============================================================ */

    @Query("DELETE FROM log_entries")
    suspend fun clearLogEntries()

    @Query(
        """
        DELETE FROM log_entries
        WHERE logDateIso < :dateIso
        """
    )
    suspend fun clearLogEntriesBefore(dateIso: String)

    @Query(
        """
        DELETE FROM log_entries
        WHERE logDateIso > :dateIso
        """
    )
    suspend fun clearLogEntriesAfter(dateIso: String)

    /* ============================================================
     * B) RECIPE BATCHES
     * ============================================================ */

    @Query("DELETE FROM recipe_batches")
    suspend fun clearRecipeBatches()

    @Query(
        """
        DELETE FROM recipe_batches
        WHERE createdAt < :startOfSelectedDayEpochMs
        """
    )
    suspend fun clearRecipeBatchesBefore(startOfSelectedDayEpochMs: Long)

    @Query(
        """
        DELETE FROM recipe_batches
        WHERE createdAt > :endOfSelectedDayEpochMs
        """
    )
    suspend fun clearRecipeBatchesAfter(endOfSelectedDayEpochMs: Long)

    /* ============================================================
     * C) PLANNER DATA
     * ============================================================ */

    /**
     * Planner reset scope intentionally includes only planner/prep data:
     *
     * - planned_items
     * - planned_meals
     * - planned_series_items
     * - planned_series_slot_rules
     * - planned_series
     * - ious
     *
     * It intentionally preserves:
     * - foods
     * - recipes
     * - recipe batches
     * - logs
     * - nutrient/settings/profile-like data
     *
     * Delete order matters:
     * - occurrence/template children first
     * - then occurrence/template parents
     */

    @Query("DELETE FROM planned_items")
    suspend fun clearPlannedItems()

    @Query("DELETE FROM planned_meals")
    suspend fun clearPlannedMeals()

    @Query("DELETE FROM planned_series_items")
    suspend fun clearPlannedSeriesItems()

    @Query("DELETE FROM planned_series_slot_rules")
    suspend fun clearPlannedSeriesSlotRules()

    @Query("DELETE FROM planned_series")
    suspend fun clearPlannedSeries()

    @Query("DELETE FROM ious")
    suspend fun clearPlannerIous()

    /**
     * Full planner-domain wipe.
     *
     * Ordering is explicit so callers only need one DAO method for the common case.
     */
    suspend fun clearAllPlannerData() {
        clearPlannedItems()
        clearPlannedMeals()
        clearPlannedSeriesItems()
        clearPlannedSeriesSlotRules()
        clearPlannedSeries()
        clearPlannerIous()
    }

    @Query(
        """
        DELETE FROM planned_items
        WHERE mealId IN (
            SELECT id FROM planned_meals
            WHERE date < :dateIso
        )
        """
    )
    suspend fun clearPlannedItemsBefore(dateIso: String)

    @Query(
        """
        DELETE FROM planned_meals
        WHERE date < :dateIso
        """
    )
    suspend fun clearPlannedMealsBefore(dateIso: String)

    @Query(
        """
        DELETE FROM ious
        WHERE dateIso < :dateIso
        """
    )
    suspend fun clearPlannerIousBefore(dateIso: String)

    @Query(
        """
        DELETE FROM planned_items
        WHERE mealId IN (
            SELECT id FROM planned_meals
            WHERE date > :dateIso
        )
        """
    )
    suspend fun clearPlannedItemsAfter(dateIso: String)

    @Query(
        """
        DELETE FROM planned_meals
        WHERE date > :dateIso
        """
    )
    suspend fun clearPlannedMealsAfter(dateIso: String)

    @Query(
        """
        DELETE FROM ious
        WHERE dateIso > :dateIso
        """
    )
    suspend fun clearPlannerIousAfter(dateIso: String)

    /* ============================================================
     * EXISTING GLOBAL CLEANUPS (UNCHANGED)
     * ============================================================ */

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