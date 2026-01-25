package com.example.adobongkangkong.domain.importing.model

data class ImportReport(
    val runId: Long,
    val totalRows: Int,
    val insertedFoods: Int,
    val warnings: Int,
    val errors: Int
)
