package com.example.adobongkangkong.ui.daylog.usecase

import com.example.adobongkangkong.domain.repository.IouRepository
import com.example.adobongkangkong.ui.daylog.model.DayLogIouRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Observes IOU rows for the Day Log screen for a single ISO day (yyyy-MM-dd).
 *
 * IMPORTANT:
 * - IOUs are day-based and MUST be driven by `dateIso`.
 * - IOUs do NOT affect totals.
 * - IOUs may carry optional macro estimates for UI display only.
 */
class ObserveDayLogIousUseCase @Inject constructor(
    private val ious: IouRepository
) {

    operator fun invoke(dateIso: String): Flow<List<DayLogIouRow>> {
        return ious.observeForDate(dateIso)
            .map { entities ->
                entities.map { e ->
                    DayLogIouRow(
                        iouId = e.id,
                        description = e.description,
                        estimatedCaloriesKcal = e.estimatedCaloriesKcal,
                        estimatedProteinG = e.estimatedProteinG,
                        estimatedCarbsG = e.estimatedCarbsG,
                        estimatedFatG = e.estimatedFatG
                    )
                }
            }
    }
}