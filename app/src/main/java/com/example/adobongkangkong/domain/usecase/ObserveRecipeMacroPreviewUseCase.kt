package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.domain.model.RecipeMacroPreview
import com.example.adobongkangkong.domain.repository.FoodNutrientRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest
import javax.inject.Inject

class ObserveRecipeMacroPreviewUseCase @Inject constructor(
    private val foodNutrientRepo: FoodNutrientRepository
) {
    operator fun invoke(
        ingredients: Flow<List<Pair<Long, Double>>>
    ): Flow<RecipeMacroPreview> =
        ingredients.mapLatest { list ->
            foodNutrientRepo.computeRecipeMacroPreview(list)
        }
}
