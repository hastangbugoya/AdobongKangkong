package com.example.adobongkangkong.ui.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.data.local.db.entity.MealSlot
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

/**
 * Planned Meal editor ViewModel.
 *
 * FoodEditor parity rules (behavior contract):
 * - Opening the editor in "new" mode MUST NOT write to the database.
 * - Only an explicit Save commits changes.
 * - Back/Cancel never saves.
 *
 * New vs Edit:
 * - New (draft): state.mealId == null, draftDateIso/draftSlot are set via startNewPlannedMeal()
 * - Edit: state.mealId != null, loaded via setMealId()
 */
@HiltViewModel
class PlannedMealEditorViewModel @Inject constructor(
    private val plannedMeals: PlannedMealRepository,
    private val plannedItems: PlannedItemRepository,
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

    // Draft-only fields for NEW planned meals (no DB row until Save).
    private var draftDateIso: String? = null
    private var draftSlot: MealSlot? = null

    /**
     * Initialize the editor for creating a NEW planned meal (draft mode).
     *
     * IMPORTANT:
     * - This must not insert into the DB.
     * - The meal row is inserted only when Save is pressed.
     *
     * Returning from the food picker re-enters this route. In that case we must NOT
     * wipe the in-memory draft that already has picked foods / edits.
     */
    fun startNewPlannedMeal(
        dateIso: String,
        slot: MealSlot,
        subtitle: String? = null
    ) {
        val current = _state.value
        val sameDraftContext =
            current.mealId == null &&
                draftDateIso == dateIso &&
                draftSlot == slot

        val hasDraftContent =
            current.items.isNotEmpty() ||
                current.isDirty ||
                current.name.isNotBlank()

        if (sameDraftContext && hasDraftContent) {
            if (current.subtitle != subtitle) {
                _state.value = current.copy(subtitle = subtitle)
            }
            return
        }

        draftDateIso = dateIso
        draftSlot = slot

        _state.value = _state.value.copy(
            mealId = null,
            mode = MealEditorMode.PLANNED,
            subtitle = subtitle,
            name = defaultNameOverride(slot, dateIso),
            items = emptyList(),
            errorMessage = null,
            isDirty = false
        )
    }

    /**
     * Initialize the editor for EDITING an existing planned meal.
     * (Route-level wiring.)
     */
    fun setMealId(mealId: Long, subtitle: String? = null) {
        // Clear draft fields when editing an existing meal.
        draftDateIso = null
        draftSlot = null

        _state.value = _state.value.copy(mealId = mealId, subtitle = subtitle, errorMessage = null)

        // Load current DB rows once.
        viewModelScope.launch {
            try {
                val meal = plannedMeals.getById(mealId)
                val existing = plannedItems.getItemsForMeal(mealId).sortedBy { it.sortOrder }

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

                val resolvedName = if (meal != null) {
                    meal.nameOverride ?: defaultNameOverride(meal.slot, meal.date)
                } else {
                    _state.value.name
                }

                val current = _state.value
                if (!current.isDirty) {
                    _state.value = current.copy(
                        name = resolvedName,
                        items = uiItems,
                        errorMessage = null,
                        isDirty = false
                    )
                }
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    errorMessage = t.message ?: "Failed to load meal."
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
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, errorMessage = null)
            try {
                val current = _state.value
                val existingMealId = current.mealId

                val resolvedMealId: Long =
                    if (existingMealId == null) {
                        // NEW draft -> insert the meal now (explicit Save is the commit).
                        val dateIso = draftDateIso
                        val slot = draftSlot
                        if (dateIso.isNullOrBlank() || slot == null) {
                            throw IllegalStateException("Cannot save: missing draft date/slot.")
                        }

                        val sortOrder = plannedMeals.getMaxSortOrderForDate(dateIso) + 1
                        val nameOverride = resolveNonNullNameOverrideForDraft(dateIso = dateIso, slot = slot)

                        val newId = plannedMeals.insert(
                            PlannedMealEntity(
                                id = 0L,
                                date = dateIso,
                                slot = slot,
                                customLabel = null,
                                nameOverride = nameOverride,
                                sortOrder = sortOrder,
                                seriesId = null
                            )
                        )

                        // Update UI state so the screen is now in Edit mode after save.
                        _state.value = _state.value.copy(mealId = newId, name = nameOverride)
                        newId
                    } else {
                        // EDIT existing -> update nameOverride (never null in practice).
                        val meal = plannedMeals.getById(existingMealId)
                            ?: throw IllegalStateException("Cannot save: meal not found.")
                        val nameOverride = resolveNonNullNameOverrideForExisting(meal)
                        if (meal.nameOverride != nameOverride) {
                            plannedMeals.update(meal.copy(nameOverride = nameOverride))
                        }
                        existingMealId
                    }

                // Replace items (applies to both new and edit).
                plannedItems.deleteForMeal(resolvedMealId)
                _state.value.items.forEachIndexed { index, ui ->
                    plannedItems.insert(
                        PlannedItemEntity(
                            id = 0L,
                            mealId = resolvedMealId,
                            type = PlannedItemSource.FOOD,
                            refId = ui.foodId,
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
            val dateIso = draftDateIso
            val slot = draftSlot
            _state.value = _state.value.copy(
                name = if (dateIso != null && slot != null) defaultNameOverride(slot, dateIso) else _state.value.name,
                items = emptyList(),
                errorMessage = null,
                isDirty = false
            )
            return
        }

        viewModelScope.launch {
            try {
                val meal = plannedMeals.getById(mealId)
                val existing = plannedItems.getItemsForMeal(mealId).sortedBy { it.sortOrder }

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

                _state.value = _state.value.copy(
                    name = if (meal != null) resolveEditorName(meal) else _state.value.name,
                    items = uiItems,
                    errorMessage = null,
                    isDirty = false
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(errorMessage = t.message ?: "Discard failed")
            }
        }
    }

    private fun resolveEditorName(meal: PlannedMealEntity): String {
        return meal.nameOverride ?: defaultNameOverride(meal.slot, meal.date)
    }

    private fun resolveNonNullNameOverrideForExisting(meal: PlannedMealEntity): String {
        val trimmed = _state.value.name.trim()
        return if (trimmed.isNotBlank()) {
            trimmed
        } else {
            defaultNameOverride(meal.slot, meal.date)
        }
    }

    private fun resolveNonNullNameOverrideForDraft(dateIso: String, slot: MealSlot): String {
        val trimmed = _state.value.name.trim()
        return if (trimmed.isNotBlank()) {
            trimmed
        } else {
            defaultNameOverride(slot, dateIso)
        }
    }

    private fun defaultNameOverride(slot: MealSlot, dateIso: String): String {
        val date = LocalDate.parse(dateIso)
        val dateStr = date.format(DateTimeFormatter.ofPattern("MMM-dd-yyyy", Locale.US))
        return "${slot.display}($dateStr)"
    }
}
