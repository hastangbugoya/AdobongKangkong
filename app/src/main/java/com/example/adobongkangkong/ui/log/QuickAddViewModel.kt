package com.example.adobongkangkong.ui.log

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.data.local.db.dao.FoodGoalFlagsDao
import com.example.adobongkangkong.data.local.db.dao.RecipeBatchDao
import com.example.adobongkangkong.data.local.db.dao.RecipeDao
import com.example.adobongkangkong.data.local.db.entity.MealSlot
import com.example.adobongkangkong.domain.logging.CreateLogEntryUseCase
import com.example.adobongkangkong.domain.logging.UpdateLogEntryUseCase
import com.example.adobongkangkong.domain.logging.model.AmountInput
import com.example.adobongkangkong.domain.logging.model.BatchSummary
import com.example.adobongkangkong.domain.logging.model.FoodRef
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.model.LogEntry
import com.example.adobongkangkong.domain.model.LogUnit
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.nutrition.gramsPerServingUnitResolved
import com.example.adobongkangkong.domain.planner.model.QuickAddPlannedItemCandidate
import com.example.adobongkangkong.domain.planner.usecase.CreateIouUseCase
import com.example.adobongkangkong.domain.planner.usecase.ObserveTodayPlannedItemsForQuickAddUseCase
import com.example.adobongkangkong.domain.recipes.CreateSnapshotFoodFromRecipeUseCase
import com.example.adobongkangkong.domain.repository.FoodBarcodeRepository
import com.example.adobongkangkong.domain.repository.FoodNutritionSnapshotRepository
import com.example.adobongkangkong.domain.repository.FoodRepository
import com.example.adobongkangkong.domain.repository.LogRepository
import com.example.adobongkangkong.domain.usage.FoodValidationResult
import com.example.adobongkangkong.domain.usage.UsageContext
import com.example.adobongkangkong.domain.usage.ValidateFoodForUsageUseCase
import com.example.adobongkangkong.domain.usecase.SearchFoodsUseCase
import com.example.adobongkangkong.ui.food.FoodListItemUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Quick Add view model for both creating and editing log entries.
 *
 * Editing rules:
 * - Quick Add is the single UI for create and edit.
 * - In edit mode, food / recipe identity is locked.
 * - Users may change amount and meal slot only.
 * - If the wrong item was logged, the correct action is delete + recreate, not retargeting.
 * - When current nutrition differs materially from the stored snapshot, the user must choose:
 *   - use current nutrition, or
 *   - keep original nutrition and scale the stored snapshot.
 */
