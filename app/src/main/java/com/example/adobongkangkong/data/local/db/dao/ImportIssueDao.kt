package com.example.adobongkangkong.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.adobongkangkong.data.local.db.entity.ImportIssueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportIssueDao {
    @Insert
    suspend fun insertAll(items: List<ImportIssueEntity>)

    @Query("""
        SELECT * FROM import_issues
        WHERE runId = :runId
        ORDER BY 
            CASE severity WHEN 'ERROR' THEN 0 ELSE 1 END,
            rowIndex ASC,
            code ASC
    """)
    fun observeForRun(runId: Long): Flow<List<ImportIssueEntity>>

    @Query("SELECT COUNT(*) FROM import_issues WHERE runId = :runId AND severity = 'WARNING'")
    suspend fun countWarnings(runId: Long): Int

    @Query("SELECT COUNT(*) FROM import_issues WHERE runId = :runId AND severity = 'ERROR'")
    suspend fun countErrors(runId: Long): Int
}
