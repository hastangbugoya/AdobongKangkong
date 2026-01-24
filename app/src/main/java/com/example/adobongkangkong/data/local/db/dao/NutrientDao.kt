package com.example.adobongkangkong.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.adobongkangkong.data.local.db.entity.NutrientEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NutrientDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<NutrientEntity>)

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
}
