package com.example.adobongkangkong.ui.planner.templatepicker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.data.local.db.entity.MealTemplateEntity
import com.example.adobongkangkong.domain.repository.MealTemplateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.update

@HiltViewModel
class MealTemplatePickerViewModel @Inject constructor(
    templates: MealTemplateRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
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
                MealTemplatePickerUiState(
                    query = q,
                    templates = filtered
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MealTemplatePickerUiState())

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
    val templates: List<MealTemplateEntity> = emptyList()
)

sealed interface MealTemplatePickerEvent {
    data object Back : MealTemplatePickerEvent
    data class UpdateQuery(val value: String) : MealTemplatePickerEvent
    data class SelectTemplate(val templateId: Long) : MealTemplatePickerEvent
}