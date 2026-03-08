package com.example.adobongkangkong.ui.calendar

import androidx.compose.foundation.background
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.adobongkangkong.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
    val weekEnd = weekStart.plusDays(6)
    val weekLabel = "${weekStart.format(MM_DD)} to ${weekEnd.format(MM_DD)}"
    val maxBarCalories = bars.maxOfOrNull { it.totalCalories.roundToInt() } ?: 0
    val graphMaxCalories = max(max(maxBarCalories, targetCalories ?: 0), 1)
    val targetFraction = targetCalories?.toFloat()?.div(graphMaxCalories.toFloat())

    Card(
        modifier = modifier.fillMaxWidth(),
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
                            dayLabel = bar.date.dayOfWeekLabel,
                            totalCalories = bar.totalCalories.roundToInt(),
                            proteinFraction = bar.proteinFraction,
                            carbsFraction = bar.carbsFraction,
                            fatFraction = bar.fatFraction,
                            maxCalories = graphMaxCalories,
                            modifier = Modifier.weight(1f)
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
}

@Composable
private fun WeeklyMacroBar(
    dayLabel: String,
    totalCalories: Int,
    proteinFraction: Float,
    carbsFraction: Float,
    fatFraction: Float,
    maxCalories: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        Text(
            text = totalCalories.toString(),
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

            if (totalCalories > 0) {
                val normalizedHeightFraction =
                    totalCalories.toFloat() / maxCalories.toFloat()

                val barHeight =
                    120.dp * normalizedHeightFraction.coerceIn(0f, 1f)

                Box(
                    modifier = Modifier
                        .width(26.dp)
                        .height(barHeight)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    val proteinWeight = proteinFraction.coerceAtLeast(0f)
                    val carbsWeight = carbsFraction.coerceAtLeast(0f)
                    val fatWeight = fatFraction.coerceAtLeast(0f)
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
            text = dayLabel,
            style = MaterialTheme.typography.labelMedium
        )
    }
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