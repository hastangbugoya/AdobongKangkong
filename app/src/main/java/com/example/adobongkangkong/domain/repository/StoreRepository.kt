package com.example.adobongkangkong.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for store rows used by food pricing.
 *
 * First-pass scope:
 * - expose store names/lists for picker UI
 * - resolve store ids by name
 * - support minimal store CRUD for food-editor-managed store maintenance
 *
 * Identity rule:
 * - Store rows keep a stable primary key (`id` / storeId).
 * - Store `name` is the distinct user-facing identity shown in dropdowns.
 * - Duplicate names are not allowed after app-layer normalization.
 *
 * Design rule:
 * - ViewModels should depend on this repository, not directly on StoreDao.
 */
interface StoreRepository {

    /**
     * Observe all store names sorted for UI display.
     */
    fun observeAllStoreNames(): Flow<List<String>>

    /**
     * One-shot read of all store names sorted for UI display.
     */
    suspend fun getAllStoreNames(): List<String>

    /**
     * Resolve a store id by exact persisted name.
     */
    suspend fun getStoreIdByName(name: String): Long?

    /**
     * Resolve a store id by normalized name comparison.
     *
     * Returns:
     * - matching store id if a normalized duplicate exists
     * - null otherwise
     */
    suspend fun getStoreIdByNormalizedName(name: String): Long?

    /**
     * Returns true if at least one store exists.
     */
    suspend fun hasAnyStores(): Boolean

    /**
     * Create a new store using the provided user-facing name.
     *
     * Contract:
     * - Name must be non-blank after trimming.
     * - Duplicate names are not allowed using normalized comparison.
     * - Returns the created store id.
     *
     * Throws:
     * - IllegalArgumentException when input is invalid
     * - IllegalStateException when a normalized duplicate already exists
     */
    suspend fun createStore(name: String): Long

    /**
     * Rename an existing store.
     *
     * Contract:
     * - Name must be non-blank after trimming.
     * - Duplicate names are not allowed using normalized comparison.
     * - Renaming to the same normalized name for the same row is treated as a no-op.
     * - Store id remains the stable primary key.
     *
     * Throws:
     * - IllegalArgumentException when input is invalid
     * - IllegalStateException when the target row does not exist
     * - IllegalStateException when a different row already uses the same normalized name
     */
    suspend fun renameStore(storeId: Long, newName: String)

    /**
     * Delete a store by id.
     *
     * Store id is the deletion target because it is the true row identity.
     */
    suspend fun deleteStore(storeId: Long)
}

/**
 * =============================================================================
 * FUTURE-YOU / FUTURE AI NOTES — DO NOT DELETE
 * =============================================================================
 *
 * Current persistence rule
 * - Only `name` is persisted as store content in this pass.
 * - `id` remains the stable PK used by relationships.
 *
 * Current editor-preview rule
 * - Any temporary address/contact fields shown in a future store editor preview
 *   must remain UI-only until explicitly added to the DB schema.
 *
 * Likely future additions
 * - observeStores() returning richer domain models
 * - getStoreById(storeId)
 * - sealed CRUD result types instead of exceptions
 *
 * Guardrail
 * - Keep StoreDao out of ViewModels.
 * - Repository remains the boundary even for simple CRUD flows.
 */