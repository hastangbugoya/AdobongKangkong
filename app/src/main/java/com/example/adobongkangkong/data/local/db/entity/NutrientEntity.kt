package com.example.adobongkangkong.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "nutrients",
    indices = [
        Index(value = ["code"], unique = true),
        Index(value = ["category"])
    ]
)
data class NutrientEntity(
    @PrimaryKey(autoGenerate = false)
    val id: Long, // from CSV (or you define)
    val code: String, // e.g. "CALORIES", "PROTEIN", "VITAMIN_C"
    val displayName: String, // e.g. "Calories", "Protein", "Vitamin C"
    val unit: String, // "kcal", "g", "mg", "µg"
    val category: String // "macro", "vitamin", "mineral", "other"
)
