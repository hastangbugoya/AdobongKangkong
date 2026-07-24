package com.example.adobongkangkong.domain.repository

import com.example.adobongkangkong.domain.grocery.PinnedGroceryItem
import kotlinx.coroutines.flow.Flow

/**
 * Repository for AK's small always-on grocery-list add-on.
 *
 * This only stores user-managed staple items. Planner-generated grocery output
 * should stay derived from planner/recipe data and should not be persisted here.
 */
interface PinnedGroceryItemRepository {

    fun observeAll(): Flow<List<PinnedGroceryItem>>

    suspend fun getById(id: Long): PinnedGroceryItem?

    suspend fun add(
        name: String,
        quantityText: String?
    ): Long

    suspend fun updateText(
        id: Long,
        name: String,
        quantityText: String?
    )

    suspend fun setChecked(
        id: Long,
        isChecked: Boolean
    )

    suspend fun delete(id: Long)
}
