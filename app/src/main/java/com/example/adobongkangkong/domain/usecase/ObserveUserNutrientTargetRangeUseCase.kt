package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.repository.UserNutrientTargetRepository
import com.example.adobongkangkong.ui.calendar.model.TargetRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ObserveUserNutrientTargetRangeUseCase @Inject constructor(
    private val repo: UserNutrientTargetRepository
) {
    operator fun invoke(key: NutrientKey): Flow<TargetRange> =
        repo.observeTargets()
            .map { map ->
                val t = map[key.value] // or key.value depending on your NutrientKey implementation
                TargetRange(
                    min = t?.minPerDay,
                    target = t?.targetPerDay,
                    max = t?.maxPerDay
                )
            }
            .distinctUntilChanged()
}
