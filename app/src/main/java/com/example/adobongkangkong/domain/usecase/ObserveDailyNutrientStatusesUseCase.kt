package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.domain.model.DailyNutrientStatus
import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.trend.model.TargetStatus
import com.example.adobongkangkong.domain.repository.UserNutrientTargetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * ObserveDailyNutrientStatusesUseCase
 *
 * ## Purpose
 * Produces a reactive list of [DailyNutrientStatus] for a given date, combining:
 * - actual consumed nutrient totals for the day, and
 * - user-configured daily nutrient targets.
 *
 * ## What are nutrition targets?
 * A nutrition target defines desired intake bounds for a specific nutrient code:
 * - `minPerDay` → lower bound (optional)
 * - `targetPerDay` → ideal target (optional, informational)
 * - `maxPerDay` → upper bound (optional)
 *
 * These are user-configurable and stored via [UserNutrientTargetRepository].
 *
 * This use case evaluates whether the user is:
 * - BELOW minimum → [TargetStatus.LOW]
 * - ABOVE maximum → [TargetStatus.HIGH]
 * - Within range → [TargetStatus.OK]
 * - No bounds defined → [TargetStatus.NO_TARGET]
 *
 * ## Rationale
 * Daily nutrient statuses power:
 * - Trend views
 * - Daily dashboards
 * - Target compliance indicators
 * - Highlighting nutrients that need attention
 *
 * Centralizing this logic ensures:
 * - Consistent evaluation rules across UI
 * - Single definition of LOW/HIGH/OK semantics
 * - Reactive updates when either totals OR targets change
 *
 * ## Behavior
 * - Combines:
 *   1) [ObserveDailyNutritionTotalsUseCase] (consumed totals for the date)
 *   2) [UserNutrientTargetRepository.observeTargets] (all configured nutrient targets)
 * - Produces a list of [DailyNutrientStatus] for each nutrient that has a target.
 *
 * ## Important rule
 * Only nutrients that have configured targets are included.
 * This use case does NOT:
 * - Automatically include all nutrients
 * - Include pinned nutrients unless they also have targets
 *
 * (Pinned nutrients are a separate UI concern unless target-configured.)
 *
 * ## Parameters
 * @param date The calendar day being evaluated.
 * @param zoneId Used by downstream totals calculation for day boundaries.
 *
 * ## Return
 * @return Flow<List<DailyNutrientStatus>>
 * Emits new values whenever:
 * - consumed totals change (e.g., new log entry), OR
 * - user nutrient targets change.
 *
 * ## Edge cases
 * - If a nutrient has a target but no consumption for the day → consumed defaults to 0.0.
 * - If both min and max are null → status = NO_TARGET.
 * - If only min is defined → only LOW detection applies.
 * - If only max is defined → only HIGH detection applies.
 */
class ObserveDailyNutrientStatusesUseCase @Inject constructor(
    private val observeTotals: ObserveDailyNutritionTotalsUseCase,
    private val targetsRepo: UserNutrientTargetRepository
) {

    operator fun invoke(date: LocalDate, zoneId: ZoneId): Flow<List<DailyNutrientStatus>> =
        combine(
            observeTotals(date, zoneId),
            targetsRepo.observeTargets()
        ) { totals, targets ->
            val result = mutableListOf<DailyNutrientStatus>()

            // Only show nutrients that have targets (logic first).
            for ((code, target) in targets) {
                val consumed = totals.totalsByCode[NutrientKey(code)] ?: 0.0
                result += DailyNutrientStatus(
                    nutrientCode = code,
                    consumed = consumed,
                    min = target.minPerDay,
                    target = target.targetPerDay,
                    max = target.maxPerDay,
                    status = computeStatus(consumed, target.minPerDay, target.maxPerDay)
                )
            }
            result
        }

    /**
     * Computes the status of a nutrient based on consumed amount and bounds.
     *
     * Rules:
     * - If min != null AND consumed < min → LOW
     * - If max != null AND consumed > max → HIGH
     * - If no bounds defined → NO_TARGET
     * - Otherwise → OK
     */
    private fun computeStatus(consumed: Double, min: Double?, max: Double?): TargetStatus =
        when {
            min != null && consumed < min -> TargetStatus.LOW
            max != null && consumed > max -> TargetStatus.HIGH
            min == null && max == null -> TargetStatus.NO_TARGET
            else -> TargetStatus.OK
        }
}

/**
 * FUTURE AI ASSISTANT NOTES
 *
 * - This file follows the standard two-KDoc pattern:
 *   1) Top KDoc: dev-facing purpose, rationale, target semantics, edge rules.
 *   2) Bottom KDoc: constraints/invariants for automated edits.
 *
 * - Do NOT change inclusion rules silently.
 *   This use case intentionally only returns nutrients with targets.
 *   If pinned nutrients must be included regardless of target,
 *   create a new use case or explicitly extend behavior.
 *
 * - Do NOT move target evaluation logic into UI.
 *   computeStatus(...) is the canonical definition of LOW/HIGH/OK/NO_TARGET.
 *
 * - Keep this reactive.
 *   If converting to non-Flow implementation, ensure callers are updated accordingly.
 *
 * - Day membership logic is determined by ObserveDailyNutritionTotalsUseCase.
 *   This use case must not introduce timestamp-based filtering.
 */