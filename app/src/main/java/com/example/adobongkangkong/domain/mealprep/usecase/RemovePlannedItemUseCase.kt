package com.example.adobongkangkong.domain.mealprep.usecase

import com.example.adobongkangkong.domain.repository.PlannedItemRepository
import javax.inject.Inject

/**
 * Removes a planned item by id.
 *
 * Does not re-pack sortOrder (keeps minimal; reorder use case can handle later).
 */
class RemovePlannedItemUseCase @Inject constructor(
    private val items: PlannedItemRepository
) {
    suspend operator fun invoke(itemId: Long) {
        require(itemId > 0) { "itemId must be > 0" }
        items.deleteById(itemId)
    }
}
