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
    val bg = MaterialTheme.colorScheme.surfaceVariant

    val today = LocalDate.now()
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

                val dailyGoalIcon = when (iconStatus) {
                    DayIconStatus.OK -> painterResource(R.drawable.check_circle__1_)
                    DayIconStatus.MISSED -> painterResource(R.drawable.exclamation)
                    DayIconStatus.NO_DATA -> painterResource(R.drawable.empty_set)
                    DayIconStatus.NO_TARGETS, null -> painterResource(R.drawable.interrogation)
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
                        modifier = Modifier.size(AppIconSize.CardAction),
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
