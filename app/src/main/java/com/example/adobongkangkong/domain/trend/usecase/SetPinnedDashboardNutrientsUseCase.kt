package com.example.adobongkangkong.domain.trend.usecase

import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.repository.UserPinnedNutrientRepository
import javax.inject.Inject

/**
 * Sets the user-selected dashboard nutrients.
 *
 * Dashboard contract:
 * - Always displays the fixed macro nutrients:
 *   - CALORIES_KCAL
 *   - PROTEIN_G
 *   - CARBS_G
 *   - FAT_G
 * - Plus up to two user-pinned extra nutrients (slot 0, slot 1).
 *
 * Invariants enforced:
 * 1) Stable slot ordering (slot 0, slot 1)
 * 2) Fixed dashboard nutrients cannot be pinned
 * 3) No duplicates across slots
 * 4) Canonical (trim + uppercase) keys persisted
 */
class SetPinnedDashboardNutrientsUseCase @Inject constructor(
    private val pinnedRepo: UserPinnedNutrientRepository
) {
    suspend operator fun invoke(slot0: NutrientKey?, slot1: NutrientKey?) {
        val cleaned0 = slot0.canonical()
            ?.takeUnless { it in fixed }

        var cleaned1 = slot1.canonical()
            ?.takeUnless { it in fixed }

        // Prevent duplicates: if slot1 equals slot0, clear slot1.
        if (cleaned0 != null && cleaned0 == cleaned1) {
            cleaned1 = null
        }

        pinnedRepo.setPinnedPositions(cleaned0, cleaned1)
    }

    private fun NutrientKey?.canonical(): NutrientKey? =
        this?.let { NutrientKey(it.value.trim().uppercase()) }

    private val fixed: Set<NutrientKey> = setOf(
        NutrientKey("CALORIES_KCAL"),
        NutrientKey("PROTEIN_G"),
        NutrientKey("CARBS_G"),
        NutrientKey("FAT_G")
    )
}


