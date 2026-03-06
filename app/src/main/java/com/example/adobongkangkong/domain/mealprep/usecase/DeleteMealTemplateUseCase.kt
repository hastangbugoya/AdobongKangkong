package com.example.adobongkangkong.domain.mealprep.usecase

import com.example.adobongkangkong.domain.repository.MealTemplateWriterRepository
import javax.inject.Inject

class DeleteMealTemplateUseCase @Inject constructor(
    private val writer: MealTemplateWriterRepository
) {
    suspend operator fun invoke(templateId: Long) {
        require(templateId > 0L) { "templateId must be > 0" }
        writer.delete(templateId)
    }
}
