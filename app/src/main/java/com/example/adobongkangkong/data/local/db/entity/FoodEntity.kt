package com.example.adobongkangkong.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.adobongkangkong.domain.model.ServingUnit

@Entity(
    tableName = "foods",
    indices = [
        Index(value = ["name"]),
        Index(value = ["isRecipe"]),
    ]
)
data class FoodEntity(
    @PrimaryKey(autoGenerate = false)
    val id: Long, // from CSV; stable ID

    val name: String,
    val brand: String? = null,

    // Serving model (v1): nutrients stored per serving; logging uses "servings"
    val servingSize: Double = 1.0,
    val servingUnit: ServingUnit = ServingUnit.G,

    val servingsPerPackage: Double? = null,
    val gramsPerServing: Double? = null,

    // Treat recipes as foods so they can be logged identically
    val isRecipe: Boolean = false
)

