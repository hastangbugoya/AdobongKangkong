package com.example.adobongkangkong.domain.repository


import com.example.adobongkangkong.domain.model.MacroTotals
import com.example.adobongkangkong.domain.nutrition.NutrientMap
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface SummaryRepository {

    /** Full nutrient totals for arbitrary summary views (dashboard, charts, exports). */
    fun observeNutrientTotals(
        startInclusive: Instant,
        endExclusive: Instant
    ): Flow<NutrientMap>

    /** Convenience wrapper for common macro display. */
    fun observeMacroTotals(
        startInclusive: Instant,
        endExclusive: Instant
    ): Flow<MacroTotals>
}