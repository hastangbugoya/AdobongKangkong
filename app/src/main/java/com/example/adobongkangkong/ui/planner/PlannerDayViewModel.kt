package com.example.adobongkangkong.ui.planner

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.data.local.db.entity.MealSlot
import com.example.adobongkangkong.data.local.db.entity.PlannedItemEntity
import com.example.adobongkangkong.domain.planner.usecase.AddPlannedFoodItemUseCase
import com.example.adobongkangkong.domain.planner.usecase.AddPlannedRecipeBatchItemUseCase
import com.example.adobongkangkong.domain.planner.usecase.CreatePlannedMealUseCase
import com.example.adobongkangkong.domain.planner.usecase.DuplicatePlannedMealUseCase
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

    private val duplicateMeal: DuplicatePlannedMealUseCase,
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

            is PlannerDayEvent.AddMeal -> {
                openAddSheet(slot = event.slot)
            }

            // ✅ Close sheet immediately, then cleanup empty created meal (async) + log notice.
            PlannerDayEvent.DismissAddSheet -> {
                dismissAddSheetWithCleanup()
            }

            is PlannerDayEvent.UpdateAddSheetName -> {
                _state.update { s ->
                    val sheet = s.addSheet ?: return@update s
                    s.copy(addSheet = sheet.copy(nameOverride = event.value))
                }
            }

            is PlannerDayEvent.UpdateAddSheetCustomLabel -> {
                _state.update { s ->
                    val sheet = s.addSheet ?: return@update s
                    s.copy(addSheet = sheet.copy(customLabel = event.value))
                }
            }

            PlannerDayEvent.CreateMealIfNeeded -> {
                createMealIfNeeded()
            }

            PlannerDayEvent.CreateAnotherMeal -> {
                _state.update { s ->
                    val current = s.addSheet ?: return@update s
                    s.copy(addSheet = current.copy(isCreating = false, createdMealId = null, errorMessage = null))
                }
                createMealIfNeeded()
            }

            is PlannerDayEvent.StartAddItem -> {
                _state.update { s ->
                    val sh = s.addSheet ?: return@update s
                    s.copy(
                        addSheet = sh.copy(
                            addItemMode = event.mode,
                            query = "",
                            results = emptyList(),
                            selectedRefId = null,
                            selectedTitle = null,
                            gramsText = "",
                            servingsText = "",
                            addItemError = null
                        )
                    )
                }
            }

            PlannerDayEvent.CancelAddItem -> {
                searchJob?.cancel()
                _state.update { s ->
                    val sh = s.addSheet ?: return@update s
                    s.copy(
                        addSheet = sh.copy(
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
                        )
                    )
                }
            }

            is PlannerDayEvent.UpdateAddQuery -> {
                _state.update { s ->
                    val sh = s.addSheet ?: return@update s
                    s.copy(
                        addSheet = sh.copy(
                            query = event.value,
                            isSearching = true,
                            addItemError = null,
                            selectedRefId = null,
                            selectedTitle = null
                        )
                    )
                }
                refreshFoodSearch()
            }

            is PlannerDayEvent.SelectSearchResult -> {
                _state.update { s ->
                    val sh = s.addSheet ?: return@update s
                    s.copy(
                        addSheet = sh.copy(
                            selectedRefId = event.id,
                            selectedTitle = event.title,
                            addItemError = null
                        )
                    )
                }
            }

            is PlannerDayEvent.UpdateAddGrams -> {
                _state.update { s ->
                    val sh = s.addSheet ?: return@update s
                    s.copy(addSheet = sh.copy(gramsText = event.value, addItemError = null))
                }
            }

            is PlannerDayEvent.UpdateAddServings -> {
                _state.update { s ->
                    val sh = s.addSheet ?: return@update s
                    s.copy(addSheet = sh.copy(servingsText = event.value, addItemError = null))
                }
            }

            PlannerDayEvent.ConfirmAddItem -> {
                addSelectedFoodToMeal()
            }

            is PlannerDayEvent.RemoveEmptyPlannedMeal -> {
                clearUndoIfForMeal(event.mealId)
                removeEmptyMeal(event.mealId)
            }

            is PlannerDayEvent.RemovePlannedItem -> {
                removeItemWithUndo(event.itemId)
            }

            is PlannerDayEvent.UndoRemovePlannedItem -> {
                undoRemove(event.undoId)
            }

            is PlannerDayEvent.UndoSnackbarConsumed -> {
                _state.update { s ->
                    if (s.undo?.id == event.undoId) s.copy(undo = null) else s
                }
                if (event.undoId == lastUndoId) {
                    lastRemovedSnapshot = null
                }
            }

            is PlannerDayEvent.DuplicateMeal -> {
                // Default behavior: open duplicate sheet with "today" pre-selected.
                openDuplicateSheet(event.mealId)
            }

            is PlannerDayEvent.OpenDuplicateSheet -> {
                openDuplicateSheet(event.mealId)
            }

            PlannerDayEvent.DismissDuplicateSheet -> {
                _state.update { it.copy(duplicateSheet = null) }
            }

            PlannerDayEvent.DuplicateAddToday -> {
//                addDuplicateDate(_state.value.date)
                // One-tap shortcut: replace selection with ONLY today, then duplicate once.
                val date = _state.value.date
                _state.update { s ->
                    val sheet = s.duplicateSheet ?: return@update s
                    s.copy(duplicateSheet = sheet.copy(selectedDates = listOf(date), errorMessage = null))
                }
                confirmDuplicateDates()
            }

            PlannerDayEvent.DuplicateAddTomorrow -> {
//                addDuplicateDate(_state.value.date.plusDays(1))
                // One-tap shortcut: replace selection with ONLY tomorrow, then duplicate once.
                val date = _state.value.date.plusDays(1)
                _state.update { s ->
                    val sheet = s.duplicateSheet ?: return@update s
                    s.copy(duplicateSheet = sheet.copy(selectedDates = listOf(date), errorMessage = null))
                }
                confirmDuplicateDates()
            }

            is PlannerDayEvent.DuplicateAddDate -> {
//                addDuplicateDate(LocalDate.parse(event.dateIso))
                val date = LocalDate.parse(event.dateIso)

                _state.update { s ->
                    val sheet = s.duplicateSheet ?: return@update s
                    s.copy(
                        duplicateSheet = sheet.copy(
                            selectedDates = listOf(date),
                            errorMessage = null
                        )
                    )
                }

                confirmDuplicateDates()
            }

            is PlannerDayEvent.DuplicateRemoveDate -> {
                removeDuplicateDate(LocalDate.parse(event.dateIso))
            }

            PlannerDayEvent.ConfirmDuplicateDates -> {
                confirmDuplicateDates()
            }
        }
    }



    /**
     * Dismiss the add-sheet. If a meal container was created but no items were added,
     * delete it so empty meals don't get stuck in the planner.
     *
     * This is a safe cleanup: [RemoveEmptyPlannedMealUseCase] no-ops if the meal has items.
     *
     * "Process notice": we close the sheet immediately, then cleanup async, and log the action.
     */
    private fun dismissAddSheetWithCleanup() {
        val mealIdToCleanup = _state.value.addSheet?.createdMealId

        // Stop any in-flight search to avoid wasted work after dismiss.
        searchJob?.cancel()

        // Close immediately (UI first).
        _state.update { it.copy(addSheet = null) }

        if (mealIdToCleanup != null && mealIdToCleanup > 0L) {
            Log.d(
                "PlannerCleanup",
                "Dismissed add sheet; attempting cleanup of empty mealId=$mealIdToCleanup"
            )
            viewModelScope.launch {
                try {
                    removeEmptyPlannedMeal(mealIdToCleanup)
                } catch (t: Throwable) {
                    // Don't block dismissal; surface as a non-fatal error.
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
                    st.copy(
                        addSheet = sht.copy(
                            isAddingItem = false,
                            selectedRefId = null,
                            selectedTitle = null,
                            gramsText = "",
                            servingsText = "",
                            addItemError = null
                        )
                    )
                }
            } catch (t: Throwable) {
                _state.update { st ->
                    val sht = st.addSheet ?: return@update st
                    st.copy(
                        addSheet = sht.copy(
                            isAddingItem = false,
                            addItemError = t.message ?: "Failed to add food."
                        )
                    )
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

    private fun openDuplicateSheet(mealId: Long) {
        if (mealId <= 0L) return

        val today = _state.value.date
        _state.update { s ->
            s.copy(
                duplicateSheet = DuplicateSheetState(
                    sourceMealId = mealId,
                    selectedDates = listOf(today)
                ),
                errorMessage = null
            )
        }
    }

    private fun addDuplicateDate(date: LocalDate) {
        _state.update { s ->
            val sheet = s.duplicateSheet ?: return@update s
            val next = (sheet.selectedDates + date).distinct().sorted()
            s.copy(duplicateSheet = sheet.copy(selectedDates = next, errorMessage = null))
        }
    }

    private fun removeDuplicateDate(date: LocalDate) {
        _state.update { s ->
            val sheet = s.duplicateSheet ?: return@update s
            val next = sheet.selectedDates.filterNot { it == date }
            s.copy(duplicateSheet = sheet.copy(selectedDates = next, errorMessage = null))
        }
    }

    private fun confirmDuplicateDates() {
        val snapshot = _state.value.duplicateSheet ?: return
        if (snapshot.isDuplicating) return
        if (snapshot.selectedDates.isEmpty()) return

        _state.update { s ->
            val sh = s.duplicateSheet ?: return@update s
            s.copy(duplicateSheet = sh.copy(isDuplicating = true, errorMessage = null))
        }

        viewModelScope.launch {
            try {
                for (d in snapshot.selectedDates) {
                    duplicateMeal(
                        sourceMealId = snapshot.sourceMealId,
                        targetDateIso = d.toString()
                    )
                }
                _state.update { it.copy(duplicateSheet = null) }
            } catch (t: Throwable) {
                _state.update { s ->
                    val sh = s.duplicateSheet
                    if (sh == null) s.copy(errorMessage = t.message ?: "Failed to duplicate meal")
                    else s.copy(
                        duplicateSheet = sh.copy(
                            isDuplicating = false,
                            errorMessage = t.message ?: "Failed to duplicate meal"
                        )
                    )
                }
            } finally {
                _state.update { s ->
                    val sh = s.duplicateSheet ?: return@update s
                    if (sh.isDuplicating) s.copy(duplicateSheet = sh.copy(isDuplicating = false)) else s
                }
            }
        }
    }

    private fun clearUndoIfForMeal(mealId: Long) {
        val snapshot = lastRemovedSnapshot ?: return
        if (snapshot.mealId != mealId) return

        // The parent meal is about to be deleted; any pending undo would fail FK restore.
        lastRemovedSnapshot = null
        _state.update { s -> if (s.undo != null) s.copy(undo = null) else s }
    }

}
