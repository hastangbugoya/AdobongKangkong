package com.example.adobongkangkong.domain.mealprep.usecase

import com.example.adobongkangkong.domain.mealprep.model.MealTemplate
import javax.inject.Inject

/**
 * Creates a duplicate of an existing meal template.
 *
 * ## For developers
 * - The duplicate receives a new template id.
 * - Item ordering and quantities are preserved.
 * - Banner/media files are intentionally NOT copied here because banner storage is owner-id based
 *   and UI/media handling belongs outside the domain layer.
 * - The duplicate name uses a predictable "(copy)" suffix so the user can rename immediately.
 */
class DuplicateMealTemplateUseCase @Inject constructor(
    private val getMealTemplate: GetMealTemplateUseCase,
    private val saveMealTemplate: SaveMealTemplateUseCase
) {

    suspend operator fun invoke(templateId: Long): Long {
        require(templateId > 0L) { "templateId must be > 0" }

        val source = getMealTemplate(templateId)
        val duplicate = MealTemplate(
            id = 0L,
            name = duplicatedName(source.name),
            defaultSlot = source.defaultSlot,
            items = source.items
        )
        return saveMealTemplate(duplicate)
    }

    private fun duplicatedName(name: String): String {
        val base = name.trim().ifBlank { "Meal Template" }
        return if (base.endsWith("(copy)", ignoreCase = true)) {
            base
        } else {
            "$base (copy)"
        }
    }
}

/**
 * Bottom KDoc for future AI assistant.
 *
 * This use case duplicates only domain template data:
 * - header fields
 * - ordered item set
 *
 * It intentionally does not copy banner/media files. Banner storage is deterministic by owner id,
 * so copying media should remain an explicit UI/infrastructure concern if that behavior is ever
 * desired later.
 */
