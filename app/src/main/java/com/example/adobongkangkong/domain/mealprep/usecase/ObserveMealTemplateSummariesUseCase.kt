package com.example.adobongkangkong.domain.mealprep.usecase

import com.example.adobongkangkong.domain.mealprep.model.MealTemplateSummary
import com.example.adobongkangkong.domain.repository.MealTemplateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ObserveMealTemplateSummariesUseCase @Inject constructor(
    private val templates: MealTemplateRepository
) {
    operator fun invoke(): Flow<List<MealTemplateSummary>> =
        templates.observeAll().map { entities ->
            entities.map { entity ->
                MealTemplateSummary(
                    id = entity.id,
                    name = entity.name,
                    defaultSlot = entity.defaultSlot
                )
            }
        }
}
