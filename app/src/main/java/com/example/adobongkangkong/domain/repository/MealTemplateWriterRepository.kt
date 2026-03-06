package com.example.adobongkangkong.domain.repository

import com.example.adobongkangkong.domain.mealprep.model.MealTemplate

/**
 * Transactional writer for create/update/delete template operations that must keep
 * meal_templates and meal_template_items in sync.
 */
interface MealTemplateWriterRepository {
    suspend fun save(template: MealTemplate): Long
    suspend fun delete(templateId: Long)
}
