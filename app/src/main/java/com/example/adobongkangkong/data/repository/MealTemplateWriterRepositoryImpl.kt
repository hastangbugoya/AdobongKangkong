package com.example.adobongkangkong.data.repository

import androidx.room.withTransaction
import com.example.adobongkangkong.data.local.db.NutriDatabase
import com.example.adobongkangkong.data.local.db.entity.MealTemplateEntity
import com.example.adobongkangkong.data.local.db.entity.MealTemplateItemEntity
import com.example.adobongkangkong.domain.mealprep.model.FoodMealTemplateItem
import com.example.adobongkangkong.domain.mealprep.model.MealTemplate
import com.example.adobongkangkong.domain.mealprep.model.MealTemplateItem
import com.example.adobongkangkong.domain.mealprep.model.RecipeBatchMealTemplateItem
import com.example.adobongkangkong.domain.mealprep.model.RecipeMealTemplateItem
import com.example.adobongkangkong.domain.planner.model.PlannedItemSource
import com.example.adobongkangkong.domain.repository.MealTemplateWriterRepository
import javax.inject.Inject

class MealTemplateWriterRepositoryImpl @Inject constructor(
    private val db: NutriDatabase
) : MealTemplateWriterRepository {

    override suspend fun save(template: MealTemplate): Long = db.withTransaction {
        val templateDao = db.mealTemplateDao()
        val itemDao = db.mealTemplateItemDao()

        val normalizedName = template.name.trim()
        require(normalizedName.isNotBlank()) { "template.name must not be blank" }

        val templateId = if (template.id > 0L) {
            val existing = templateDao.getById(template.id)
                ?: throw IllegalArgumentException("Meal template not found: id=${template.id}")

            templateDao.update(
                existing.copy(
                    name = normalizedName,
                    defaultSlot = template.defaultSlot
                )
            )
            template.id
        } else {
            templateDao.insert(
                MealTemplateEntity(
                    name = normalizedName,
                    defaultSlot = template.defaultSlot
                )
            )
        }

        itemDao.deleteItemsForTemplate(templateId)

        template.items.forEachIndexed { index, item ->
            itemDao.insert(
                MealTemplateItemEntity(
                    templateId = templateId,
                    type = item.toEntityType(),
                    refId = item.toRefId(),
                    grams = item.quantity.grams,
                    servings = item.quantity.servings,
                    sortOrder = index
                )
            )
        }

        templateId
    }

    override suspend fun delete(templateId: Long) = db.withTransaction {
        require(templateId > 0L) { "templateId must be > 0" }

        val templateDao = db.mealTemplateDao()
        val existing = templateDao.getById(templateId)
            ?: throw IllegalArgumentException("Meal template not found: id=$templateId")

        templateDao.delete(existing)
    }

    private fun MealTemplateItem.toEntityType(): PlannedItemSource = when (this) {
        is FoodMealTemplateItem -> PlannedItemSource.FOOD
        is RecipeMealTemplateItem -> PlannedItemSource.RECIPE
        is RecipeBatchMealTemplateItem -> PlannedItemSource.RECIPE_BATCH
    }

    private fun MealTemplateItem.toRefId(): Long = when (this) {
        is FoodMealTemplateItem -> foodId
        is RecipeMealTemplateItem -> recipeId
        is RecipeBatchMealTemplateItem -> recipeBatchId
    }
}
