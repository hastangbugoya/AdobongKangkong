package com.example.adobongkangkong.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.adobongkangkong.domain.planner.model.PlannedItemSource

@Entity(
    tableName = "meal_template_items",
    foreignKeys = [
        ForeignKey(
            entity = MealTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["templateId"]),
        Index(value = ["templateId", "sortOrder"])
    ]
)
data class MealTemplateItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    val templateId: Long,

    /** FOOD | RECIPE_BATCH */
    val type: PlannedItemSource,

    /** foodId or recipeBatchId depending on type */
    val refId: Long,

    val grams: Double? = null,
    val servings: Double? = null,

    val sortOrder: Int
)
