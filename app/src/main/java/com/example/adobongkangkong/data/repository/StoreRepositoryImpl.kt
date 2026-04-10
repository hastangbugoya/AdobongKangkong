package com.example.adobongkangkong.data.repository

import com.example.adobongkangkong.data.local.db.dao.StoreDao
import com.example.adobongkangkong.data.local.db.entity.StoreEntity
import com.example.adobongkangkong.domain.repository.StoreRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Implementation of StoreRepository backed by StoreDao.
 *
 * Current scope:
 * - expose store names for UI (dropdown)
 * - resolve storeId by name
 * - support minimal create / rename / delete
 *
 * Important:
 * - Store id remains the stable primary key.
 * - Store name remains the distinct user-facing identity used in dropdowns.
 * - Duplicate names are blocked using normalized app-layer comparison before touching DB.
 *
 * Normalization rule:
 * - trim leading/trailing whitespace
 * - collapse internal whitespace runs to a single space
 * - compare case-insensitively
 *
 * Persistence rule:
 * - Only `name` is stored for now.
 * - Any preview-only fields in a future editor (address/contact/etc.) remain UI-only
 *   until explicitly added to the schema.
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

    override suspend fun getStoreIdByNormalizedName(name: String): Long? {
        val normalizedTarget = normalizeStoreName(name)
        if (normalizedTarget.isBlank()) return null

        return storeDao
            .getAllStores()
            .firstOrNull { store ->
                normalizeStoreName(store.name) == normalizedTarget
            }
            ?.id
    }

    override suspend fun hasAnyStores(): Boolean {
        return storeDao.countStores() > 0
    }

    override suspend fun createStore(name: String): Long {
        val normalizedTarget = normalizeStoreName(name)
        require(normalizedTarget.isNotBlank()) { "Store name is required." }

        val existingId = getStoreIdByNormalizedName(normalizedTarget)
        check(existingId == null) { "A store with that name already exists." }

        return storeDao.upsertStore(
            StoreEntity(
                name = normalizedTarget
            )
        )
    }

    override suspend fun renameStore(storeId: Long, newName: String) {
        val normalizedTarget = normalizeStoreName(newName)
        require(normalizedTarget.isNotBlank()) { "Store name is required." }

        val existing = storeDao.getById(storeId)
            ?: throw IllegalStateException("Store not found.")

        val existingNormalized = normalizeStoreName(existing.name)
        if (existingNormalized == normalizedTarget) {
            return
        }

        val conflicting = storeDao
            .getAllStores()
            .firstOrNull { store ->
                store.id != storeId &&
                        normalizeStoreName(store.name) == normalizedTarget
            }

        check(conflicting == null) { "A store with that name already exists." }

        storeDao.updateStore(
            existing.copy(name = normalizedTarget)
        )
    }

    override suspend fun deleteStore(storeId: Long) {
        storeDao.deleteById(storeId)
    }

    private fun normalizeStoreName(raw: String): String {
        return raw
            .trim()
            .replace(Regex("\\s+"), " ")
            .lowercase()
            .split(" ")
            .joinToString(" ") { token ->
                token.replaceFirstChar { ch ->
                    if (ch.isLowerCase()) ch.titlecase() else ch.toString()
                }
            }
    }
}

/**
 * =============================================================================
 * FUTURE-YOU / FUTURE AI NOTES — DO NOT DELETE
 * =============================================================================
 *
 * Why normalization lives here
 * - ViewModels should not duplicate store-name normalization or duplicate detection logic.
 * - Repository is the narrowest current boundary that can protect distinct dropdown names.
 *
 * Current identity model
 * - storeId = stable row identity / FK target
 * - name = distinct user-facing identity
 *
 * Future extensions (do NOT implement yet unless feature scope expands)
 * - richer domain model instead of raw String names
 * - explicit create/rename/delete result sealed classes
 * - branch/location-aware duplicate rules
 *
 * Guardrail
 * - Keep ViewModels dependent on StoreRepository, never StoreDao directly.
 */