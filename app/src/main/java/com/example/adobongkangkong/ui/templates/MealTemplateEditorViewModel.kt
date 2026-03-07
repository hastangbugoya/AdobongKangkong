package com.example.adobongkangkong.ui.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.data.local.db.entity.MealTemplateEntity
import com.example.adobongkangkong.data.local.db.entity.MealTemplateItemEntity
import com.example.adobongkangkong.domain.mealprep.usecase.ComputeMealTemplateDraftMacroTotalsUseCase
import com.example.adobongkangkong.domain.planner.model.PlannedItemSource
import com.example.adobongkangkong.domain.repository.FoodRepository
import com.example.adobongkangkong.domain.repository.MealTemplateItemRepository
import com.example.adobongkangkong.domain.repository.MealTemplateRepository
import com.example.adobongkangkong.ui.meal.editor.MealEditorContract
import com.example.adobongkangkong.ui.meal.editor.MealEditorMode
import com.example.adobongkangkong.ui.meal.editor.MealEditorUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel backing the meal template editor.
 *
 * ## For developers
 * Responsibilities:
 * - load/create template draft state
 * - translate editor rows into template persistence rows on save
 * - expose template-only live macro guidance for Phase 3A
 * - keep the shared editor contract intact so the screen remains reusable
 *
 * Macro-guidance rules:
 * - totals are computed from the in-memory draft, not persisted template ids
 * - the summary is advisory only and must never block save
 * - formatting reuses the shared compact template formatter so editor/list/picker stay aligned
 */
