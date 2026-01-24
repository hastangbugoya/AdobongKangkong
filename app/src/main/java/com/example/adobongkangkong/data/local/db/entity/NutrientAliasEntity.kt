package com.example.adobongkangkong.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One nutrient can have many aliases/synonyms:
 * "b6", "vitamin b-6", "pyridoxine", etc.
 *
 * We store a normalized key for fast + consistent search and uniqueness.
 */
@Entity(
    tableName = "nutrient_aliases",
    indices = [
        Index(value = ["nutrientId"]),
        Index(value = ["aliasKey"], unique = true)
    ]
)
data class NutrientAliasEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val nutrientId: Long,
    val aliasDisplay: String,   // what user typed (nice display)
    val aliasKey: String        // normalized (lowercase/trimmed), unique across all nutrients
)

