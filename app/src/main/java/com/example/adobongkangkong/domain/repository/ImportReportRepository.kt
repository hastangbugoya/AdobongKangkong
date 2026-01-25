package com.example.adobongkangkong.domain.repository

import com.example.adobongkangkong.data.local.db.entity.ImportIssueEntity
import com.example.adobongkangkong.data.local.db.entity.ImportRunEntity
import kotlinx.coroutines.flow.Flow

interface ImportReportRepository {
    suspend fun startRun(source: String, totalRows: Int): Long
    suspend fun finishRun(
        runId: Long,
        foodsInserted: Int,
        nutrientsUpserted: Int,
        foodNutrientsUpserted: Int,
        skippedRows: Int
    )

    suspend fun writeIssues(runId: Long, issues: List<ImportIssueEntity>)

    fun observeLatestRun(): Flow<ImportRunEntity?>
    fun observeIssues(runId: Long): Flow<List<ImportIssueEntity>>
}
