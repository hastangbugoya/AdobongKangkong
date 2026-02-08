package com.example.adobongkangkong.domain.mealprep.usecase

import com.example.adobongkangkong.domain.mealprep.model.PlannedDay
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObservePlannedDayUseCase @Inject constructor(
    private val observePlannedDays: ObservePlannedDaysUseCase
) {
    operator fun invoke(dateIso: String): Flow<PlannedDay> =
        observePlannedDays.observeDay(dateIso)
}