@HiltViewModel
class QuickAddViewModel @Inject constructor(
    private val searchFoods: SearchFoodsUseCase,
    private val createLogEntry: CreateLogEntryUseCase,
    private val updateLogEntry: UpdateLogEntryUseCase,
    private val recipeDao: RecipeDao,
    private val recipeBatchDao: RecipeBatchDao,
    private val createBatchFoodFromRecipeUseCase: CreateSnapshotFoodFromRecipeUseCase,
    private val foodGoalFlagsDao: FoodGoalFlagsDao,
    private val foodRepository: FoodRepository,
    private val logRepository: LogRepository,

    // validation + snapshot access for preflight gating
    private val snapshotRepo: FoodNutritionSnapshotRepository,
    private val validateFoodForUsage: ValidateFoodForUsageUseCase,
    private val foodBarcodeRepository: FoodBarcodeRepository,

    // IOUs (planner narrative placeholders)
    private val createPlannerIou: CreateIouUseCase,

    // From Day Planner
    private val observeTodayPlannedItemsForQuickAddUseCase: ObserveTodayPlannedItemsForQuickAddUseCase,
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")
    private val selectedFoodFlow = MutableStateFlow<Food?>(null)

    private val modeFlow = MutableStateFlow(QuickAddMode.CREATE)
    private val editingLogIdFlow = MutableStateFlow<Long?>(null)
    private val isIdentityLockedFlow = MutableStateFlow(false)

    // canonical amount
    private val servingsFlow = MutableStateFlow(1.0)
    private val inputModeFlow = MutableStateFlow(InputMode.SERVINGS)

    // user input (Amount + Unit) used by QuickAdd
    private val inputUnitFlow = MutableStateFlow(ServingUnit.G)
    private val inputAmountFlow = MutableStateFlow<Double?>(null)

    // recipe context
    private val selectedRecipeIdFlow = MutableStateFlow<Long?>(null)
    private val selectedRecipeStableIdFlow = MutableStateFlow<String?>(null)
    private val selectedRecipeServingsYieldDefaultFlow = MutableStateFlow<Double?>(null)
    private val selectedBatchIdFlow = MutableStateFlow<Long?>(null)

    // create-batch dialog
    private val yieldGramsTextFlow = MutableStateFlow("")
    private val servingsYieldTextFlow = MutableStateFlow("")
    private val isCreateBatchDialogOpenFlow = MutableStateFlow(false)

    // Optional categorization for Day Log grouping (not required to log)
    private val mealSlotFlow = MutableStateFlow<MealSlot?>(null)

    private val isSavingFlow = MutableStateFlow(false)
    private val errorFlow = MutableStateFlow<String?>(null)

    private val isResolveMassDialogOpenFlow = MutableStateFlow(false)
    private val gramsPerServingTextFlow = MutableStateFlow("")

    private val isNutritionChoiceDialogOpenFlow = MutableStateFlow(false)
    private val nutritionChoiceMessageFlow = MutableStateFlow<String?>(null)

    private var pendingNutritionChoice: PendingNutritionChoice? = null

    // -----------------------------
    // From today's plan picker
    // -----------------------------

    private val isTodayPlanPickerOpenFlow = MutableStateFlow(false)
    private val todayPlanSectionsFlow =
        MutableStateFlow<Map<MealSlot, List<QuickAddPlannedItemCandidate>>>(emptyMap())
    private val isTodayPlanLoadingFlow = MutableStateFlow(false)
    private var todayPlanJob: Job? = null

    private data class PendingResolveMass(
        val food: Food,
        val timestamp: Instant,
        val amountInput: AmountInput,
        val logDateIso: String
    )

    private data class PendingNutritionChoice(
        val logId: Long,
        val amountInput: AmountInput,
        val mealSlot: MealSlot?
    )

    private var pendingResolveMass: PendingResolveMass? = null

    private val resultsFlow =
        queryFlow
            .debounce(150)
            .distinctUntilChanged()
            .flatMapLatest { q ->
                if (q.isBlank()) flowOf(emptyList())
                else searchFoods(q, limit = 50)
            }

    private val flagsByFoodIdFlow =
        foodGoalFlagsDao
            .observeAll()
            .map { list -> list.associateBy { it.foodId } }

    private val resultsUiFlow =
        combine(resultsFlow, flagsByFoodIdFlow) { foods, flagsById ->
            foods.map { food ->
                FoodListItemUiModel(
                    food = food,
                    goalFlags = flagsById[food.id]
                )
            }
        }

    private val batchesFlow =
        selectedRecipeIdFlow.flatMapLatest { recipeId ->
            if (recipeId == null) {
                flowOf(emptyList())
            } else {
                recipeBatchDao.observeBatchesForRecipe(recipeId).map { list ->
                    list.map {
                        BatchSummary(
                            batchId = it.id,
                            recipeId = it.recipeId,
                            cookedYieldGrams = it.cookedYieldGrams,
                            servingsYieldUsed = it.servingsYieldUsed,
                            createdAt = it.createdAt
                        )
                    }
                }
            }
        }

    // -----------------------------
    // Barcode scan (QuickLog only)
    // -----------------------------

    private val isScannerOpenFlow = MutableStateFlow(false)

    private data class FoundBarcodeDialogState(
        val barcode: String,
        val food: Food
    )

    private data class NotFoundBarcodeDialogState(
        val barcode: String
    )

    private val foundBarcodeDialogFlow = MutableStateFlow<FoundBarcodeDialogState?>(null)
    private val notFoundBarcodeDialogFlow = MutableStateFlow<NotFoundBarcodeDialogState?>(null)

    // -----------------------------
    // IOU (planner narrative)
    // -----------------------------

    private val isIouDialogOpenFlow = MutableStateFlow(false)
    private val iouDescriptionFlow = MutableStateFlow("")
    private val iouCaloriesTextFlow = MutableStateFlow("")
    private val iouProteinTextFlow = MutableStateFlow("")
    private val iouCarbsTextFlow = MutableStateFlow("")
    private val iouFatTextFlow = MutableStateFlow("")
    private val isSavingIouFlow = MutableStateFlow(false)
    private val iouErrorFlow = MutableStateFlow<String?>(null)

    fun startCreate() {
        modeFlow.value = QuickAddMode.CREATE
        editingLogIdFlow.value = null
        isIdentityLockedFlow.value = false
        pendingNutritionChoice = null
        isNutritionChoiceDialogOpenFlow.value = false
        nutritionChoiceMessageFlow.value = null
        clearSelection()
        queryFlow.value = ""
    }

    fun startEdit(logId: Long) {
        if (modeFlow.value == QuickAddMode.EDIT &&
            editingLogIdFlow.value == logId &&
            selectedFoodFlow.value != null
        ) {
            return
        }

        viewModelScope.launch {
            modeFlow.value = QuickAddMode.EDIT
            editingLogIdFlow.value = logId
            isIdentityLockedFlow.value = true
            isSavingFlow.value = true
            errorFlow.value = null
            pendingNutritionChoice = null
            isNutritionChoiceDialogOpenFlow.value = false
            nutritionChoiceMessageFlow.value = null
            isTodayPlanPickerOpenFlow.value = false

            try {
                val entry = logRepository.getById(logId)
                if (entry == null) {
                    errorFlow.value = "Log entry not found."
                    return@launch
                }

                val stableId = entry.foodStableId
                if (stableId.isNullOrBlank()) {
                    errorFlow.value = "Log entry is missing food identity."
                    return@launch
                }

                val food = foodRepository.getByStableId(stableId)
                if (food == null) {
                    errorFlow.value = "Logged food no longer exists."
                    return@launch
                }

                selectedFoodFlow.value = food
                mealSlotFlow.value = entry.mealSlot
                errorFlow.value = null

                applyRecipeContextForFood(
                    food = food,
                    selectedBatchIdOverride = entry.recipeBatchId
                )

                restoreAmountForEdit(
                    food = food,
                    entry = entry
                )
            } finally {
                isSavingFlow.value = false
            }
        }
    }

    private fun restoreAmountForEdit(
        food: Food,
        entry: LogEntry
    ) {
        when (entry.unit) {
            LogUnit.GRAM_COOKED -> {
                onGramsChanged(entry.amount)
            }

            LogUnit.SERVING,
            LogUnit.ITEM -> {
                onServingsChanged(entry.amount)
                inputUnitFlow.value = food.servingUnit
                inputAmountFlow.value = entry.amount * food.servingSize
            }
        }
    }

    fun dismissNutritionChoiceDialog() {
        isNutritionChoiceDialogOpenFlow.value = false
        nutritionChoiceMessageFlow.value = null
        pendingNutritionChoice = null
    }

    fun confirmUseCurrentNutrition(
        onDone: () -> Unit,
    ) {
        val pending = pendingNutritionChoice ?: return
        executeEditSave(
            logId = pending.logId,
            amountInput = pending.amountInput,
            mealSlot = pending.mealSlot,
            decision = UpdateLogEntryUseCase.NutritionDecision.USE_CURRENT,
            onDone = onDone,
//            logDate = logDate
        )
    }

    fun confirmKeepOriginalNutrition(
        onDone: () -> Unit,
    ) {
        val pending = pendingNutritionChoice ?: return
        executeEditSave(
            logId = pending.logId,
            amountInput = pending.amountInput,
            mealSlot = pending.mealSlot,
            decision = UpdateLogEntryUseCase.NutritionDecision.KEEP_ORIGINAL,
            onDone = onDone,
//            logDate = logDate
        )
    }

    fun openIouDialog() {
        iouDescriptionFlow.value = ""
        iouCaloriesTextFlow.value = ""
        iouProteinTextFlow.value = ""
        iouCarbsTextFlow.value = ""
        iouFatTextFlow.value = ""
        iouErrorFlow.value = null
        isIouDialogOpenFlow.value = true
    }

    fun closeIouDialog() {
        isIouDialogOpenFlow.value = false
        iouErrorFlow.value = null
    }

    fun onIouDescriptionChanged(value: String) {
        iouDescriptionFlow.value = value
        iouErrorFlow.value = null
    }

    fun onIouCaloriesChanged(value: String) {
        iouCaloriesTextFlow.value = value
        iouErrorFlow.value = null
    }

    fun onIouProteinChanged(value: String) {
        iouProteinTextFlow.value = value
        iouErrorFlow.value = null
    }

    fun onIouCarbsChanged(value: String) {
        iouCarbsTextFlow.value = value
        iouErrorFlow.value = null
    }

    fun onIouFatChanged(value: String) {
        iouFatTextFlow.value = value
        iouErrorFlow.value = null
    }

    private fun parseOptionalNonNegativeDouble(raw: String): Double? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val value = trimmed.toDoubleOrNull() ?: return Double.NaN
        return if (value >= 0.0) value else Double.NaN
    }

    fun saveIou(logDate: LocalDate, onSaved: () -> Unit) {
        if (isSavingIouFlow.value) return

        val desc = iouDescriptionFlow.value.trim()
        if (desc.isBlank()) {
            iouErrorFlow.value = "Description is required."
            return
        }

        val estimatedCaloriesKcal = parseOptionalNonNegativeDouble(iouCaloriesTextFlow.value)
        val estimatedProteinG = parseOptionalNonNegativeDouble(iouProteinTextFlow.value)
        val estimatedCarbsG = parseOptionalNonNegativeDouble(iouCarbsTextFlow.value)
        val estimatedFatG = parseOptionalNonNegativeDouble(iouFatTextFlow.value)

        if (listOf(
                estimatedCaloriesKcal,
                estimatedProteinG,
                estimatedCarbsG,
                estimatedFatG
            ).any { it?.isNaN() == true }
        ) {
            iouErrorFlow.value = "Macro estimates must be valid non-negative numbers."
            return
        }

        isSavingIouFlow.value = true
        iouErrorFlow.value = null

        viewModelScope.launch {
            try {
                createPlannerIou(
                    dateIso = logDate.toString(),
                    description = desc,
                    estimatedCaloriesKcal = estimatedCaloriesKcal,
                    estimatedProteinG = estimatedProteinG,
                    estimatedCarbsG = estimatedCarbsG,
                    estimatedFatG = estimatedFatG
                )
                isSavingIouFlow.value = false
                isIouDialogOpenFlow.value = false
                onSaved()
            } catch (t: Throwable) {
                isSavingIouFlow.value = false
                iouErrorFlow.value = t.message ?: "Failed to save IOU"
            }
        }
    }

    fun openBarcodeScanner() {
        if (modeFlow.value == QuickAddMode.EDIT) return
        isScannerOpenFlow.value = true
    }

    fun closeBarcodeScanner() {
        isScannerOpenFlow.value = false
    }

    fun dismissFoundBarcodeDialog() {
        foundBarcodeDialogFlow.value = null
    }

    fun dismissNotFoundBarcodeDialog() {
        notFoundBarcodeDialogFlow.value = null
    }

    private fun normalizeBarcode(raw: String): String {
        return raw.trim().filter { it.isDigit() }
    }

    fun onBarcodeScanned(barcode: String) {
        if (modeFlow.value == QuickAddMode.EDIT) return

        val code = normalizeBarcode(barcode)
        Log.d("Meow", "QuickAddViewModel > barcode scanned raw='$barcode' normalized='$code'")

        if (code.isBlank()) return

        isScannerOpenFlow.value = false

        viewModelScope.launch {
            val nowMs = System.currentTimeMillis()

            Log.d("Meow", "QuickAddViewModel > barcode lookup START barcode='$code'")
            val foodId = foodBarcodeRepository.getFoodIdForBarcode(code)
            Log.d("Meow", "QuickAddViewModel > barcode lookup RESULT barcode='$code' foodId=$foodId")

            if (foodId == null) {
                notFoundBarcodeDialogFlow.value = NotFoundBarcodeDialogState(code)
                return@launch
            }

            foodBarcodeRepository.touchLastSeen(code, nowMs)

            val food = foodRepository.getById(foodId)

            Log.d(
                "Meow",
                "QuickAddViewModel > barcode food load barcode='$code' foodId=$foodId foodFound=${food != null}"
            )

            if (food != null) {
                foundBarcodeDialogFlow.value = FoundBarcodeDialogState(code, food)
            } else {
                notFoundBarcodeDialogFlow.value = NotFoundBarcodeDialogState(code)
            }
        }
    }

    fun confirmFoundBarcodeLogByServings() {
        val found = foundBarcodeDialogFlow.value ?: return
        dismissFoundBarcodeDialog()
        onFoodSelected(found.food)
        onInputModeChanged(InputMode.SERVINGS)
    }

    private fun gramsPerServingResolved(food: Food): Double? {
        val gPerUnit = food.gramsPerServingUnitResolved() ?: return null
        if (gPerUnit <= 0.0) return null
        if (food.servingSize <= 0.0) return null
        return food.servingSize * gPerUnit
    }

    fun confirmFoundBarcodeLogByGrams() {
        val found = foundBarcodeDialogFlow.value ?: return
        dismissFoundBarcodeDialog()
        onFoodSelected(found.food)
        onGramsChanged(gramsPerServingResolved(found.food) ?: 0.0)
    }

    fun confirmNotFoundBarcodeAddFood() {
        dismissNotFoundBarcodeDialog()
    }

    sealed class QuickAddNavEvent {
        data class Navigate(val route: String) : QuickAddNavEvent()
    }

    private val navEventFlow = MutableStateFlow<QuickAddNavEvent?>(null)
    val navEvent: StateFlow<QuickAddNavEvent?> = navEventFlow.asStateFlow()

    fun consumeNavEvent() {
        navEventFlow.value = null
    }

    private data class CoreInputs(
        val query: String,
        val results: List<FoodListItemUiModel>,
        val selected: Food?,
        val servings: Double,
        val mealSlot: MealSlot?,
        val inputMode: InputMode,
        val inputUnit: ServingUnit,
        val inputAmount: Double?,
        val mode: QuickAddMode,
        val editingLogId: Long?,
        val isIdentityLocked: Boolean
    )

    private data class RecipeInputs(
        val batches: List<BatchSummary>,
        val selectedBatchId: Long?,
        val yieldGramsText: String,
        val servingsYieldText: String
    )

    private data class UiFlags(
        val isDialogOpen: Boolean,
        val isSaving: Boolean,
        val error: String?,
        val isResolveMassDialogOpen: Boolean,
        val gramsPerServingText: String,
        val isNutritionChoiceDialogOpen: Boolean,
        val nutritionChoiceMessage: String?,
    )

    private data class BarcodeUi(
        val isScannerOpen: Boolean,
        val found: FoundBarcodeDialogState?,
        val notFound: NotFoundBarcodeDialogState?
    )

    private data class IouUi(
        val isOpen: Boolean,
        val description: String,
        val caloriesText: String,
        val proteinText: String,
        val carbsText: String,
        val fatText: String,
        val isSaving: Boolean,
        val error: String?
    )

    private data class TodayPlanUi(
        val isOpen: Boolean,
        val sections: Map<MealSlot, List<QuickAddPlannedItemCandidate>>,
        val isLoading: Boolean
    )

    val state: StateFlow<QuickAddState> = run {
        data class CoreA(
            val query: String,
            val results: List<FoodListItemUiModel>,
            val selected: Food?,
            val servings: Double,
            val mealSlot: MealSlot?,
            val mode: QuickAddMode,
            val editingLogId: Long?,
            val isIdentityLocked: Boolean
        )

        data class CoreB(
            val inputMode: InputMode,
            val inputUnit: ServingUnit,
            val inputAmount: Double?
        )

        data class CoreALeft(
            val query: String,
            val results: List<FoodListItemUiModel>,
            val selected: Food?,
            val servings: Double
        )

        data class CoreARight(
            val mealSlot: MealSlot?,
            val mode: QuickAddMode,
            val editingLogId: Long?,
            val isIdentityLocked: Boolean
        )

        val coreAFlow = combine(
            combine(
                queryFlow,
                resultsUiFlow,
                selectedFoodFlow,
                servingsFlow
            ) { query, results, selected, servings ->
                CoreALeft(
                    query = query,
                    results = results,
                    selected = selected,
                    servings = servings
                )
            },
            combine(
                mealSlotFlow,
                modeFlow,
                editingLogIdFlow,
                isIdentityLockedFlow
            ) { mealSlot, mode, editingLogId, isIdentityLocked ->
                CoreARight(
                    mealSlot = mealSlot,
                    mode = mode,
                    editingLogId = editingLogId,
                    isIdentityLocked = isIdentityLocked
                )
            }
        ) { left, right ->
            CoreA(
                query = left.query,
                results = left.results,
                selected = left.selected,
                servings = left.servings,
                mealSlot = right.mealSlot,
                mode = right.mode,
                editingLogId = right.editingLogId,
                isIdentityLocked = right.isIdentityLocked
            )
        }

        val coreBFlow = combine(
            inputModeFlow,
            inputUnitFlow,
            inputAmountFlow
        ) { inputMode, inputUnit, inputAmount ->
            CoreB(inputMode, inputUnit, inputAmount)
        }

        val coreFlow = combine(coreAFlow, coreBFlow) { a, b ->
            CoreInputs(
                query = a.query,
                results = a.results,
                selected = a.selected,
                servings = a.servings,
                mealSlot = a.mealSlot,
                inputMode = b.inputMode,
                inputUnit = b.inputUnit,
                inputAmount = b.inputAmount,
                mode = a.mode,
                editingLogId = a.editingLogId,
                isIdentityLocked = a.isIdentityLocked
            )
        }

        val recipeFlow = combine(
            batchesFlow,
            selectedBatchIdFlow,
            yieldGramsTextFlow,
            servingsYieldTextFlow
        ) { batches, selectedBatchId, yieldGramsText, servingsYieldText ->
            RecipeInputs(
                batches = batches,
                selectedBatchId = selectedBatchId,
                yieldGramsText = yieldGramsText,
                servingsYieldText = servingsYieldText
            )
        }

        val flagsFlow = combine(
            combine(
                isCreateBatchDialogOpenFlow,
                isSavingFlow,
                errorFlow,
                isResolveMassDialogOpenFlow
            ) { isDialogOpen, isSaving, error, isResolveMassDialogOpen ->
                arrayOf(isDialogOpen, isSaving, error, isResolveMassDialogOpen)
            },
            combine(
                gramsPerServingTextFlow,
                isNutritionChoiceDialogOpenFlow,
                nutritionChoiceMessageFlow
            ) { gramsPerServingText, isNutritionChoiceDialogOpen, nutritionChoiceMessage ->
                arrayOf(gramsPerServingText, isNutritionChoiceDialogOpen, nutritionChoiceMessage)
            }
        ) { left, right ->
            UiFlags(
                isDialogOpen = left[0] as Boolean,
                isSaving = left[1] as Boolean,
                error = left[2] as String?,
                isResolveMassDialogOpen = left[3] as Boolean,
                gramsPerServingText = right[0] as String,
                isNutritionChoiceDialogOpen = right[1] as Boolean,
                nutritionChoiceMessage = right[2] as String?
            )
        }

        val barcodeFlow = combine(
            isScannerOpenFlow,
            foundBarcodeDialogFlow,
            notFoundBarcodeDialogFlow
        ) { isScannerOpen, found, notFound ->
            BarcodeUi(
                isScannerOpen = isScannerOpen,
                found = found,
                notFound = notFound
            )
        }

        val iouFlow = combine(
            combine(
                isIouDialogOpenFlow,
                iouDescriptionFlow,
                iouCaloriesTextFlow,
                iouProteinTextFlow
            ) { isOpen, description, caloriesText, proteinText ->
                arrayOf(isOpen, description, caloriesText, proteinText)
            },
            combine(
                iouCarbsTextFlow,
                iouFatTextFlow,
                isSavingIouFlow,
                iouErrorFlow
            ) { carbsText, fatText, isSaving, error ->
                arrayOf(carbsText, fatText, isSaving, error)
            }
        ) { left, right ->
            IouUi(
                isOpen = left[0] as Boolean,
                description = left[1] as String,
                caloriesText = left[2] as String,
                proteinText = left[3] as String,
                carbsText = right[0] as String,
                fatText = right[1] as String,
                isSaving = right[2] as Boolean,
                error = right[3] as String?
            )
        }

        val todayPlanFlow = combine(
            isTodayPlanPickerOpenFlow,
            todayPlanSectionsFlow,
            isTodayPlanLoadingFlow
        ) { isOpen, sections, isLoading ->
            TodayPlanUi(
                isOpen = isOpen,
                sections = sections,
                isLoading = isLoading
            )
        }

        val topLevelFlow = combine(
            combine(coreFlow, recipeFlow) { core, recipe -> core to recipe },
            combine(flagsFlow, barcodeFlow) { flags, barcode -> flags to barcode },
            combine(iouFlow, todayPlanFlow) { iou, todayPlan -> iou to todayPlan }
        ) { left, middle, right ->
            Triple(left, middle, right)
        }

        topLevelFlow
            .map { triple ->
                val core = triple.first.first
                val recipe = triple.first.second
                val flags = triple.second.first
                val barcode = triple.second.second
                val iou = triple.third.first
                val todayPlan = triple.third.second

                val gramsAmount: Double? = core.selected?.let { food ->
                    computeGramsAmount(
                        food = food,
                        servings = core.servings,
                        inputMode = core.inputMode,
                        inputUnit = core.inputUnit,
                        inputAmount = core.inputAmount
                    )
                }

                val servingsEquivalent: Double? = core.selected?.let { food ->
                    when (core.inputMode) {
                        InputMode.SERVINGS -> core.servings
                        else -> {
                            val grams = gramsAmount ?: return@let null
                            val gPerServing = gramsPerServingResolved(food) ?: return@let null
                            if (gPerServing <= 0.0) return@let null
                            grams / gPerServing
                        }
                    }
                }

                val servingUnitAmount: Double? = core.selected?.let { food ->
                    val s = servingsEquivalent ?: return@let null
                    s * food.servingSize
                }

                QuickAddState(
                    query = core.query,
                    results = core.results,
                    mode = core.mode,
                    editingLogId = core.editingLogId,
                    isIdentityLocked = core.isIdentityLocked,
                    selectedFood = core.selected,
                    servings = core.servings,
                    servingsEquivalent = servingsEquivalent,
                    inputUnit = core.inputUnit,
                    inputAmount = core.inputAmount,
                    servingUnitAmount = servingUnitAmount,
                    gramsAmount = gramsAmount,
                    inputMode = core.inputMode,

                    batches = recipe.batches,
                    selectedBatchId = recipe.selectedBatchId,
                    mealSlot = core.mealSlot,
                    yieldGramsText = recipe.yieldGramsText,
                    servingsYieldText = recipe.servingsYieldText,

                    isCreateBatchDialogOpen = flags.isDialogOpen,
                    isSaving = flags.isSaving,
                    errorMessage = flags.error,
                    isResolveMassDialogOpen = flags.isResolveMassDialogOpen,
                    gramsPerServingText = flags.gramsPerServingText,
                    isNutritionChoiceDialogOpen = flags.isNutritionChoiceDialogOpen,
                    nutritionChoiceMessage = flags.nutritionChoiceMessage,

                    isScannerOpen = barcode.isScannerOpen,
                    foundBarcodeDialogFood = barcode.found?.food,
                    foundBarcodeDialogBarcode = barcode.found?.barcode,
                    notFoundBarcodeDialogBarcode = barcode.notFound?.barcode,

                    isIouDialogOpen = iou.isOpen,
                    iouDescription = iou.description,
                    iouCaloriesText = iou.caloriesText,
                    iouProteinText = iou.proteinText,
                    iouCarbsText = iou.carbsText,
                    iouFatText = iou.fatText,
                    isSavingIou = iou.isSaving,
                    iouErrorMessage = iou.error,

                    isTodayPlanPickerOpen = todayPlan.isOpen,
                    todayPlanSections = todayPlan.sections,
                    isTodayPlanLoading = todayPlan.isLoading,
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QuickAddState())
    }

    fun onQueryChange(q: String) {
        if (modeFlow.value == QuickAddMode.EDIT) return
        queryFlow.value = q
    }

    fun onFoodSelected(food: Food) {
        if (modeFlow.value == QuickAddMode.EDIT && isIdentityLockedFlow.value) return

        selectedFoodFlow.value = food
        servingsFlow.value = 1.0
        inputModeFlow.value = InputMode.SERVINGS

        Log.d(
            "Meow",
            "QuickAdd init foodId=${food.id} name=${food.name} gramsPerServing(resolved)=${gramsPerServingResolved(food)}"
        )

        inputUnitFlow.value =
            if (food.servingUnit == ServingUnit.SERVING ||
                food.servingUnit.asG != null ||
                food.servingUnit.asMl != null
            ) {
                food.servingUnit
            } else {
                ServingUnit.G
            }

        inputAmountFlow.value = food.servingSize.coerceAtLeast(0.0)

        errorFlow.value = null

        viewModelScope.launch {
            applyRecipeContextForFood(
                food = food,
                selectedBatchIdOverride = null
            )
        }
    }

    private suspend fun applyRecipeContextForFood(
        food: Food,
        selectedBatchIdOverride: Long?
    ) {
        if (!food.isRecipe) {
            selectedRecipeIdFlow.value = null
            selectedRecipeStableIdFlow.value = null
            selectedRecipeServingsYieldDefaultFlow.value = null
            selectedBatchIdFlow.value = null
            return
        }

        val recipe = recipeDao.getByFoodId(food.id)
            ?: recipeDao.getById(food.id)

        if (recipe == null) {
            selectedRecipeIdFlow.value = null
            selectedRecipeStableIdFlow.value = null
            selectedRecipeServingsYieldDefaultFlow.value = null
            selectedBatchIdFlow.value = null
            errorFlow.value = "Recipe data missing for this item."
            return
        }

        selectedRecipeIdFlow.value = recipe.id
        selectedRecipeStableIdFlow.value = recipe.stableId
        selectedRecipeServingsYieldDefaultFlow.value = recipe.servingsYield
        selectedBatchIdFlow.value = selectedBatchIdOverride
    }

    fun clearSelection() {
        selectedFoodFlow.value = null
        servingsFlow.value = 1.0
        inputModeFlow.value = InputMode.SERVINGS
        inputUnitFlow.value = ServingUnit.G
        inputAmountFlow.value = null

        mealSlotFlow.value = null

        selectedRecipeIdFlow.value = null
        selectedRecipeStableIdFlow.value = null
        selectedRecipeServingsYieldDefaultFlow.value = null
        selectedBatchIdFlow.value = null
        isTodayPlanPickerOpenFlow.value = false
        errorFlow.value = null
    }

    fun onMealSlotChanged(slot: MealSlot?) {
        mealSlotFlow.value = slot
    }

    fun onInputModeChanged(mode: InputMode) {
        inputModeFlow.value = mode
    }

    fun onInputUnitChanged(unit: ServingUnit) {
        inputUnitFlow.value = unit

        val food = selectedFoodFlow.value ?: return
        val currentGrams = computeGramsAmount(
            food = food,
            servings = servingsFlow.value,
            inputMode = inputModeFlow.value,
            inputUnit = inputUnitFlow.value,
            inputAmount = inputAmountFlow.value
        )

        if (currentGrams != null) {
            unit.asG?.let { gPerUnit ->
                if (gPerUnit > 0.0) {
                    inputAmountFlow.value = currentGrams / gPerUnit
                    inputModeFlow.value = InputMode.GRAMS
                    return
                }
            }

            unit.asMl?.let { inputMlPerUnit ->
                val grams = currentGrams
                val foodMlPerUnit = food.servingUnit.asMl
                val gPerUnit = food.gramsPerServingUnitResolved()
                if (foodMlPerUnit != null &&
                    gPerUnit != null &&
                    gPerUnit > 0.0
                ) {
                    val density = gPerUnit / foodMlPerUnit
                    if (density > 0.0) {
                        val ml = grams / density
                        inputAmountFlow.value = ml / inputMlPerUnit
                        inputModeFlow.value = InputMode.SERVING_UNIT
                        return
                    }
                }
            }
        }
    }

    fun openTodayPlanPicker() {
        if (modeFlow.value == QuickAddMode.EDIT) return

        isTodayPlanPickerOpenFlow.value = true
        errorFlow.value = null

        if (todayPlanJob != null) return

        isTodayPlanLoadingFlow.value = true

        todayPlanJob = viewModelScope.launch {
            observeTodayPlannedItemsForQuickAddUseCase()
                .collect { sections ->
                    todayPlanSectionsFlow.value = sections
                    isTodayPlanLoadingFlow.value = false
                }
        }
    }

    fun closeTodayPlanPicker() {
        isTodayPlanPickerOpenFlow.value = false
    }

    fun onPlannedItemSelected(candidate: QuickAddPlannedItemCandidate) {
        if (modeFlow.value == QuickAddMode.EDIT) return

        viewModelScope.launch {
            val resolvedFoodId = candidate.foodId ?: candidate.recipeId
            if (resolvedFoodId == null) {
                errorFlow.value = "Planned item is missing a food reference."
                return@launch
            }

            val food = foodRepository.getById(resolvedFoodId)
            if (food == null) {
                errorFlow.value = "Planned item food not found."
                return@launch
            }

            selectedFoodFlow.value = food
            servingsFlow.value = 1.0
            inputModeFlow.value = InputMode.SERVINGS

            inputUnitFlow.value =
                if (food.servingUnit == ServingUnit.SERVING ||
                    food.servingUnit.asG != null ||
                    food.servingUnit.asMl != null
                ) {
                    food.servingUnit
                } else {
                    ServingUnit.G
                }

            inputAmountFlow.value = food.servingSize.coerceAtLeast(0.0)
            errorFlow.value = null

            val batchOverride =
                if (candidate.type == QuickAddPlannedItemCandidate.Type.RECIPE_BATCH) {
                    candidate.batchId
                } else {
                    null
                }

            applyRecipeContextForFood(
                food = food,
                selectedBatchIdOverride = batchOverride
            )

            onMealSlotChanged(candidate.slot)

            candidate.plannedServings?.let { onServingsChanged(it) }
            candidate.plannedGrams?.let { onGramsChanged(it) }

            isTodayPlanPickerOpenFlow.value = false
        }
    }

    fun onInputAmountChanged(amount: Double) {
        val food = selectedFoodFlow.value ?: return
        val unit = inputUnitFlow.value
        val a = amount.coerceAtLeast(0.0)
        inputAmountFlow.value = a

        val grams = computeGramsFromAmountAndUnit(food, a, unit)

        if (grams != null) {
            val gPerServing = gramsPerServingResolved(food)
            if (gPerServing != null && gPerServing > 0.0) {
                servingsFlow.value = (grams / gPerServing).coerceAtLeast(0.0)
            }
            inputModeFlow.value = if (unit.asG != null) InputMode.GRAMS else InputMode.SERVING_UNIT
        } else {
            if (unit == food.servingUnit && food.servingSize > 0.0) {
                servingsFlow.value = (a / food.servingSize).coerceAtLeast(0.0)
                inputModeFlow.value = InputMode.SERVING_UNIT
            }
        }
    }

    fun onServingsChanged(servings: Double) {
        val s = servings.coerceAtLeast(0.0)
        servingsFlow.value = s
        inputModeFlow.value = InputMode.SERVINGS

        selectedFoodFlow.value?.let { food ->
            inputUnitFlow.value = food.servingUnit
            inputAmountFlow.value = s * food.servingSize
        }
    }

    fun onServingUnitAmountChanged(amount: Double) {
        val food = selectedFoodFlow.value ?: return
        inputUnitFlow.value = food.servingUnit
        onInputAmountChanged(amount)
    }

    fun onGramsChanged(grams: Double) {
        val food = selectedFoodFlow.value ?: return
        val gramsClamped = grams.coerceAtLeast(0.0)

        inputUnitFlow.value = ServingUnit.G
        inputAmountFlow.value = gramsClamped
        inputModeFlow.value = InputMode.GRAMS

        val gPerServing = gramsPerServingResolved(food)
        if (gPerServing != null && gPerServing > 0.0) {
            servingsFlow.value = (gramsClamped / gPerServing).coerceAtLeast(0.0)
        }
    }

    fun onPackageClicked(multiplier: Double = 1.0) {
        val food = selectedFoodFlow.value ?: return
        val spp = food.servingsPerPackage ?: return
        val s = (spp * multiplier).coerceAtLeast(0.0)
        servingsFlow.value = s
        inputModeFlow.value = InputMode.SERVINGS

        inputUnitFlow.value = food.servingUnit
        inputAmountFlow.value = s * food.servingSize
    }

    fun onBatchSelected(batchId: Long?) {
        selectedBatchIdFlow.value = batchId
    }

    fun openCreateBatchDialog() {
        isCreateBatchDialogOpenFlow.value = true
        errorFlow.value = null

        val food = selectedFoodFlow.value

        if (food != null && food.isRecipe) {
            val servingsDefault = selectedRecipeServingsYieldDefaultFlow.value
            val gramsPerServing = gramsPerServingResolved(food)

            servingsYieldTextFlow.value =
                servingsDefault?.let { formatCompact(it) }.orEmpty()

            yieldGramsTextFlow.value =
                if (servingsDefault != null && gramsPerServing != null) {
                    formatCompact(servingsDefault * gramsPerServing)
                } else {
                    ""
                }
        } else {
            yieldGramsTextFlow.value = ""
            servingsYieldTextFlow.value = ""
        }
    }

    private fun formatCompact(v: Double): String {
        val s = "%.2f".format(v)
        return s.trimEnd('0').trimEnd('.')
    }

    fun closeCreateBatchDialog() {
        isCreateBatchDialogOpenFlow.value = false
    }

    fun onYieldGramsTextChange(v: String) {
        yieldGramsTextFlow.value = v
    }

    fun onServingsYieldTextChange(v: String) {
        servingsYieldTextFlow.value = v
    }

    fun createBatchForSelectedRecipe() {
        if (isSavingFlow.value) {
            Log.w("Meow", "QuickAddViewModel > createBatch ignored (already saving)")
            return
        }

        val recipeId = selectedRecipeIdFlow.value
        if (recipeId == null) {
            errorFlow.value = "Select a recipe first."
            return
        }

        val cookedYieldGrams = yieldGramsTextFlow.value.toDoubleOrNull()
        if (cookedYieldGrams == null || cookedYieldGrams <= 0.0) {
            errorFlow.value = "Enter a cooked yield in grams (must be > 0)."
            return
        }

        val servingsYieldUsed =
            servingsYieldTextFlow.value.trim()
                .takeIf { it.isNotBlank() }
                ?.toDoubleOrNull()

        if (servingsYieldTextFlow.value.trim().isNotBlank() &&
            (servingsYieldUsed == null || servingsYieldUsed <= 0.0)
        ) {
            errorFlow.value = "Servings yield must be a number > 0 (or leave blank)."
            return
        }

        viewModelScope.launch {
            isSavingFlow.value = true
            try {
                errorFlow.value = null
                Log.w("Meow", "CreateSnapshotFoodFromRecipeUseCase.execute() VERSION=2026-02-21A (byCode lookup)")
                Log.d(
                    "Meow",
                    "QuickAddViewModel > createBatch START recipeId=$recipeId cookedYieldGrams=$cookedYieldGrams servingsYieldUsed=$servingsYieldUsed"
                )
                when (val r = createBatchFoodFromRecipeUseCase.execute(
                    recipeId = recipeId,
                    cookedYieldGrams = cookedYieldGrams,
                    servingsYieldUsed = servingsYieldUsed
                )) {
                    is CreateSnapshotFoodFromRecipeUseCase.Result.Success -> {
                        Log.d(
                            "Meow",
                            "QuickAddViewModel > createBatch SUCCESS batchId=${r.batchId} batchFoodId=${r.batchFoodId}"
                        )
                        selectedBatchIdFlow.value = r.batchId
                        isCreateBatchDialogOpenFlow.value = false
                        yieldGramsTextFlow.value = ""
                        servingsYieldTextFlow.value = ""
                        errorFlow.value = null
                    }

                    is CreateSnapshotFoodFromRecipeUseCase.Result.Blocked -> {
                        Log.w("Meow", "QuickAddViewModel > createBatch BLOCKED messages=${r.messages}")
                        errorFlow.value = r.messages.joinToString("\n")
                    }

                    is CreateSnapshotFoodFromRecipeUseCase.Result.Error -> {
                        Log.e("Meow", "QuickAddViewModel > createBatch ERROR message=${r.message}")
                        errorFlow.value = r.message
                    }
                }
            } catch (t: Throwable) {
                errorFlow.value = t.message ?: "Failed to create batch."
            } finally {
                isSavingFlow.value = false
            }
        }
    }

    fun closeResolveMassDialog() {
        isResolveMassDialogOpenFlow.value = false
        pendingResolveMass = null
    }

    fun onGramsPerServingTextChange(text: String) {
        gramsPerServingTextFlow.value = text
    }

    fun useEstimateJustOnce() {
        val pending = pendingResolveMass ?: return
        val food = pending.food
        val estimatedGpsu = estimateGramsPerServingFromMl(food) ?: return

        retryPendingLog(
            overrideGpsu = estimatedGpsu,
            persistGpsu = null
        )
    }

    fun useEstimateAlways() {
        val pending = pendingResolveMass ?: return
        val food = pending.food
        val estimatedGpsu = estimateGramsPerServingFromMl(food) ?: return

        retryPendingLog(
            overrideGpsu = null,
            persistGpsu = estimatedGpsu
        )
    }

    fun confirmEnteredGramsPerServing() {
        val pending = pendingResolveMass ?: return
        val grams = gramsPerServingTextFlow.value.toDoubleOrNull()
            ?.takeIf { it > 0.0 } ?: return

        retryPendingLog(overrideGpsu = null, persistGpsu = grams)
    }

    private fun estimateGramsPerServingFromMl(food: Food): Double? {
        val mlPerUnit = food.mlPerServingUnit ?: return null
        val mlPerServing = food.servingSize * mlPerUnit
        if (mlPerServing <= 0.0) return null
        return mlPerServing
    }

    private fun retryPendingLog(
        overrideGpsu: Double?,
        persistGpsu: Double?
    ) {
        val pending = pendingResolveMass ?: return

        viewModelScope.launch {
            try {
                if (persistGpsu != null) {
                    foodRepository.upsert(
                        pending.food.copy(gramsPerServingUnit = persistGpsu)
                    )
                    selectedFoodFlow.value = pending.food.copy(gramsPerServingUnit = persistGpsu)
                }

                val result = createLogEntry.execute(
                    ref = FoodRef.Food(pending.food.id),
                    timestamp = Instant.now(),
                    amountInput = pending.amountInput,
                    overrideGramsPerServingUnit = overrideGpsu,
                    logDateIso = pending.logDateIso
                )

                Log.d("Meow", "QuickAddViewModel > createLogEntry result = $result")

                when (result) {
                    is CreateLogEntryUseCase.Result.Success -> {
                        closeResolveMassDialog()
                        clearSelection()
                        queryFlow.value = ""
                    }

                    is CreateLogEntryUseCase.Result.Blocked -> errorFlow.value = result.message
                    is CreateLogEntryUseCase.Result.Error -> errorFlow.value = result.message
                }
            } finally {
                isResolveMassDialogOpenFlow.value = false
                pendingResolveMass = null
            }
        }
    }

    private fun computeGramsAmount(
        food: Food,
        servings: Double,
        inputMode: InputMode,
        inputUnit: ServingUnit,
        inputAmount: Double?
    ): Double? {
        return when (inputMode) {
            InputMode.SERVINGS -> {
                val gPerServing = gramsPerServingResolved(food) ?: return null
                if (gPerServing <= 0.0) return null
                servings * gPerServing
            }

            InputMode.GRAMS,
            InputMode.SERVING_UNIT -> {
                val amount = inputAmount ?: return null
                computeGramsFromAmountAndUnit(food, amount, inputUnit)
            }
        }
    }

    private fun computeGramsFromAmountAndUnit(
        food: Food,
        amount: Double,
        unit: ServingUnit
    ): Double? {
        val a = amount.coerceAtLeast(0.0)

        unit.asG?.let { gPerUnit ->
            if (gPerUnit > 0.0) return a * gPerUnit
        }

        if (unit == food.servingUnit) {
            val gPerUnit = food.gramsPerServingUnitResolved() ?: return null
            if (gPerUnit <= 0.0) return null
            return a * gPerUnit
        }

        val inputMlPerUnit = unit.asMl ?: return null
        val foodMlPerUnit = food.servingUnit.asMl ?: return null
        if (foodMlPerUnit <= 0.0) return null

        val gPerUnit = food.gramsPerServingUnitResolved() ?: return null
        if (gPerUnit <= 0.0) return null

        val densityGPerMl = gPerUnit / foodMlPerUnit
        val ml = a * inputMlPerUnit
        return ml * densityGPerMl
    }

    private fun buildAmountInputForSave(): AmountInput? {
        val food = selectedFoodFlow.value ?: return null
        val servings = servingsFlow.value

        val gramsForSave = computeGramsAmount(
            food = food,
            servings = servings,
            inputMode = inputModeFlow.value,
            inputUnit = inputUnitFlow.value,
            inputAmount = inputAmountFlow.value
        )

        return if (gramsForSave != null &&
            gramsForSave > 0.0 &&
            inputModeFlow.value != InputMode.SERVINGS
        ) {
            AmountInput.ByGrams(gramsForSave)
        } else {
            if (servings <= 0.0) return null
            AmountInput.ByServings(servings)
        }
    }

    fun save(onDone: () -> Unit, logDate: LocalDate) {
        val food = selectedFoodFlow.value ?: return
        val mealSlot = mealSlotFlow.value
        val amountInput = buildAmountInputForSave() ?: return

        Log.d(
            "Meow",
            "QuickAddViewModel > save START mode=${modeFlow.value} amountInput=$amountInput selected=${selectedFoodFlow.value?.name}"
        )

        if (modeFlow.value == QuickAddMode.EDIT) {
            val logId = editingLogIdFlow.value ?: run {
                errorFlow.value = "Missing log id for edit."
                return
            }

            executeEditSave(
                logId = logId,
                amountInput = amountInput,
                mealSlot = mealSlot,
                decision = null,
                onDone = onDone,
//                logDate = logDate
            )
            return
        }

        viewModelScope.launch {
            isSavingFlow.value = true
            errorFlow.value = null

            try {
                val timestamp = ZonedDateTime.of(logDate, LocalTime.now(), ZoneId.systemDefault()).toInstant()

                if (!food.isRecipe) {
                    val snapshot = snapshotRepo.getSnapshot(food.id)

                    val validation = validateFoodForUsage.execute(
                        ValidateFoodForUsageUseCase.PersistedInput(
                            servingUnit = food.servingUnit,
                            gramsPerServingUnit = food.gramsPerServingUnit,
                            mlPerServingUnit = food.mlPerServingUnit,
                            amountInput = amountInput,
                            context = UsageContext.LOGGING,
                            snapshot = snapshot
                        )
                    )

                    when (validation) {
                        FoodValidationResult.Ok -> Unit
                        is FoodValidationResult.Warning -> {
                            Log.w("Meow", "QuickAddViewModel > validation WARNING reason=${validation.reason} msg=${validation.message}")
                        }

                        is FoodValidationResult.Blocked -> {
                            Log.d(
                                "Meow",
                                "QuickAddViewModel > validation BLOCKED reason=${validation.reason} " +
                                        "servingUnit=${food.servingUnit} gpsu=${food.gramsPerServingUnit} mlpsu=${food.mlPerServingUnit} amountInput=$amountInput"
                            )

                            val shouldOfferMlEstimate =
                                validation.reason == FoodValidationResult.Reason.MissingGramsPerServing &&
                                        amountInput is AmountInput.ByServings &&
                                        food.gramsPerServingUnit == null &&
                                        food.mlPerServingUnit != null

                            if (shouldOfferMlEstimate) {
                                Log.d("Meow", "QuickAddViewModel > opening resolve-mass dialog ml=${food.mlPerServingUnit}")

                                pendingResolveMass = PendingResolveMass(
                                    food = food,
                                    timestamp = timestamp,
                                    amountInput = amountInput,
                                    logDateIso = logDate.toString()
                                )

                                gramsPerServingTextFlow.value = ""
                                isResolveMassDialogOpenFlow.value = true
                                errorFlow.value = null
                                return@launch
                            }

                            errorFlow.value = validation.message
                            return@launch
                        }
                    }
                }

                val result = if (!food.isRecipe) {
                    createLogEntry.execute(
                        ref = FoodRef.Food(foodId = food.id),
                        timestamp = timestamp,
                        amountInput = amountInput,
                        recipeBatchId = null,
                        logDateIso = logDate.toString(),
                        mealSlot = mealSlot
                    )
                } else {
                    val recipeId = selectedRecipeIdFlow.value
                        ?: run {
                            errorFlow.value = "Recipe data missing for this item."
                            return@launch
                        }

                    val stableId = selectedRecipeStableIdFlow.value ?: food.stableId
                    val servingsYieldDefault = selectedRecipeServingsYieldDefaultFlow.value ?: 1.0

                    val batchId =
                        when (inputModeFlow.value) {
                            InputMode.GRAMS -> selectedBatchIdFlow.value
                                ?: run {
                                    errorFlow.value = "Select or create a cooked batch (yield grams) to log by grams."
                                    return@launch
                                }

                            else -> selectedBatchIdFlow.value
                        }

                    createLogEntry.execute(
                        ref = FoodRef.Recipe(
                            recipeId = recipeId,
                            stableId = stableId,
                            displayName = food.name,
                            servingsYieldDefault = servingsYieldDefault
                        ),
                        timestamp = Instant.now(),
                        amountInput = amountInput,
                        recipeBatchId = batchId,
                        logDateIso = logDate.toString(),
                        mealSlot = mealSlot
                    )
                }

                when (result) {
                    is CreateLogEntryUseCase.Result.Success -> {
                        clearSelection()
                        queryFlow.value = ""
                        onDone()
                    }

                    is CreateLogEntryUseCase.Result.Blocked -> {
                        errorFlow.value = result.message
                    }

                    is CreateLogEntryUseCase.Result.Error -> {
                        errorFlow.value = result.message
                    }
                }
            } finally {
                Log.d("Meow", "QuickAddViewModel > save FINALLY")
                isSavingFlow.value = false
            }
        }
    }

    private fun executeEditSave(
        logId: Long,
        amountInput: AmountInput,
        mealSlot: MealSlot?,
        decision: UpdateLogEntryUseCase.NutritionDecision?,
        onDone: () -> Unit,
//        logDate: LocalDate
    ) {
        viewModelScope.launch {
            isSavingFlow.value = true
            errorFlow.value = null

            try {
                when (val result = updateLogEntry.execute(
                    logId = logId,
                    amountInput = amountInput,
                    mealSlot = mealSlot,
                    nutritionDecision = decision
                )) {
                    is UpdateLogEntryUseCase.Result.Success -> {
                        pendingNutritionChoice = null
                        isNutritionChoiceDialogOpenFlow.value = false
                        nutritionChoiceMessageFlow.value = null
                        onDone()
                    }

                    is UpdateLogEntryUseCase.Result.Blocked -> {
                        errorFlow.value = result.message
                    }

                    is UpdateLogEntryUseCase.Result.Error -> {
                        errorFlow.value = result.message
                    }

                    is UpdateLogEntryUseCase.Result.NutritionChoiceRequired -> {
                        pendingNutritionChoice = PendingNutritionChoice(
                            logId = logId,
                            amountInput = amountInput,
                            mealSlot = mealSlot
                        )
                        nutritionChoiceMessageFlow.value =
                            "Nutrition has changed since this item was logged."
                        isNutritionChoiceDialogOpenFlow.value = true
                    }
                }
            } finally {
                isSavingFlow.value = false
            }
        }
    }
}