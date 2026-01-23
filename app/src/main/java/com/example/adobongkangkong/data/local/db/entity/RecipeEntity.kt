package com.example.adobongkangkong.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val foodId: Long,                 // points to FoodEntity(name="Overnight Mango Oats")
    val servingsProduced: Double? = null, // 5 containers, 6 servings, etc
    val notes: String? = null
)
