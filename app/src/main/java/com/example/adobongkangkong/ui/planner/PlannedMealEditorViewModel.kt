package com.example.adobongkangkong.ui.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.data.local.db.entity.PlannedItemEntity
import com.example.adobongkangkong.data.local.db.entity.PlannedMealEntity
import com.example.adobongkangkong.domain.planner.model.PlannedItemSource
import com.example.adobongkangkong.domain.repository.FoodRepository
import com.example.adobongkangkong.domain.repository.PlannedItemRepository
import com.example.adobongkangkong.domain.repository.PlannedMealRepository
import com.example.adobongkangkong.ui.meal.editor.MealEditorContract
import com.example.adobongkangkong.ui.meal.editor.MealEditorMode
import com.example.adobongkangkong.ui.meal.editor.MealEditorUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PlannedMealEditorViewModel @Inject constructor(
    private val plannedItems: PlannedItemRepository,
    private val plannedMeals: PlannedMealRepository,
    private val foods: FoodRepository
) : ViewModel(), MealEditorContract {

    private val _state = MutableStateFlow(
        MealEditorUiState(
            mealId = null,
            name = "",
            mode = MealEditorMode.PLANNED,
            subtitle = null,
            items = emptyList(),
            isSaving = false,
            canSave = true,
            errorMessage = null
        )
    )

    override val state: StateFlow<MealEditorUiState> = _state.asStateFlow()

    /**
     * Cached meal entity for the current editor session.
     *
     * Used to compute the default non-null nameOverride when the user leaves the meal name blank.
     */
    private var currentMeal: PlannedMealEntity? = null

    /**
     * Call this when you enter the editor so save() knows what meal to persist into.
     * (Not part of shared contract; it’s route-level wiring.)
     */
    fun setMealId(mealId: Long, subtitle: String? = null) {
        _state.value = _state.value.copy(mealId = mealId, subtitle = subtitle, errorMessage = null)

        // Load current DB rows once.
        viewModelScope.launch {
            try {
                val meal = plannedMeals.getById(mealId)
                currentMeal = meal

                val existing = plannedItems.getItemsForMeal(mealId)
                    .sortedBy { it.sortOrder }

                val uiItems = existing.map { entity ->
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

                val current = _state.value
                // If the user already changed the UI (e.g. picked a food), do NOT overwrite items from DB.
                if (!current.isDirty) {
                    val resolvedName = resolveEditorName(meal)
                    _state.value = current.copy(
                        name = resolvedName,
                        items = uiItems,
                        errorMessage = null,
                        isDirty = false
                    )
                }
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    errorMessage = t.message ?: "Failed to load meal items."
                )
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
                isDirty = true // <-- REQUIRED so setMealId() can't overwrite it later
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
        // NOTE: PlannedItemEntity currently does NOT have milliliters.
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
        val mealId = _state.value.mealId
        if (mealId == null) {
            _state.value = _state.value.copy(errorMessage = "Cannot save: mealId is null.")
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, errorMessage = null)
            try {
                // Persist nameOverride as non-null.
                val meal = currentMeal ?: plannedMeals.getById(mealId)
                if (meal == null) {
                    _state.value = _state.value.copy(errorMessage = "Cannot save: meal not found.")
                    return@launch
                }

                val nameOverride = resolveNonNullNameOverride(meal)
                if (meal.nameOverride != nameOverride) {
                    plannedMeals.update(meal.copy(nameOverride = nameOverride))
                    currentMeal = meal.copy(nameOverride = nameOverride)
                }

                // Required behavior:
                // - mealId already exists
                // - deleteForMeal(mealId)
                // - insert PlannedItemEntity rows in UI order (sortOrder)
                plannedItems.deleteForMeal(mealId)

                _state.value.items.forEachIndexed { index, ui ->
                    plannedItems.insert(
                        PlannedItemEntity(
                            id = 0L,
                            mealId = mealId,
                            type = PlannedItemSource.FOOD,
                            refId = ui.foodId, // FOOD => refId = foodId
                            grams = ui.grams,
                            servings = ui.servings.toDoubleOrNull(),
                            sortOrder = index
                        )
                    )
                }

                _state.value = _state.value.copy(isDirty = false)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(errorMessage = t.message ?: "Save failed")
            } finally {
                _state.value = _state.value.copy(isSaving = false)
            }
        }
    }

    override fun discardChanges() {
        val mealId = _state.value.mealId
        if (mealId == null) {
            _state.value = _state.value.copy(errorMessage = null, isDirty = false)
            return
        }

        viewModelScope.launch {
            try {
                val meal = plannedMeals.getById(mealId)
                currentMeal = meal

                val existing = plannedItems.getItemsForMeal(mealId)
                    .sortedBy { it.sortOrder }

                val uiItems = existing.map { entity ->
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

                val resolvedName = resolveEditorName(meal)
                _state.value = _state.value.copy(
                    name = resolvedName,
                    items = uiItems,
                    errorMessage = null,
                    isDirty = false
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(errorMessage = t.message ?: "Discard failed")
            }
        }
    }

    private fun resolveEditorName(meal: PlannedMealEntity?): String {
        if (meal == null) return _state.value.name
        return meal.nameOverride ?: defaultNameOverride(meal)
    }

    private fun resolveNonNullNameOverride(meal: PlannedMealEntity): String {
        val trimmed = _state.value.name.trim()
        return if (trimmed.isNotBlank()) {
            trimmed
        } else {
            defaultNameOverride(meal)
        }
    }

    private fun defaultNameOverride(meal: PlannedMealEntity): String {
        val date = LocalDate.parse(meal.date)
        val dateStr = date.format(DateTimeFormatter.ofPattern("MMM-dd-yyyy", Locale.US))
        return "${meal.slot.display}($dateStr)"
    }
}