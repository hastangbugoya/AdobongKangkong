package com.example.adobongkangkong.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

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

    /** FOOD | RECIPE_BATCH */
    val type: String,

    /** foodId or recipeBatchId depending on type */
    val refId: Long,

    val grams: Double? = null,
    val servings: Double? = null,

    /** Order within the meal */
    val sortOrder: Int
)
