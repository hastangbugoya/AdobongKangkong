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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.adobongkangkong.domain.reports.model.IntakeWeightTrendDailyValue
import com.example.adobongkangkong.domain.reports.model.IntakeWeightTrendReport
import com.example.adobongkangkong.domain.reports.model.MacroDailyValue
import com.example.adobongkangkong.domain.reports.model.MacroReportMetric
import com.example.adobongkangkong.domain.reports.model.TrendDirection
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    onBack: () -> Unit,
    vm: ReportsViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    var selectedMetricIndex by remember { mutableIntStateOf(0) }
    var showIntakeWeightTrend by remember { mutableStateOf(false) }

    val data = state.data
    val metrics = data?.metrics.orEmpty()
    val safeIndex = selectedMetricIndex.coerceIn(
        0,
        (metrics.size - 1).coerceAtLeast(0)
    )
    val selectedMetric = metrics.getOrNull(safeIndex)
    val trend = state.intakeWeightTrend
    val showTrendCard = showIntakeWeightTrend && trend != null

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
                .verticalScroll(rememberScrollState())
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
                if (trend != null) {
                    TextButton(
                        onClick = { showIntakeWeightTrend = true }
                    ) {
                        Text(
                            if (showTrendCard) {
                                "• Intake ↔ Weight"
                            } else {
                                "Intake ↔ Weight"
                            }
                        )
                    }
                }

                metrics.forEachIndexed { index, metric ->
                    TextButton(
                        onClick = {
                            showIntakeWeightTrend = false
                            selectedMetricIndex = index
                        }
                    ) {
                        Text(
                            if (!showTrendCard && index == safeIndex) {
                                "• ${metric.name}"
                            } else {
                                metric.name
                            }
                        )
                    }
                }
            }

            if (showTrendCard) {
                IntakeWeightTrendCard(trend = trend)
            } else {
                selectedMetric?.let { metric ->
                    MacroReportCard(
                        metric = metric,
                        indexText = "${safeIndex + 1} / ${metrics.size}",
                        onPrev = {
                            showIntakeWeightTrend = false
                            selectedMetricIndex =
                                if (safeIndex <= 0) metrics.lastIndex else safeIndex - 1
                        },
                        onNext = {
                            showIntakeWeightTrend = false
                            selectedMetricIndex =
                                if (safeIndex >= metrics.lastIndex) 0 else safeIndex + 1
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun IntakeWeightTrendCard(
    trend: IntakeWeightTrendReport
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Intake ↔ Weight Trend",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = trend.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IntakeWeightTrendChart(
                values = trend.dailyValues,
                weightUnit = trend.weightUnit
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatRow(
                    label = "Avg kcal, last 7 days",
                    value = trend.averageCaloriesLast7.formatMetric("kcal")
                )

                StatRow(
                    label = "Previous 7 days",
                    value = trend.averageCaloriesPrevious7.formatMetric("kcal")
                )

                StatRow(
                    label = "Calorie direction",
                    value = trend.calorieChangePerDay.formatTrendChange(
                        unit = "kcal/day",
                        direction = trend.calorieDirection
                    )
                )

                StatRow(
                    label = "Weight direction",
                    value = trend.weightChange.formatTrendChange(
                        unit = trend.weightUnit,
                        direction = trend.weightDirection
                    )
                )
            }

            Text(
                text = trend.note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun IntakeWeightTrendChart(
    values: List<IntakeWeightTrendDailyValue>,
    weightUnit: String
) {
    val calorieColor = MaterialTheme.colorScheme.primary
    val missingColor = MaterialTheme.colorScheme.surfaceVariant
    val weightColor = MaterialTheme.colorScheme.tertiary
    val dividerColor = MaterialTheme.colorScheme.outlineVariant

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Calories consumed",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Trend weight ($weightUnit)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            if (values.isEmpty()) return@Canvas

            val sectionGap = 18.dp.toPx()
            val topHeight = (size.height - sectionGap) * 0.48f
            val bottomTop = topHeight + sectionGap
            val bottomHeight = size.height - bottomTop

            drawLine(
                color = dividerColor,
                start = Offset(0f, topHeight + sectionGap / 2f),
                end = Offset(size.width, topHeight + sectionGap / 2f),
                strokeWidth = 1.dp.toPx()
            )

            val maxCalories = values
                .maxOfOrNull { it.calories }
                ?.coerceAtLeast(1.0)
                ?: 1.0

            val gap = 2.dp.toPx()
            val count = values.size
            val barWidth = ((size.width - gap * (count - 1)) / count)
                .coerceAtLeast(1f)

            values.forEachIndexed { index, daily ->
                val x = index * (barWidth + gap)
                val barHeight = if (daily.calories <= 0.0) {
                    3.dp.toPx()
                } else {
                    ((daily.calories / maxCalories) * topHeight).toFloat()
                        .coerceAtLeast(3.dp.toPx())
                }

                drawRect(
                    color = if (daily.isFoodLogged) calorieColor else missingColor,
                    topLeft = Offset(
                        x = x,
                        y = topHeight - barHeight
                    ),
                    size = Size(
                        width = barWidth,
                        height = barHeight
                    )
                )
            }

            val weightValues = values.mapNotNull { it.trendWeight }
            if (weightValues.isEmpty()) return@Canvas

            val rawMinWeight = weightValues.minOrNull() ?: return@Canvas
            val rawMaxWeight = weightValues.maxOrNull() ?: return@Canvas
            val padding = 0.5
            val minWeight = if (rawMinWeight == rawMaxWeight) {
                rawMinWeight - padding
            } else {
                rawMinWeight
            }
            val maxWeight = if (rawMinWeight == rawMaxWeight) {
                rawMaxWeight + padding
            } else {
                rawMaxWeight
            }
            val weightRange = (maxWeight - minWeight).coerceAtLeast(0.1)

            val points = values.mapIndexedNotNull { index, daily ->
                val weight = daily.trendWeight ?: return@mapIndexedNotNull null
                val x = if (values.size == 1) {
                    size.width / 2f
                } else {
                    (index.toFloat() / (values.size - 1).toFloat()) * size.width
                }
                val y = bottomTop + bottomHeight -
                        (((weight - minWeight) / weightRange) * bottomHeight).toFloat()
                Offset(x, y)
            }

            if (points.size == 1) {
                drawCircle(
                    color = weightColor,
                    radius = 3.dp.toPx(),
                    center = points.first()
                )
            } else if (points.size >= 2) {
                val path = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    points.drop(1).forEach { point ->
                        lineTo(point.x, point.y)
                    }
                }

                drawPath(
                    path = path,
                    color = weightColor,
                    style = Stroke(width = 2.dp.toPx())
                )

                points.forEach { point ->
                    drawCircle(
                        color = weightColor,
                        radius = 2.dp.toPx(),
                        center = point
                    )
                }
            }
        }
    }
}

private fun Double?.formatTrendChange(
    unit: String,
    direction: TrendDirection
): String {
    if (this == null || direction == TrendDirection.UNKNOWN) return "—"

    val label = when (direction) {
        TrendDirection.UP -> "up"
        TrendDirection.DOWN -> "down"
        TrendDirection.FLAT -> "flat"
        TrendDirection.UNKNOWN -> "unknown"
    }

    if (direction == TrendDirection.FLAT) return "mostly flat"

    val magnitude = kotlin.math.abs(this)
    val rounded = when (unit) {
        "kcal/day" -> magnitude.roundToInt().toString()
        else -> {
            val oneDecimal = (magnitude * 10.0).roundToInt() / 10.0
            if (oneDecimal % 1.0 == 0.0) {
                oneDecimal.roundToInt().toString()
            } else {
                "%.1f".format(oneDecimal)
            }
        }
    }

    return "$label $rounded $unit"
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

            MacroGraphRangeLegend(metric)

            MacroBarcodeChart(
                values = metric.dailyValues,
                referenceValue = metric.referenceValue
            )
            ReportStatsBlock(metric)
        }
    }
}

