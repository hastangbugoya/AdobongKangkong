package com.example.adobongkangkong.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Immutable
data class CalendarCell(
    val date: LocalDate?,
    val hasPlannedMeals: Boolean = false,
)

@Composable
fun MonthlyCalendar(
    month: YearMonth,
    plannedDates: Set<LocalDate> = emptySet(),
    modifier: Modifier = Modifier,
    selectedDate: LocalDate? = null,
    onDateClick: (LocalDate) -> Unit
) {
    val cells = remember(month, plannedDates) {
        buildCalendarCells(month, plannedDates)
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
            userScrollEnabled = false
        ) {
            items(cells) { cell ->
                CalendarDayCell(
                    cell = cell,
                    isSelected = (cell.date != null && cell.date == selectedDate),
                    onClick = {
                        val date = cell.date ?: return@CalendarDayCell
                        onDateClick(date)
                    }
                )
            }
        }
    }
}

@Composable
private fun WeekdayHeader(modifier: Modifier = Modifier) {
    val labels = remember {
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
private fun CalendarDayCell(
    cell: CalendarCell,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(10.dp)

    val border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    val bg = MaterialTheme.colorScheme.surfaceVariant

    val today = LocalDate.now()
    val isToday = cell.date != null && cell.date == today

    val isDark = isSystemInDarkTheme()

    // Regular days: light (even in dark theme)
    val normalBg = if (isDark) MaterialTheme.colorScheme.inverseSurface
    else MaterialTheme.colorScheme.surface

    // Today: darker than regular days (but still readable)
    val todayBg = if (isDark) MaterialTheme.colorScheme.surfaceVariant
    else MaterialTheme.colorScheme.surfaceVariant

    val backgroundColor = if (isToday) todayBg else normalBg

    val borderStroke =
        if (isSelected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
        else null

    // Make text readable on the chosen bg
    val dayTextColor = when {
        isToday -> MaterialTheme.colorScheme.onSurfaceVariant
        isDark -> MaterialTheme.colorScheme.inverseOnSurface // because bg == inverseSurface
        else -> MaterialTheme.colorScheme.onSurface
    }

    val dotColor = when {
        isToday -> MaterialTheme.colorScheme.onSurfaceVariant
        isDark -> MaterialTheme.colorScheme.inverseOnSurface // because bg == inverseSurface
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        color = backgroundColor,
        shape = shape,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = borderStroke,
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
                textAlign = TextAlign.Center,
                color = dayTextColor
            )

            if (cell.date != null && cell.hasPlannedMeals) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(6.dp)
                ) {
                    Surface(
                        color = dotColor, // MaterialTheme.colorScheme.onSurface,
                        shape = RoundedCornerShape(50),
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                    ) {}
                }
            }
        }
    }
}

private fun buildCalendarCells(
    month: YearMonth,
    plannedDates: Set<LocalDate>
): List<CalendarCell> {
    val first = month.atDay(1)

    val leadingBlanks = when (first.dayOfWeek) {
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

    repeat(leadingBlanks) { cells += CalendarCell(date = null) }

    for (d in 1..totalDays) {
        val date = month.atDay(d)
        cells += CalendarCell(
            date = date,
            hasPlannedMeals = plannedDates.contains(date)
        )
    }

    val remainder = cells.size % 7
    if (remainder != 0) repeat(7 - remainder) { cells += CalendarCell(date = null) }

    return cells
}