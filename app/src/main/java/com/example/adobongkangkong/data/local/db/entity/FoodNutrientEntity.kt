package com.example.adobongkangkong.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.example.adobongkangkong.domain.model.ServingUnit

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
    val basisType: BasisType
)

enum class BasisType { PER_SERVING, PER_100G }

internal fun decideBasisType(
    gramsPerServing: Double?,
    servingUnit: ServingUnit?
): BasisType = when {
    gramsPerServing != null && gramsPerServing > 0 -> BasisType.PER_SERVING
    else -> BasisType.PER_100G
}
