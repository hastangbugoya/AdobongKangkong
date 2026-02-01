package com.example.adobongkangkong.ui.heatmap

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.adobongkangkong.domain.trend.model.TargetStatus
import com.example.adobongkangkong.ui.heatmap.model.HeatmapDay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Immutable
data class CalendarCell(
    val date: LocalDate?,
    val status: TargetStatus? = null,
    val value: Double? = null
)

@Composable
fun MonthlyHeatmapCalendar(
    month: YearMonth,
    days: List<HeatmapDay>,
    modifier: Modifier = Modifier,
    selectedDate: LocalDate? = null,
    onDayClick: (HeatmapDay) -> Unit
) {
    val daysByDate = remember(days) { days.associateBy { it.date } }

    // Compute the max value for this month once; used to derive intensity buckets.
    val monthMax = remember(days) {
        days.maxOfOrNull { it.value ?: 0.0 } ?: 0.0
    }

    val cells = remember(month, daysByDate) {
        buildCalendarCells(month, daysByDate)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        WeekdayHeader()

        Spacer(Modifier.height(8.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            userScrollEnabled = false // grid fits its content; parent can scroll
        ) {
            items(cells) { cell ->
                HeatmapDayCell(
                    cell = cell,
                    monthMax = monthMax,
                    isSelected = (cell.date != null && cell.date == selectedDate),
                    onClick = {
                        val date = cell.date ?: return@HeatmapDayCell
                        val model = daysByDate[date] ?: return@HeatmapDayCell
                        onDayClick(model)
                    }
                )
            }
        }
    }
}

@Composable
private fun WeekdayHeader(modifier: Modifier = Modifier) {
    val labels = remember {
        // Sunday-first labels
        listOf(
            DayOfWeek.SUNDAY,
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
            DayOfWeek.SATURDAY
        ).map { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
    }

    Row(modifier = modifier.fillMaxWidth()) {
        labels.forEach { label ->
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun HeatmapDayCell(
    cell: CalendarCell,
    monthMax: Double,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(10.dp)

    val v = cell.value ?: 0.0
    val intensity =
        if (monthMax > 0.0) (v / monthMax).coerceIn(0.0, 1.0) else 0.0

    val bg = when {
        v <= 0.0 -> MaterialTheme.colorScheme.surfaceVariant
        intensity < 0.34 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
        intensity < 0.67 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.80f)
    }

    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else null

    Surface(
        color = bg, // <-- this is the important part (no modifier.background needed)
        shape = shape,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = borderColor?.let { androidx.compose.foundation.BorderStroke(2.dp, it) },
        modifier = Modifier
            .aspectRatio(1f)
            .sizeIn(minWidth = 36.dp, minHeight = 36.dp)
            .clickable(enabled = cell.date != null) { onClick() }
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = cell.date?.dayOfMonth?.toString() ?: "",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }

}


private fun buildCalendarCells(
    month: YearMonth,
    daysByDate: Map<LocalDate, HeatmapDay>
): List<CalendarCell> {
    val first = month.atDay(1)

    // Convert java DayOfWeek (Mon=1..Sun=7) to a Sunday-first index (Sun=0..Sat=6)
    val firstDow = first.dayOfWeek
    val leadingBlanks = when (firstDow) {
        DayOfWeek.SUNDAY -> 0
        DayOfWeek.MONDAY -> 1
        DayOfWeek.TUESDAY -> 2
        DayOfWeek.WEDNESDAY -> 3
        DayOfWeek.THURSDAY -> 4
        DayOfWeek.FRIDAY -> 5
        DayOfWeek.SATURDAY -> 6
    }

    val totalDays = month.lengthOfMonth()
    val cells = ArrayList<CalendarCell>(leadingBlanks + totalDays + 7)

    repeat(leadingBlanks) {
        cells += CalendarCell(date = null)
    }

    for (d in 1..totalDays) {
        val date = month.atDay(d)
        val model = daysByDate[date]
        cells += CalendarCell(
            date = date,
            status = model?.status ?: TargetStatus.NO_TARGET,
            value = model?.value
        )
    }

    // Pad to full weeks so the grid looks clean
    val remainder = cells.size % 7
    if (remainder != 0) {
        repeat(7 - remainder) {
            cells += CalendarCell(date = null)
        }
    }

    return cells
}
