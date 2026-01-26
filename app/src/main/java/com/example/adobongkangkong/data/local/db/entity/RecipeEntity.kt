package com.example.adobongkangkong.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

@Entity(
    tableName = "recipes",
    indices = [
        Index(value = ["stableId"], unique = true),
        Index(value = ["foodId"])
    ]
)
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /**
     * Stable identifier used for export/import reconciliation.
     * This must never change once created.
     */
    val stableId: String = UUID.randomUUID().toString(),

    // the Food row that represents this recipe for logging/search
    val foodId: Long,

    val name: String,

    // how many servings this batch yields
    val servingsYield: Double,

    val createdAt: Instant = Instant.now()
)

