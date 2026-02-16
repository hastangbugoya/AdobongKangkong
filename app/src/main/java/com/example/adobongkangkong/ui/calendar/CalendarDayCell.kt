package com.example.adobongkangkong.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.adobongkangkong.ui.calendar.model.CalendarDay

@Composable
fun CalendarDayCell(
    day: CalendarDay,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme

    val bg = calendarCellColor(
        status = day.status,
        baseOk = colors.primary,
        baseLow = colors.error,
        baseHigh = colors.tertiary,
        baseNoTarget = colors.onSurface
    )

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = day.date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

