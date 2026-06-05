package com.example.adobongkangkong.ui.weight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.domain.weight.BodyWeightLog
import com.example.adobongkangkong.domain.weight.BodyWeightLogRepository
import com.example.adobongkangkong.domain.weight.BodyWeightUnit
import com.example.adobongkangkong.domain.weight.UpsertBodyWeightLogUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for quiet body-weight tracking.
 *
 * This screen is intentionally not a dashboard metric by default.
 * It provides:
 * - easy data entry
 * - latest/current selected-day weight
 * - recent historical entries
 *
 * Reminder behavior:
 * - Saving weight goes through [UpsertBodyWeightLogUseCase].
 * - That use case resets the weight-log reminder counter.
 */
@HiltViewModel
class BodyWeightTrackerViewModel @Inject constructor(
    private val bodyWeightLogRepository: BodyWeightLogRepository,
    private val upsertBodyWeightLog: UpsertBodyWeightLogUseCase,
    private val zoneId: ZoneId,
) : ViewModel() {

    private val selectedDateFlow = MutableStateFlow(LocalDate.now(zoneId))
    private val weightTextFlow = MutableStateFlow("")
    private val noteTextFlow = MutableStateFlow("")
    private val selectedUnitFlow = MutableStateFlow(BodyWeightUnit.LB)
    private val isSavingFlow = MutableStateFlow(false)
    private val messageFlow = MutableStateFlow<String?>(null)

    private val selectedDateLogFlow =
        selectedDateFlow
            .flatMapLatest { date ->
                bodyWeightLogRepository.observeByDate(date)
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
            recentLogsFlow,
            weightTextFlow,
            noteTextFlow,
            selectedUnitFlow,
            isSavingFlow,
            messageFlow
        ) { values ->
            @Suppress("UNCHECKED_CAST")
            val selectedDate = values[0] as LocalDate
            val selectedDateLog = values[1] as BodyWeightLog?
            val recentLogs = values[2] as List<BodyWeightLogUiModel>
            val weightText = values[3] as String
            val noteText = values[4] as String
            val selectedUnit = values[5] as BodyWeightUnit
            val isSaving = values[6] as Boolean
            val message = values[7] as String?

            BodyWeightTrackerState(
                selectedDate = selectedDate,
                selectedDateLog = selectedDateLog?.let { BodyWeightLogUiModel.from(it) },
                recentLogs = recentLogs,
                weightText = weightText,
                noteText = noteText,
                selectedUnit = selectedUnit,
                isSaving = isSaving,
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
    val recentLogs: List<BodyWeightLogUiModel> = emptyList(),
    val weightText: String = "",
    val noteText: String = "",
    val selectedUnit: BodyWeightUnit = BodyWeightUnit.LB,
    val isSaving: Boolean = false,
    val message: String? = null
)

data class BodyWeightLogUiModel(
    val id: Long,
    val date: LocalDate,
    val dateText: String,
    val weightText: String,
    val note: String?
) {
    companion object {
        fun from(log: BodyWeightLog): BodyWeightLogUiModel =
            BodyWeightLogUiModel(
                id = log.id,
                date = log.date,
                dateText = log.date.toString(),
                weightText = "${log.weight.cleanWeightText()} ${log.unit.symbol}",
                note = log.note
            )
    }
}

private fun Double.cleanWeightText(): String =
    if (this % 1.0 == 0.0) {
        this.toInt().toString()
    } else {
        "%,.1f".format(this).trimEnd('0').trimEnd('.')
    }