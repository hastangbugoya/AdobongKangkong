package com.example.adobongkangkong.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "food_category_cross_refs",
    primaryKeys = ["foodId", "categoryId"],
    foreignKeys = [
        ForeignKey(
            entity = FoodEntity::class,
            parentColumns = ["id"],
            childColumns = ["foodId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = FoodCategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["foodId"]),
        Index(value = ["categoryId"]),
    ]
)
data class FoodCategoryCrossRefEntity(
    val foodId: Long,
    val categoryId: Long,
)
