package com.example.adobongkangkong.ui.dashboard.pinned.usecase

import com.example.adobongkangkong.domain.model.Nutrient
import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.repository.NutrientRepository
import com.example.adobongkangkong.ui.dashboard.pinned.model.DashboardPinOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ObserveDashboardPinOptionsUseCase @Inject constructor(
    private val nutrientRepo: NutrientRepository
) {
    operator fun invoke(): Flow<List<DashboardPinOption>> =
        nutrientRepo.observeAllNutrients()
            .map { list ->
                list
                    .map { it.toPinOption() }
                    .filterNot { it.key in fixedMacros }
                    .sortedWith(
                        compareBy<DashboardPinOption> { it.category.ordinal }
                            .thenBy { it.displayName.lowercase() }
                    )
            }

    private fun Nutrient.toPinOption() =
        DashboardPinOption(
            key = NutrientKey(code),
            displayName = displayName,
            unit = unit.name.lowercase(),
            category = category
        )

    private val fixedMacros = setOf(
        NutrientKey("CALORIES_KCAL"),
        NutrientKey("PROTEIN_G"),
        NutrientKey("CARBS_G"),
        NutrientKey("FAT_G")
    )
}
