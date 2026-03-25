package com.example.adobongkangkong.domain.shared.usecase

import com.example.adobongkangkong.domain.shared.serialization.SharedNutritionSnapshotJsonSerializer
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * BuildSharedNutritionSnapshotJsonUseCase
 *
 * ## Purpose
 * Produces the shared daily nutrition snapshot as a JSON string on demand.
 *
 * This is the minimal internal/debug exposure path for the new shared nutrition
 * contract inside AdobongKangkong.
 *
 * ## Architecture
 * This use case is intentionally thin:
 * - [BuildSharedNutritionSnapshotUseCase] builds the contract model
 * - [SharedNutritionSnapshotJsonSerializer] encodes it to JSON
 *
 * This use case exists so UI/debug surfaces do not need to know:
 * - how the snapshot is built
 * - which serializer is used
 * - any schema/model details beyond "give me JSON for this day"
 *
 * ## Important rules
 * - No business logic here
 * - No nutrition math here
 * - No persistence here
 * - No worker / background generation here
 * - No file export / IPC / content provider plumbing here
 *
 * ## Current intended use
 * - temporary internal testing from debug-friendly UI surfaces
 * - developer inspection of payload shape
 * - validation that the producer contract is stable before any consumer exists
 *
 * ## Future evolution
 * If later we add file export, IPC, or app-to-app sharing, those transport layers
 * should call this producer path or the underlying snapshot builder rather than
 * duplicating contract generation logic.
 */
class BuildSharedNutritionSnapshotJsonUseCase @Inject constructor(
    private val buildSharedNutritionSnapshot: BuildSharedNutritionSnapshotUseCase,
    private val serializer: SharedNutritionSnapshotJsonSerializer
) {

    /**
     * Builds and serializes the shared nutrition snapshot for [date] using [zoneId].
     *
     * @return pretty-printed JSON representation of the current shared snapshot payload.
     */
    suspend operator fun invoke(
        date: LocalDate,
        zoneId: ZoneId
    ): String {
        val snapshot = buildSharedNutritionSnapshot(
            date = date,
            zoneId = zoneId
        )
        return serializer.serialize(snapshot)
    }
}