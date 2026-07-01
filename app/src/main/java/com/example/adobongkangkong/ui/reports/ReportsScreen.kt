package com.example.adobongkangkong.ui.reports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.adobongkangkong.domain.reports.model.MacroDailyValue
import com.example.adobongkangkong.domain.reports.model.MacroReportMetric
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    onBack: () -> Unit,
    vm: ReportsViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    var selectedMetricIndex by remember { mutableIntStateOf(0) }

    val data = state.data
    val metrics = data?.metrics.orEmpty()
    val safeIndex = selectedMetricIndex.coerceIn(
        0,
        (metrics.size - 1).coerceAtLeast(0)
    )
    val selectedMetric = metrics.getOrNull(safeIndex)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reports") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.isLoading) {
                CircularProgressIndicator()
                return@Column
            }

            state.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                return@Column
            }

            if (data == null || metrics.isEmpty()) {
                Text(
                    text = "No report data yet.",
                    style = MaterialTheme.typography.bodyMedium
                )
                return@Column
            }

            Text(
                text = data.subtitle,
                style = MaterialTheme.typography.titleMedium
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                metrics.forEachIndexed { index, metric ->
                    TextButton(
                        onClick = { selectedMetricIndex = index }
                    ) {
                        Text(
                            if (index == safeIndex) {
                                "• ${metric.name}"
                            } else {
                                metric.name
                            }
                        )
                    }
                }
            }

            selectedMetric?.let { metric ->
                MacroReportCard(
                    metric = metric,
                    indexText = "${safeIndex + 1} / ${metrics.size}",
                    onPrev = {
                        selectedMetricIndex =
                            if (safeIndex <= 0) metrics.lastIndex else safeIndex - 1
                    },
                    onNext = {
                        selectedMetricIndex =
                            if (safeIndex >= metrics.lastIndex) 0 else safeIndex + 1
                    }
                )
            }
        }
    }
}

@Composable
private fun MacroReportCard(
    metric: MacroReportMetric,
    indexText: String,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = metric.name,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = indexText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row {
                    TextButton(onClick = onPrev) {
                        Text("Prev")
                    }
                    TextButton(onClick = onNext) {
                        Text("Next")
                    }
                }
            }

            MacroBarcodeChart(
                values = metric.dailyValues
            )

            ReportStatsBlock(metric)
        }
    }
}

@Composable
private fun MacroBarcodeChart(
    values: List<MacroDailyValue>
) {
    val loggedColor = MaterialTheme.colorScheme.primary
    val missingColor = MaterialTheme.colorScheme.surfaceVariant

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
    ) {
        if (values.isEmpty()) return@Canvas

        val maxValue = values.maxOfOrNull { it.value }
            ?.takeIf { it > 0.0 }
            ?: 1.0

        val gap = 2.dp.toPx()
        val count = values.size
        val barWidth = ((size.width - gap * (count - 1)) / count)
            .coerceAtLeast(1f)

        values.forEachIndexed { index, daily ->
            val x = index * (barWidth + gap)

            val barHeight = if (daily.value <= 0.0) {
                3.dp.toPx()
            } else {
                ((daily.value / maxValue) * size.height).toFloat()
                    .coerceAtLeast(3.dp.toPx())
            }

            drawRect(
                color = if (daily.isLogged) loggedColor else missingColor,
                topLeft = Offset(
                    x = x,
                    y = size.height - barHeight
                ),
                size = Size(
                    width = barWidth,
                    height = barHeight
                )
            )
        }
    }
}

@Composable
private fun ReportStatsBlock(
    metric: MacroReportMetric
) {
    val stats = metric.stats

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        StatRow(
            label = "Average",
            value = stats.average.formatMetric(metric.unit)
        )

        StatRow(
            label = "High",
            value = stats.high.formatMetric(metric.unit)
        )

        StatRow(
            label = "Low",
            value = stats.low.formatMetric(metric.unit)
        )

        StatRow(
            label = "Logged days",
            value = "${stats.loggedDays} / ${stats.totalDays}"
        )
    }
}

@Composable
private fun StatRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun Double?.formatMetric(unit: String): String {
    if (this == null) return "—"

    val rounded = when (unit) {
        "kcal" -> roundToInt().toString()
        else -> {
            val oneDecimal = (this * 10.0).roundToInt() / 10.0
            if (oneDecimal % 1.0 == 0.0) {
                oneDecimal.roundToInt().toString()
            } else {
                "%.1f".format(oneDecimal)
            }
        }
    }

    return "$rounded $unit"
}

