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

    // NEW: subscribe so plannedDates stays warm; also needed for calendar dots
    val plannedDates by vm.plannedDates.collectAsState()


    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    LaunchedEffect(selectedDay) {
        if (selectedDay != null) sheetState.show()
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
                plannedDates = plannedDates, // NEW
                selectedDate = selectedDay?.date,
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
            val hasPlanner = plannedDates.contains(date)

            if (selectedDay != null) {
                // ---- Consumption details (existing behavior) ----
                val option = options.firstOrNull { it.key == selectedDay!!.nutrientKey }
                val nutrientName = option?.displayName ?: selectedDay!!.nutrientKey.value
                val nutrientUnit = option?.unit

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

                if (hasPlanner) {
                    Spacer(Modifier.size(12.dp))
                    Button(
                        onClick = {
                            vm.dismissDayDetails()
                            onNavigateToPlannerDay(date)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                    ) {
                        Text("Open planner")
                    }
                }

                // Future: we can add a button here to open Dashboard for `date`
                // (or a dashboard panel embedded in the sheet) without changing domain logic.
            } else {
                // ---- Planner-only day (no consumption heatmap model for this date) ----
                Column(Modifier.padding(horizontal = 12.dp)) {
                    Text(date.toString())
                    Spacer(Modifier.size(12.dp))

                    if (hasPlanner) {
                        Button(
                            onClick = {
                                vm.dismissDayDetails()
                                onNavigateToPlannerDay(date)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Open planner")
                        }
                    } else {
                        Text("No planned meals for this day.")
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
