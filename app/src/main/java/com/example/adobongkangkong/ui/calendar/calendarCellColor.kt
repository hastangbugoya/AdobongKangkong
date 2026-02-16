package com.example.adobongkangkong.ui.calendar

import androidx.compose.ui.graphics.Color
import com.example.adobongkangkong.domain.trend.model.TargetStatus

fun calendarCellColor(
    status: TargetStatus,
    baseOk: Color,
    baseLow: Color,
    baseHigh: Color,
    baseNoTarget: Color
): Color {
    return when (status) {
        TargetStatus.OK -> baseOk.copy(alpha = 0.85f)          // strong fill
        TargetStatus.LOW -> baseLow.copy(alpha = 0.55f)        // medium fill
        TargetStatus.HIGH -> baseHigh.copy(alpha = 0.55f)      // medium fill
        TargetStatus.NO_TARGET -> baseNoTarget.copy(alpha = 0.10f) // almost empty
    }
}
