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
    primaryKeys = ["nutrientId", "aliasKey"],
    indices = [
        Index(value = ["aliasKey"]),
        Index(value = ["nutrientId"])
    ]
)
data class NutrientAliasEntity(
    val nutrientId: Long,
    val aliasDisplay: String,   // what user typed (nice display)
    val aliasKey: String        // normalized (lowercase/trimmed), unique across all nutrients
)

