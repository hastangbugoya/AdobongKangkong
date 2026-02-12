package com.example.adobongkangkong.ui.heatmap

import android.annotation.SuppressLint
import android.content.Intent
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.adobongkangkong.R
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeatmapScreen(
    onNavigateToDayLog: (LocalDate) -> Unit,
    onNavigateToPlannerDay: (LocalDate) -> Unit = {},
    onBack: () -> Unit,
    vm: HeatmapViewModel = hiltViewModel()
) {
    val month by vm.month.collectAsState()
    val days by vm.heatmapDays.collectAsState()
    val options by vm.nutrientOptions.collectAsState()
    val selectedNutrient by vm.selectedNutrient.collectAsState()

    val selectedDay by vm.selectedDay.collectAsState()
    val selectedDate by vm.selectedDate.collectAsState()

    // Subscribe so plannedDates stays warm; also needed for calendar dots
    val plannedDates by vm.plannedDates.collectAsState()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    LaunchedEffect(selectedDay, selectedDate) {
        if (selectedDay != null || selectedDate != null) sheetState.show()
    }

    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.size(32.dp))
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .padding(start = 8.dp, top = 8.dp)
                .size(40.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.angle_circle_left),
                contentDescription = "Back"
            )
        }

        Spacer(Modifier.size(8.dp))
        MonthHeader(
            month = month,
            onPrevMonth = vm::goPrevMonth,
            onNextMonth = vm::goNextMonth
        )

        NutrientSelector(
            options = options,
            selected = selectedNutrient,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            onSelect = vm::selectNutrient
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .monthSwipe(
                    onPrev = vm::goPrevMonth,
                    onNext = vm::goNextMonth
                )
        ) {
            MonthlyHeatmapCalendar(
                month = month,
                days = days,
                plannedDates = plannedDates,
                selectedDate = selectedDay?.date ?: selectedDate,
                onDayClick = vm::onDayClicked,
                onDateClick = vm::onDateClicked,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }

    if (selectedDay != null || selectedDate != null) {
        ModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = vm::dismissDayDetails
        ) {
            val date = selectedDay?.date ?: selectedDate!!

            if (selectedDay != null) {
                val option = options.firstOrNull { it.key == selectedDay!!.nutrientKey }
                val nutrientName = option?.displayName ?: selectedDay!!.nutrientKey.value
                val nutrientUnit = option?.unit

                // Existing behavior (consumption details + share)
                HeatmapDayDetailsSheet(
                    day = selectedDay!!,
                    nutrientDisplayName = nutrientName,
                    nutrientUnit = nutrientUnit,
                    onViewLogs = onNavigateToDayLog,
                    onShare = {
                        val unitSuffix = nutrientUnit?.let { u -> " $u" }.orEmpty()
                        val valueText = selectedDay!!.value?.let { v -> "%,.2f".format(v).trimEnd('0').trimEnd('.') + unitSuffix } ?: "—"
                        val minText = selectedDay!!.min?.let { v -> "%,.2f".format(v).trimEnd('0').trimEnd('.') + unitSuffix } ?: "—"
                        val targetText = selectedDay!!.target?.let { v -> "%,.2f".format(v).trimEnd('0').trimEnd('.') + unitSuffix } ?: "—"
                        val maxText = selectedDay!!.max?.let { v -> "%,.2f".format(v).trimEnd('0').trimEnd('.') + unitSuffix } ?: "—"
                        val statusText = selectedDay!!.status.name

                        val message = buildString {
                            appendLine(selectedDay!!.date.toString())
                            appendLine(nutrientName)
                            appendLine()
                            appendLine("Value: $valueText")
                            appendLine("Min: $minText")
                            appendLine("Target: $targetText")
                            appendLine("Max: $maxText")
                            appendLine("Status: $statusText")
                        }

                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, message)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share day summary"))
                    },
                    onClose = vm::dismissDayDetails
                )
            } else {
                // Planner-only day (no nutrient selected / no heatmap model available)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = date.toString(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = "Select a nutrient to view consumption totals.",
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.size(12.dp))
                    Button(
                        onClick = {
                            vm.dismissDayDetails()
                            onNavigateToPlannerDay(date)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open planner")
                    }
                    Spacer(Modifier.size(8.dp))
                    Button(
                        onClick = vm::dismissDayDetails,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@SuppressLint("ModifierFactoryUnreferencedReceiver")
private fun Modifier.monthSwipe(
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
