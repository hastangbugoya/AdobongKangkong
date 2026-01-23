package com.example.adobongkangkong.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "food_nutrients",
    primaryKeys = ["foodId", "nutrientId"],
    indices = [Index("foodId"), Index("nutrientId")],
    foreignKeys = [
        ForeignKey(
            entity = FoodEntity::class,
            parentColumns = ["id"],
            childColumns = ["foodId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = NutrientEntity::class,
            parentColumns = ["id"],
            childColumns = ["nutrientId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class FoodNutrientEntity(
    val foodId: Long,
    val nutrientId: Long,

    val nutrientAmountPerBasis: Double,                 // value for the basis below
)

enum class BasisType { PER_SERVING, PER_100G }

