package com.example.adobongkangkong.domain.usecase.recipevariant

import com.example.adobongkangkong.data.local.db.dao.FoodDao
import com.example.adobongkangkong.data.local.db.dao.RecipeDao
import com.example.adobongkangkong.data.local.db.dao.RecipeIngredientDao
import com.example.adobongkangkong.data.local.db.entity.FoodEntity
import com.example.adobongkangkong.data.local.db.entity.RecipeIngredientEntity
import com.example.adobongkangkong.data.local.db.entity.RecipeVariantIngredientChangeEntity
import com.example.adobongkangkong.data.local.db.entity.RecipeVariantIngredientChangeType
import com.example.adobongkangkong.domain.model.AssembledRecipeVariant
import com.example.adobongkangkong.domain.model.AssembledRecipeVariantIngredientLine
import com.example.adobongkangkong.domain.model.RecipeVariantIngredientLineSource
import com.example.adobongkangkong.domain.model.RemovedRecipeVariantIngredientLine
import com.example.adobongkangkong.domain.repository.RecipeVariantRepository
import javax.inject.Inject

class AssembleRecipeVariantUseCase @Inject constructor(
    private val recipeVariantRepository: RecipeVariantRepository,
    private val recipeDao: RecipeDao,
    private val recipeIngredientDao: RecipeIngredientDao,
    private val foodDao: FoodDao,
) {

    suspend operator fun invoke(
        variantId: Long,
        draftChanges: List<RecipeVariantIngredientChangeEntity>? = null,
    ): AssembledRecipeVariant {
        val warnings = mutableListOf<String>()

        val variant = recipeVariantRepository.getVariantById(variantId)
            ?: return AssembledRecipeVariant(
                recipeFoodId = 0L,
                recipeName = "",
                variantId = variantId,
                variantName = "Missing variant",
                variantNotes = null,
                finalIngredientLines = emptyList(),
                removedIngredientLines = emptyList(),
                warnings = listOf("Variant not found."),
            )

        val recipeFood = foodDao.getById(variant.recipeFoodId)

        val recipe = recipeDao.getByFoodId(variant.recipeFoodId)
            ?: return AssembledRecipeVariant(
                recipeFoodId = variant.recipeFoodId,
                recipeName = recipeFood?.name.orEmpty(),
                variantId = variant.id,
                variantName = variant.name,
                variantNotes = variant.notes,
                finalIngredientLines = emptyList(),
                removedIngredientLines = emptyList(),
                warnings = listOf("Base recipe not found."),
            )

        val baseIngredients = recipeIngredientDao.getForRecipe(recipe.id)
        val changes = draftChanges
            ?: recipeVariantRepository.getChangesForVariant(variant.id)

        val foodIds = buildSet {
            baseIngredients.forEach { add(it.foodId) }
            changes.forEach { change ->
                change.foodId?.let(::add)
            }
        }

        val foodsById = foodDao.getByIds(foodIds.toList())
            .associateBy { it.id }

        val finalLinesByBaseIngredientId = baseIngredients
            .associate { ingredient ->
                ingredient.id to ingredient.toOriginalLine(
                    food = foodsById[ingredient.foodId],
                    warnings = warnings,
                )
            }
            .filterValues { it != null }
            .mapValues { it.value!! }
            .toMutableMap()

        val removedLines = mutableListOf<RemovedRecipeVariantIngredientLine>()
        val addedLines = mutableListOf<AssembledRecipeVariantIngredientLine>()

        val removeChanges = changes
            .filter { it.changeType == RecipeVariantIngredientChangeType.REMOVE }

        val adjustChanges = changes
            .filter { it.changeType == RecipeVariantIngredientChangeType.ADJUST }

        val addChanges = changes
            .filter { it.changeType == RecipeVariantIngredientChangeType.ADD }

        val removedBaseIngredientIds = removeChanges
            .mapNotNull { it.baseRecipeIngredientId }
            .toSet()

        removeChanges.forEach { change ->
            val baseIngredientId = change.baseRecipeIngredientId

            if (baseIngredientId == null) {
                warnings += "Remove change is missing a base ingredient id."
                return@forEach
            }

            val baseIngredient = baseIngredients.firstOrNull { it.id == baseIngredientId }

            if (baseIngredient == null) {
                warnings += "Remove change points to an ingredient that no longer exists."
                return@forEach
            }

            finalLinesByBaseIngredientId.remove(baseIngredientId)

            removedLines += RemovedRecipeVariantIngredientLine(
                baseRecipeIngredientId = baseIngredientId,
                food = foodsById[baseIngredient.foodId],
                servings = baseIngredient.amountServings,
                grams = baseIngredient.amountGrams,
                note = change.note,
            )
        }

        adjustChanges.forEach { change ->
            val baseIngredientId = change.baseRecipeIngredientId

            if (baseIngredientId == null) {
                warnings += "Adjust change is missing a base ingredient id."
                return@forEach
            }

            if (baseIngredientId in removedBaseIngredientIds) {
                warnings += "Ignored adjustment for a removed ingredient."
                return@forEach
            }

            val baseIngredient = baseIngredients.firstOrNull { it.id == baseIngredientId }

            if (baseIngredient == null) {
                warnings += "Adjust change points to an ingredient that no longer exists."
                return@forEach
            }

            val food = foodsById[baseIngredient.foodId]

            if (food == null) {
                warnings += "Adjusted ingredient food was not found."
                return@forEach
            }

            finalLinesByBaseIngredientId[baseIngredientId] =
                AssembledRecipeVariantIngredientLine(
                    source = RecipeVariantIngredientLineSource.ADJUSTED,
                    baseRecipeIngredientId = baseIngredientId,
                    food = food,

                    // Final variant amount.
                    servings = change.servings,
                    grams = change.grams,

                    // Permanent base recipe reference amount.
                    originalServings = baseIngredient.amountServings,
                    originalGrams = baseIngredient.amountGrams,

                    note = change.note,
                    sortOrder = baseIngredient.sortOrder,
                )
        }

        val maxBaseSortOrder = baseIngredients.maxOfOrNull { it.sortOrder } ?: 0

        addChanges.forEachIndexed { index, change ->
            val foodId = change.foodId

            if (foodId == null) {
                warnings += "Add change is missing a food id."
                return@forEachIndexed
            }

            val food = foodsById[foodId]

            if (food == null) {
                warnings += "Added ingredient food was not found."
                return@forEachIndexed
            }

            addedLines += AssembledRecipeVariantIngredientLine(
                source = RecipeVariantIngredientLineSource.ADDED,
                baseRecipeIngredientId = null,
                food = food,

                // Final variant amount.
                servings = change.servings,
                grams = change.grams,

                // Added ingredients have no base recipe amount.
                originalServings = null,
                originalGrams = null,

                note = change.note,
                sortOrder = maxBaseSortOrder + 1 + index,
            )
        }

        val finalLines = (finalLinesByBaseIngredientId.values + addedLines)
            .sortedBy { it.sortOrder }

        return AssembledRecipeVariant(
            recipeFoodId = variant.recipeFoodId,
            recipeName = recipeFood?.name.orEmpty(),
            variantId = variant.id,
            variantName = variant.name,
            variantNotes = variant.notes,
            finalIngredientLines = finalLines,
            removedIngredientLines = removedLines,
            warnings = warnings,
        )
    }

    private fun RecipeIngredientEntity.toOriginalLine(
        food: FoodEntity?,
        warnings: MutableList<String>,
    ): AssembledRecipeVariantIngredientLine? {
        if (food == null) {
            warnings += "Original ingredient food was not found."
            return null
        }

        return AssembledRecipeVariantIngredientLine(
            source = RecipeVariantIngredientLineSource.ORIGINAL,
            baseRecipeIngredientId = id,
            food = food,

            // Original recipe amount is also the final amount until adjusted.
            servings = amountServings,
            grams = amountGrams,

            // Permanent base recipe reference amount.
            originalServings = amountServings,
            originalGrams = amountGrams,

            note = null,
            sortOrder = sortOrder,
        )
    }
}
