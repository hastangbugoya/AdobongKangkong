package com.example.adobongkangkong.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Template-scoped preference hints.
 *
 * bias: "NEUTRAL" | "EAT_MORE" | "LIMIT"
 */
@Entity(
    tableName = "meal_template_prefs",
    primaryKeys = ["templateId"],
    foreignKeys = [
        ForeignKey(
            entity = MealTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["isFavorite"]),
        Index(value = ["bias"])
    ]
)
data class MealTemplatePrefsEntity(
    val templateId: Long,
    val isFavorite: Boolean = false,
    val bias: MealTemplateBias = MealTemplateBias.NEUTRAL
)

enum class MealTemplateBias(
    val display: String
){
    NEUTRAL("Neutral"),
    EAT_MORE("Eat more"),
    LIMIT("Limit")
}