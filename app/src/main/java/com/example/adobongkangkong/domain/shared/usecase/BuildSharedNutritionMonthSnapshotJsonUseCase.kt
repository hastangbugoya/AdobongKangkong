package com.example.adobongkangkong.domain.shared.usecase

import com.example.adobongkangkong.domain.shared.serialization.SharedNutritionMonthSnapshotJsonSerializer
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/**
 * BuildSharedNutritionMonthSnapshotJsonUseCase
 *
 * ## Purpose
 * Produces the compact month-level shared nutrition JSON contract for external
 * consumers such as HastangHubaga's calendar month view.
 *
 * ## Architecture
 * This is the month-level sibling of the existing day JSON builder:
 *
 *     BuildSharedNutritionMonthSnapshotUseCase -> SharedNutritionMonthSnapshotJsonSerializer
 *
 * It remains intentionally thin:
 * - build shared month snapshot model
 * - serialize to transport JSON
 *
 * ## Critical rule
 * DO NOT duplicate month assembly or nutrition logic here.
 * This layer is serialization orchestration only.
 */
class BuildSharedNutritionMonthSnapshotJsonUseCase @Inject constructor(
    private val buildSharedNutritionMonthSnapshotUseCase: BuildSharedNutritionMonthSnapshotUseCase,
    private val serializer: SharedNutritionMonthSnapshotJsonSerializer
) {

    suspend operator fun invoke(
        month: YearMonth,
        zoneId: ZoneId
    ): String {
        val snapshot = buildSharedNutritionMonthSnapshotUseCase(
            month = month,
            zoneId = zoneId
        )
        return serializer.serialize(snapshot)
    }
}