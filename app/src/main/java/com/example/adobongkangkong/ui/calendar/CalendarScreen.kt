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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.adobongkangkong.R
import java.time.LocalDate
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onNavigateToDayLog: (LocalDate) -> Unit,
    onNavigateToPlannerDay: (LocalDate) -> Unit = {},
    onNavigateToShopping: (LocalDate) -> Unit = {},
    onNavigateToDashboard: (LocalDate) -> Unit = {},
    onNavigateToTemplates: (LocalDate) -> Unit = {},
    onNavigateToReports: (mode: String, anchorDate: LocalDate) -> Unit = { _, _ -> },
    onBack: () -> Unit,
    vm: CalendarViewModel = hiltViewModel()
) {
    val month by vm.month.collectAsState()
    val plannedDates by vm.plannedDates.collectAsState()
    val selectedDate by vm.selectedDate.collectAsState()
    val dayIconStatusByDate by vm.dayIconStatusByDate.collectAsState()
    val laxRuleDates by vm.laxRuleDates.collectAsState()
    val selectedDateIsLaxRuleDay by vm.selectedDateIsLaxRuleDay.collectAsState()
    val pendingLaxRuleWeekWarning by vm.pendingLaxRuleWeekWarning.collectAsState()
    val graphWeekStart by vm.graphWeekStart.collectAsState()
    val graphBars by vm.graphBars.collectAsState()
    val caloriesReference by vm.graphCaloriesReference.collectAsState()
    val settingsSheetOpen by vm.settingsSheetOpen.collectAsState()
    val calendarSuccessOptions by vm.calendarSuccessOptions.collectAsState()
    val selectedCalendarSuccessKeys by vm.selectedCalendarSuccessKeys.collectAsState()
    val currentDate by vm.currentDate.collectAsState()

    val daySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val settingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var reportRangeSheetOpen by remember { mutableStateOf(false) }
    val reportRangeSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(selectedDate) {
        if (selectedDate != null) daySheetState.show()
    }

    LaunchedEffect(settingsSheetOpen) {
        if (settingsSheetOpen) settingsSheetState.show()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.onScreenResumed()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Calendar") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(R.drawable.ms_arrow_back),
                                contentDescription = "Back"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = vm::openSettingsSheet) {
                            Icon(
                                painter = painterResource(R.drawable.ms_settings),
                                contentDescription = "Calendar settings"
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
                    onNextMonth = vm::goNextMonth,
                    onMonthClick = {
                        reportRangeSheetOpen = true
                    }
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
                        laxRuleDates = laxRuleDates,
                        selectedDate = selectedDate,
                        onDateClick = vm::onDateClicked,
                        modifier = Modifier.padding(horizontal = 12.dp),
                        currentDate = currentDate
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
                    sheetState = daySheetState,
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
                            onClick = { vm.onLaxRuleDayToggleClicked(date) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (selectedDateIsLaxRuleDay) {
                                    "Remove lax rules day"
                                } else {
                                    "Mark as lax rules day"
                                }
                            )
                        }

                        Spacer(Modifier.size(8.dp))

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

            val laxRuleWarning = pendingLaxRuleWeekWarning
            if (laxRuleWarning != null) {
                AlertDialog(
                    onDismissRequest = vm::dismissLaxRuleDayWarning,
                    title = { Text("Lax rules day already marked") },
                    text = {
                        Text(
                            text = "This week already has ${laxRuleWarning.existingMarkedCount} " +
                                    "lax rules day marked. You can still mark this date."
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = vm::confirmLaxRuleDayAfterWarning) {
                            Text("Mark anyway")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = vm::dismissLaxRuleDayWarning) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (settingsSheetOpen) {
                ModalBottomSheet(
                    sheetState = settingsSheetState,
                    onDismissRequest = vm::dismissSettingsSheet
                ) {
                    CalendarSettingsSheet(
                        options = calendarSuccessOptions,
                        selectedKeys = selectedCalendarSuccessKeys,
                        onToggle = vm::onCalendarSuccessToggle,
                        onClearAll = vm::clearCalendarSuccessSelectedKeys,
                        onDismiss = vm::dismissSettingsSheet
                    )
                }
            }

            if (reportRangeSheetOpen) {
                ModalBottomSheet(
                    sheetState = reportRangeSheetState,
                    onDismissRequest = { reportRangeSheetOpen = false }
                ) {
                    val rollingAnchor =
                        if (month.year == currentDate.year && month.month == currentDate.month) {
                            currentDate
                        } else {
                            month.atEndOfMonth()
                        }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 18.dp)
                    ) {
                        Text(
                            text = "Open reports",
                            style = MaterialTheme.typography.titleLarge
                        )

                        Spacer(Modifier.size(12.dp))

                        Button(
                            onClick = {
                                reportRangeSheetOpen = false
                                onNavigateToReports("ROLLING_30", rollingAnchor)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Rolling 30 days")
                        }

                        Spacer(Modifier.size(8.dp))

                        Button(
                            onClick = {
                                reportRangeSheetOpen = false
                                onNavigateToReports("MONTH", month.atDay(1))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("This month")
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