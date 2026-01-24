package com.example.adobongkangkong.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    // the Food row that represents this recipe for logging/search
    val foodId: Long,

    val name: String,

    // how many servings this batch yields
    val servingsYield: Double,

    val createdAt: Instant = Instant.now()
)
