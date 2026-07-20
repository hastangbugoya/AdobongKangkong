package com.example.adobongkangkong.ui.weight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.domain.weight.BodyWeightLog
import com.example.adobongkangkong.domain.weight.BodyWeightLogRepository
import com.example.adobongkangkong.domain.weight.BodyWeightMeasurement
import com.example.adobongkangkong.domain.weight.BodyWeightMeasurementSource
import com.example.adobongkangkong.domain.weight.BodyWeightTrendSelectionMethod
import com.example.adobongkangkong.domain.weight.BodyWeightUnit
import com.example.adobongkangkong.domain.weight.ImportLatestHealthConnectWeightUseCase
import com.example.adobongkangkong.domain.weight.UpsertBodyWeightLogUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for quiet body-weight tracking.
 *
 * This screen is intentionally not a dashboard metric by default. It exposes
 * the selected day's official trend weight and the same-day raw measurements
 * that may later be used to choose that daily trend value.
 *
 * Data rules:
 * - BodyWeightLog remains one official trend value per calendar date.
 * - BodyWeightMeasurement rows preserve raw readings/imports and may contain
 *   multiple readings for the same date.
 * - Manual saves create a raw MANUAL measurement and make it the selected trend
 *   value for the date.
 * - Health Connect imports create raw HEALTH_CONNECT measurements. They only
 *   create a daily trend row when the imported date has no trend value yet.
 *
 * Future user preference:
 * - AK can later choose the daily trend reading from multiple measurements using
 *   a setting such as closest to preferred weigh-in time.
 */
