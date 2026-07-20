package com.example.adobongkangkong.ui.weight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.adobongkangkong.R
import com.example.adobongkangkong.domain.weight.BodyWeightUnit
import com.example.adobongkangkong.ui.theme.AppIconSize
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Quiet body-weight tracking screen.
 *
 * Intent:
 * - Provide easy entry/edit access for the official daily body-weight trend row.
 * - Allow an explicit import of the latest Health Connect scale reading.
 * - Show same-day raw measurements when available without making weight tracking
 *   prominent on the dashboard.
 * - Keep future trend-selection behavior understandable to the user by showing
 *   which raw reading currently backs the day's trend value.
 *
 * Data rules:
 * - BodyWeightLog remains one official trend value per calendar date.
 * - BodyWeightMeasurement may contain multiple manual/imported readings per day.
 * - Imported Health Connect readings are kept as raw measurements and only
 *   become the daily trend when no trend exists for that day yet.
 * - Saving weight resets the dashboard weight-log reminder counter.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyWeightTrackerScreen(
    startDate: LocalDate,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: BodyWeightTrackerViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(startDate) {
        vm.load(startDate)
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        vm.consumeMessage()
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        topBar = {
            TopAppBar(
                title = { Text("Weight tracker") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ms_arrow_back),
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                WeightDateHeader(
                    selectedDate = state.selectedDate,
                    onPreviousDay = {
                        vm.onSelectedDateChanged(state.selectedDate.minusDays(1))
                    },
                    onNextDay = {
                        vm.onSelectedDateChanged(state.selectedDate.plusDays(1))
                    }
                )
            }

            item {
                WeightEntrySection(
                    state = state,
                    onWeightTextChanged = vm::onWeightTextChanged,
                    onNoteTextChanged = vm::onNoteTextChanged,
                    onUnitChanged = vm::onUnitChanged,
                    onSave = vm::saveWeight,
                    onImportLatestScaleWeight = vm::importLatestScaleWeight
                )
            }

            item {
                SameDayMeasurementsSection(
                    measurements = state.selectedDateMeasurements
                )
            }

            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Recent entries",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            if (state.recentLogs.isEmpty()) {
                item {
                    Text(
                        text = "No weight logs yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(
                    items = state.recentLogs,
                    key = { it.id }
                ) { log ->
                    BodyWeightRecentRow(log = log)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun WeightDateHeader(
    selectedDate: LocalDate,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit
) {
    val dateText = rememberDateText(selectedDate)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPreviousDay) {
            Icon(
                painter = painterResource(R.drawable.ms_arrow_back),
                contentDescription = "Previous day",
                modifier = Modifier.size(AppIconSize.Inline)
            )
        }

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "Log date",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = dateText,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(onClick = onNextDay) {
            Icon(
                painter = painterResource(R.drawable.ms_arrow_forward),
                contentDescription = "Next day",
                modifier = Modifier.size(AppIconSize.Inline)
            )
        }
    }
}

@Composable
private fun WeightEntrySection(
    state: BodyWeightTrackerState,
    onWeightTextChanged: (String) -> Unit,
    onNoteTextChanged: (String) -> Unit,
    onUnitChanged: (BodyWeightUnit) -> Unit,
    onSave: () -> Unit,
    onImportLatestScaleWeight: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        state.selectedDateLog?.let { existing ->
            Text(
                text = "Daily trend: ${existing.weightText}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            existing.trendSelectionText?.let { selectionText ->
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Trend source: $selectionText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))
        }

        OutlinedTextField(
            value = state.weightText,
            onValueChange = onWeightTextChanged,
            label = { Text("Weight") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        BodyWeightUnitToggle(
            selectedUnit = state.selectedUnit,
            onUnitChanged = onUnitChanged
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = state.noteText,
            onValueChange = onNoteTextChanged,
            label = { Text("Note optional") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        Button(
            enabled = !state.isSaving && !state.isImportingHealthConnectWeight,
            onClick = onSave,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (state.selectedDateLog == null) "Log weight" else "Update trend weight")
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            enabled = !state.isSaving && !state.isImportingHealthConnectWeight,
            onClick = onImportLatestScaleWeight,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (state.isImportingHealthConnectWeight) {
                    "Importing scale weight..."
                } else {
                    "Import latest scale weight"
                }
            )
        }

        Spacer(Modifier.height(4.dp))

        Text(
            text = "Imports from Health Connect are saved as raw readings. AK only uses an imported reading as the daily trend when that day has no trend yet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SameDayMeasurementsSection(
    measurements: List<BodyWeightMeasurementUiModel>
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Spacer(Modifier.height(8.dp))

        Text(
            text = "Readings for this day",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(Modifier.height(4.dp))

        if (measurements.isEmpty()) {
            Text(
                text = "No raw readings saved for this day yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        Text(
            text = "AK can keep multiple readings per day. One reading is used as the daily trend weight.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(6.dp))

        measurements.forEach { measurement ->
            BodyWeightMeasurementRow(measurement = measurement)
            HorizontalDivider()
        }
    }
}

@Composable
private fun BodyWeightMeasurementRow(
    measurement: BodyWeightMeasurementUiModel
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = measurement.timeText,
                style = MaterialTheme.typography.bodyLarge
            )

            Text(
                text = measurement.weightText,
                style = MaterialTheme.typography.bodyLarge
            )
        }

        Spacer(Modifier.height(2.dp))

        Text(
            text = if (measurement.isSelectedTrend) {
                "Daily trend • ${measurement.sourceText}"
            } else {
                measurement.sourceText
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        measurement.note?.takeIf { it.isNotBlank() }?.let { note ->
            Spacer(Modifier.height(2.dp))
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BodyWeightUnitToggle(
    selectedUnit: BodyWeightUnit,
    onUnitChanged: (BodyWeightUnit) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BodyWeightUnit.entries.forEach { unit ->
            if (unit == selectedUnit) {
                Button(
                    onClick = { onUnitChanged(unit) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(unit.symbol)
                }
            } else {
                OutlinedButton(
                    onClick = { onUnitChanged(unit) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(unit.symbol)
                }
            }
        }
    }
}

@Composable
private fun BodyWeightRecentRow(
    log: BodyWeightLogUiModel
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = log.dateText,
                style = MaterialTheme.typography.bodyLarge
            )

            Text(
                text = log.weightText,
                style = MaterialTheme.typography.bodyLarge
            )
        }

        log.trendSelectionText?.let { selectionText ->
            Spacer(Modifier.height(2.dp))
            Text(
                text = selectionText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        log.note?.takeIf { it.isNotBlank() }?.let { note ->
            Spacer(Modifier.height(2.dp))
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun rememberDateText(date: LocalDate): String {
    val formatter = remember {
        DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault())
    }

    return remember(date, formatter) {
        date.format(formatter)
    }
}
