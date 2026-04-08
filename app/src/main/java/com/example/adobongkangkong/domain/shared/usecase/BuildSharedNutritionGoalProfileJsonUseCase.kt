package com.example.adobongkangkong.domain.shared.usecase

import com.example.adobongkangkong.domain.shared.serialization.SharedNutritionGoalProfileJsonSerializer
import javax.inject.Inject

/**
 * BuildSharedNutritionGoalProfileJsonUseCase
 *
 * ## Purpose
 * Produces the shared JSON contract for AdobongKangkong's current active
 * nutrition goal profile.
 *
 * ## Architecture
 * This use case is intentionally thin:
 *
 *     BuildSharedNutritionGoalProfileUseCase -> SharedNutritionGoalProfileJsonSerializer
 *
 * It exists so transport surfaces do not need to know:
 * - how the goal profile is built
 * - which serializer is used
 * - any schema/model details beyond "give me the current goal profile JSON"
 *
 * ## Important rules
 * - No business logic here
 * - No nutrition math here
 * - No persistence here
 * - No content provider / IPC plumbing here
 * - No HH-specific import logic here
 *
 * ## Product intent
 * This exports AK's single current active nutrition profile snapshot.
 * It does NOT imply:
 * - multi-goal support
 * - historical goal records
 * - write-back behavior
 *
 * ## Future evolution
 * If later we add new sharing surfaces, they should call this producer path or
 * the underlying profile builder rather than duplicating contract generation.
 */
class BuildSharedNutritionGoalProfileJsonUseCase @Inject constructor(
    private val buildSharedNutritionGoalProfileUseCase: BuildSharedNutritionGoalProfileUseCase,
    private val serializer: SharedNutritionGoalProfileJsonSerializer
) {

    /**
     * Builds and serializes the current shared nutrition goal profile.
     *
     * @return pretty-printed JSON representation of the current AK goal export payload.
     */
    suspend operator fun invoke(): String {
        val profile = buildSharedNutritionGoalProfileUseCase()
        return serializer.serialize(profile)
    }
}