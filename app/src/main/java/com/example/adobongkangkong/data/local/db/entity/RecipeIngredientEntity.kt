package com.example.adobongkangkong.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recipe_ingredient",
    indices = [
        Index("recipeId"),
        Index("foodId"),
    ]
)
data class RecipeIngredientEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    val recipeId: Long,
    val foodId: Long,

    /** Mutually exclusive with [amountGrams]. */
    val amountServings: Double? = null,

    /** Mutually exclusive with [amountServings]. */
    val amountGrams: Double? = null,

    val sortOrder: Int = 0
)
