package com.example.adobongkangkong.ui.heatmap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.example.adobongkangkong.R
import com.example.adobongkangkong.ui.common.chevronheader.CenteredChevronHeader


@Composable
fun MonthHeader(
    month: YearMonth,
    modifier: Modifier = Modifier,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        val formatter = remember {
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
        }
        val text = month.atDay(1).format(formatter)
        CenteredChevronHeader(
            text = text,
            onPrev = onPrevMonth,
            onNext = onNextMonth,
            prevIcon = painterResource(id = R.drawable.angle_small_left),
            nextIcon = painterResource(id = R.drawable.angle_small_right),
            modifier = modifier,
            prevContentDescription = "Previous month",
            nextContentDescription = "Next month",
        )
    }
}
