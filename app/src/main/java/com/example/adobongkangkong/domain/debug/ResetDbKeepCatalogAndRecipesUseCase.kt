package com.example.adobongkangkong.domain.debug

import androidx.room.withTransaction
import com.example.adobongkangkong.data.local.db.NutriDatabase
import javax.inject.Inject

/**
 * DEBUG ONLY.
 *
 * Clears all tables EXCEPT:
 * - foods
 * - food_nutrients
 * - nutrients
 * - nutrient_aliases
 * - recipes
 * - recipe_ingredient
 *
 * Intended for rapid testing without recreating your recipe definitions or nutrient catalog.
 *
 * FUTURE-YOU NOTE (2026-02-22):
 * Keep delete order "leaf -> parent" to avoid FK issues if you later enable FK constraints.
 */
class ResetDbKeepCatalogAndRecipesUseCase @Inject constructor(
    private val db: NutriDatabase
) {
    suspend fun execute() {
        db.withTransaction {
            val dao = db.debugResetDao()

            dao.clearRecipeBatches()

            dao.clearPlannedItems()
            dao.clearPlannedMeals()

            dao.clearLogEntries()

            dao.clearMealTemplateItems()
            dao.clearMealTemplatePrefs()
            dao.clearMealTemplates()

            dao.clearFoodBarcodes()
            dao.clearFoodGoalFlags()

            dao.clearUserNutrientTargets()
            dao.clearUserPinnedNutrients()

            dao.clearImportIssues()
            dao.clearImportRuns()
        }
    }
}