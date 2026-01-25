package com.example.adobongkangkong.domain.importing.model

data class ImportCleanupResult(
    val food: SanitizedFoodImport?,
    val issues: List<ImportIssue>
)