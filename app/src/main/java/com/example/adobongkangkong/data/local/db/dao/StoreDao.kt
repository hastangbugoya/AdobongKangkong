package com.example.adobongkangkong.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.adobongkangkong.data.local.db.entity.StoreEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for store rows used by food pricing.
 *
 * First-pass scope:
 * - simple create/update/delete
 * - lookup by id or name
 * - observe all stores for future picker/list UI
 *
 * Important:
 * - StoreEntity.name is unique.
 * - Inserts therefore behave like "create if missing, replace if same unique name conflicts"
 *   when using REPLACE.
 *
 * Caution:
 * - REPLACE can behave like delete+insert in SQLite.
 * - That is acceptable for this first pass because StoreEntity is still minimal.
 * - If later store rows gain more relational meaning or audit/history semantics,
 *   revisit whether REPLACE is still appropriate.
 */
@Dao
interface StoreDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStore(entity: StoreEntity): Long

    @Update
    suspend fun updateStore(entity: StoreEntity)

    @Query(
        """
        DELETE FROM stores
        WHERE id = :storeId
        """
    )
    suspend fun deleteById(storeId: Long)

    @Query(
        """
        SELECT *
        FROM stores
        WHERE id = :storeId
        LIMIT 1
        """
    )
    suspend fun getById(storeId: Long): StoreEntity?

    @Query(
        """
        SELECT *
        FROM stores
        WHERE name = :name
        LIMIT 1
        """
    )
    suspend fun getByName(name: String): StoreEntity?

    @Query(
        """
        SELECT id
        FROM stores
        WHERE name = :name
        LIMIT 1
        """
    )
    suspend fun getIdByName(name: String): Long?

    @Query(
        """
        SELECT *
        FROM stores
        ORDER BY name COLLATE NOCASE ASC
        """
    )
    fun observeAllStores(): Flow<List<StoreEntity>>

    @Query(
        """
        SELECT *
        FROM stores
        ORDER BY name COLLATE NOCASE ASC
        """
    )
    suspend fun getAllStores(): List<StoreEntity>

    @Query(
        """
        SELECT COUNT(*)
        FROM stores
        """
    )
    suspend fun countStores(): Int
}

/**
 * =============================================================================
 * FUTURE-YOU / FUTURE AI NOTES — DO NOT DELETE
 * =============================================================================
 *
 * Why this DAO is intentionally simple
 * - The current goal is only to support food pricing relationships cleanly.
 * - Avoid adding store-search complexity until UI/use cases actually need it.
 *
 * Likely future additions
 * - searchStores(query)
 * - observeStoreById(storeId)
 * - "create if missing by normalized name" helper at repository/use-case level
 * - favorite/default store behavior
 *
 * Important guardrail
 * - Name uniqueness is currently the only store identity rule.
 * - If later branch/location support is added, do not casually remove uniqueness
 *   without rethinking what a "store" row represents.
 */