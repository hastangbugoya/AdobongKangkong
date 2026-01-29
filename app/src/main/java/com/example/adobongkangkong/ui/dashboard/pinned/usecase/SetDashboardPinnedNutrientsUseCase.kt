package com.example.adobongkangkong.ui.dashboard.pinned.usecase

import com.example.adobongkangkong.domain.repository.UserPinnedNutrientRepository
import com.example.adobongkangkong.ui.dashboard.pinned.model.DashboardPinnedSelection
import javax.inject.Inject

class SetDashboardPinnedNutrientsUseCase @Inject constructor(
    private val repo: UserPinnedNutrientRepository
) {
    suspend operator fun invoke(selection: DashboardPinnedSelection) {
        require(selection.slot0 != selection.slot1) {
            "Pinned nutrients must be distinct"
        }
        repo.setPinnedPositions(
            slot0 = selection.slot0,
            slot1 = selection.slot1
        )
    }
}