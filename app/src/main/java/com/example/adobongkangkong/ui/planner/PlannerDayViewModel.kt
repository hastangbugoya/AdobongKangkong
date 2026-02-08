package com.example.adobongkangkong.ui.planner

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.data.local.db.entity.MealSlot
import com.example.adobongkangkong.domain.planner.usecase.AddPlannedFoodItemUseCase
import com.example.adobongkangkong.domain.planner.usecase.AddPlannedRecipeBatchItemUseCase
import com.example.adobongkangkong.domain.planner.usecase.CreatePlannedMealUseCase
import com.example.adobongkangkong.domain.planner.usecase.ObservePlannedDayUseCase
import com.example.adobongkangkong.domain.planner.usecase.RemovePlannedItemUseCase
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
    private val addPlannedFoodItem: AddPlannedFoodItemUseCase,
    private val addPlannedRecipeItem: AddPlannedRecipeBatchItemUseCase,
    private val searchFoods: SearchFoodsUseCase,
    private val removePlannedItem: RemovePlannedItemUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(PlannerDayUiState(date = LocalDate.now()))
    val state: StateFlow<PlannerDayUiState> = _state.asStateFlow()

    private var observeJob: Job? = null
    private var searchJob: Job? = null

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
                // Route handles these (or no-ops for now)
            }

            is PlannerDayEvent.AddMeal -> openAddSheet(event.slot)

            PlannerDayEvent.DismissAddSheet ->
                _state.update { it.copy(addSheet = null) }

            is PlannerDayEvent.UpdateAddSheetName ->
                _state.update { s ->
                    val sheet = s.addSheet ?: return@update s
                    s.copy(addSheet = sheet.copy(nameOverride = event.value))
                }

            is PlannerDayEvent.UpdateAddSheetCustomLabel ->
                _state.update { s ->
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

            is PlannerDayEvent.SelectSearchResult ->
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

            is PlannerDayEvent.UpdateAddGrams ->
                _state.update { s ->
                    val sh = s.addSheet ?: return@update s
                    s.copy(addSheet = sh.copy(gramsText = event.value, addItemError = null))
                }

            is PlannerDayEvent.UpdateAddServings ->
                _state.update { s ->
                    val sh = s.addSheet ?: return@update s
                    s.copy(addSheet = sh.copy(servingsText = event.value, addItemError = null))
                }

            PlannerDayEvent.ConfirmAddItem -> addSelectedFoodToMeal()

            is PlannerDayEvent.RemovePlannedItem -> removeItem(event.itemId)
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

    private fun removeItem(itemId: Long) {
        if (itemId <= 0) return
        viewModelScope.launch {
            try {
                removePlannedItem(itemId)
            } catch (t: Throwable) {
                _state.update { it.copy(errorMessage = t.message ?: "Failed to remove item") }
            }
        }
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
