package com.example.adobongkangkong.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.adobongkangkong.R
import com.example.adobongkangkong.ui.theme.AppIconSize
import com.example.adobongkangkong.ui.theme.EatMoreGreen
import com.example.adobongkangkong.ui.theme.LimitRed
import java.time.LocalDate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.ui.tooling.preview.Preview


@Composable
fun CalendarDayCell(
    cell: CalendarCell,
    isSelected: Boolean,
    iconStatus: DayIconStatus?,
    isToday: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(10.dp)

    val border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    MaterialTheme.colorScheme.surfaceVariant

    LocalDate.now()
//    val isToday = cell.date == today

    val backgroundColor = when {
        isToday -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surface
    }

    val textColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        color = backgroundColor,
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
                    textAlign = TextAlign.Center,
                    color = textColor
                )

                Spacer(Modifier.height(4.dp))

//                val dailyGoalIcon = when (iconStatus) {
//                    DayIconStatus.OK -> painterResource(R.drawable.check_circle__1_)
//                    DayIconStatus.MISSED -> painterResource(R.drawable.exclamation)
//                    DayIconStatus.NO_DATA -> painterResource(R.drawable.empty_set)
//                    DayIconStatus.NO_TARGETS, null -> painterResource(R.drawable.interrogation)
//                }

                val dailyGoalIcon = when (iconStatus) {
                    DayIconStatus.OK -> painterResource(R.drawable.ms_check_box)
                    DayIconStatus.MISSED -> painterResource(R.drawable.ms_disabled_by_default)
                    DayIconStatus.NO_DATA -> painterResource(R.drawable.ms_check_box_outline_blank)
                    DayIconStatus.NO_TARGETS, null -> painterResource(R.drawable.ms_help_center)
                }

                if (dailyGoalIcon != null && cell?.date != null ) {
                    val tint = when (iconStatus) {
                        DayIconStatus.OK -> EatMoreGreen
                        DayIconStatus.MISSED -> LimitRed
                        else -> LocalContentColor.current
                    }
                    Icon(
                        painter = dailyGoalIcon,
                        contentDescription = null,
                        modifier = Modifier.size(AppIconSize.CalendarDayCell),
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


@Preview(showBackground = true, name = "Calendar day cells - status combinations")
@Composable
private fun CalendarDayCellStatusPreview() {
    MaterialTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CalendarDayCellPreviewItem(
                    label = "OK",
                    isSelected = false,
                    isToday = false,
                    iconStatus = DayIconStatus.OK,
                    hasPlannedMeals = false
                )
                CalendarDayCellPreviewItem(
                    label = "Missed",
                    isSelected = false,
                    isToday = false,
                    iconStatus = DayIconStatus.MISSED,
                    hasPlannedMeals = false
                )
                CalendarDayCellPreviewItem(
                    label = "No data",
                    isSelected = false,
                    isToday = false,
                    iconStatus = DayIconStatus.NO_DATA,
                    hasPlannedMeals = false
                )
                CalendarDayCellPreviewItem(
                    label = "No targets",
                    isSelected = false,
                    isToday = false,
                    iconStatus = DayIconStatus.NO_TARGETS,
                    hasPlannedMeals = false
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CalendarDayCellPreviewItem(
                    label = "Selected OK",
                    isSelected = true,
                    isToday = false,
                    iconStatus = DayIconStatus.OK,
                    hasPlannedMeals = true
                )
                CalendarDayCellPreviewItem(
                    label = "Today missed",
                    isSelected = false,
                    isToday = true,
                    iconStatus = DayIconStatus.MISSED,
                    hasPlannedMeals = true
                )
                CalendarDayCellPreviewItem(
                    label = "Selected today",
                    isSelected = true,
                    isToday = true,
                    iconStatus = DayIconStatus.NO_DATA,
                    hasPlannedMeals = true
                )
                CalendarDayCellPreviewItem(
                    label = "Blank",
                    isSelected = false,
                    isToday = false,
                    iconStatus = null,
                    hasPlannedMeals = false,
                    date = null
                )
            }
        }
    }
}

@Composable
private fun CalendarDayCellPreviewItem(
    label: String,
    isSelected: Boolean,
    isToday: Boolean,
    iconStatus: DayIconStatus?,
    hasPlannedMeals: Boolean,
    date: LocalDate? = LocalDate.of(2026, 6, 15)
) {
    Column(
        modifier = Modifier.width(72.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CalendarDayCell(
            cell = CalendarCell(
                date = date,
                hasPlannedMeals = hasPlannedMeals
            ),
            isSelected = isSelected,
            iconStatus = iconStatus,
            isToday = isToday,
            onClick = {}
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center
        )
    }
}