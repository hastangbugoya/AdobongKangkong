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
 * - IOUs have NO nutrition and do not affect totals.
 */
class ObserveDayLogIousUseCase @Inject constructor(
    private val ious: IouRepository
) {

    /**
     * @param dateIso ISO date string (yyyy-MM-dd) for the selected day.
     * @return A stream of UI IOU rows for Day Log.
     */
    operator fun invoke(dateIso: String): Flow<List<DayLogIouRow>> {
        return ious.observeForDate(dateIso)
            .map { entities ->
                entities.map { e ->
                    DayLogIouRow(
                        iouId = e.id,
                        description = e.description
                    )
                }
            }
    }
}
