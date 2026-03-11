package com.example.adobongkangkong.ui.planner

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.data.local.db.entity.MealSlot
import com.example.adobongkangkong.data.local.db.entity.PlannedItemEntity
import com.example.adobongkangkong.data.local.db.entity.PlannedSeriesEndConditionType
import com.example.adobongkangkong.domain.planner.usecase.AddPlannedFoodItemUseCase
import com.example.adobongkangkong.domain.planner.usecase.AddPlannedRecipeItemUseCase
import com.example.adobongkangkong.domain.planner.usecase.ComputePlannedDayMacroTotalsUseCase
import com.example.adobongkangkong.domain.planner.usecase.CreateIouUseCase
import com.example.adobongkangkong.domain.planner.usecase.CreatePlannedMealFromTemplateUseCase
import com.example.adobongkangkong.domain.planner.usecase.CreatePlannedMealUseCase
import com.example.adobongkangkong.domain.planner.usecase.CreatePlannedSeriesUseCase
import com.example.adobongkangkong.domain.planner.usecase.CreateSeriesAndEnsureHorizonUseCase
import com.example.adobongkangkong.domain.planner.usecase.DeleteIouUseCase
import com.example.adobongkangkong.domain.planner.usecase.DuplicatePlannedMealUseCase
import com.example.adobongkangkong.domain.planner.usecase.EnsurePlannerHorizonUseCase
import com.example.adobongkangkong.domain.planner.usecase.LogPlannedMealUseCase
import com.example.adobongkangkong.domain.planner.usecase.ObservePlannedDayUseCase
import com.example.adobongkangkong.domain.planner.usecase.ObservePlannerSlotLoggedNamesUseCase
import com.example.adobongkangkong.domain.planner.usecase.PromoteMealToSeriesAndEnsureHorizonUseCase
import com.example.adobongkangkong.domain.planner.usecase.RemoveEmptyPlannedMealUseCase
import com.example.adobongkangkong.domain.planner.usecase.RemovePlannedItemForUndoUseCase
import com.example.adobongkangkong.domain.planner.usecase.ResolvePlannedItemToQuickAddCandidateUseCase
import com.example.adobongkangkong.domain.planner.usecase.RestorePlannedItemUseCase
import com.example.adobongkangkong.domain.planner.usecase.SavePlannedMealAsTemplateUseCase
import com.example.adobongkangkong.domain.planner.usecase.UpdateIouUseCase
import com.example.adobongkangkong.domain.usecase.SearchFoodsUseCase
import com.example.adobongkangkong.ui.planner.model.FoodSearchRow
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class PlannerDayViewModel @Inject constructor(
    private val observePlannedDay: ObservePlannedDayUseCase,
    private val computeDayMacros: ComputePlannedDayMacroTotalsUseCase,
    private val createPlannedMeal: CreatePlannedMealUseCase,
    private val removeEmptyPlannedMeal: RemoveEmptyPlannedMealUseCase,
    private val addPlannedFoodItem: AddPlannedFoodItemUseCase,
    private val addPlannedRecipeItem: AddPlannedRecipeItemUseCase,
    private val searchFoods: SearchFoodsUseCase,

    // Undo plumbing (items)
    private val removePlannedItemForUndo: RemovePlannedItemForUndoUseCase,
    private val restorePlannedItem: RestorePlannedItemUseCase,

    private val duplicateMeal: DuplicatePlannedMealUseCase,

    private val ensurePlannerHorizon: EnsurePlannerHorizonUseCase,
    private val createSeriesAndEnsureHorizon: CreateSeriesAndEnsureHorizonUseCase,

    private val promoteMealToSeriesAndEnsureHorizon: PromoteMealToSeriesAndEnsureHorizonUseCase,
    private val logPlannedMeal: LogPlannedMealUseCase,
    private val resolvePlannedItemToQuickAddCandidate: ResolvePlannedItemToQuickAddCandidateUseCase,
    private val observePlannerSlotLoggedNames: ObservePlannerSlotLoggedNamesUseCase,

    // NEW
    private val savePlannedMealAsTemplate: SavePlannedMealAsTemplateUseCase,

    // TEMP (Phase 1)
    private val createPlannedMealFromTemplate: CreatePlannedMealFromTemplateUseCase,

    // IOUs
    private val createPlannerIou: CreateIouUseCase,
    private val updatePlannerIou: UpdateIouUseCase,
    private val deletePlannerIou: DeleteIouUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(PlannerDayUiState(date = LocalDate.now()))
    val state: StateFlow<PlannerDayUiState> = _state.asStateFlow()

    sealed interface PlannerDayUiEvent {
        data class ShowToast(val message: String) : PlannerDayUiEvent

        /** Navigate to the dedicated Planned Meal editor screen. */
        data class NavigateToPlannedMealEditor(val mealId: Long) : PlannerDayUiEvent

        /** Open Quick Add prefilled from an exact planned item. */
        data class NavigateToQuickAddFromPlannedItem(
            val candidate: com.example.adobongkangkong.domain.planner.model.QuickAddPlannedItemCandidate,
            val dateIso: String,
        ) : PlannerDayUiEvent
    }

    private val _events = MutableSharedFlow<PlannerDayUiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<PlannerDayUiEvent> = _events.asSharedFlow()

    private val dateFlow = MutableStateFlow(_state.value.date)
    private var observeJob: Job? = null
    private var observeLoggedNamesJob: Job? = null
    private var searchJob: Job? = null

    // Single-level undo (minimal + functional)
    private var lastUndoId: Long = 0L
    private var lastRemovedSnapshot: PlannedItemEntity? = null

    private var lastEnsuredEnd: LocalDate? = null
    private var ensureJob: Job? = null

    private var debugCreateSeriesJob: Job? = null

    fun setDate(date: LocalDate) {
        if (_state.value.date == date) return

        _state.update { it.copy(date = date, isLoading = true, errorMessage = null) }

        dateFlow.value = date
        ensureHorizonFor(date)
    }

    private fun ensureHorizonFor(date: LocalDate) {
        ensureJob?.cancel()
        ensureJob = viewModelScope.launch {
            try {
                lastEnsuredEnd = ensurePlannerHorizon.execute(
                    anchorDate = date,
                    lastEnsuredEnd = lastEnsuredEnd
                )
            } catch (t: Throwable) {
                Log.w("PlannerHorizon", "Failed ensuring planner horizon: ${t.message}", t)
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

            is PlannerDayEvent.OpenMealPlanner -> {
                openMealPlanner(slot = event.slot)
            }

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
                if (_state.value.undo?.id == event.undoId) {
                    _state.update { it.copy(undo = null) }
                }
            }

            is PlannerDayEvent.DuplicateMeal -> {
                openDuplicateSheet(mealId = event.mealId)
            }

            is PlannerDayEvent.OpenDuplicateSheet -> {
                openDuplicateSheet(mealId = event.mealId)
            }

            PlannerDayEvent.DismissDuplicateSheet -> {
                _state.update { it.copy(duplicateSheet = null) }
            }

            PlannerDayEvent.DuplicateAddToday -> {
                val date = _state.value.date
                _state.update { s ->
                    val sheet = s.duplicateSheet ?: return@update s
                    s.copy(duplicateSheet = sheet.copy(selectedDates = listOf(date), errorMessage = null))
                }
                confirmDuplicateDates()
            }

            PlannerDayEvent.DuplicateAddTomorrow -> {
                val date = _state.value.date.plusDays(1)
                _state.update { s ->
                    val sheet = s.duplicateSheet ?: return@update s
                    s.copy(duplicateSheet = sheet.copy(selectedDates = listOf(date), errorMessage = null))
                }
                confirmDuplicateDates()
            }

            is PlannerDayEvent.DuplicateAddDate -> {
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

            PlannerDayEvent.DebugCreateSampleSeries -> {
                debugCreateSampleSeries()
            }

            is PlannerDayEvent.MakeMealRecurring -> {
                openRecurringEditor(event.mealId)
            }

            PlannerDayEvent.DismissRecurringEditor -> {
                _state.update { it.copy(recurringEditor = null) }
            }

            is PlannerDayEvent.UpdateRecurringFrequency -> {
                _state.update { s ->
                    val ed = s.recurringEditor ?: return@update s
                    val nextRules = when (event.frequency) {
                        RecurrenceFrequencyUi.DAILY -> ed.rules.map { it.copy(isEnabled = true) }
                        RecurrenceFrequencyUi.WEEKLY -> ed.rules.map { rule ->
                            if (rule.weekday == ed.anchorWeekday) rule.copy(isEnabled = true) else rule
                        }
                    }
                    s.copy(recurringEditor = ed.copy(frequency = event.frequency, rules = nextRules, errorMessage = null))
                }
            }

            is PlannerDayEvent.ToggleRecurringWeekday -> {
                _state.update { s ->
                    val ed = s.recurringEditor ?: return@update s
                    if (ed.frequency != RecurrenceFrequencyUi.WEEKLY) return@update s
                    val nextRules = ed.rules.map { rule ->
                        when {
                            rule.weekday == ed.anchorWeekday -> rule.copy(isEnabled = true)
                            rule.weekday == event.weekday -> rule.copy(isEnabled = event.enabled)
                            else -> rule
                        }
                    }
                    s.copy(recurringEditor = ed.copy(rules = nextRules, errorMessage = null))
                }
            }

            is PlannerDayEvent.UpdateRecurringWeekdaySlot -> {
                _state.update { s ->
                    val ed = s.recurringEditor ?: return@update s
                    val nextRules = ed.rules.map { rule ->
                        if (rule.weekday != event.weekday) rule
                        else rule.copy(
                            slot = event.slot,
                            customLabel = if (event.slot == MealSlot.CUSTOM) (rule.customLabel ?: "Custom") else null
                        )
                    }
                    s.copy(recurringEditor = ed.copy(rules = nextRules, errorMessage = null))
                }
            }

            PlannerDayEvent.ConfirmMakeRecurring -> {
                saveRecurringSeries()
            }

            is PlannerDayEvent.LogMeal -> {
                logMeal(event.mealId)
            }

            is PlannerDayEvent.LogPlannedItem -> {
                logPlannedItem(event.itemId)
            }

            is PlannerDayEvent.SaveMealAsTemplate -> {
                saveMealAsTemplate(event.mealId)
            }

            is PlannerDayEvent.CreateMealFromTemplate -> {
                createMealFromTemplate(
                    templateId = event.templateId,
                    overrideSlot = event.overrideSlot
                )
            }

            PlannerDayEvent.OpenCreateIou -> {
                openIouEditorCreate()
            }

            is PlannerDayEvent.OpenEditIou -> {
                openIouEditorEdit(event.iouId)
            }

            PlannerDayEvent.DismissIouEditor -> {
                _state.update { it.copy(iouEditor = null) }
            }

            is PlannerDayEvent.UpdateIouDescription -> {
                _state.update { s ->
                    val ed = s.iouEditor ?: return@update s
                    s.copy(iouEditor = ed.copy(description = event.value, errorMessage = null))
                }
            }

            is PlannerDayEvent.UpdateIouCaloriesText -> {
                _state.update { s ->
                    val ed = s.iouEditor ?: return@update s
                    s.copy(iouEditor = ed.copy(caloriesText = event.value, errorMessage = null))
                }
            }

            is PlannerDayEvent.UpdateIouProteinText -> {
                _state.update { s ->
                    val ed = s.iouEditor ?: return@update s
                    s.copy(iouEditor = ed.copy(proteinText = event.value, errorMessage = null))
                }
            }

            is PlannerDayEvent.UpdateIouCarbsText -> {
                _state.update { s ->
                    val ed = s.iouEditor ?: return@update s
                    s.copy(iouEditor = ed.copy(carbsText = event.value, errorMessage = null))
                }
            }

            is PlannerDayEvent.UpdateIouFatText -> {
                _state.update { s ->
                    val ed = s.iouEditor ?: return@update s
                    s.copy(iouEditor = ed.copy(fatText = event.value, errorMessage = null))
                }
            }

            PlannerDayEvent.SaveIou -> {
                saveIou()
            }

            is PlannerDayEvent.DeleteIou -> {
                deleteIou(event.iouId)
            }

            is PlannerDayEvent.OpenTemplatePicker -> {
                // Navigation-only; handled by Route/NavHost.
            }
        }
    }

    private fun openIouEditorCreate() {
        _state.update {
            it.copy(
                iouEditor = IouEditorState(
                    iouId = null,
                    description = "",
                    caloriesText = "",
                    proteinText = "",
                    carbsText = "",
                    fatText = "",
                    isSaving = false,
                    errorMessage = null
                )
            )
        }
    }

    private fun openIouEditorEdit(iouId: Long) {
        val day = _state.value.day
        val existing = day?.ious?.firstOrNull { it.id == iouId }

        if (existing == null) {
            _events.tryEmit(PlannerDayUiEvent.ShowToast("IOU not found."))
            return
        }

        _state.update {
            it.copy(
                iouEditor = IouEditorState(
                    iouId = existing.id,
                    description = existing.description,
                    caloriesText = existing.estimatedCaloriesKcal.toEditableNumberText(),
                    proteinText = existing.estimatedProteinG.toEditableNumberText(),
                    carbsText = existing.estimatedCarbsG.toEditableNumberText(),
                    fatText = existing.estimatedFatG.toEditableNumberText(),
                    isSaving = false,
                    errorMessage = null
                )
            )
        }
    }

    private fun saveIou() {
        val snapshot = _state.value.iouEditor ?: return
        val desc = snapshot.description.trim()
        if (desc.isBlank()) {
            _state.update { s ->
                val ed = s.iouEditor ?: return@update s
                s.copy(iouEditor = ed.copy(errorMessage = "Description is required."))
            }
            return
        }

        val estimatedCaloriesResult = parseOptionalNonNegativeDouble(snapshot.caloriesText, "Calories")
        val estimatedProteinResult = parseOptionalNonNegativeDouble(snapshot.proteinText, "Protein")
        val estimatedCarbsResult = parseOptionalNonNegativeDouble(snapshot.carbsText, "Carbs")
        val estimatedFatResult = parseOptionalNonNegativeDouble(snapshot.fatText, "Fat")

        if (!estimatedCaloriesResult.isValid || !estimatedProteinResult.isValid || !estimatedCarbsResult.isValid || !estimatedFatResult.isValid) {
            return
        }

        val estimatedCaloriesKcal = estimatedCaloriesResult.value
        val estimatedProteinG = estimatedProteinResult.value
        val estimatedCarbsG = estimatedCarbsResult.value
        val estimatedFatG = estimatedFatResult.value

        _state.update { s ->
            val ed = s.iouEditor ?: return@update s
            s.copy(iouEditor = ed.copy(isSaving = true, errorMessage = null))
        }

        val dateIso = _state.value.date.toString()
        viewModelScope.launch {
            try {
                val iouId = snapshot.iouId
                if (iouId == null) {
                    createPlannerIou(
                        dateIso = dateIso,
                        description = desc,
                        estimatedCaloriesKcal = estimatedCaloriesKcal,
                        estimatedProteinG = estimatedProteinG,
                        estimatedCarbsG = estimatedCarbsG,
                        estimatedFatG = estimatedFatG
                    )
                } else {
                    updatePlannerIou(
                        iouId = iouId,
                        newDescription = desc,
                        estimatedCaloriesKcal = estimatedCaloriesKcal,
                        estimatedProteinG = estimatedProteinG,
                        estimatedCarbsG = estimatedCarbsG,
                        estimatedFatG = estimatedFatG
                    )
                }

                _state.update { it.copy(iouEditor = null) }
            } catch (t: Throwable) {
                _state.update { s ->
                    val ed = s.iouEditor
                    if (ed == null) s
                    else s.copy(iouEditor = ed.copy(isSaving = false, errorMessage = t.message ?: "Failed to save IOU"))
                }
            }
        }
    }

    private fun parseOptionalNonNegativeDouble(raw: String, label: String): OptionalDoubleParseResult {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return OptionalDoubleParseResult(isValid = true, value = null)

        val parsed = trimmed.toDoubleOrNull()
        if (parsed == null) {
            _state.update { s ->
                val ed = s.iouEditor ?: return@update s
                s.copy(iouEditor = ed.copy(errorMessage = "$label must be a valid number."))
            }
            return OptionalDoubleParseResult(isValid = false, value = null)
        }

        if (parsed < 0.0) {
            _state.update { s ->
                val ed = s.iouEditor ?: return@update s
                s.copy(iouEditor = ed.copy(errorMessage = "$label cannot be negative."))
            }
            return OptionalDoubleParseResult(isValid = false, value = null)
        }

        return OptionalDoubleParseResult(isValid = true, value = parsed)
    }

    private data class OptionalDoubleParseResult(
        val isValid: Boolean,
        val value: Double?
    )

    private fun Double?.toEditableNumberText(): String {
        val value = this ?: return ""
        return if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
    }

    private fun deleteIou(iouId: Long) {
        if (iouId <= 0L) return

        viewModelScope.launch {
            try {
                deletePlannerIou(iouId)
            } catch (t: Throwable) {
                _events.tryEmit(PlannerDayUiEvent.ShowToast(t.message ?: "Failed to delete IOU"))
            }
        }
    }

    private fun createMealFromTemplate(
        templateId: Long,
        overrideSlot: MealSlot?
    ) {
        if (templateId <= 0L) return

        viewModelScope.launch {
            try {
                val dateIso = _state.value.date.toString()
                val mealId = createPlannedMealFromTemplate(
                    templateId = templateId,
                    dateIso = dateIso,
                    overrideSlot = overrideSlot,
                    nameOverride = null
                )
                _events.tryEmit(PlannerDayUiEvent.NavigateToPlannedMealEditor(mealId))
            } catch (t: Throwable) {
                _events.tryEmit(PlannerDayUiEvent.ShowToast(t.message ?: "Failed to create meal from template."))
            }
        }
    }

    private fun saveMealAsTemplate(mealId: Long) {
        if (mealId <= 0L) return

        viewModelScope.launch {
            try {
                savePlannedMealAsTemplate(plannedMealId = mealId)
                _events.tryEmit(PlannerDayUiEvent.ShowToast("Saved as template."))
            } catch (t: Throwable) {
                _events.tryEmit(PlannerDayUiEvent.ShowToast(t.message ?: "Failed to save template."))
            }
        }
    }

    private fun logMeal(mealId: Long) {
        if (mealId <= 0L) return

        val day = _state.value.day
        val slot: MealSlot? = day?.mealsBySlot
            ?.values
            ?.asSequence()
            ?.flatten()
            ?.firstOrNull { it.id == mealId }
            ?.slot

        viewModelScope.launch {
            val logDateIso = _state.value.date.toString()
            try {
                val result = logPlannedMeal.execute(
                    mealId = mealId,
                    timestamp = Instant.now(),
                    mealSlot = slot,
                    logDateIso = logDateIso
                )

                val alreadyLogged = result.loggedCount == 0 &&
                        result.blockedCount == 0 &&
                        result.errorCount == 1 &&
                        result.outcomes.size == 1 &&
                        (result.outcomes.firstOrNull()?.message?.contains("already been logged", ignoreCase = true) == true)

                if (alreadyLogged) {
                    _events.tryEmit(PlannerDayUiEvent.ShowToast("Already logged."))
                    return@launch
                }

                val msg = buildString {
                    append("Logged ${result.loggedCount}")
                    if (result.blockedCount > 0) append(" • Blocked ${result.blockedCount}")
                    if (result.errorCount > 0) append(" • Errors ${result.errorCount}")
                }

                _events.tryEmit(PlannerDayUiEvent.ShowToast(msg))
            } catch (t: Throwable) {
                _state.update { it.copy(errorMessage = t.message ?: "Failed to log meal") }
            }
        }
    }

    private fun logPlannedItem(itemId: Long) {
        if (itemId <= 0L) return

        val day = _state.value.day
        val mealWithItem = day?.mealsBySlot
            ?.values
            ?.asSequence()
            ?.flatten()
            ?.firstOrNull { meal -> meal.items.any { it.id == itemId } }

        val meal = mealWithItem ?: run {
            _events.tryEmit(PlannerDayUiEvent.ShowToast("Planned item not found."))
            return
        }

        val item = meal.items.firstOrNull { it.id == itemId } ?: run {
            _events.tryEmit(PlannerDayUiEvent.ShowToast("Planned item not found."))
            return
        }

        viewModelScope.launch {
            try {
                val candidate = resolvePlannedItemToQuickAddCandidate(
                    item = item,
                    slot = meal.slot,
                )

                if (candidate == null) {
                    _events.tryEmit(PlannerDayUiEvent.ShowToast("Unable to prepare Quick Add for this planned item."))
                    return@launch
                }

                _events.tryEmit(
                    PlannerDayUiEvent.NavigateToQuickAddFromPlannedItem(
                        candidate = candidate,
                        dateIso = _state.value.date.toString(),
                    )
                )
            } catch (t: Throwable) {
                _events.tryEmit(
                    PlannerDayUiEvent.ShowToast(
                        t.message ?: "Unable to prepare Quick Add for this planned item."
                    )
                )
            }
        }
    }

    private fun openRecurringEditor(mealId: Long) {
        if (mealId <= 0L) return

        val day = _state.value.day
        val meal = day?.mealsBySlot
            ?.values
            ?.asSequence()
            ?.flatten()
            ?.firstOrNull { it.id == mealId }

        if (meal == null) {
            _events.tryEmit(PlannerDayUiEvent.ShowToast("Meal not found."))
            return
        }

        val anchorWeekday = _state.value.date.dayOfWeek.value
        val defaultCustomLabel = if (meal.slot == MealSlot.CUSTOM) "Custom" else null
        val rules = (1..7).map { weekday ->
            RecurringDayRuleUiState(
                weekday = weekday,
                isEnabled = weekday == anchorWeekday,
                slot = meal.slot,
                customLabel = defaultCustomLabel,
            )
        }

        _state.update {
            it.copy(
                recurringEditor = RecurringEditorState(
                    mealId = mealId,
                    anchorWeekday = anchorWeekday,
                    frequency = RecurrenceFrequencyUi.WEEKLY,
                    rules = rules,
                    isSaving = false,
                    errorMessage = null,
                )
            )
        }
    }

    private fun saveRecurringSeries() {
        val snapshot = _state.value.recurringEditor ?: return
        if (snapshot.isSaving) return

        val selectedRules = when (snapshot.frequency) {
            RecurrenceFrequencyUi.DAILY -> snapshot.rules
            RecurrenceFrequencyUi.WEEKLY -> snapshot.rules.filter { it.isEnabled }
        }

        if (selectedRules.isEmpty()) {
            _state.update { s ->
                val ed = s.recurringEditor ?: return@update s
                s.copy(recurringEditor = ed.copy(errorMessage = "Select at least one day."))
            }
            return
        }

        val slotRules = selectedRules.map { rule ->
            CreatePlannedSeriesUseCase.SlotRuleInput(
                weekday = rule.weekday,
                slot = rule.slot,
                customLabel = if (rule.slot == MealSlot.CUSTOM) (rule.customLabel ?: "Custom") else null,
            )
        }

        _state.update { s ->
            val ed = s.recurringEditor ?: return@update s
            s.copy(recurringEditor = ed.copy(isSaving = true, errorMessage = null))
        }

        viewModelScope.launch {
            try {
                promoteMealToSeriesAndEnsureHorizon.execute(
                    mealId = snapshot.mealId,
                    horizonDays = 180,
                    slotRulesOverride = slotRules,
                )
                _state.update { it.copy(recurringEditor = null) }
                _events.tryEmit(PlannerDayUiEvent.ShowToast("Recurring series created."))
            } catch (t: Throwable) {
                _state.update { s ->
                    val ed = s.recurringEditor ?: return@update s
                    s.copy(recurringEditor = ed.copy(isSaving = false, errorMessage = t.message ?: "Failed to make meal recurring"))
                }
            }
        }
    }

    private fun dismissAddSheetWithCleanup() {
        val createdMealId = _state.value.addSheet?.createdMealId

        _state.update { it.copy(addSheet = null) }

        if (createdMealId != null && createdMealId > 0L) {
            viewModelScope.launch {
                try {
                    removeEmptyPlannedMeal(createdMealId)
                } catch (t: Throwable) {
                    Log.w("Meow", "Cleanup empty planned meal failed: ${t.message}", t)
                }
            }
        }
    }

    private fun removeEmptyMeal(mealId: Long) {
        viewModelScope.launch {
            try {
                removeEmptyPlannedMeal(mealId)
            } catch (t: Throwable) {
                _state.update { it.copy(errorMessage = t.message ?: "Failed to remove meal") }
            }
        }
    }

    private fun refreshFoodSearch() {
        searchJob?.cancel()

        val query = _state.value.addSheet?.query?.trim().orEmpty()
        if (query.isBlank()) {
            _state.update { st ->
                val sh = st.addSheet ?: return@update st
                st.copy(addSheet = sh.copy(results = emptyList(), isSearching = false))
            }
            return
        }

        searchJob = viewModelScope.launch {
            try {
                searchFoods(query)
                    .catch { t ->
                        _state.update { st ->
                            val sh = st.addSheet ?: return@update st
                            st.copy(addSheet = sh.copy(results = emptyList(), isSearching = false, addItemError = t.message))
                        }
                    }
                    .collectLatest { rows ->
                        _state.update { st ->
                            val sh = st.addSheet ?: return@update st
                            st.copy(
                                addSheet = sh.copy(
                                    results = rows.map { FoodSearchRow(id = it.id, title = it.name, subtitle = null) },
                                    isSearching = false
                                )
                            )
                        }
                    }
            } catch (t: Throwable) {
                _state.update { st ->
                    val sh = st.addSheet ?: return@update st
                    st.copy(addSheet = sh.copy(results = emptyList(), isSearching = false, addItemError = t.message))
                }
            }
        }
    }

    private fun addSelectedFoodToMeal() {
        val st = _state.value
        val sh = st.addSheet ?: return
        val mealId = sh.createdMealId ?: return

        val selectedId = sh.selectedRefId ?: return
        val mode = sh.addItemMode

        val grams = sh.gramsText.toDoubleOrNull()
        val servings = sh.servingsText.toDoubleOrNull()

        _state.update { s ->
            val sht = s.addSheet ?: return@update s
            s.copy(addSheet = sht.copy(isAddingItem = true, addItemError = null))
        }

        viewModelScope.launch {
            try {
                when (mode) {
                    AddItemMode.FOOD -> {
                        addPlannedFoodItem(
                            mealId = mealId,
                            foodId = selectedId,
                            grams = grams,
                            servings = servings
                        )
                    }

                    AddItemMode.RECIPE -> {
                        val plannedServings = servings
                        require(plannedServings != null && plannedServings > 0.0) {
                            "Servings required for recipe"
                        }

                        addPlannedRecipeItem(
                            mealId = mealId,
                            recipeFoodId = selectedId,
                            plannedServings = plannedServings
                        )
                    }

                    AddItemMode.NONE -> Unit
                }

                _state.update { s ->
                    val sht = s.addSheet ?: return@update s
                    s.copy(
                        addSheet = sht.copy(
                            isAddingItem = false,
                            addItemMode = AddItemMode.NONE,
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

    private fun openMealPlanner(slot: MealSlot) {
        if (slot == MealSlot.CUSTOM) {
            openAddSheet(slot)
            return
        }

        viewModelScope.launch {
            try {
                val dateIso = _state.value.date.toString()
                val mealId = createPlannedMeal(
                    dateIso = dateIso,
                    slot = slot,
                    customLabel = null,
                    nameOverride = null,
                    sortOrder = null
                )

                _events.tryEmit(PlannerDayUiEvent.NavigateToPlannedMealEditor(mealId))
            } catch (t: Throwable) {
                _state.update { it.copy(errorMessage = t.message ?: "Failed to open meal") }
            }
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

        lastRemovedSnapshot = null
        _state.update { s -> if (s.undo != null) s.copy(undo = null) else s }
    }

    init {
        observeJob = viewModelScope.launch {
            dateFlow
                .flatMapLatest { d ->
                    observePlannedDay(d.toString())
                }
                .distinctUntilChanged()
                .catch { t ->
                    t.printStackTrace()
                    Log.e("Meow", "PlannerDay> observePlannedDay failed", t)

                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = (t.message ?: t::class.simpleName ?: "Failed to load plan")
                        )
                    }
                }
                .collectLatest { plannedDay ->
                    val meals = plannedDay.mealsBySlot.values.flatten()
                    val macros = try {
                        computeDayMacros(meals)
                    } catch (t: Throwable) {
                        Log.e("Meow", "PlannerDay> computeDayMacros failed", t)
                        ComputePlannedDayMacroTotalsUseCase.Output(
                            mealTotals = emptyMap(),
                            dayTotals = com.example.adobongkangkong.domain.model.MacroTotals()
                        )
                    }

                    _state.update {
                        it.copy(
                            isLoading = false,
                            day = plannedDay,
                            errorMessage = null,
                            mealMacroTotals = macros.mealTotals,
                            dayMacroTotals = macros.dayTotals
                        )
                    }
                }
        }

        observeLoggedNamesJob = viewModelScope.launch {
            dateFlow
                .flatMapLatest { d ->
                    observePlannerSlotLoggedNames(d.toString())
                }
                .distinctUntilChanged()
                .catch { t ->
                    Log.e("Meow", "PlannerDay> observePlannerSlotLoggedNames failed", t)
                    _state.update { it.copy(loggedNamesBySlot = emptyMap()) }
                }
                .collectLatest { loggedNamesBySlot ->
                    _state.update { it.copy(loggedNamesBySlot = loggedNamesBySlot) }
                }
        }

        ensureHorizonFor(_state.value.date)
    }

    private fun debugCreateSampleSeries() {
        if (debugCreateSeriesJob?.isActive == true) return

        val anchor = _state.value.date

        debugCreateSeriesJob = viewModelScope.launch {
            try {
                val input = CreatePlannedSeriesUseCase.Input(
                    effectiveStartDateIso = anchor.toString(),
                    effectiveEndDateIso = null,
                    endConditionType = PlannedSeriesEndConditionType.INDEFINITE,
                    endConditionValue = null,
                    slotRules = listOf(
                        CreatePlannedSeriesUseCase.SlotRuleInput(weekday = 1, slot = MealSlot.LUNCH),
                        CreatePlannedSeriesUseCase.SlotRuleInput(weekday = 2, slot = MealSlot.DINNER),
                        CreatePlannedSeriesUseCase.SlotRuleInput(weekday = 5, slot = MealSlot.BREAKFAST),
                    ),
                    sourceMealId = 0L,
                    nameOverride = "test"
                )

                val seriesId = createSeriesAndEnsureHorizon.execute(
                    input = input,
                    anchorDate = anchor,
                    horizonDays = 180
                )

                Log.d("PlannerDebug", "Created sample seriesId=$seriesId and expanded occurrences.")
            } catch (t: Throwable) {
                _state.update { it.copy(errorMessage = t.message ?: "Failed to create sample series") }
            }
        }
    }
}