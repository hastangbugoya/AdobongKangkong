package com.example.adobongkangkong.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "user_nutrient_targets",
    indices = [
        Index(value = ["nutrientCode"], unique = true)
    ]
)
data class UserNutrientTargetEntity(
    @PrimaryKey
    val nutrientCode: String,          // e.g. "protein_g", "sodium_mg", "calories_kcal"

    val minPerDay: Double? = null,     // "eat at least"
    val targetPerDay: Double? = null,  // "aim for"
    val maxPerDay: Double? = null,     // "do not exceed"

    val updatedAtEpochMillis: Long = System.currentTimeMillis()
)
