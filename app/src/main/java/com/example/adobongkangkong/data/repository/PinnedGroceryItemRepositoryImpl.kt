package com.example.adobongkangkong.data.repository

import com.example.adobongkangkong.data.local.db.dao.PinnedGroceryItemDao
import com.example.adobongkangkong.data.local.db.entity.PinnedGroceryItemEntity
import com.example.adobongkangkong.domain.grocery.PinnedGroceryItem
import com.example.adobongkangkong.domain.repository.PinnedGroceryItemRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Data implementation for AK's small always-on grocery-list add-on.
 *
 * Keep this feature deliberately simple: free-text staples, optional quantity,
 * checked state, and manual deletion. Store prices, route planning, aisles,
 * inventory, and shopping-history behavior do not belong here.
 */
@Singleton
class PinnedGroceryItemRepositoryImpl @Inject constructor(
    private val dao: PinnedGroceryItemDao
) : PinnedGroceryItemRepository {

    override fun observeAll(): Flow<List<PinnedGroceryItem>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun getById(id: Long): PinnedGroceryItem? =
        dao.getById(id)?.toDomain()

    override suspend fun add(
        name: String,
        quantityText: String?
    ): Long {
        val cleanName = name.trim()
        require(cleanName.isNotBlank()) { "Pinned grocery item name cannot be blank." }

        val now = System.currentTimeMillis()
        return dao.insert(
            PinnedGroceryItemEntity(
                name = cleanName,
                quantityText = quantityText.cleanOptionalText(),
                isChecked = false,
                sortOrder = 0,
                createdAtEpochMs = now,
                updatedAtEpochMs = now
            )
        )
    }

    override suspend fun updateText(
        id: Long,
        name: String,
        quantityText: String?
    ) {
        val cleanName = name.trim()
        require(cleanName.isNotBlank()) { "Pinned grocery item name cannot be blank." }

        dao.updateText(
            id = id,
            name = cleanName,
            quantityText = quantityText.cleanOptionalText(),
            updatedAtEpochMs = System.currentTimeMillis()
        )
    }

    override suspend fun setChecked(
        id: Long,
        isChecked: Boolean
    ) {
        dao.setChecked(
            id = id,
            isChecked = isChecked,
            updatedAtEpochMs = System.currentTimeMillis()
        )
    }

    override suspend fun delete(id: Long) {
        dao.deleteById(id)
    }
}

private fun PinnedGroceryItemEntity.toDomain(): PinnedGroceryItem =
    PinnedGroceryItem(
        id = id,
        name = name,
        quantityText = quantityText,
        isChecked = isChecked,
        sortOrder = sortOrder,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs
    )

private fun String?.cleanOptionalText(): String? =
    this
        ?.trim()
        ?.takeIf { it.isNotBlank() }
