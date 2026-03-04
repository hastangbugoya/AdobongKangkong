package com.example.adobongkangkong.ui.planner.templatepicker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.data.local.db.entity.MealTemplateEntity
import com.example.adobongkangkong.domain.model.MacroTotals
import com.example.adobongkangkong.domain.planner.usecase.ComputeMealTemplateMacroTotalsUseCase
import com.example.adobongkangkong.domain.repository.MealTemplateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class MealTemplatePickerViewModel @Inject constructor(
    private val templates: MealTemplateRepository,
    private val computeMacros: ComputeMealTemplateMacroTotalsUseCase
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _macrosByTemplateId = MutableStateFlow<Map<Long, MacroTotals>>(emptyMap())

    val state: StateFlow<MealTemplatePickerUiState> =
        templates.observeAll()
            .combine(_query) { list, q ->
                val query = q.trim()
                val filtered = if (query.isBlank()) {
                    list
                } else {
                    val needle = query.lowercase()
                    list.filter { it.name.lowercase().contains(needle) }
                }
                Pair(filtered, q)
            }
            .combine(_macrosByTemplateId) { (filtered, q), macrosMap ->
                MealTemplatePickerUiState(
                    query = q,
                    templates = filtered,
                    macrosByTemplateId = macrosMap
                )
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                MealTemplatePickerUiState()
            )

    init {
        // Keep macro cache up-to-date as templates change.
        viewModelScope.launch {
            templates.observeAll().collect { list ->
                val ids = list.map { it.id }
                _macrosByTemplateId.value = computeMacros(ids)
            }
        }
    }

    fun onEvent(event: MealTemplatePickerEvent) {
        when (event) {
            MealTemplatePickerEvent.Back -> Unit
            is MealTemplatePickerEvent.UpdateQuery -> _query.update { event.value }
            is MealTemplatePickerEvent.SelectTemplate -> Unit
        }
    }
}

data class MealTemplatePickerUiState(
    val query: String = "",
    val templates: List<MealTemplateEntity> = emptyList(),
    val macrosByTemplateId: Map<Long, MacroTotals> = emptyMap()
)

sealed interface MealTemplatePickerEvent {
    data object Back : MealTemplatePickerEvent
    data class UpdateQuery(val value: String) : MealTemplatePickerEvent
    data class SelectTemplate(val templateId: Long) : MealTemplatePickerEvent
}
