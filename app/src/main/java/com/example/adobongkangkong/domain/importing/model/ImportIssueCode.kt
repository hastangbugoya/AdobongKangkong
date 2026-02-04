package com.example.adobongkangkong.domain.importing.model

data class ImportRowRef(
    val rowIndex: Int,
    val sourceLine: String? = null
)

enum class ImportSeverity { WARNING, ERROR }

enum class ImportIssueCode {
    MISSING_FOOD_NAME,
    BAD_WEIGHT_FORMAT,
    INVALID_SERVING_UNIT,
    MISSING_GRAMS_PER_SERVING_FOR_VOLUME,
    INVALID_NUMBER,
    NEGATIVE_NUMBER_CLAMPED,
    DUPLICATE_NUTRIENT_COLUMN_RESOLVED,
    UNKNOWN_NUTRIENT_COLUMN_SKIPPED,
    UNIT_CONVERSION_APPLIED,
    MISSING_GRAMS_FOR_VOLUME_UNIT
}

data class ImportIssue(
    val severity: ImportSeverity,
    val code: ImportIssueCode,
    val rowRef: ImportRowRef,
    val field: String? = null,         // "foodName", "gramsPerServingUnit", "Cu"
    val message: String,
    val rawValue: String? = null
)
