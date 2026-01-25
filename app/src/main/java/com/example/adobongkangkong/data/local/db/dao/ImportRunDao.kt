package com.example.adobongkangkong.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.adobongkangkong.data.local.db.entity.ImportRunEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportRunDao {
    @Insert
    suspend fun insert(run: ImportRunEntity): Long

    @Update
    suspend fun update(run: ImportRunEntity)

    @Query("SELECT * FROM import_runs ORDER BY startedAt DESC LIMIT 1")
    fun observeLatest(): Flow<ImportRunEntity?>

    @Query("SELECT * FROM import_runs WHERE id = :runId LIMIT 1")
    suspend fun getById(runId: Long): ImportRunEntity?
}
