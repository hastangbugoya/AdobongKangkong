package com.example.adobongkangkong.domain.importing.model

data class RawFoodCsvRow(
    val rowIndex: Int,
    val foodName: String?,
    val servingUnitRaw: String?,
    val gramsPerServingRaw: String?,
    val nutrientCells: List<RawNutrientCell>,
    val sourceLine: String? = null // optional for debugging/reporting
)