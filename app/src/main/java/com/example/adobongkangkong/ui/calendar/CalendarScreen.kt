package com.example.adobongkangkong.ui.calendar

import android.annotation.SuppressLint
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.adobongkangkong.R
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onNavigateToDayLog: (LocalDate) -> Unit,
    onNavigateToPlannerDay: (LocalDate) -> Unit = {},
    onNavigateToShopping: (LocalDate) -> Unit = {},
    onNavigateToDashboard: (LocalDate) -> Unit = {},
    onNavigateToTemplates: (LocalDate) -> Unit = {},
    onBack: () -> Unit,
    vm: CalendarViewModel = hiltViewModel()
) {
    val month by vm.month.collectAsState()
    val plannedDates by vm.plannedDates.collectAsState()
    val selectedDate by vm.selectedDate.collectAsState()
    val dayIconStatusByDate by vm.dayIconStatusByDate.collectAsState()
    val graphWeekStart by vm.graphWeekStart.collectAsState()
    val graphBars by vm.graphBars.collectAsState()
    val caloriesReference by vm.graphCaloriesReference.collectAsState()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(selectedDate) {
        if (selectedDate != null) sheetState.show()
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Calendar") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(R.drawable.angle_circle_left),
                                contentDescription = "Back"
                            )
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Spacer(Modifier.size(8.dp))

                MonthHeader(
                    month = month,
                    onPrevMonth = vm::goPrevMonth,
                    onNextMonth = vm::goNextMonth
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .monthSwipe(
                            onPrev = vm::goPrevMonth,
                            onNext = vm::goNextMonth
                        )
                ) {
                    MonthlyCalendar(
                        month = month,
                        plannedDates = plannedDates,
                        dayIconStatusByDate = dayIconStatusByDate,
                        selectedDate = selectedDate,
                        onDateClick = vm::onDateClicked,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }

                Spacer(Modifier.size(12.dp))

                CalendarWeeklyMacroGraphSection(
                    weekStart = graphWeekStart,
                    bars = graphBars,
                    onPrevWeek = vm::goPrevGraphWeek,
                    onNextWeek = vm::goNextGraphWeek,
                    onGoToCurrent = vm::goToCurrentGraphWeek,
                    modifier = Modifier.padding(horizontal = 12.dp),
                    caloriesReference = caloriesReference,
                )
            }

            val date = selectedDate
            if (date != null) {
                ModalBottomSheet(
                    sheetState = sheetState,
                    onDismissRequest = vm::dismissDayDetails
                ) {
                    val hasPlanner = plannedDates.contains(date)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 18.dp)
                    ) {
                        Text(date.toString())
                        Spacer(Modifier.size(12.dp))

                        Button(
                            onClick = {
                                vm.dismissDayDetails()
                                onNavigateToDashboard(date)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Open dashboard")
                        }

                        Spacer(Modifier.size(8.dp))

                        Button(
                            onClick = {
                                vm.dismissDayDetails()
                                onNavigateToShopping(date)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Open shopping list")
                        }

                        Spacer(Modifier.size(8.dp))

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
                            onClick = {
                                vm.dismissDayDetails()
                                onNavigateToDayLog(date)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Open day log")
                        }

                        Spacer(Modifier.size(8.dp))

                        Button(
                            onClick = {
                                vm.dismissDayDetails()
                                onNavigateToTemplates(date)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Open meal templates")
                        }

                        if (!hasPlanner) {
                            Spacer(Modifier.size(12.dp))
                            Text("No planned meals for this day.")
                        }
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