package com.example.adobongkangkong.domain.planner.usecase

import com.example.adobongkangkong.domain.planner.model.PlannedDay
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObservePlannedDayUseCase @Inject constructor(
    private val observePlannedDays: ObservePlannedDaysUseCase
) {
    operator fun invoke(dateIso: String): Flow<PlannedDay> =
        observePlannedDays.observeDay(dateIso)
}

