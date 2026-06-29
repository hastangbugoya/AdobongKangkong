package com.example.adobongkangkong.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.adobongkangkong.domain.planner.model.PlannedItemSource

@Entity(
    tableName = "planned_items",
    foreignKeys = [
        ForeignKey(
            entity = PlannedMealEntity::class,
            parentColumns = ["id"],
            childColumns = ["mealId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["mealId"]),
        Index(value = ["mealId", "sortOrder"]),
        Index(value = ["recipeVariantId"])
    ]
)
data class PlannedItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    val mealId: Long,

    /** FOOD | RECIPE | RECIPE_BATCH */
    val type: PlannedItemSource,

    /**
     * Planned item identity rules:
     * - type=FOOD         => refId = foods.id
     * - type=RECIPE       => refId = recipes.id
     * - type=RECIPE_BATCH => refId = recipe_batches.id
     *
     * Recipe variant rule:
     * - recipeVariantId is meaningful only when type == RECIPE.
     * - recipeVariantId == null means use the base recipe.
     * - recipeVariantId != null means use that selected recipe variant for planner display,
     *   planner nutrition preview, and log snapshot creation.
     *
     * Do not replace refId with recipeVariantId.
     * refId remains the base item identity; recipeVariantId is only an optional modifier.
     *
     * NOTE:
     * - Recipes have a Food row through RecipeEntity.foodId.
     * - Logs should still reconcile to the recipe Food.stableId.
     * - Variant selection changes nutrition resolution/snapshot behavior, not the base recipe identity.
     * - RECIPE_BATCH is currently paused/shelved; do not expand variant logic into batch logic yet.
     */
    val refId: Long,

    /**
     * Optional recipe variant selected for this planned recipe item.
     *
     * Valid only when type == PlannedItemSource.RECIPE.
     * Must be null for FOOD.
     * Should remain null for RECIPE_BATCH while cooked-batch planning is paused.
     *
     * This is nullable so all existing planned items migrate cleanly:
     * null means "base recipe" for recipe rows and "not applicable" for non-recipe rows.
     */
    val recipeVariantId: Long? = null,

    val grams: Double? = null,
    val servings: Double? = null,

    /** Order within the meal */
    val sortOrder: Int
)