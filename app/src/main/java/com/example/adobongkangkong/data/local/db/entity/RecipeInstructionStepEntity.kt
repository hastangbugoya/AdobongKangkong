package com.example.adobongkangkong.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Ordered instruction / notes step belonging to a recipe.
 *
 * Design notes:
 * - Child-table model (not a blob on RecipeEntity) so future UI can reorder, edit,
 *   and attach media per-step.
 * - [stableId] exists for future backup/import/export reconciliation and long-term identity.
 * - [position] is the explicit user-visible ordering field.
 * - [imagePath] stores only an app-owned internal-storage relative path; image bytes do NOT
 *   live in Room.
 *
 * Current scope:
 * - one optional image per step
 * - no timestamps yet
 * - no step title yet
 * - no checklist / timer / rich text yet
 */
@Entity(
    tableName = "recipe_instruction_steps",
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["stableId"], unique = true),
        Index(value = ["recipeId"]),
        Index(value = ["recipeId", "position"], unique = true)
    ]
)
data class RecipeInstructionStepEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /**
     * Stable identifier used for future export/import reconciliation.
     * This must never change once created.
     */
    val stableId: String = UUID.randomUUID().toString(),

    val recipeId: Long,

    /**
     * Explicit ordered position within the recipe.
     * Expected to be managed by repository reorder/update operations.
     */
    val position: Int,

    /**
     * User-authored instruction text / note content for this step.
     */
    val text: String,

    /**
     * App-owned internal-storage relative path for the optional step image.
     *
     * Example shape:
     * - recipe_instruction_images/{recipeId}/{stepStableId}/step.jpg
     *
     * Storing a relative path keeps DB rows portable across installs/device file roots.
     */
    val imagePath: String? = null
)