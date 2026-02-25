package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.domain.model.LogEntry
import com.example.adobongkangkong.domain.model.TodayLogItem
import com.example.adobongkangkong.domain.nutrition.MacroKeys
import com.example.adobongkangkong.domain.repository.LogRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * ObserveTodayLogItemsUseCase
 *
 * ## Purpose
 * Observes a UI-friendly list of “log items” ([TodayLogItem]) for a single selected calendar day.
 *
 * ## What this use case returns
 * A stream of [TodayLogItem] rows intended for lightweight list UIs (dashboard “Logged Today”,
 * day summary lists, etc.). The returned items are expected to be derived from **immutable snapshot
 * logs** (not live joins against Foods/Recipes tables).
 *
 * ## Critical correctness rule (prevents the Day Log bug)
 * Day membership is determined **ONLY** by `logDateIso` (yyyy-MM-dd), not by timestamp ranges.
 *
 * This use case therefore queries the repository using the ISO date string:
 * - `dateIso = date.toString()`
 * - `logRepository.observeTodayItems(dateIso)`
 *
 * Timestamp is preserved for ordering/posterity only and must not be used to decide which day a log
 * belongs to.
 *
 * ## Why no joins (historical correctness)
 * Log entries store immutable snapshot nutrients at log-time. We intentionally avoid joining
 * foods/recipes/nutrient tables so history remains correct even if:
 * - foods are edited later,
 * - foods/recipes are deleted,
 * - nutrient definitions change.
 *
 * ## About “Today” naming
 * Despite the name, this use case works for *any* provided [date]. “Today” refers to the list-style
 * presentation (a compact “what was logged on that day”), not necessarily `LocalDate.now()`.
 *
 * ## About ZoneId
 * The [zoneId] parameter is kept for call-site compatibility. Once a [LocalDate] is provided, the
 * calendar day is already chosen, and the query uses `date.toString()` (yyyy-MM-dd).
 * Timezone does not affect which rows are included for the selected day.
 *
 * ## Pitfalls / gotchas
 * - Do not change the implementation to call `observeRange(start,end)`; that reintroduces the
 *   cross-day display bug when timestamps and day boundaries diverge.
 * - If logs are inserted with mismatched `timestamp` vs `logDateIso`, the UI must still trust
 *   `logDateIso` for inclusion.
 */
class ObserveTodayLogItemsUseCase @Inject constructor(
    private val logRepository: LogRepository,
) {

    /**
     * Observes log items for the given [date].
     *
     * Implementation detail:
     * - Uses [LogRepository.observeTodayItems], which should be backed by an optimized DAO projection
     *   for list UIs (no joins, snapshot-safe).
     *
     * @param date The calendar day to display.
     * @param zoneId Kept for compatibility; intentionally unused for membership filtering.
     * @return A stream of [TodayLogItem] for that day only.
     */
    @Suppress("UNUSED_PARAMETER")
    operator fun invoke(
        date: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Flow<List<TodayLogItem>> {
        val dateIso = date.toString() // yyyy-MM-dd
        return logRepository.observeTodayItems(dateIso)
    }

    /**
     * Legacy mapper kept as a local utility in case a call-site ever needs to build [TodayLogItem]
     * from full [LogEntry] objects (e.g., if the optimized projection is temporarily bypassed).
     *
     * Not used by the current implementation, which prefers [LogRepository.observeTodayItems].
     *
     * Notes:
     * - This mapper only extracts common macros (calories/protein/carbs/fat).
     * - If “today list” expands to include more fields, prefer expanding the DAO projection rather
     *   than mapping full LogEntry objects in hot UI paths.
     */
    private fun LogEntry.toTodayLogItem(): TodayLogItem {
        val n = nutrients
        return TodayLogItem(
            logId = id,
            itemName = itemName,
            timestamp = timestamp,
            caloriesKcal = n[MacroKeys.CALORIES],
            proteinG = n[MacroKeys.PROTEIN],
            carbsG = n[MacroKeys.CARBS],
            fatG = n[MacroKeys.FAT],
        )
    }
}

/**
 * FUTURE AI ASSISTANT NOTES
 *
 * - Two-KDoc standard for this codebase:
 *   1) Top KDoc: dev-facing purpose, rationale, params/return, pitfalls.
 *   2) Bottom KDoc: invariants/constraints for automated edits.
 *
 * - Invariant: membership filtering MUST remain `logDateIso == dateIso`.
 *   Do NOT change to timestamp range queries.
 *
 * - This use case intentionally returns repository-projected [TodayLogItem] rows.
 *   Avoid loading Foods or joining tables for this list.
 *
 * - Keep `zoneId` parameter unless all call sites are updated; it is currently unused by design.
 *
 * - If KMP migration happens:
 *   - `java.time.LocalDate` / `ZoneId` are JVM-only; replace with kotlinx.datetime equivalents only
 *     when moving this file into shared code.
 */