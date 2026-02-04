package com.example.adobongkangkong.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.example.adobongkangkong.domain.model.NutrientUnit

@Entity(
    tableName = "food_nutrients",
    primaryKeys = ["foodId", "nutrientId", "basisType"],
    indices = [
        Index("foodId"),
        Index("nutrientId"),
        Index("basisType")
    ],
    foreignKeys = [
        ForeignKey(
            entity = FoodEntity::class,
            parentColumns = ["id"],
            childColumns = ["foodId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class FoodNutrientEntity(
    val foodId: Long,

    /**
     * Authoritative nutrient key (USDA nutrientId).
     * Intentionally NOT an FK to NutrientEntity so we can store nutrients
     * even when the app's catalog doesn't know them yet.
     */
    val nutrientId: Long,

    /**
     * Nutrient value for the specified basisType, stored in canonical app units.
     */
    val nutrientAmountPerBasis: Double,

    /**
     * Canonical unit for nutrientAmountPerBasis.
     * (e.g., KCAL, G, MG, UG, IU if you support it)
     */
    val unit: NutrientUnit,

    val basisType: BasisType
)

enum class BasisType {
    /**
     * Exactly what USDA reports (serving size/unit + household text live on FoodEntity for traceability).
     */
    USDA_REPORTED_SERVING,

    /**
     * Standard mass normalization when serving is mass-backed.
     */
    PER_100G,

    /**
     * Optional volume normalization if/when you choose to compute it.
     * (Safe to keep now even if unused.)
     */
    PER_100ML
}
