package com.example.adobongkangkong.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "food_categories",
    indices = [
        Index(value = ["name"], unique = true)
    ]
)
data class FoodCategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val sortOrder: Int,
    val createdAtEpochMs: Long,
    val isSystem: Boolean = false,
)
