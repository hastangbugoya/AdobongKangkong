package com.example.adobongkangkong.domain.planner.model

/**
 * Type-safe domain/UI reference for a planned meal item.
 *
 * Room keeps planned item identity as flat fields:
 *
 * - sourceType
 * - sourceId
 * - optional recipeVariantId
 *
 * This sealed model is the safer domain/UI representation of those fields.
 *
 * Why this exists:
 * - FOOD refId means foodId.
 * - RECIPE refId means recipeId.
 * - RECIPE_BATCH refId means recipeBatchId.
 *
 * Keeping those as loose Longs everywhere made it too easy for planner/editor code
 * to accidentally treat every planned item as a Food. This sealed reference lets
 * domain/UI code branch safely while Room remains simple and query-friendly.
 */
sealed interface PlannedItemRef {

    val sourceType: PlannedItemSource
    val sourceId: Long

    /**
     * Only meaningful for [Recipe].
     *
     * For [Food] and [RecipeBatch], this is always null even if bad persisted data
     * accidentally contains a variant id.
     */
    val recipeVariantId: Long?
        get() = null

    data class Food(
        val foodId: Long,
    ) : PlannedItemRef {
        override val sourceType: PlannedItemSource = PlannedItemSource.FOOD
        override val sourceId: Long = foodId
    }

    data class Recipe(
        val recipeId: Long,
        override val recipeVariantId: Long? = null,
    ) : PlannedItemRef {
        override val sourceType: PlannedItemSource = PlannedItemSource.RECIPE
        override val sourceId: Long = recipeId
    }

    data class RecipeBatch(
        val batchId: Long,
    ) : PlannedItemRef {
        override val sourceType: PlannedItemSource = PlannedItemSource.RECIPE_BATCH
        override val sourceId: Long = batchId
    }

    companion object {

        /**
         * Builds the sealed domain reference from the flat persisted/domain fields.
         *
         * The variant id is intentionally ignored unless [sourceType] is RECIPE.
         */
        fun from(
            sourceType: PlannedItemSource,
            sourceId: Long,
            recipeVariantId: Long? = null,
        ): PlannedItemRef {
            return when (sourceType) {
                PlannedItemSource.FOOD -> Food(foodId = sourceId)

                PlannedItemSource.RECIPE -> Recipe(
                    recipeId = sourceId,
                    recipeVariantId = recipeVariantId,
                )

                PlannedItemSource.RECIPE_BATCH -> RecipeBatch(batchId = sourceId)
            }
        }
    }
}

data class PlannedItem(
    val id: Long,

    /**
     * Legacy/flat identity fields kept for compatibility with existing planner code.
     *
     * New code should prefer [ref] when branching by item type.
     */
    val sourceType: PlannedItemSource,
    val sourceId: Long,

    val qtyGrams: Double?,
    val qtyServings: Double?,

    /**
     * Optional resolved display title for UI, e.g. food name or base recipe name.
     * This is derived, not stored in planner DB.
     */
    val title: String?,

    /**
     * Optional selected recipe variant.
     *
     * Meaningful only when sourceType == PlannedItemSource.RECIPE.
     * Null means the base recipe.
     */
    val recipeVariantId: Long? = null,

    /**
     * Optional resolved variant display title.
     *
     * Example:
     * title = "Adobo"
     * variantTitle = "Less oil"
     */
    val variantTitle: String? = null,
) {
    /**
     * Type-safe view of [sourceType], [sourceId], and [recipeVariantId].
     */
    val ref: PlannedItemRef =
        PlannedItemRef.from(
            sourceType = sourceType,
            sourceId = sourceId,
            recipeVariantId = recipeVariantId,
        )

    val normalizedRecipeVariantId: Long?
        get() = when (ref) {
            is PlannedItemRef.Recipe -> ref.recipeVariantId
            is PlannedItemRef.Food,
            is PlannedItemRef.RecipeBatch -> null
        }
}