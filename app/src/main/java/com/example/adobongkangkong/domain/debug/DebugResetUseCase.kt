package com.example.adobongkangkong.domain.debug

import androidx.room.withTransaction
import com.example.adobongkangkong.data.local.db.NutriDatabase
import com.example.adobongkangkong.data.local.db.dao.DebugResetDao
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * DebugResetUseCase
 *
 * Purpose
 * - Provides itemized destructive debug cleanup for test/dev workflows.
 *
 * Supported domains
 * - Logs
 * - Recipe batches
 * - Planner data
 *
 * Supported scopes
 * - All
 * - Before selected date
 * - After selected date
 *
 * Caller responsibility
 * - Pass the dashboard-selected date
 * - Decide which domain(s) to clear
 *
 * Notes
 * - Planner deletes children before parents.
 * - Recipe batches use createdAt epoch boundaries because RecipeBatchEntity stores Instant.
 * - Logs and planner use the selected LocalDate ISO string directly.
 */
class DebugResetUseCase @Inject constructor(
    private val db: NutriDatabase,
    private val debugResetDao: DebugResetDao
) {

    enum class ResetDomain {
        LOGS,
        RECIPE_BATCHES,
        PLANNER
    }

    enum class ResetScope {
        ALL,
        BEFORE_SELECTED_DATE,
        AFTER_SELECTED_DATE
    }

    suspend operator fun invoke(
        domains: Set<ResetDomain>,
        scope: ResetScope,
        selectedDate: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault()
    ) {
        if (domains.isEmpty()) return

        val selectedDateIso = selectedDate.toString()
        val startOfSelectedDayEpochMs = selectedDate
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        val endOfSelectedDayEpochMs = selectedDate
            .plusDays(1)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli() - 1L

        db.withTransaction {
            domains.forEach { domain ->
                when (domain) {
                    ResetDomain.LOGS -> clearLogs(
                        scope = scope,
                        selectedDateIso = selectedDateIso
                    )

                    ResetDomain.RECIPE_BATCHES -> clearRecipeBatches(
                        scope = scope,
                        startOfSelectedDayEpochMs = startOfSelectedDayEpochMs,
                        endOfSelectedDayEpochMs = endOfSelectedDayEpochMs
                    )

                    ResetDomain.PLANNER -> clearPlanner(
                        scope = scope,
                        selectedDateIso = selectedDateIso
                    )
                }
            }
        }
    }

    private suspend fun clearLogs(
        scope: ResetScope,
        selectedDateIso: String
    ) {
        when (scope) {
            ResetScope.ALL -> debugResetDao.clearLogEntries()
            ResetScope.BEFORE_SELECTED_DATE -> debugResetDao.clearLogEntriesBefore(selectedDateIso)
            ResetScope.AFTER_SELECTED_DATE -> debugResetDao.clearLogEntriesAfter(selectedDateIso)
        }
    }

    private suspend fun clearRecipeBatches(
        scope: ResetScope,
        startOfSelectedDayEpochMs: Long,
        endOfSelectedDayEpochMs: Long
    ) {
        when (scope) {
            ResetScope.ALL -> debugResetDao.clearRecipeBatches()
            ResetScope.BEFORE_SELECTED_DATE ->
                debugResetDao.clearRecipeBatchesBefore(startOfSelectedDayEpochMs)

            ResetScope.AFTER_SELECTED_DATE ->
                debugResetDao.clearRecipeBatchesAfter(endOfSelectedDayEpochMs)
        }
    }

    private suspend fun clearPlanner(
        scope: ResetScope,
        selectedDateIso: String
    ) {
        when (scope) {
            ResetScope.ALL -> {
                debugResetDao.clearPlannedItems()
                debugResetDao.clearPlannedMeals()
            }

            ResetScope.BEFORE_SELECTED_DATE -> {
                debugResetDao.clearPlannedItemsBefore(selectedDateIso)
                debugResetDao.clearPlannedMealsBefore(selectedDateIso)
            }

            ResetScope.AFTER_SELECTED_DATE -> {
                debugResetDao.clearPlannedItemsAfter(selectedDateIso)
                debugResetDao.clearPlannedMealsAfter(selectedDateIso)
            }
        }
    }
}