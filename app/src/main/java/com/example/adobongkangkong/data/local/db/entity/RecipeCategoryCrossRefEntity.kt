package com.example.adobongkangkong.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "recipe_category_cross_refs",
    primaryKeys = ["recipeId", "categoryId"],
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
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
        Index(value = ["recipeId"]),
        Index(value = ["categoryId"]),
    ]
)
data class RecipeCategoryCrossRefEntity(
    val recipeId: Long,
    val categoryId: Long,
)