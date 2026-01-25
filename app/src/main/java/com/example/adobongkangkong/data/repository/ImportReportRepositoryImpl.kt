package com.example.adobongkangkong.data.repository

import com.example.adobongkangkong.data.local.db.NutriDatabase
import com.example.adobongkangkong.data.local.db.entity.ImportIssueEntity
import com.example.adobongkangkong.data.local.db.entity.ImportRunEntity
import com.example.adobongkangkong.domain.repository.ImportReportRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ImportReportRepositoryImpl @Inject constructor(
    private val db: NutriDatabase
) : ImportReportRepository {

    private val runDao = db.importRunDao()
    private val issueDao = db.importIssueDao()

    override suspend fun startRun(source: String, totalRows: Int): Long {
        val now = System.currentTimeMillis()
        return runDao.insert(
            ImportRunEntity(
                startedAt = now,
                finishedAt = null,
                source = source,
                totalRows = totalRows,
                foodsInserted = 0,
                nutrientsUpserted = 0,
                foodNutrientsUpserted = 0,
                skippedRows = 0,
                warningCount = 0,
                errorCount = 0
            )
        )
    }

    override suspend fun finishRun(
        runId: Long,
        foodsInserted: Int,
        nutrientsUpserted: Int,
        foodNutrientsUpserted: Int,
        skippedRows: Int
    ) {
        val existing = runDao.getById(runId) ?: return
        val now = System.currentTimeMillis()

        // Recompute counts from issues table (source of truth)
        val warnings = issueDao.countWarnings(runId)
        val errors = issueDao.countErrors(runId)

        runDao.update(
            existing.copy(
                finishedAt = now,
                foodsInserted = foodsInserted,
                nutrientsUpserted = nutrientsUpserted,
                foodNutrientsUpserted = foodNutrientsUpserted,
                skippedRows = skippedRows,
                warningCount = warnings,
                errorCount = errors
            )
        )
    }

    override suspend fun writeIssues(runId: Long, issues: List<ImportIssueEntity>) {
        if (issues.isEmpty()) return
        issueDao.insertAll(issues.map { it.copy(runId = runId) })
    }

    override fun observeLatestRun(): Flow<ImportRunEntity?> = runDao.observeLatest()

    override fun observeIssues(runId: Long): Flow<List<ImportIssueEntity>> =
        issueDao.observeForRun(runId)
}
