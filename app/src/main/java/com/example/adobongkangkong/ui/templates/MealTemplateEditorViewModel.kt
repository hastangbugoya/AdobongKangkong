package com.example.adobongkangkong.ui.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.data.local.db.entity.MealTemplateEntity
import com.example.adobongkangkong.data.local.db.entity.MealTemplateItemEntity
import com.example.adobongkangkong.domain.planner.model.PlannedItemSource
import com.example.adobongkangkong.domain.repository.FoodRepository
import com.example.adobongkangkong.domain.repository.MealTemplateItemRepository
import com.example.adobongkangkong.domain.repository.MealTemplateRepository
import com.example.adobongkangkong.ui.meal.editor.MealEditorContract
import com.example.adobongkangkong.ui.meal.editor.MealEditorMode
import com.example.adobongkangkong.ui.meal.editor.MealEditorUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel backing the meal template editor.
 *
 * For developers:
 * - Owns template draft load/save behavior.
 * - Restores Step 2 editor actions: duplicate + delete.
 * - Uses one-shot [effects] so AppNavHost can navigate after duplicate/delete.
 * - Duplicate copies header + items but intentionally does not copy banner files.
 */
@HiltViewModel
class MealTemplateEditorViewModel @Inject constructor(
    private val templates: MealTemplateRepository,
    private val templateItems: MealTemplateItemRepository,
    private val foods: FoodRepository
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
            errorMessage = null
        )
    )

    override val state: StateFlow<MealEditorUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<Effect>(extraBufferCapacity = 1)
    val effects: SharedFlow<Effect> = _effects.asSharedFlow()

    sealed interface Effect {
        data class OpenTemplate(val templateId: Long) : Effect
        data class Deleted(val templateId: Long) : Effect
    }

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
            _state.value = _state.value.copy(
                items = _state.value.items + newItem,
                errorMessage = null,
                isDirty = true
            )
        }
    }

    override fun updateServings(lineId: String, servingsText: String) {
        _state.value = _state.value.copy(
            items = _state.value.items.map { if (it.lineId == lineId) it.copy(servings = servingsText) else it },
            errorMessage = null,
            isDirty = true
        )
    }

    override fun updateGrams(lineId: String, grams: String) {
        val g = grams.toDoubleOrNull()
        _state.value = _state.value.copy(
            items = _state.value.items.map { if (it.lineId == lineId) it.copy(grams = g) else it },
            errorMessage = null,
            isDirty = true
        )
    }

    override fun updateMilliliters(lineId: String, ml: String) {
        // NOTE: MealTemplateItemEntity currently does NOT have milliliters.
        // Keep the UI field but don’t persist it.
        val v = ml.toDoubleOrNull()
        _state.value = _state.value.copy(
            items = _state.value.items.map { if (it.lineId == lineId) it.copy(milliliters = v) else it },
            errorMessage = null,
            isDirty = true
        )
    }

    override fun removeItem(lineId: String) {
        _state.value = _state.value.copy(
            items = _state.value.items.filterNot { it.lineId == lineId },
            errorMessage = null,
            isDirty = true
        )
    }

    override fun moveItem(fromIndex: Int, toIndex: Int) {
        val list = _state.value.items.toMutableList()
        if (fromIndex !in list.indices || toIndex !in list.indices) return
        val moved = list.removeAt(fromIndex)
        list.add(toIndex, moved)
        _state.value = _state.value.copy(items = list, errorMessage = null, isDirty = true)
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
            } catch (t: Throwable) {
                _state.value = _state.value.copy(errorMessage = t.message ?: "Save failed")
            } finally {
                _state.value = _state.value.copy(isSaving = false)
            }
        }
    }

    fun duplicateTemplate() {
        val currentTemplateId = _state.value.mealId
        if (currentTemplateId == null || currentTemplateId <= 0L) {
            _state.value = _state.value.copy(errorMessage = "Save template before duplicating.")
            return
        }

        viewModelScope.launch {
            try {
                val source = templates.getById(currentTemplateId)
                val duplicateName = buildDuplicateName(_state.value.name.ifBlank { source?.name.orEmpty() })

                val newTemplateId = templates.insert(
                    MealTemplateEntity(
                        id = 0L,
                        name = duplicateName,
                        defaultSlot = source?.defaultSlot
                    )
                )

                _state.value.items.forEachIndexed { index, ui ->
                    val sourceType = if (foods.getById(ui.foodId)?.isRecipe == true) {
                        PlannedItemSource.RECIPE
                    } else {
                        PlannedItemSource.FOOD
                    }

                    templateItems.insert(
                        MealTemplateItemEntity(
                            id = 0L,
                            templateId = newTemplateId,
                            type = sourceType,
                            refId = ui.foodId,
                            grams = if (sourceType == PlannedItemSource.RECIPE) null else ui.grams,
                            servings = ui.servings.toDoubleOrNull(),
                            sortOrder = index
                        )
                    )
                }

                _effects.tryEmit(Effect.OpenTemplate(newTemplateId))
            } catch (t: Throwable) {
                _state.value = _state.value.copy(errorMessage = t.message ?: "Duplicate failed")
            }
        }
    }

    fun deleteTemplate() {
        val currentTemplateId = _state.value.mealId
        if (currentTemplateId == null || currentTemplateId <= 0L) {
            _state.value = _state.value.copy(errorMessage = "Nothing to delete.")
            return
        }

        viewModelScope.launch {
            try {
                templateItems.deleteItemsForTemplate(currentTemplateId)
                templates.getById(currentTemplateId)?.let { templates.delete(it) }
                _effects.tryEmit(Effect.Deleted(currentTemplateId))
            } catch (t: Throwable) {
                _state.value = _state.value.copy(errorMessage = t.message ?: "Delete failed")
            }
        }
    }

    override fun discardChanges() {
        val templateId = _state.value.mealId
        if (templateId == null) {
            _state.value = _state.value.copy(errorMessage = null, isDirty = false)
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
            } catch (t: Throwable) {
                _state.value = _state.value.copy(errorMessage = t.message ?: "Discard failed")
            }
        }
    }

    private fun buildDuplicateName(sourceName: String): String {
        val trimmed = sourceName.trim()
        return if (trimmed.isBlank()) "Meal Template Copy" else "$trimmed Copy"
    }
}

/**
 * Bottom KDoc for future AI assistant.
 *
 * Duplicate/Delete were intentionally restored here instead of being pushed into the shared
 * meal editor contract because they are template-only behaviors. Keep that separation.
 *
 * Duplicate policy:
 * - copy current editor draft rows
 * - preserve defaultSlot from source template
 * - do not copy banner media yet
 *
 * Delete policy:
 * - delete template items first
 * - delete template entity second
 * - banner file cleanup is handled by AppNavHost on Deleted effect
 */
