package com.example.adobongkangkong.domain.importing.model

data class ImportProgress(
    val totalRows: Int,
    val processedRows: Int,
    val insertedFoods: Int,
    val warnings: Int,
    val errors: Int
) {
    val percent: Int =
        if (totalRows <= 0) 0 else ((processedRows * 100.0) / totalRows).toInt().coerceIn(0, 100)
}
