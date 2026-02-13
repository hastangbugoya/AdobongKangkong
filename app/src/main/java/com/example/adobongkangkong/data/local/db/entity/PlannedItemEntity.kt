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
        Index(value = ["mealId", "sortOrder"])
    ]
)
data class PlannedItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    val mealId: Long,

    /** FOOD | RECIPE | RECIPE_BATCH */
    val type: PlannedItemSource,


    /** foodId (FOOD), recipeFoodId/recipeId (RECIPE), or recipeBatchId (RECIPE_BATCH) */
    val refId: Long,

    val grams: Double? = null,
    val servings: Double? = null,

    /** Order within the meal */
    val sortOrder: Int
)
