package com.example.adobongkangkong.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.adobongkangkong.data.local.db.entity.NutrientEntity
import com.example.adobongkangkong.domain.model.NutrientCategory
import com.example.adobongkangkong.domain.model.NutrientUnit
import kotlinx.coroutines.flow.Flow

@Dao
interface NutrientDao {

    @Query("SELECT * FROM nutrients WHERE id = :nutrientId LIMIT 1")
    suspend fun getById(nutrientId: Long): NutrientEntity?

    @Query("SELECT * FROM nutrients ORDER BY category ASC, displayName ASC")
    suspend fun getAll(): List<NutrientEntity>

    @Query("SELECT id FROM nutrients WHERE code = :code LIMIT 1")
    suspend fun getIdByCode(code: String): Long?

    @Query("""
    SELECT * FROM nutrients
    WHERE displayName LIKE '%' || :query || '%'
       OR code LIKE '%' || :query || '%'
    ORDER BY category ASC, displayName ASC
    LIMIT :limit
""")
    suspend fun search(query: String, limit: Int = 50): List<NutrientEntity>


    @Query(
        """
    SELECT DISTINCT n.*
    FROM nutrients n
    LEFT JOIN nutrient_aliases a ON a.nutrientId = n.id
    WHERE
        lower(n.displayName) LIKE :q
        OR lower(n.code) LIKE :q
        OR a.aliasKey LIKE :q
    ORDER BY n.category ASC, n.displayName ASC
    LIMIT :limit
    """
    )
    fun searchWithAliases(q: String, limit: Int): Flow<List<NutrientEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(items: List<NutrientEntity>): List<Long>

    @Query("""
        UPDATE nutrients
        SET displayName = :displayName,
            unit = :unit,
            category = :category
        WHERE code = :code
    """)
    suspend fun updateByCode(
        code: String,
        displayName: String,
        unit: NutrientUnit,
        category: NutrientCategory
    )

    @Query("DELETE FROM nutrients WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT id, code FROM nutrients")
    suspend fun getIdCodePairs(): List<NutrientIdCodeRow>

    data class NutrientIdCodeRow(val id: Long, val code: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoreAll(items: List<NutrientEntity>): List<Long>

    @Transaction
    suspend fun upsertAll(items: List<NutrientEntity>) {
        for (it in items) {
            updateByCode(
                code = it.code,
                displayName = it.displayName,
                unit = it.unit,
                category = it.category
            )
        }
        insertIgnoreAll(items)
    }

    /**
     * Observes all nutrient metadata (definitions) in the database.
     *
     * This is the canonical source for:
     * - display name
     * - unit
     * - grouping/category (if present)
     * - any other nutrient metadata you store
     *
     * Used by dashboard composition to map nutrient codes -> display metadata.
     */
    @Query("SELECT * FROM nutrients ORDER BY displayName ASC")
    fun observeAllNutrients(): Flow<List<NutrientEntity>>

    @Query("SELECT unit FROM nutrients WHERE id = :nutrientId LIMIT 1")
    suspend fun getUnitById(nutrientId: Long): NutrientUnit?

    @Query("SELECT unit FROM nutrients WHERE code = :code LIMIT 1")
    suspend fun getUnitByCode(code: String): NutrientUnit?

    @Query("SELECT * FROM nutrients WHERE code = :code LIMIT 1")
    suspend fun getByCode(code: String): NutrientEntity?

    @Query("SELECT id, code FROM nutrients WHERE id IN (:ids)")
    suspend fun getCodesByIds(ids: List<Long>): List<NutrientIdCodeRow>
}

data class NutrientIdCodeRow(val id: Long, val code: String)
