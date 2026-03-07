package com.example.adobongkangkong.ui.planner.templatepicker

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.domain.model.MacroTotals
import com.example.adobongkangkong.domain.planner.usecase.BuildMealTemplatePickerDetailsUseCase
import com.example.adobongkangkong.domain.planner.usecase.ComputeMealTemplateMacroTotalsUseCase
import com.example.adobongkangkong.domain.repository.MealTemplateRepository
import com.example.adobongkangkong.ui.templates.toMealTemplateMacroSummaryLine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
/**
 * Renders the template library/browse screen.
 *
 * Relationship to Template Picker:
 * - This screen and MealTemplatePickerScreen intentionally present nearly the same
 *   template-card content:
 *   - banner
 *   - macro summary
 *   - default meal slot
 *   - food preview/details text
 * - The flows differ in purpose (library management vs template selection), but
 *   card-content changes should usually be reviewed in both places.
 *
 * Maintenance rule:
 * - If you change template-card fields, formatting, or shared detail-building logic,
 *   also inspect:
 *   - MealTemplatePickerScreen
 *   - MealTemplatePickerViewModel
 *   - any shared template detail/card helpers
 *
 * Do not assume a change is list-only unless the difference is intentionally
 * screen-specific.
 */
@HiltViewModel
class MealTemplatePickerViewModel @Inject constructor(
    private val templates: MealTemplateRepository,
    private val computeMacros: ComputeMealTemplateMacroTotalsUseCase,
    private val buildDetails: BuildMealTemplatePickerDetailsUseCase
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val macrosByTemplateId = MutableStateFlow<Map<Long, MacroTotals>>(emptyMap())
    private val detailsByTemplateId = MutableStateFlow<Map<Long, com.example.adobongkangkong.domain.planner.usecase.MealTemplatePickerDetails>>(emptyMap())

    val state: StateFlow<MealTemplatePickerUiState> =
        templates.observeAll()
            .combine(query) { templateEntities, rawQuery ->
                templateEntities to rawQuery
            }
            .combine(macrosByTemplateId) { (templateEntities, rawQuery), macroMap ->
                Triple(templateEntities, rawQuery, macroMap)
            }
            .combine(detailsByTemplateId) { (templateEntities, rawQuery, macroMap), detailMap ->
                val needle = rawQuery.trim().lowercase()

                val rows = templateEntities
                    .asSequence()
                    .filter { needle.isBlank() || it.name.lowercase().contains(needle) }
                    .map { template ->
                        val details = detailMap[template.id]
                        MealTemplatePickerRowModel(
                            id = template.id,
                            name = template.name,
                            defaultSlotLabel = template.defaultSlot?.display,
                            macrosLine = macroMap[template.id]?.toMealTemplateMacroSummaryLine(),
                            foodPreviewLine = details?.previewLine,
                            itemCount = details?.itemCount ?: 0
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
                val ids = list.map { it.id }
                macrosByTemplateId.value = computeMacros(ids)
                detailsByTemplateId.value = buildDetails(ids)
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
    val defaultSlotLabel: String?,
    val macrosLine: String?,
    val foodPreviewLine: String?,
    val itemCount: Int
)

sealed interface MealTemplatePickerEvent {
    data object Back : MealTemplatePickerEvent
    data class UpdateQuery(val value: String) : MealTemplatePickerEvent
    data class SelectTemplate(val templateId: Long) : MealTemplatePickerEvent
}
