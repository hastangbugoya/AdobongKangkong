package com.example.adobongkangkong.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "meal_templates",
    indices = [
        Index(value = ["name"])
    ]
)
data class MealTemplateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    val name: String,

    /** Optional default slot for quick planning (does not force anything). */
    val defaultSlot: MealSlot? = null
)
