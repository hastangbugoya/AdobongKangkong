package com.example.adobongkangkong.ui.planner

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.data.local.db.entity.MealSlot
import com.example.adobongkangkong.data.local.db.entity.PlannedItemEntity
import com.example.adobongkangkong.domain.planner.usecase.AddPlannedFoodItemUseCase
import com.example.adobongkangkong.domain.planner.usecase.AddPlannedRecipeBatchItemUseCase
import com.example.adobongkangkong.domain.planner.usecase.CreatePlannedMealUseCase
import com.example.adobongkangkong.domain.planner.usecase.ObservePlannedDayUseCase
import com.example.adobongkangkong.domain.planner.usecase.RemoveEmptyPlannedMealUseCase
import com.example.adobongkangkong.domain.planner.usecase.RemovePlannedItemForUndoUseCase
import com.example.adobongkangkong.domain.planner.usecase.RestorePlannedItemUseCase
import com.example.adobongkangkong.domain.usecase.SearchFoodsUseCase
import com.example.adobongkangkong.ui.planner.model.FoodSearchRow
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class PlannerDayViewModel @Inject constructor(
    private val observePlannedDay: ObservePlannedDayUseCase,
    private val createPlannedMeal: CreatePlannedMealUseCase,
    private val removeEmptyPlannedMeal: RemoveEmptyPlannedMealUseCase,
    private val addPlannedFoodItem: AddPlannedFoodItemUseCase,
    private val addPlannedRecipeItem: AddPlannedRecipeBatchItemUseCase,
    private val searchFoods: SearchFoodsUseCase,

    // Undo plumbing (items)
    private val removePlannedItemForUndo: RemovePlannedItemForUndoUseCase,
    private val restorePlannedItem: RestorePlannedItemUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(PlannerDayUiState(date = LocalDate.now()))
    val state: StateFlow<PlannerDayUiState> = _state.asStateFlow()

    private var observeJob: Job? = null
    private var searchJob: Job? = null

    // Single-level undo (minimal + functional)
    private var lastUndoId: Long = 0L
    private var lastRemovedSnapshot: PlannedItemEntity? = null

    fun setDate(date: LocalDate) {
        if (_state.value.date == date && observeJob != null) return

        _state.update { it.copy(date = date, isLoading = true, errorMessage = null) }

        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            try {
                observePlannedDay(date.toString()).collect { plannedDay ->
                    _state.update { it.copy(isLoading = false, day = plannedDay, errorMessage = null) }
                }
            } catch (t: Throwable) {
                _state.update { it.copy(isLoading = false, errorMessage = t.message ?: "Failed to load plan") }
            }
        }
    }

    fun onEvent(event: PlannerDayEvent) {
        Log.d("Meow", "PlannerDayVM> onEvent: $event")
        when (event) {
            PlannerDayEvent.Back,
            PlannerDayEvent.PickDate,
            PlannerDayEvent.PrevDay,
            PlannerDayEvent.NextDay,
            is PlannerDayEvent.OpenMeal -> {
                // Route handles these (or they are no-ops for now)
            }

            is PlannerDayEvent.AddMeal -> openAddSheet(slot = event.slot)

            // ✅ NEW: Auto-clean created empty meal when user dismisses sheet
            PlannerDayEvent.DismissAddSheet -> dismissAddSheetWithCleanup()

            is PlannerDayEvent.UpdateAddSheetName -> _state.update { s ->
                val sheet = s.addSheet ?: return@update s
                s.copy(addSheet = sheet.copy(nameOverride = event.value))
            }

            is PlannerDayEvent.UpdateAddSheetCustomLabel -> _state.update { s ->
                val sheet = s.addSheet ?: return@update s
                s.copy(addSheet = sheet.copy(customLabel = event.value))
            }

            PlannerDayEvent.CreateMealIfNeeded -> createMealIfNeeded()

            PlannerDayEvent.CreateAnotherMeal -> {
                _state.update { s ->
                    val current = s.addSheet ?: return@update s
                    s.copy(addSheet = current.copy(isCreating = false, createdMealId = null, errorMessage = null))
                }
                createMealIfNeeded()
            }

            is PlannerDayEvent.StartAddItem -> _state.update { s ->
                val sh = s.addSheet ?: return@update s
                s.copy(addSheet = sh.copy(
                    addItemMode = event.mode,
                    query = "",
                    results = emptyList(),
                    selectedRefId = null,
                    selectedTitle = null,
                    gramsText = "",
                    servingsText = "",
                    addItemError = null
                ))
            }

            PlannerDayEvent.CancelAddItem -> {
                searchJob?.cancel()
                _state.update { s ->
                    val sh = s.addSheet ?: return@update s
                    s.copy(addSheet = sh.copy(
                        addItemMode = AddItemMode.NONE,
                        query = "",
                        results = emptyList(),
                        selectedRefId = null,
                        selectedTitle = null,
                        gramsText = "",
                        servingsText = "",
                        isSearching = false,
                        isAddingItem = false,
                        addItemError = null
                    ))
                }
            }

            is PlannerDayEvent.UpdateAddQuery -> {
                _state.update { s ->
                    val sh = s.addSheet ?: return@update s
                    s.copy(addSheet = sh.copy(
                        query = event.value,
                        isSearching = true,
                        addItemError = null,
                        selectedRefId = null,
                        selectedTitle = null
                    ))
                }
                refreshFoodSearch()
            }

            is PlannerDayEvent.SelectSearchResult -> _state.update { s ->
                val sh = s.addSheet ?: return@update s
                s.copy(addSheet = sh.copy(
                    selectedRefId = event.id,
                    selectedTitle = event.title,
                    addItemError = null
                ))
            }

            is PlannerDayEvent.UpdateAddGrams -> _state.update { s ->
                val sh = s.addSheet ?: return@update s
                s.copy(addSheet = sh.copy(gramsText = event.value, addItemError = null))
            }

            is PlannerDayEvent.UpdateAddServings -> _state.update { s ->
                val sh = s.addSheet ?: return@update s
                s.copy(addSheet = sh.copy(servingsText = event.value, addItemError = null))
            }

            PlannerDayEvent.ConfirmAddItem -> addSelectedFoodToMeal()

            // Remove empty meal container (functional cleanup) — if you already wired this event, keep it
            is PlannerDayEvent.RemoveEmptyPlannedMeal -> removeEmptyMeal(event.mealId)

            // Remove + Undo plumbing (works for FOOD and RECIPE items)
            is PlannerDayEvent.RemovePlannedItem -> removeItemWithUndo(event.itemId)
            is PlannerDayEvent.UndoRemovePlannedItem -> undoRemove(event.undoId)
            is PlannerDayEvent.UndoSnackbarConsumed -> {
                _state.update { s ->
                    if (s.undo?.id == event.undoId) s.copy(undo = null) else s
                }
                if (event.undoId == lastUndoId) {
                    lastRemovedSnapshot = null
                }
            }
        }
    }

    /**
     * Close the add sheet. If a meal container was created and is still empty, delete it.
     * This prevents "stuck empty meals" when user bails out mid-flow.
     */
    private fun dismissAddSheetWithCleanup() {
        val mealIdToCleanup = _state.value.addSheet?.createdMealId
        _state.update { it.copy(addSheet = null) }

        if (mealIdToCleanup != null && mealIdToCleanup > 0L) {
            viewModelScope.launch {
                try {
                    removeEmptyPlannedMeal(mealIdToCleanup)
                } catch (t: Throwable) {
                    // Don't block dismissal; just surface error if you want
                    _state.update { it.copy(errorMessage = t.message ?: "Failed to clean up empty meal") }
                }
            }
        }
    }

    private fun removeEmptyMeal(mealId: Long) {
        if (mealId <= 0L) return

        val day = _state.value.day
        val isEmpty = day?.mealsBySlot?.values
            ?.asSequence()
            ?.flatten()
            ?.firstOrNull { it.id == mealId }
            ?.items
            ?.isEmpty()
            ?: false

        if (!isEmpty) return

        viewModelScope.launch {
            try {
                removeEmptyPlannedMeal(mealId)
            } catch (t: Throwable) {
                _state.update { it.copy(errorMessage = t.message ?: "Failed to remove meal") }
            }
        }
    }

    private fun refreshFoodSearch() {
        val s = _state.value
        val sh = s.addSheet ?: return
        if (sh.addItemMode != AddItemMode.FOOD) return

        val q = sh.query.trim()
        searchJob?.cancel()

        if (q.isBlank()) {
            _state.update { st ->
                val sht = st.addSheet ?: return@update st
                st.copy(addSheet = sht.copy(isSearching = false, results = emptyList()))
            }
            return
        }

        searchJob = viewModelScope.launch {
            searchFoods(query = q, limit = 50).collect { foods ->
                val rows = foods.map { f ->
                    FoodSearchRow(
                        id = f.id,
                        title = f.name,
                        subtitle = f.brand?.takeIf { it.isNotBlank() }
                    )
                }
                _state.update { st ->
                    val sht = st.addSheet ?: return@update st
                    st.copy(addSheet = sht.copy(isSearching = false, results = rows))
                }
            }
        }
    }

    private fun addSelectedFoodToMeal() {
        val s = _state.value
        val sh = s.addSheet ?: return
        val mealId = sh.createdMealId ?: return

        val foodId = sh.selectedRefId
        val grams = sh.gramsText.trim().toDoubleOrNull()
        val servings = sh.servingsText.trim().toDoubleOrNull()

        val error = when {
            sh.addItemMode != AddItemMode.FOOD -> "Food add not active."
            foodId == null -> "Select a food first."
            grams == null && servings == null -> "Enter grams or servings."
            else -> null
        }

        if (error != null) {
            _state.update { st ->
                val sht = st.addSheet ?: return@update st
                st.copy(addSheet = sht.copy(addItemError = error))
            }
            return
        }

        if (sh.isAddingItem) return
        _state.update { st ->
            val sht = st.addSheet ?: return@update st
            st.copy(addSheet = sht.copy(isAddingItem = true, addItemError = null))
        }

        viewModelScope.launch {
            try {
                addPlannedFoodItem(
                    mealId,
                    foodId = foodId!!,
                    grams = grams,
                    servings = servings
                )

                _state.update { st ->
                    val sht = st.addSheet ?: return@update st
                    st.copy(addSheet = sht.copy(
                        isAddingItem = false,
                        selectedRefId = null,
                        selectedTitle = null,
                        gramsText = "",
                        servingsText = "",
                        addItemError = null
                    ))
                }
            } catch (t: Throwable) {
                _state.update { st ->
                    val sht = st.addSheet ?: return@update st
                    st.copy(addSheet = sht.copy(
                        isAddingItem = false,
                        addItemError = t.message ?: "Failed to add food."
                    ))
                }
            }
        }
    }

    private fun removeItemWithUndo(itemId: Long) {
        if (itemId <= 0) return
        val title = findPlannedItemTitle(itemId) ?: "item"

        viewModelScope.launch {
            try {
                val snapshot = removePlannedItemForUndo(itemId)
                lastRemovedSnapshot = snapshot

                lastUndoId += 1
                val undoId = lastUndoId

                _state.update { it.copy(undo = UndoUiState(id = undoId, message = "Removed $title")) }
            } catch (t: Throwable) {
                _state.update { it.copy(errorMessage = t.message ?: "Failed to remove item") }
            }
        }
    }

    private fun undoRemove(undoId: Long) {
        if (undoId != lastUndoId) return
        val snapshot = lastRemovedSnapshot ?: return

        viewModelScope.launch {
            try {
                restorePlannedItem(snapshot)
            } catch (t: Throwable) {
                _state.update { it.copy(errorMessage = t.message ?: "Failed to undo") }
            } finally {
                lastRemovedSnapshot = null
                _state.update { s ->
                    if (s.undo?.id == undoId) s.copy(undo = null) else s
                }
            }
        }
    }

    private fun findPlannedItemTitle(itemId: Long): String? {
        val day = _state.value.day ?: return null
        return day.mealsBySlot.values
            .asSequence()
            .flatten()
            .flatMap { it.items.asSequence() }
            .firstOrNull { it.id == itemId }
            ?.title
            ?.takeIf { it.isNotBlank() }
    }

    private fun openAddSheet(slot: MealSlot) {
        _state.update {
            it.copy(
                addSheet = AddSheetState(
                    slot = slot,
                    isCreating = false,
                    createdMealId = null,
                    customLabel = if (slot == MealSlot.CUSTOM) "" else null,
                    nameOverride = ""
                ),
                errorMessage = null
            )
        }
    }

    private fun createMealIfNeeded() {
        val current = _state.value
        val sheet = current.addSheet ?: return

        if (sheet.isCreating || sheet.createdMealId != null) return

        _state.update { it.copy(addSheet = sheet.copy(isCreating = true, errorMessage = null)) }

        viewModelScope.launch {
            try {
                val dateIso = current.date.toString()

                val customLabel: String? =
                    if (sheet.slot == MealSlot.CUSTOM) sheet.customLabel?.trim().takeUnless { it.isNullOrBlank() }
                    else null

                val nameOverride: String? =
                    sheet.nameOverride?.trim().takeUnless { it.isNullOrBlank() }

                val mealId = createPlannedMeal(
                    dateIso = dateIso,
                    slot = sheet.slot,
                    customLabel = customLabel,
                    nameOverride = nameOverride,
                    sortOrder = null
                )

                _state.update { s ->
                    val sSheet = s.addSheet
                    if (sSheet == null) s
                    else s.copy(addSheet = sSheet.copy(isCreating = false, createdMealId = mealId))
                }
            } catch (t: Throwable) {
                _state.update { s ->
                    val sSheet = s.addSheet
                    if (sSheet == null) s.copy(errorMessage = t.message ?: "Failed to create meal")
                    else s.copy(addSheet = sSheet.copy(isCreating = false, errorMessage = t.message ?: "Failed to create meal"))
                }
            }
        }
    }
}
