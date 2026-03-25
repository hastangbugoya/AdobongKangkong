package com.example.adobongkangkong.domain.shared.usecase

import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.shared.model.*
import com.example.adobongkangkong.domain.trend.model.TargetStatus
import com.example.adobongkangkong.domain.usecase.ObserveDailyNutritionSummaryUseCase
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * BuildSharedNutritionSnapshotUseCase
 *
 * ## Purpose
 * Produces a one-shot [SharedNutritionSnapshot] for a given day.
 *
 * This is the **producer-side entry point** for the shared nutrition contract.
 *
 * ## Architecture
 * - Depends ONLY on existing trusted domain outputs:
 *   - [ObserveDailyNutritionSummaryUseCase]
 * - Performs **mapping only** (no recomputation).
 *
 * ## Critical rule
 * DO NOT introduce new nutrition math here.
 * This must remain a pure adapter layer:
 *
 *     Domain (app-owned logic) → Shared contract (export)
 *
 * ## Behavior
 * - Pulls the latest snapshot via Flow `.first()`
 * - Extracts macro totals from NutrientMap
 * - Extracts targets + statuses from DailyNutrientStatus
 * - Produces a stable, consumer-facing snapshot
 *
 * ## Non-goals
 * - No background generation
 * - No persistence
 * - No IPC / sharing yet
 * - No breakdown of foods / meals / recipes
 *
 * ## Future
 * - Nutrients section can be populated later without breaking schema
 * - Source breakdown can be added when a clean source exists
 */
class BuildSharedNutritionSnapshotUseCase @Inject constructor(
    private val summaryUseCase: ObserveDailyNutritionSummaryUseCase
) {

    suspend operator fun invoke(
        date: LocalDate,
        zoneId: ZoneId
    ): SharedNutritionSnapshot {

        val summary = summaryUseCase(date, zoneId).first()

        val totalsMap = summary.totals.totalsByCode

        // -------------------------
        // Macro totals (safe lookup: defaults to 0.0)
        // -------------------------
        val macroTotals = MacroTotals(
            calories = totalsMap[NutrientKey.CALORIES_KCAL],
            proteinG = totalsMap[NutrientKey.PROTEIN_G],
            carbsG = totalsMap[NutrientKey.CARBS_G],
            fatG = totalsMap[NutrientKey.FAT_G],
            sugarsG = totalsMap[NutrientKey.SUGARS_G].takeIf { it != 0.0 }
        )

        // -------------------------
        // Macro targets + status
        // -------------------------
        val statusMap = summary.statuses.associateBy { it.nutrientCode }

        fun mapRange(key: NutrientKey): MacroTargetRange {
            val s = statusMap[key.value]
            return MacroTargetRange(
                min = s?.min,
                target = s?.target,
                max = s?.max
            )
        }

        fun mapStatus(key: NutrientKey): MacroStatus {
            val s = statusMap[key.value] ?: return MacroStatus.UNKNOWN

            return when (s.status) {
                TargetStatus.LOW -> MacroStatus.BELOW_MIN
                TargetStatus.OK -> MacroStatus.ON_TARGET
                TargetStatus.HIGH -> MacroStatus.ABOVE_MAX
                TargetStatus.NO_TARGET -> MacroStatus.UNKNOWN
            }
        }

        val macroTargets = MacroTargets(
            calories = mapRange(NutrientKey.CALORIES_KCAL),
            protein = mapRange(NutrientKey.PROTEIN_G),
            carbs = mapRange(NutrientKey.CARBS_G),
            fat = mapRange(NutrientKey.FAT_G)
        )

        val macroStatus = MacroStatusSummary(
            calories = mapStatus(NutrientKey.CALORIES_KCAL),
            protein = mapStatus(NutrientKey.PROTEIN_G),
            carbs = mapStatus(NutrientKey.CARBS_G),
            fat = mapStatus(NutrientKey.FAT_G)
        )

        // -------------------------
        // Final snapshot
        // -------------------------
        return SharedNutritionSnapshot(
            schemaVersion = SharedNutritionSnapshot.CURRENT_SCHEMA_VERSION,
            dateIso = date.toString(),
            producedAtEpochMs = System.currentTimeMillis(),
            macros = MacroSnapshot(
                totals = macroTotals,
                targets = macroTargets,
                status = macroStatus,
                sourceBreakdown = null // intentionally omitted for now
            ),
            nutrients = null // reserved for future expansion
        )
    }
}