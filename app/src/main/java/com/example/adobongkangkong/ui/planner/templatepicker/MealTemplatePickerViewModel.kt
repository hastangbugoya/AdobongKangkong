package com.example.adobongkangkong.ui.planner.templatepicker

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.domain.model.MacroTotals
import com.example.adobongkangkong.domain.planner.usecase.ComputeMealTemplateMacroTotalsUseCase
import com.example.adobongkangkong.domain.repository.MealTemplateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class MealTemplatePickerViewModel @Inject constructor(
    private val templates: MealTemplateRepository,
    private val computeMacros: ComputeMealTemplateMacroTotalsUseCase
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val macrosByTemplateId = MutableStateFlow<Map<Long, MacroTotals>>(emptyMap())

    val state: StateFlow<MealTemplatePickerUiState> =
        templates.observeAll()
            .combine(query) { templateEntities, rawQuery ->
                templateEntities to rawQuery
            }
            .combine(macrosByTemplateId) { (templateEntities, rawQuery), macroMap ->
                val needle = rawQuery.trim().lowercase()

                val rows = templateEntities
                    .asSequence()
                    .filter { needle.isBlank() || it.name.lowercase().contains(needle) }
                    .map { template ->
                        MealTemplatePickerRowModel(
                            id = template.id,
                            name = template.name,
                            macrosLine = macroMap[template.id]?.toPickerMacrosLine()
                        )
                    }
                    .sortedBy { it.name.lowercase() }
                    .toList()

                MealTemplatePickerUiState(
                    query = rawQuery,
                    rows = rows
                )
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                MealTemplatePickerUiState()
            )

    init {
        viewModelScope.launch {
            templates.observeAll().collect { list ->
                macrosByTemplateId.value = computeMacros(list.map { it.id })
            }
        }
    }

    fun onEvent(event: MealTemplatePickerEvent) {
        when (event) {
            MealTemplatePickerEvent.Back -> Unit
            is MealTemplatePickerEvent.UpdateQuery -> query.update { event.value }
            is MealTemplatePickerEvent.SelectTemplate -> Unit
        }
    }
}

@Immutable
data class MealTemplatePickerUiState(
    val query: String = "",
    val rows: List<MealTemplatePickerRowModel> = emptyList()
)

@Immutable
data class MealTemplatePickerRowModel(
    val id: Long,
    val name: String,
    val macrosLine: String?
)

sealed interface MealTemplatePickerEvent {
    data object Back : MealTemplatePickerEvent
    data class UpdateQuery(val value: String) : MealTemplatePickerEvent
    data class SelectTemplate(val templateId: Long) : MealTemplatePickerEvent
}

private fun MacroTotals.toPickerMacrosLine(): String {
    val kcal = caloriesKcal.roundToInt()
    val p = proteinG.roundToInt()
    val c = carbsG.roundToInt()
    val f = fatG.roundToInt()
    return "$kcal kcal • P $p • C $c • F $f"
}
