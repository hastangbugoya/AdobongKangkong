package com.example.adobongkangkong.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.adobongkangkong.R
import com.example.adobongkangkong.domain.trend.model.TargetStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 *
 * DO NOT CHANGE THIS FILE (2025-03-07)
 *
 * Compact weekly macro graph placed below the monthly calendar.
 *
 * Layout note:
 * - This version intentionally preserves the original compact graph dimensions and text sizing
 *   that were previously approved in UI review.
 * - Bar height reflects stored daily calories.
 * - Internal stacked segments show macro composition from protein / carbs / fat.
 */

/**
 * Weekly Macro Graph
 *
 * Design invariants (DO NOT change without reviewing Calendar screen layout):
 *
 * - Compact graph placed directly under the monthly calendar.
 * - Shows a single independent week (Sun–Sat).
 * - Bar height reflects stored daily calories.
 * - Bar segments represent macro energy proportions:
 *      protein = g * 4
 *      carbs   = g * 4
 *      fat     = g * 9
 * - Stacked bars are rendered as ONE clipped column (not separate rounded blocks).
 * - Graph dimensions and typography are intentionally compact.
 *
 * Future improvements may add:
 * - reference lines (goal / average)
 * - tap interaction
 * - tooltips
 * - "unaccounted calories" segment
 */
@Composable
fun CalendarWeeklyMacroGraphSection(
    weekStart: LocalDate,
    bars: List<CalendarWeeklyMacroDayUi>,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onGoToCurrent: () -> Unit,
    modifier: Modifier = Modifier,
    targetCalories: Int?,
) {
    var popupBar by remember { mutableStateOf<CalendarWeeklyMacroDayUi?>(null) }

    val weekEnd = weekStart.plusDays(6)
    val weekLabel = "${weekStart.format(MM_DD)} to ${weekEnd.format(MM_DD)}"
    val maxBarCalories = bars.maxOfOrNull { it.totalCalories.roundToInt() } ?: 0
    val graphMaxCalories = max(max(maxBarCalories, targetCalories ?: 0), 1)
    val targetFraction = targetCalories?.toFloat()?.div(graphMaxCalories.toFloat())

    Card(
        modifier = modifier.fillMaxWidth().weekSwipe(onPrev = onPrevWeek, onNext = onNextWeek),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onPrevWeek, modifier = Modifier.size(40.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.angle_small_left),
                        contentDescription = "Previous graph week"
                    )
                }

                Text(
                    text = weekLabel,
                    style = MaterialTheme.typography.titleSmall
                )

                IconButton(onClick = onNextWeek, modifier = Modifier.size(40.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.angle_small_right),
                        contentDescription = "Next graph week"
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = "Preview only",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(176.dp),
            ) {
                targetFraction?.let { fraction ->
                    val lineHeight = 120.dp * fraction.coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .align(Alignment.BottomCenter)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .align(Alignment.BottomCenter)
                                .offset(y = -lineHeight)
                                .background(
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                )
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    bars.forEach { bar ->
                        WeeklyMacroBar(
                            bar = bar,
                            maxCalories = graphMaxCalories,
                            modifier = Modifier.weight(1f),
                            onLongPress = { popupBar = bar }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GraphLegendItem(color = ProteinColor, label = "Protein")
                GraphLegendItem(color = CarbsColor, label = "Carbs")
                GraphLegendItem(color = FatColor, label = "Fat")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onGoToCurrent) {
                    Text(
                        text = "Go to current",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                    )
                }
            }
        }
    }

    popupBar?.let { bar ->
        MacroStatusDialog(
            bar = bar,
            onDismiss = { popupBar = null }
        )
    }
}

