package com.example.adobongkangkong.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "recipe_ingredients",
    primaryKeys = ["recipeFoodId", "ingredientFoodId"],
    foreignKeys = [
        ForeignKey(
            entity = FoodEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeFoodId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = FoodEntity::class,
            parentColumns = ["id"],
            childColumns = ["ingredientFoodId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["recipeFoodId"]),
        Index(value = ["ingredientFoodId"])
    ]
)
data class RecipeIngredientEntity(
    val recipeFoodId: Long,
    val ingredientFoodId: Long,
    val servings: Double // how many servings of the ingredient contribute to 1 recipe batch/definition
)
