package com.example.adobongkangkong.domain.planner.usecase

import com.example.adobongkangkong.data.local.db.entity.PlannedItemEntity
import com.example.adobongkangkong.domain.repository.PlannedItemRepository
import javax.inject.Inject

/**
 * Removes a planned item but returns a restore token (the entity snapshot)
 * so UI can support Undo later without lossy reconstruction.
 *
 * Notes:
 * - This is pure functionality; UI can decide whether to expose Undo.
 * - Does not repack sortOrder (consistent with RemovePlannedItemUseCase).
 */
class RemovePlannedItemForUndoUseCase @Inject constructor(
    private val items: PlannedItemRepository
) {
    /**
     * @return the removed entity snapshot to be used for restore (undo).
     * @throws IllegalStateException if item doesn't exist.
     */
    suspend operator fun invoke(itemId: Long): PlannedItemEntity {
        require(itemId > 0) { "itemId must be > 0" }

        val entity = items.getById(itemId)
            ?: throw IllegalStateException("Planned item not found for id=$itemId")

        items.deleteById(itemId)

        return entity
    }
}
