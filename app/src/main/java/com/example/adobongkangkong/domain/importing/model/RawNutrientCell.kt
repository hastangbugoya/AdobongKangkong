package com.example.adobongkangkong.domain.importing.model

data class RawNutrientCell(
    val columnName: String,     // e.g., "Cu" or "Vitamin C"
    val rawValue: String?       // "0.2", "", "N/A"
)