package com.example.adobongkangkong.data.repository

import com.example.adobongkangkong.data.local.db.dao.StoreDao
import com.example.adobongkangkong.domain.repository.StoreRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Implementation of StoreRepository backed by StoreDao.
 *
 * First-pass scope:
 * - expose store names for UI (dropdown)
 * - resolve storeId by name
 * - simple existence checks
 *
 * Important:
 * - No business logic here beyond simple mapping
 * - Keep this thin; move any future logic to use cases if needed
 */
class StoreRepositoryImpl @Inject constructor(
    private val storeDao: StoreDao
) : StoreRepository {

    override fun observeAllStoreNames(): Flow<List<String>> {
        return storeDao
            .observeAllStores()
            .map { list -> list.map { it.name } }
    }

    override suspend fun getAllStoreNames(): List<String> {
        return storeDao.getAllStores().map { it.name }
    }

    override suspend fun getStoreIdByName(name: String): Long? {
        return storeDao.getIdByName(name)
    }

    override suspend fun hasAnyStores(): Boolean {
        return storeDao.countStores() > 0
    }
}

/**
 * =============================================================================
 * FUTURE-YOU / FUTURE AI NOTES — DO NOT DELETE
 * =============================================================================
 *
 * Why this stays thin
 * - Repository should not accumulate business logic.
 * - It is a boundary layer over DAO.
 *
 * Future extensions (do NOT implement yet):
 * - createStore(name)
 * - normalized name handling (case-insensitive matching, trimming, etc.)
 * - richer domain model instead of raw String names
 *
 * Guardrail:
 * - Keep ViewModels dependent on StoreRepository, never StoreDao directly.
 */