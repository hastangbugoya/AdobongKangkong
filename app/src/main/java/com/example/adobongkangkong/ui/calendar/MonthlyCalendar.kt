package com.example.adobongkangkong.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import com.example.adobongkangkong.R
import com.example.adobongkangkong.ui.theme.EatMoreGreen
import com.example.adobongkangkong.ui.theme.FavoriteYellow
import com.example.adobongkangkong.ui.theme.LimitRed

@Immutable
data class CalendarCell(
    val date: LocalDate?,
    val hasPlannedMeals: Boolean = false,
)

@Composable
fun MonthlyCalendar(
    month: YearMonth,
    plannedDates: Set<LocalDate> = emptySet(),
    dayIconStatusByDate: Map<LocalDate, DayIconStatus> = emptyMap(),
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
                    iconStatus = cell.date?.let { dayIconStatusByDate[it] },
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
    iconStatus: DayIconStatus?,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(10.dp)

    val border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    val bg = MaterialTheme.colorScheme.surfaceVariant

    Surface(
        color = bg,
        shape = shape,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = border,
        modifier = Modifier
            .aspectRatio(1f)
            .sizeIn(minWidth = 36.dp, minHeight = 36.dp)
            .clickable(enabled = cell.date != null) { onClick() }
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = cell.date?.dayOfMonth?.toString() ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(4.dp))

                val dayIcon = when (iconStatus) {
                    DayIconStatus.OK -> painterResource(R.drawable.check_circle__1_)
                    DayIconStatus.MISSED -> painterResource(R.drawable.exclamation)
                    DayIconStatus.NO_DATA -> painterResource(R.drawable.interrogation)
                    DayIconStatus.NO_TARGETS, null -> null
                }

                val tint = when (iconStatus) {
                    DayIconStatus.OK -> EatMoreGreen
                    DayIconStatus.MISSED -> LimitRed
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }

                if (dayIcon != null) {
                    Icon(
                        painter = dayIcon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = tint
                    )
                } else {
                    Spacer(Modifier.size(18.dp))
                }
            }

            if (cell.date != null && cell.hasPlannedMeals) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(6.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.onSurface,
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