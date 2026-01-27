package com.example.adobongkangkong.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "food_goal_flags")
data class FoodGoalFlagsEntity(
    @PrimaryKey
    val foodId: Long,

    val eatMore: Boolean,
    val limit: Boolean,
    val favorite: Boolean,

    val updatedAt: Instant = Instant.now()
)