@HiltViewModel
class MealTemplateEditorViewModel @Inject constructor(
    private val templates: MealTemplateRepository,
    private val templateItems: MealTemplateItemRepository,
    private val foods: FoodRepository,
    private val computeDraftMacros: ComputeMealTemplateDraftMacroTotalsUseCase
) : ViewModel(), MealEditorContract {

    private val _state = MutableStateFlow(
        MealEditorUiState(
            mealId = null,
            name = "",
            mode = MealEditorMode.TEMPLATE,
            subtitle = null,
            items = emptyList(),
            isSaving = false,
            canSave = true,
            errorMessage = null,
            liveMacroSummaryLine = "0 kcal • P 0 • C 0 • F 0"
        )
    )

    override val state: StateFlow<MealEditorUiState> = _state.asStateFlow()

    /**
     * Optional edit mode (route-level wiring; not part of shared contract).
     */
    fun setTemplateId(templateId: Long) {
        _state.value = _state.value.copy(mealId = templateId, errorMessage = null)

        viewModelScope.launch {
            try {
                val template = templates.getById(templateId)
                val items = templateItems.getItemsForTemplate(templateId)
                    .sortedBy { it.sortOrder }

                val uiItems = items.map { entity ->
                    val foodName = foods.getById(entity.refId)?.name ?: "Food #${entity.refId}"
                    MealEditorUiState.Item(
                        lineId = UUID.randomUUID().toString(),
                        id = entity.id,
                        foodId = entity.refId,
                        foodName = foodName,
                        servings = entity.servings?.toString() ?: "",
                        grams = entity.grams,
                        milliliters = null
                    )
                }

                _state.value = _state.value.copy(
                    name = template?.name ?: _state.value.name,
                    items = uiItems,
                    errorMessage = null,
                    isDirty = false
                )
                recomputeDraftMacroGuidance(uiItems)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(errorMessage = t.message ?: "Failed to load template")
            }
        }
    }

    override fun setName(name: String) {
        _state.value = _state.value.copy(name = name, errorMessage = null, isDirty = true)
    }

    override fun addFood(foodId: Long) {
        viewModelScope.launch {
            val foodName = foods.getById(foodId)?.name ?: "Food #$foodId"
            val newItem = MealEditorUiState.Item(
                lineId = UUID.randomUUID().toString(),
                id = null,
                foodId = foodId,
                foodName = foodName,
                servings = "1",
                grams = null,
                milliliters = null
            )
            val updatedItems = _state.value.items + newItem
            _state.value = _state.value.copy(
                items = updatedItems,
                errorMessage = null,
                isDirty = true
            )
            recomputeDraftMacroGuidance(updatedItems)
        }
    }

    override fun updateServings(lineId: String, servingsText: String) {
        val updatedItems = _state.value.items.map {
            if (it.lineId == lineId) it.copy(servings = servingsText) else it
        }
        _state.value = _state.value.copy(
            items = updatedItems,
            errorMessage = null,
            isDirty = true
        )
        recomputeDraftMacroGuidance(updatedItems)
    }

    override fun updateGrams(lineId: String, grams: String) {
        val g = grams.toDoubleOrNull()
        val updatedItems = _state.value.items.map {
            if (it.lineId == lineId) it.copy(grams = g) else it
        }
        _state.value = _state.value.copy(
            items = updatedItems,
            errorMessage = null,
            isDirty = true
        )
        recomputeDraftMacroGuidance(updatedItems)
    }

    override fun updateMilliliters(lineId: String, ml: String) {
        // NOTE: MealTemplateItemEntity currently does NOT have milliliters.
        // Keep the UI field but don’t persist it.
        val v = ml.toDoubleOrNull()
        val updatedItems = _state.value.items.map {
            if (it.lineId == lineId) it.copy(milliliters = v) else it
        }
        _state.value = _state.value.copy(
            items = updatedItems,
            errorMessage = null,
            isDirty = true
        )
        recomputeDraftMacroGuidance(updatedItems)
    }

    override fun removeItem(lineId: String) {
        val updatedItems = _state.value.items.filterNot { it.lineId == lineId }
        _state.value = _state.value.copy(
            items = updatedItems,
            errorMessage = null,
            isDirty = true
        )
        recomputeDraftMacroGuidance(updatedItems)
    }

    override fun moveItem(fromIndex: Int, toIndex: Int) {
        val list = _state.value.items.toMutableList()
        if (fromIndex !in list.indices || toIndex !in list.indices) return
        val moved = list.removeAt(fromIndex)
        list.add(toIndex, moved)
        _state.value = _state.value.copy(items = list, errorMessage = null, isDirty = true)
        recomputeDraftMacroGuidance(list)
    }

    override fun save() {
        viewModelScope.launch {
            val name = _state.value.name.trim()
            if (name.isBlank()) {
                _state.value = _state.value.copy(errorMessage = "Template name is required.")
                return@launch
            }

            _state.value = _state.value.copy(isSaving = true, errorMessage = null)
            try {
                val existingTemplateId = _state.value.mealId
                val templateId = if (existingTemplateId != null && existingTemplateId > 0L) {
                    templates.update(
                        MealTemplateEntity(
                            id = existingTemplateId,
                            name = name,
                            defaultSlot = templates.getById(existingTemplateId)?.defaultSlot
                        )
                    )
                    existingTemplateId
                } else {
                    templates.insert(
                        MealTemplateEntity(
                            id = 0L,
                            name = name,
                            defaultSlot = null
                        )
                    )
                }

                templateItems.deleteItemsForTemplate(templateId)

                _state.value.items.forEachIndexed { index, ui ->
                    val sourceType = if (foods.getById(ui.foodId)?.isRecipe == true) {
                        PlannedItemSource.RECIPE
                    } else {
                        PlannedItemSource.FOOD
                    }

                    templateItems.insert(
                        MealTemplateItemEntity(
                            id = 0L,
                            templateId = templateId,
                            type = sourceType,
                            refId = ui.foodId,
                            grams = if (sourceType == PlannedItemSource.RECIPE) null else ui.grams,
                            servings = ui.servings.toDoubleOrNull(),
                            sortOrder = index
                        )
                    )
                }

                _state.value = _state.value.copy(mealId = templateId, isDirty = false)
                recomputeDraftMacroGuidance(_state.value.items)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(errorMessage = t.message ?: "Save failed")
            } finally {
                _state.value = _state.value.copy(isSaving = false)
            }
        }
    }

    override fun discardChanges() {
        val templateId = _state.value.mealId
        if (templateId == null) {
            _state.value = _state.value.copy(errorMessage = null, isDirty = false)
            recomputeDraftMacroGuidance(emptyList())
            return
        }

        viewModelScope.launch {
            try {
                val template = templates.getById(templateId)
                val items = templateItems.getItemsForTemplate(templateId)
                    .sortedBy { it.sortOrder }

                val uiItems = items.map { entity ->
                    val foodName = foods.getById(entity.refId)?.name ?: "Food #${entity.refId}"
                    MealEditorUiState.Item(
                        lineId = UUID.randomUUID().toString(),
                        id = entity.id,
                        foodId = entity.refId,
                        foodName = foodName,
                        servings = entity.servings?.toString() ?: "",
                        grams = entity.grams,
                        milliliters = null
                    )
                }

                _state.value = _state.value.copy(
                    name = template?.name ?: _state.value.name,
                    items = uiItems,
                    errorMessage = null,
                    isDirty = false
                )
                recomputeDraftMacroGuidance(uiItems)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(errorMessage = t.message ?: "Discard failed")
            }
        }
    }

    private fun recomputeDraftMacroGuidance(items: List<MealEditorUiState.Item>) {
        viewModelScope.launch {
            val totals = computeDraftMacros(
                items.map { item ->
                    ComputeMealTemplateDraftMacroTotalsUseCase.DraftItem(
                        foodId = item.foodId,
                        servings = item.servings.toDoubleOrNull(),
                        grams = item.grams
                    )
                }
            )
            _state.value = _state.value.copy(
                liveMacroTotals = totals,
                liveMacroSummaryLine = totals.toMealTemplateMacroSummaryLine()
            )
        }
    }
}

/**
 * Bottom KDoc for future AI assistant.
 *
 * Phase 3A added live macro guidance here instead of creating a template-only screen because the
 * shared meal editor already had the correct interaction model. Keep the coupling minimal:
 * - ViewModel computes in-memory draft totals
 * - shared state carries display-ready macro text
 * - header renders the summary when editor mode is TEMPLATE
 *
 * If future work adds macro-goal comparisons, extend this ViewModel with goal inputs and keep the
 * composable layer passive.
 */
