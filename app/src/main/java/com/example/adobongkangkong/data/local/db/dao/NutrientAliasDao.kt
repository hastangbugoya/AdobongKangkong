package com.example.adobongkangkong.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.example.adobongkangkong.data.local.db.entity.NutrientAliasEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NutrientAliasDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAliases(aliases: List<NutrientAliasEntity>)

    @Query("DELETE FROM nutrient_aliases WHERE nutrientId = :nutrientId")
    suspend fun deleteAliasesForNutrient(nutrientId: Long)

    @Query("""
        SELECT aliasDisplay
        FROM nutrient_aliases
        WHERE nutrientId = :nutrientId
        ORDER BY aliasKey ASC
    """)
    fun observeAliasesForNutrient(nutrientId: Long): Flow<List<String>>

    @Query("DELETE FROM nutrient_aliases WHERE nutrientId = :nutrientId AND aliasKey = :aliasKey")
    suspend fun deleteAlias(nutrientId: Long, aliasKey: String)

    @Upsert
    suspend fun upsert(entity: NutrientAliasEntity)
}
