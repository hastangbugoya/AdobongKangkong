package com.example.adobongkangkong.domain.shared.model

/**
 * SharedNutritionMonthSummary
 *
 * Compact, calendar-focused monthly nutrition export.
 *
 * ## Design rules
 * - Purpose-built for calendar hydration (NOT a full domain export)
 * - Keeps payload small and predictable
 * - Contains only what month UI needs
 *
 * ## Transport shape (target)
 * {
 *   "monthIso": "2026-03",
 *   "days": [...]
 * }
 */
data class SharedNutritionMonthSummary(
    val monthIso: String,
    val days: List<SharedNutritionMonthDaySummary>
)

/**
 * Per-day summary inside a month payload.
 *
 * Only includes:
 * - 4 core macros
 * - pinned nutrients (compact)
 */
data class SharedNutritionMonthDaySummary(
    val dateIso: String,
    val caloriesKcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val pinnedNutrients: List<SharedPinnedNutrientAmount>
)

/**
 * Compact nutrient representation for pinned nutrients.
 *
 * Keeps payload small:
 * - code instead of full metadata
 * - amount + unit only
 */
data class SharedPinnedNutrientAmount(
    val nutrientCode: String,
    val amount: Double,
    val unit: String
)