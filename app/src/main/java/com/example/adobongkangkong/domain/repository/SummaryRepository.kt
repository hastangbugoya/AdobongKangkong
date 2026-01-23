package com.example.adobongkangkong.domain.repository


import com.example.adobongkangkong.domain.model.MacroTotals
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface SummaryRepository {
    fun observeMacroTotals(
        startInclusive: Instant,
        endExclusive: Instant
    ): Flow<MacroTotals>
}