package com.example.adobongkangkong.ui.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.data.local.db.entity.MealTemplateEntity
import com.example.adobongkangkong.domain.model.MacroTotals
import com.example.adobongkangkong.domain.planner.usecase.BuildMealTemplatePickerDetailsUseCase
import com.example.adobongkangkong.domain.planner.usecase.ComputeMealTemplateMacroTotalsUseCase
import com.example.adobongkangkong.domain.repository.MealTemplateItemRepository
import com.example.adobongkangkong.domain.repository.MealTemplateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * UI state producer for the meal template list screen.
 *
 * ## For developers
 * Responsibilities:
 * - observe all meal templates
 * - apply search filtering
 * - apply list sorting
 * - compute per-template item counts
 * - attach already-computed macro totals for each row
 * - pre-format compact macro summary text for LazyColumn row rendering
 * - attach a short food preview line for each template row
 *
 * Performance notes:
 * - Macro totals are computed once per observed template set inside the ViewModel state pipeline,
 *   not in row composables.
 * - Row models include preformatted macro text so LazyColumn recomposition stays lightweight.
 * - Stable `id` keys should continue to be used by the screen.
 *
 * Design note:
 * - Macro display intentionally follows the same formatter used by the template picker so the two
 *   screens stay visually and semantically aligned.
 *
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
class MealTemplateListViewModel @Inject constructor(
    private val templates: MealTemplateRepository,
    private val templateItems: MealTemplateItemRepository,
    private val computeMacros: ComputeMealTemplateMacroTotalsUseCase,
    private val buildDetails: BuildMealTemplatePickerDetailsUseCase
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val sort = MutableStateFlow(MealTemplateListSort.MOST_RECENT)

    /** Public immutable screen state. */
    val state: StateFlow<MealTemplateListUiState> =
        combine(
            templates.observeAll(),
            query,
            sort
        ) { templateList, rawQuery, selectedSort ->
            val templateIds = templateList.map { it.id }
            val items = templateItems.getItemsForTemplates(templateIds)
            val itemCountByTemplateId = items.groupingBy { it.templateId }.eachCount()
            val macrosByTemplateId = computeMacros(templateIds)
            val detailsByTemplateId = buildDetails(templateIds)

            val needle = rawQuery.trim().lowercase()
            val filtered = if (needle.isBlank()) {
                templateList
            } else {
                templateList.filter { it.name.lowercase().contains(needle) }
            }

            val rows = filtered
                .sortedWith(selectedSort.comparator)
                .map { template ->
                    val macros = macrosByTemplateId[template.id] ?: MacroTotals()
                    val details = detailsByTemplateId[template.id]
                    MealTemplateListRow(
                        id = template.id,
                        name = template.name,
                        defaultSlotLabel = template.defaultSlot?.display,
                        itemCount = details?.itemCount ?: itemCountByTemplateId[template.id] ?: 0,
                        macroTotals = macros,
                        macroSummaryLine = macros.toMealTemplateMacroSummaryLine(),
                        foodPreviewLine = details?.previewLine
                    )
                }

            MealTemplateListUiState(
                query = rawQuery,
                sort = selectedSort,
                rows = rows
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MealTemplateListUiState()
        )

    /** Updates the current search query. */
    fun onQueryChange(value: String) {
        query.update { value }
    }

    /** Updates the active sort option. */
    fun onSortChange(value: MealTemplateListSort) {
        sort.update { value }
    }
}

/** Immutable screen model for the meal template list. */
data class MealTemplateListUiState(
    val query: String = "",
    val sort: MealTemplateListSort = MealTemplateListSort.MOST_RECENT,
    val rows: List<MealTemplateListRow> = emptyList()
)

/**
 * Flat UI row model for LazyColumn rendering.
 *
 * `macroSummaryLine` is preformatted upstream to avoid repeated string formatting work in the UI.
 */
data class MealTemplateListRow(
    val id: Long,
    val name: String,
    val defaultSlotLabel: String?,
    val itemCount: Int,
    val macroTotals: MacroTotals,
    val macroSummaryLine: String,
    val foodPreviewLine: String?
)

/** Sort modes currently supported by the meal template list screen. */
enum class MealTemplateListSort(
    val label: String,
    val comparator: Comparator<MealTemplateEntity>
) {
    MOST_RECENT(
        label = "Most recent",
        comparator = compareByDescending<MealTemplateEntity> { it.id }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
    ),
    NAME(
        label = "Name",
        comparator = compareBy<MealTemplateEntity, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
            .thenByDescending { it.id }
    )
}

/**
 * Bottom KDoc for future AI assistant.
 *
 * Why macro text is stored on [MealTemplateListRow]:
 * - the template picker already established a compact macro-summary UX
 * - the list screen should mirror that output exactly
 * - precomputing the final display string keeps row composables render-only
 *
 * Why preview text is stored on [MealTemplateListRow]:
 * - resolving FOOD / RECIPE / RECIPE_BATCH names belongs outside Compose
 * - list rows should not perform repository work during recomposition
 *
 * If future chips/badges need numeric macro access, reuse [MealTemplateListRow.macroTotals]
 * while continuing to render [MealTemplateListRow.macroSummaryLine] for display.
 */
