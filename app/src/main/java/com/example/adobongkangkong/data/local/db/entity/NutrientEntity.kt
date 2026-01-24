package com.example.adobongkangkong.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.adobongkangkong.domain.model.NutrientUnit
import com.example.adobongkangkong.domain.model.ServingUnit

@Entity(
    tableName = "nutrients",
    indices = [
        Index(value = ["code"], unique = true),
        Index(value = ["category"])
    ]
)
data class NutrientEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val code: String, // e.g. "CALORIES", "PROTEIN", "VITAMIN_C"
    val displayName: String, // e.g. "Calories", "Protein", "Vitamin C"
    val unit: NutrientUnit = NutrientUnit.G, // "kcal", "g", "mg", "µg"
    val category: String // "macro", "vitamin", "mineral", "other"
)
