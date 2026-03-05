package com.example.adobongkangkong.ui.daylog.model

/**
 * UI model for an IOU row in the Day Log screen.
 *
 * IOUs do not have nutrition and do not affect daily totals.
 */
data class DayLogIouRow(
    val iouId: Long,
    val description: String
)