@Composable
private fun WeeklyMacroBar(
    bar: CalendarWeeklyMacroDayUi,
    maxCalories: Int,
    modifier: Modifier = Modifier,
    onLongPress: () -> Unit,
) {
    Column(
        modifier = modifier.combinedClickable(
            onClick = {},
            onLongClick = onLongPress
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        Text(
            text = bar.totalCalories.roundToInt().toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .width(26.dp)
                .height(120.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.BottomCenter
        ) {

            if (bar.totalCalories.roundToInt() > 0) {
                val normalizedHeightFraction =
                    bar.totalCalories.toFloat() / maxCalories.toFloat()

                val barHeight =
                    120.dp * normalizedHeightFraction.coerceIn(0f, 1f)

                Box(
                    modifier = Modifier
                        .width(26.dp)
                        .height(barHeight)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    val proteinWeight = bar.proteinFraction.coerceAtLeast(0f)
                    val carbsWeight = bar.carbsFraction.coerceAtLeast(0f)
                    val fatWeight = bar.fatFraction.coerceAtLeast(0f)
                    val weightSum = proteinWeight + carbsWeight + fatWeight

                    if (weightSum > 0f) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(proteinWeight)
                                    .background(ProteinColor)
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(carbsWeight)
                                    .background(CarbsColor)
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(fatWeight)
                                    .background(FatColor)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = bar.date.dayOfWeekLabel,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun MacroStatusDialog(
    bar: CalendarWeeklyMacroDayUi,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        title = {
            Text(
                text = bar.date.format(MMM_DD_YYYY),
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MacroStatusRow(
                    color = ProteinColor,
                    label = "Protein",
                    actual = bar.proteinG,
                    min = bar.proteinMinG,
                    target = bar.proteinTargetG,
                    max = bar.proteinMaxG,
                    status = resolvePopupStatus(
                        actual = bar.proteinG,
                        min = bar.proteinMinG,
                        target = bar.proteinTargetG,
                        max = bar.proteinMaxG,
                    ),
                    unit = "g"
                )
                MacroStatusRow(
                    color = CarbsColor,
                    label = "Carbs",
                    actual = bar.carbsG,
                    min = bar.carbsMinG,
                    target = bar.carbsTargetG,
                    max = bar.carbsMaxG,
                    status = resolvePopupStatus(
                        actual = bar.carbsG,
                        min = bar.carbsMinG,
                        target = bar.carbsTargetG,
                        max = bar.carbsMaxG,
                    ),
                    unit = "g"
                )
                MacroStatusRow(
                    color = FatColor,
                    label = "Fat",
                    actual = bar.fatG,
                    min = bar.fatMinG,
                    target = bar.fatTargetG,
                    max = bar.fatMaxG,
                    status = resolvePopupStatus(
                        actual = bar.fatG,
                        min = bar.fatMinG,
                        target = bar.fatTargetG,
                        max = bar.fatMaxG,
                    ),
                    unit = "g"
                )
            }
        }
    )
}

@Composable
private fun MacroStatusRow(
    color: Color,
    label: String,
    actual: Double,
    min: Double?,
    target: Double?,
    max: Double?,
    status: TargetStatus,
    unit: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(color = color, shape = RoundedCornerShape(3.dp))
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = status.label,
                style = MaterialTheme.typography.labelMedium,
                color = status.color
            )
        }
        Text(
            text = "Actual ${actual.formatForMacro()} $unit • ${goalText(min, target, max, unit)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun goalText(
    min: Double?,
    target: Double?,
    max: Double?,
    unit: String
): String = when {
    target != null -> "TARGET ${target.formatForMacro()} $unit"
    min != null -> "MIN ${min.formatForMacro()} $unit"
    max != null -> "MAX ${max.formatForMacro()} $unit"
    else -> "NO TARGET"
}

@Composable
private fun GraphLegendItem(
    color: Color,
    label: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color = color, shape = RoundedCornerShape(3.dp))
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

data class CalendarWeeklyMacroDayUi(
    val date: LocalDate,
    val totalCalories: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val proteinStatus: TargetStatus,
    val carbsStatus: TargetStatus,
    val fatStatus: TargetStatus,
    val proteinMinG: Double?,
    val proteinTargetG: Double?,
    val proteinMaxG: Double?,
    val carbsMinG: Double?,
    val carbsTargetG: Double?,
    val carbsMaxG: Double?,
    val fatMinG: Double?,
    val fatTargetG: Double?,
    val fatMaxG: Double?,
) {
    val proteinFraction: Float
        get() = macroFractions().first

    val carbsFraction: Float
        get() = macroFractions().second

    val fatFraction: Float
        get() = macroFractions().third

    private fun macroFractions(): Triple<Float, Float, Float> {
        val proteinEnergy = max(0.0, proteinG * 4.0)
        val carbsEnergy = max(0.0, carbsG * 4.0)
        val fatEnergy = max(0.0, fatG * 9.0)
        val totalMacroEnergy = proteinEnergy + carbsEnergy + fatEnergy

        if (totalMacroEnergy <= 0.0) return Triple(0f, 0f, 0f)

        return Triple(
            (proteinEnergy / totalMacroEnergy).toFloat(),
            (carbsEnergy / totalMacroEnergy).toFloat(),
            (fatEnergy / totalMacroEnergy).toFloat()
        )
    }
}

private val MM_DD: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd")
private val MMM_DD_YYYY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM dd yyyy")
private val ProteinColor = Color(0xFF6787B3)
private val CarbsColor = Color(0xFFC98669)
private val FatColor = Color(0xFFAE8CB9)

private val LocalDate.dayOfWeekLabel: String
    get() = when (dayOfWeek.value % 7) {
        0 -> "Sun"
        1 -> "Mon"
        2 -> "Tue"
        3 -> "Wed"
        4 -> "Thu"
        5 -> "Fri"
        else -> "Sat"
    }

private fun Double.formatForMacro(): String {
    val rounded = roundToInt().toDouble()
    return if (abs(this - rounded) < 0.05) rounded.roundToInt().toString() else String.format("%.1f", this)
}

private fun Double?.formatForMacroGoal(): String =
    this?.formatForMacro() ?: "—"

private val TargetStatus.label: String
    get() = when (this) {
        TargetStatus.LOW -> "LOW"
        TargetStatus.OK -> "OK"
        TargetStatus.HIGH -> "HIGH"
        TargetStatus.NO_TARGET -> "NO TARGET"
    }

private val TargetStatus.color: Color
    @Composable
    get() = when (this) {
        TargetStatus.OK -> MaterialTheme.colorScheme.primary
        TargetStatus.LOW,
        TargetStatus.HIGH -> MaterialTheme.colorScheme.error
        TargetStatus.NO_TARGET -> MaterialTheme.colorScheme.onSurfaceVariant
    }

private fun resolvePopupStatus(
    actual: Double,
    min: Double?,
    target: Double?,
    max: Double?,
): TargetStatus {
    val epsilon = 0.05

    return when {
        min != null && actual + epsilon < min -> TargetStatus.LOW
        max != null && actual - epsilon > max -> TargetStatus.HIGH
        target != null && actual + epsilon < target -> TargetStatus.LOW
        target != null && actual - epsilon > target -> TargetStatus.HIGH
        min != null || target != null || max != null -> TargetStatus.OK
        else -> TargetStatus.NO_TARGET
    }
}


private fun Modifier.weekSwipe(
    onPrev: () -> Unit,
    onNext: () -> Unit,
    thresholdPx: Float = 120f
): Modifier = pointerInput(Unit) {
    var totalDx = 0f

    detectHorizontalDragGestures(
        onDragEnd = {
            when {
                totalDx > thresholdPx -> onPrev()
                totalDx < -thresholdPx -> onNext()
            }
            totalDx = 0f
        },
        onHorizontalDrag = { _, dx ->
            totalDx += dx
        }
    )
}
