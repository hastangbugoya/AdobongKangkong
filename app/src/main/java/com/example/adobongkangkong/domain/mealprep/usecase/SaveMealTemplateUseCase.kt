package com.example.adobongkangkong.domain.mealprep.usecase

import com.example.adobongkangkong.domain.mealprep.model.MealTemplate
import com.example.adobongkangkong.domain.repository.MealTemplateWriterRepository
import javax.inject.Inject

/**
 * Creates or updates a meal template and replaces its item set transactionally.
 *
 * Conventions:
 * - template.id == 0L -> create
 * - template.id > 0L  -> update existing template
 */
class SaveMealTemplateUseCase @Inject constructor(
    private val writer: MealTemplateWriterRepository
) {
    suspend operator fun invoke(template: MealTemplate): Long {
        require(template.name.isNotBlank()) { "template.name must not be blank" }
        return writer.save(template)
    }
}
