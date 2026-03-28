package com.example.adobongkangkong.domain.shared.usecase

import com.example.adobongkangkong.data.local.db.dao.LogEntryDao
import com.example.adobongkangkong.domain.shared.serialization.SharedLogExportJsonSerializer
import javax.inject.Inject

/**
 * BuildSharedLogExportJsonUseCase
 *
 * ## Purpose
 * Produces the shared raw log export JSON contract for external consumers such as
 * HastangHubaga and future suite apps.
 *
 * ## Architecture
 * This use case is intentionally thin:
 * - loads recent persisted log rows from [LogEntryDao]
 * - serializes them into the transport JSON contract via
 *   [SharedLogExportJsonSerializer]
 *
 * ## Important rules
 * - No grouping/aggregation here
 * - No meal reconstruction here
 * - No nutrition math here
 * - No content provider / IPC plumbing here
 *
 * Consumer apps are expected to:
 * - upsert by stableId
 * - compare modifiedAt for change detection
 * - optionally aggregate logs into meals after import
 */
class BuildSharedLogExportJsonUseCase @Inject constructor(
    private val logEntryDao: LogEntryDao,
    private val serializer: SharedLogExportJsonSerializer
) {

    /**
     * Builds and serializes the recent raw log export payload.
     *
     * @param limit max number of most recent log rows to export
     */
    suspend operator fun invoke(
        limit: Int = 200
    ): String {
        val logs = logEntryDao.getRecent(limit)
        return serializer.serialize(logs)
    }
}