@HiltViewModel
class BodyWeightTrackerViewModel @Inject constructor(
    private val bodyWeightLogRepository: BodyWeightLogRepository,
    private val upsertBodyWeightLog: UpsertBodyWeightLogUseCase,
    private val importLatestHealthConnectWeight: ImportLatestHealthConnectWeightUseCase,
    private val zoneId: ZoneId,
) : ViewModel() {

    private val selectedDateFlow = MutableStateFlow(LocalDate.now(zoneId))
    private val weightTextFlow = MutableStateFlow("")
    private val noteTextFlow = MutableStateFlow("")
    private val selectedUnitFlow = MutableStateFlow(BodyWeightUnit.LB)
    private val isSavingFlow = MutableStateFlow(false)
    private val isImportingHealthConnectWeightFlow = MutableStateFlow(false)
    private val messageFlow = MutableStateFlow<String?>(null)

    private val selectedDateLogFlow =
        selectedDateFlow
            .flatMapLatest { date ->
                bodyWeightLogRepository.observeByDate(date)
            }

    private val selectedDateMeasurementsFlow =
        selectedDateFlow
            .flatMapLatest { date ->
                bodyWeightLogRepository.observeMeasurementsByDate(date)
            }

    private val recentLogsFlow =
        bodyWeightLogRepository
            .observeRecent(limit = 30)
            .map { logs ->
                logs.map { log ->
                    BodyWeightLogUiModel.from(log)
                }
            }

    val state: StateFlow<BodyWeightTrackerState> =
        combine(
            selectedDateFlow,
            selectedDateLogFlow,
            selectedDateMeasurementsFlow,
            recentLogsFlow,
            weightTextFlow,
            noteTextFlow,
            selectedUnitFlow,
            isSavingFlow,
            isImportingHealthConnectWeightFlow,
            messageFlow
        ) { values ->
            @Suppress("UNCHECKED_CAST")
            val selectedDate = values[0] as LocalDate
            val selectedDateLog = values[1] as BodyWeightLog?
            val selectedDateMeasurements = values[2] as List<BodyWeightMeasurement>
            val recentLogs = values[3] as List<BodyWeightLogUiModel>
            val weightText = values[4] as String
            val noteText = values[5] as String
            val selectedUnit = values[6] as BodyWeightUnit
            val isSaving = values[7] as Boolean
            val isImportingHealthConnectWeight = values[8] as Boolean
            val message = values[9] as String?

            BodyWeightTrackerState(
                selectedDate = selectedDate,
                selectedDateLog = selectedDateLog?.let { BodyWeightLogUiModel.from(it) },
                selectedDateMeasurements = selectedDateMeasurements.map { measurement ->
                    BodyWeightMeasurementUiModel.from(
                        measurement = measurement,
                        selectedMeasurementId = selectedDateLog?.selectedMeasurementId,
                        displayUnit = selectedUnit,
                        zoneId = zoneId
                    )
                },
                recentLogs = recentLogs,
                weightText = weightText,
                noteText = noteText,
                selectedUnit = selectedUnit,
                isSaving = isSaving,
                isImportingHealthConnectWeight = isImportingHealthConnectWeight,
                message = message
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BodyWeightTrackerState(
                selectedDate = LocalDate.now(zoneId)
            )
        )

    fun load(date: LocalDate = LocalDate.now(zoneId)) {
        selectedDateFlow.value = date
        hydrateEditorFromDate(date)
    }

    fun onSelectedDateChanged(date: LocalDate) {
        selectedDateFlow.value = date
        hydrateEditorFromDate(date)
    }

    fun onWeightTextChanged(value: String) {
        weightTextFlow.value =
            value.filter { char ->
                char.isDigit() || char == '.'
            }
    }

    fun onNoteTextChanged(value: String) {
        noteTextFlow.value = value
    }

    fun onUnitChanged(unit: BodyWeightUnit) {
        selectedUnitFlow.value = unit
    }

    fun saveWeight() {
        val date = selectedDateFlow.value
        val weight = weightTextFlow.value.toDoubleOrNull()

        if (weight == null || weight <= 0.0) {
            messageFlow.value = "Enter a weight greater than 0."
            return
        }

        viewModelScope.launch {
            isSavingFlow.value = true
            messageFlow.value = null

            try {
                when (
                    val result = upsertBodyWeightLog(
                        date = date,
                        weight = weight,
                        unit = selectedUnitFlow.value,
                        note = noteTextFlow.value
                    )
                ) {
                    is UpsertBodyWeightLogUseCase.Result.Success -> {
                        messageFlow.value = "Weight saved."
                    }

                    is UpsertBodyWeightLogUseCase.Result.Error -> {
                        messageFlow.value = result.message
                    }
                }
            } finally {
                isSavingFlow.value = false
            }
        }
    }

    fun importLatestScaleWeight() {
        viewModelScope.launch {
            isImportingHealthConnectWeightFlow.value = true
            messageFlow.value = null

            try {
                val displayUnit = selectedUnitFlow.value
                when (
                    val result = importLatestHealthConnectWeight(
                        displayUnit = displayUnit
                    )
                ) {
                    is ImportLatestHealthConnectWeightUseCase.Result.Imported -> {
                        selectedDateFlow.value = result.date
                        hydrateEditorFromDate(result.date)
                        messageFlow.value = result.toUiMessage(
                            displayUnit = displayUnit,
                            zoneId = zoneId
                        )
                    }

                    is ImportLatestHealthConnectWeightUseCase.Result.HealthConnectUnavailable -> {
                        messageFlow.value = "Health Connect unavailable: ${result.sdkStatus}."
                    }

                    ImportLatestHealthConnectWeightUseCase.Result.PermissionMissing -> {
                        messageFlow.value = "Weight permission not granted. Grant Health Connect weight access first."
                    }

                    is ImportLatestHealthConnectWeightUseCase.Result.NoWeightRecords -> {
                        messageFlow.value = "No Health Connect weight records found in the last ${result.lookbackDays} days."
                    }

                    is ImportLatestHealthConnectWeightUseCase.Result.AlreadyImported -> {
                        messageFlow.value = "That Health Connect weight was already imported."
                    }

                    is ImportLatestHealthConnectWeightUseCase.Result.NearDuplicate -> {
                        messageFlow.value = "Skipped likely duplicate scale reading."
                    }

                    is ImportLatestHealthConnectWeightUseCase.Result.TooCloseToExisting -> {
                        messageFlow.value =
                            "Skipped scale reading: another reading that day is ${result.gapMinutes} min away. Minimum gap is ${result.requiredGapMinutes / 60} hr."
                    }

                    is ImportLatestHealthConnectWeightUseCase.Result.Error -> {
                        messageFlow.value = "Health Connect weight import failed: ${result.message}"
                    }
                }
            } finally {
                isImportingHealthConnectWeightFlow.value = false
            }
        }
    }

    fun consumeMessage() {
        messageFlow.value = null
    }

    private fun hydrateEditorFromDate(date: LocalDate) {
        viewModelScope.launch {
            val existing = bodyWeightLogRepository.getByDate(date)

            if (existing == null) {
                weightTextFlow.value = ""
                noteTextFlow.value = ""
                return@launch
            }

            weightTextFlow.value = existing.weight.cleanWeightText()
            noteTextFlow.value = existing.note.orEmpty()
            selectedUnitFlow.value = existing.unit
        }
    }
}

data class BodyWeightTrackerState(
    val selectedDate: LocalDate = LocalDate.now(),
    val selectedDateLog: BodyWeightLogUiModel? = null,
    val selectedDateMeasurements: List<BodyWeightMeasurementUiModel> = emptyList(),
    val recentLogs: List<BodyWeightLogUiModel> = emptyList(),
    val weightText: String = "",
    val noteText: String = "",
    val selectedUnit: BodyWeightUnit = BodyWeightUnit.LB,
    val isSaving: Boolean = false,
    val isImportingHealthConnectWeight: Boolean = false,
    val message: String? = null
)

data class BodyWeightLogUiModel(
    val id: Long,
    val date: LocalDate,
    val dateText: String,
    val weightText: String,
    val note: String?,
    val selectedMeasurementId: Long?,
    val trendSelectionText: String?,
    val isTrendUserOverride: Boolean
) {
    companion object {
        fun from(log: BodyWeightLog): BodyWeightLogUiModel =
            BodyWeightLogUiModel(
                id = log.id,
                date = log.date,
                dateText = log.date.toString(),
                weightText = "${log.weight.cleanWeightText()} ${log.unit.symbol}",
                note = log.note,
                selectedMeasurementId = log.selectedMeasurementId,
                trendSelectionText = log.trendSelectionMethod?.toUiText(),
                isTrendUserOverride = log.isTrendUserOverride
            )
    }
}

data class BodyWeightMeasurementUiModel(
    val id: Long,
    val date: LocalDate,
    val timeText: String,
    val weightText: String,
    val sourceText: String,
    val note: String?,
    val isSelectedTrend: Boolean
) {
    companion object {
        fun from(
            measurement: BodyWeightMeasurement,
            selectedMeasurementId: Long?,
            displayUnit: BodyWeightUnit,
            zoneId: ZoneId
        ): BodyWeightMeasurementUiModel {
            val displayWeight = measurement.weightKg.toDisplayWeight(displayUnit)

            return BodyWeightMeasurementUiModel(
                id = measurement.id,
                date = measurement.date,
                timeText = measurement.measuredAt
                    .atZone(zoneId)
                    .toLocalTime()
                    .format(weightTimeFormatter),
                weightText = "${displayWeight.cleanWeightText()} ${displayUnit.symbol}",
                sourceText = measurement.toSourceText(),
                note = measurement.note,
                isSelectedTrend = selectedMeasurementId != null && measurement.id == selectedMeasurementId
            )
        }
    }
}

private fun ImportLatestHealthConnectWeightUseCase.Result.Imported.toUiMessage(
    displayUnit: BodyWeightUnit,
    zoneId: ZoneId
): String {
    val weightText = "${weightKg.toDisplayWeight(displayUnit).cleanWeightText()} ${displayUnit.symbol}"
    val timeText = measuredAt.toLocalDateTimeText(zoneId)

    return if (usedAsDailyTrend) {
        "Imported $weightText from Health Connect at $timeText and used it as the daily trend."
    } else {
        "Imported $weightText from Health Connect at $timeText. Daily trend unchanged."
    }
}

private val weightTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

private fun Double.cleanWeightText(): String =
    if (this % 1.0 == 0.0) {
        this.toInt().toString()
    } else {
        "%,.1f".format(this).trimEnd('0').trimEnd('.')
    }

private fun Double.toDisplayWeight(unit: BodyWeightUnit): Double =
    when (unit) {
        BodyWeightUnit.KG -> this
        BodyWeightUnit.LB -> this * KG_TO_LB
    }

private fun Instant.toLocalDateTimeText(zoneId: ZoneId): String =
    atZone(zoneId)
        .toLocalDateTime()
        .toString()

private fun BodyWeightMeasurement.toSourceText(): String =
    when (source) {
        BodyWeightMeasurementSource.MANUAL -> "Manual"
        BodyWeightMeasurementSource.HEALTH_CONNECT -> {
            val packageText = sourcePackage?.takeIf { it.isNotBlank() }
            if (packageText == null) {
                "Health Connect"
            } else {
                "Health Connect • $packageText"
            }
        }
        BodyWeightMeasurementSource.LEGACY_WEIGHT_LOG -> "Legacy weight log"
    }

private fun BodyWeightTrendSelectionMethod.toUiText(): String =
    when (this) {
        BodyWeightTrendSelectionMethod.CLOSEST_TO_PREFERRED_TIME -> "Closest to preferred time"
        BodyWeightTrendSelectionMethod.FIRST_OF_DAY -> "First reading of day"
        BodyWeightTrendSelectionMethod.LATEST_OF_DAY -> "Latest reading of day"
        BodyWeightTrendSelectionMethod.LOWEST_OF_DAY -> "Lowest reading of day"
        BodyWeightTrendSelectionMethod.HIGHEST_OF_DAY -> "Highest reading of day"
        BodyWeightTrendSelectionMethod.AVERAGE_OF_DAY -> "Average of day"
        BodyWeightTrendSelectionMethod.MANUAL_SELECTED -> "Manually selected"
    }

private const val KG_TO_LB = 2.2046226218
