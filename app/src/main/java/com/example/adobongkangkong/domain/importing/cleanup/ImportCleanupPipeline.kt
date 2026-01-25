package com.example.adobongkangkong.domain.importing.cleanup

import com.example.adobongkangkong.domain.importing.model.ImportCleanupResult
import com.example.adobongkangkong.domain.importing.model.RawFoodCsvRow

interface ImportCleanupPipeline {
    fun sanitizeRow(raw: RawFoodCsvRow): ImportCleanupResult
}