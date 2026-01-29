package com.example.adobongkangkong.ui.heatmap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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


@Composable
fun MonthHeader(
    month: YearMonth,
    modifier: Modifier = Modifier,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    val formatter = remember {
        // Example: "January 2026"
        DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPrevMonth) {
            Icon(
                painter = painterResource(id = R.drawable.settings),
                contentDescription = "Previous month"
            )
        }

        Text(
            text = month.atDay(1).format(formatter),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )

        IconButton(onClick = onNextMonth) {
            Icon(
                painter = painterResource(id = R.drawable.settings),
                contentDescription = "Next month"
            )
        }
    }
}
