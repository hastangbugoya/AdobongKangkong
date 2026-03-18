package com.example.adobongkangkong.domain.repository

import com.example.adobongkangkong.domain.nutrition.NutrientKey
import kotlinx.coroutines.flow.Flow

/**
 * Calendar-only nutrient selection for monthly day success evaluation.
 *
 * Scope guard:
 * - Used only by the Calendar Success Revamp monthly calendar success logic.
 * - Does NOT affect dashboard pinned nutrients, critical nutrients, user targets,
 *   logging, weekly graph behavior, or shared domain nutrient status computation.
 *
 * Behavior:
 * - Repository stores the subset of nutrient keys selected by the user for
 *   monthly calendar success evaluation.
 * - If the stored set is empty, calendar UI should fall back to existing behavior.
 */
interface CalendarSuccessNutrientRepository {

    /**
     * Observes the currently selected nutrient keys for monthly calendar success.
     *
     * Empty list means:
     * - no explicit calendar filter is selected
     * - caller should fall back to existing/default monthly calendar behavior
     */
    fun observeSelectedKeys(): Flow<List<NutrientKey>>

    /**
     * Replaces the full selected nutrient set.
     *
     * Expected caller behavior:
     * - pass the full final selection, already de-duplicated as needed
     * - empty list is allowed and represents "no explicit selection"
     */
    suspend fun setSelectedKeys(keys: List<NutrientKey>)

    /**
     * Clears all explicit calendar success nutrient selections.
     *
     * This should restore fallback/default monthly calendar evaluation behavior.
     */
    suspend fun clearSelectedKeys()
}