@Composable
private fun MacroGraphRangeLegend(
    metric: MacroReportMetric
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        GraphLegendValue(
            label = "Min",
            value = metric.stats.low.formatMetric(metric.unit)
        )

        GraphLegendValue(
            label = "Avg",
            value = metric.stats.average.formatMetric(metric.unit)
        )

        GraphLegendValue(
            label = "Max",
            value = metric.stats.high.formatMetric(metric.unit)
        )

        metric.referenceValue?.let { reference ->
            Spacer(Modifier.height(4.dp))

            Text(
                text = "${metric.referenceLabel ?: "Reference"}: ${reference.formatMetric(metric.unit)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun GraphLegendValue(
    label: String,
    value: String
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun MacroBarcodeChart(
    values: List<MacroDailyValue>,
    referenceValue: Double?
) {
    val loggedColor = MaterialTheme.colorScheme.primary
    val missingColor = MaterialTheme.colorScheme.surfaceVariant
    val referenceColor = MaterialTheme.colorScheme.error

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
    ) {
        if (values.isEmpty()) return@Canvas

        val maxDailyValue = values.maxOfOrNull { it.value } ?: 0.0
        val maxValue = maxOf(
            maxDailyValue,
            referenceValue ?: 0.0,
            1.0
        )

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

        referenceValue
            ?.takeIf { it > 0.0 }
            ?.let { reference ->
                val y = size.height -
                        ((reference / maxValue) * size.height).toFloat()

                drawLine(
                    color = referenceColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = androidx.compose.ui.graphics.PathEffect
                        .dashPathEffect(floatArrayOf(10f, 8f), 0f)
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

