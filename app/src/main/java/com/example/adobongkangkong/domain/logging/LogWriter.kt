package com.example.adobongkangkong.domain.logging

import com.example.adobongkangkong.data.local.db.entity.LogEntryEntity

/**
 * Minimal write surface the logging use case needs.
 * Implemented by data layer (repo).
 */
interface LogWriter {
    suspend fun insertLogEntry(entity: LogEntryEntity): Long
}
