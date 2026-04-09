package com.example.adobongkangkong.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for store rows used by food pricing.
 *
 * First-pass scope:
 * - expose store names/lists for picker UI
 * - resolve store ids by name
 * - allow simple create-if-missing behavior if needed later
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
     * Resolve a store id by exact name.
     */
    suspend fun getStoreIdByName(name: String): Long?

    /**
     * Returns true if at least one store exists.
     */
    suspend fun hasAnyStores(): Boolean
}

/**
 * =============================================================================
 * FUTURE-YOU / FUTURE AI NOTES — DO NOT DELETE
 * =============================================================================
 *
 * Why this starts small
 * - The current need is only dropdown/picker support plus name -> id lookup.
 * - Avoid overbuilding CRUD/domain models until store management UI actually exists.
 *
 * Likely future additions
 * - createStore(name)
 * - deleteStore(storeId)
 * - renameStore(storeId, newName)
 * - observeStores() returning richer domain models
 *
 * Guardrail
 * - Keep StoreDao out of ViewModels.
 * - Repository remains the boundary even for simple lookup/query flows.
 */