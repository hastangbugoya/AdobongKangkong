package com.example.adobongkangkong.data.local.db.seed

import com.example.adobongkangkong.data.local.db.dao.StoreDao
import com.example.adobongkangkong.data.local.db.entity.StoreEntity
import javax.inject.Inject

/**
 * Seeds a small set of default stores.
 *
 * Design:
 * - Idempotent: safe to run multiple times
 * - Uses unique index on StoreEntity.name to prevent duplicates
 * - Keeps store creation out of CSV / DB callbacks
 *
 * Usage:
 * - Call once on app start (Application or first screen load)
 */
class SeedStoresUseCase @Inject constructor(
    private val storeDao: StoreDao
) {

    suspend operator fun invoke() {
        val defaultStores = listOf(
            "Costco",
            "Walmart",
            "Target",
            "Trader Joe's",
            "Kroger",
            "Winco"
        )

        defaultStores.sorted().forEach { name ->
            val existingId = storeDao.getIdByName(name)
            if (existingId == null) {
                storeDao.upsertStore(StoreEntity(name = name))
            }
        }
    }
}

/**
 * =============================================================================
 * FUTURE-YOU / FUTURE AI NOTES — DO NOT DELETE
 * =============================================================================
 *
 * Why this is not a Room Callback
 * - Keeps seeding logic explicit and testable
 * - Avoids hidden DB behavior
 * - Works well with evolving schema
 *
 * Idempotency rule
 * - Always check existence before insert
 * - Do not rely purely on REPLACE to avoid accidental data overwrite later
 *
 * Future ideas
 * - Allow user-defined default store list
 * - Mark some stores as "suggested" vs "user-added"
 */