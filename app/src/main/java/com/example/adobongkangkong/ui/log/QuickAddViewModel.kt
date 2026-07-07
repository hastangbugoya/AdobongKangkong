package com.example.adobongkangkong.ui.log

import android.content.Context
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
import com.example.adobongkangkong.domain.repository.RecipeVariantRepository
import com.example.adobongkangkong.domain.usage.FoodValidationResult
import com.example.adobongkangkong.domain.usage.UsageContext
import com.example.adobongkangkong.domain.usage.ValidateFoodForUsageUseCase
import com.example.adobongkangkong.domain.usecase.SearchFoodsUseCase
import com.example.adobongkangkong.ui.food.FoodListItemUiModel
import com.example.adobongkangkong.widget.CaffeineWidgetProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.recipes.FoodNutritionSnapshot
import com.example.adobongkangkong.domain.recipes.ObserveActiveRecipeMeasuredYieldUseCase
import kotlinx.coroutines.flow.flow
import com.example.adobongkangkong.domain.planner.usecase.LogPlannedMealUseCase

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
 *
 * Quantity model:
 * - inputMode is the active quantity driver.
 * - SERVINGS   -> servingsFlow is authoritative.
 * - GRAMS      -> inputAmountFlow is authoritative grams; servings are derived for display.
 * - SERVING_UNIT -> inputAmountFlow is authoritative in inputUnitFlow; servings are derived for display.
 *
 * Derived values (gramsAmount / servingUnitAmount / servingsEquivalent) are computed in state mapping
 * and should not be treated as separate sources of truth.
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
    private val recipeVariantRepository: RecipeVariantRepository,
    private val observeActiveRecipeMeasuredYield: ObserveActiveRecipeMeasuredYieldUseCase,

    // validation + snapshot access for preflight gating
    private val snapshotRepo: FoodNutritionSnapshotRepository,
    private val validateFoodForUsage: ValidateFoodForUsageUseCase,
    private val foodBarcodeRepository: FoodBarcodeRepository,

    // IOUs (planner narrative placeholders)
    private val createPlannerIou: CreateIouUseCase,

    // From Day Planner
    private val observeTodayPlannedItemsForQuickAddUseCase: ObserveTodayPlannedItemsForQuickAddUseCase,
    private val logPlannedMeal: LogPlannedMealUseCase,

    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")
    private val selectedFoodFlow = MutableStateFlow<Food?>(null)

    private val selectedFoodSnapshotFlow =
        selectedFoodFlow.flatMapLatest { food ->
            if (food == null) {
                flowOf<FoodNutritionSnapshot?>(null)
            } else {
                flow<FoodNutritionSnapshot?> {
                    emit(snapshotRepo.getSnapshot(food.id))
                }
            }
        }

    private val modeFlow = MutableStateFlow(QuickAddMode.CREATE)
    private val editingLogIdFlow = MutableStateFlow<Long?>(null)
    private val isIdentityLockedFlow = MutableStateFlow(false)

    // Canonical servings source ONLY when inputMode == SERVINGS.
    private val servingsFlow = MutableStateFlow(1.0)
    private val inputModeFlow = MutableStateFlow(InputMode.SERVINGS)

    // Authoritative amount ONLY when inputMode == GRAMS or SERVING_UNIT.
    // In SERVINGS mode, these may be kept in sync for UI convenience, but are not authoritative.
    private val inputUnitFlow = MutableStateFlow(ServingUnit.G)
    private val inputAmountFlow = MutableStateFlow<Double?>(null)

    // recipe context
    private val selectedRecipeIdFlow = MutableStateFlow<Long?>(null)
    private val selectedRecipeStableIdFlow = MutableStateFlow<String?>(null)
    private val selectedRecipeServingsYieldDefaultFlow = MutableStateFlow<Double?>(null)
    private val selectedBatchIdFlow = MutableStateFlow<Long?>(null)
    private val selectedRecipeVariantIdFlow = MutableStateFlow<Long?>(null)

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
    private val isPlannedMealRelogDialogOpenFlow = MutableStateFlow(false)
    private val plannedMealRelogMessageFlow = MutableStateFlow<String?>(null)
    private var todayPlanJob: Job? = null
    private var todayPlanDateIso: String? = null

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

    private data class PendingPlannedMealRelog(
        val mealId: Long,
        val logDate: LocalDate,
        val mealSlot: MealSlot?
    )

    private var pendingResolveMass: PendingResolveMass? = null
    private var pendingPlannedMealRelog: PendingPlannedMealRelog? = null

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

    private val recipeVariantsFlow =
        selectedFoodFlow.flatMapLatest { food ->
            if (food == null || !food.isRecipe) {
                flowOf(emptyList())
            } else {
                recipeVariantRepository.observeActiveVariantsForRecipe(food.id)
                    .map { variants ->
                        variants.map { variant ->
                            QuickAddRecipeVariantUi(
                                id = variant.id,
                                name = variant.name,
                                notes = variant.notes,
                                servingsYieldOverride = variant.servingsYieldOverride,
                            )
                        }
                    }
            }
        }

    private val activeMeasuredYieldFlow =
        combine(
            selectedRecipeIdFlow,
            selectedRecipeVariantIdFlow
        ) { recipeId, variantId ->
            recipeId to variantId
        }.flatMapLatest { (recipeId, variantId) ->
            if (recipeId == null) {
                flowOf(null)
            } else {
                observeActiveRecipeMeasuredYield.execute(
                    recipeId = recipeId,
                    variantId = variantId
                )
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            null
        )

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
        dismissPlannedMealRelogDialog()
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
            dismissPlannedMealRelogDialog()
            stopTodayPlanObservation()

            clearEditIdentityState()

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

                val food = resolveFoodForLogEntry(entry, stableId)
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
                selectedRecipeVariantIdFlow.value = entry.recipeVariantId

                restoreAmountForEdit(
                    food = food,
                    entry = entry
                )
            } finally {
                isSavingFlow.value = false
            }
        }
    }

    private suspend fun resolveFoodForLogEntry(
        entry: LogEntry,
        stableId: String
    ): Food? {
        foodRepository.getByStableId(stableId)?.let { return it }

        val recipeId = recipeDao.getIdByStableId(stableId) ?: return null
        val recipe = recipeDao.getById(recipeId) ?: return null

        if (entry.recipeBatchId != null) {
            Log.d(
                "Meow",
                "QuickAddViewModel > startEdit resolved recipe stableId=$stableId recipeId=$recipeId foodId=${recipe.foodId} batchId=${entry.recipeBatchId}"
            )
        }

        return foodRepository.getById(recipe.foodId)
    }

    private fun clearEditIdentityState() {
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
        selectedRecipeVariantIdFlow.value = null

        errorFlow.value = null
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
        val selectedSnapshot: FoodNutritionSnapshot?,
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
        val servingsYieldText: String,
        val recipeVariants: List<QuickAddRecipeVariantUi>,
        val selectedRecipeVariantId: Long?,
        val activeMeasuredYieldGrams: Double?,
        val activeMeasuredYieldUpdatedAtEpochMs: Long?,
        val activeMeasuredYieldNote: String?,
        val recipeGramLoggingAvailable: Boolean,
        val recipeServingsYieldDefault: Double?,
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
        val isLoading: Boolean,
        val isRelogDialogOpen: Boolean,
        val relogMessage: String?
    )

    val state: StateFlow<QuickAddState> = run {

        data class CoreA(
            val query: String,
            val results: List<FoodListItemUiModel>,
            val selected: Food?,
            val selectedSnapshot: FoodNutritionSnapshot?,
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
            val selectedSnapshot: FoodNutritionSnapshot?,
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
                selectedFoodSnapshotFlow,
                servingsFlow
            ) { query, results, selected, selectedSnapshot, servings ->
                CoreALeft(
                    query = query,
                    results = results,
                    selected = selected,
                    selectedSnapshot = selectedSnapshot,
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
                selectedSnapshot = left.selectedSnapshot,
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
                selectedSnapshot = a.selectedSnapshot,
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

        val recipeBaseFlow = combine(
            batchesFlow,
            selectedBatchIdFlow,
            yieldGramsTextFlow,
            servingsYieldTextFlow,
            selectedRecipeServingsYieldDefaultFlow
        ) { batches, selectedBatchId, yieldGramsText, servingsYieldText, servingsYieldDefault ->
            RecipeInputs(
                batches = batches,
                selectedBatchId = selectedBatchId,
                yieldGramsText = yieldGramsText,
                servingsYieldText = servingsYieldText,
                recipeVariants = emptyList(),
                selectedRecipeVariantId = null,
                activeMeasuredYieldGrams = null,
                activeMeasuredYieldUpdatedAtEpochMs = null,
                activeMeasuredYieldNote = null,
                recipeGramLoggingAvailable = false,
                recipeServingsYieldDefault = servingsYieldDefault,
            )
        }

        val recipeFlow = combine(
            recipeBaseFlow,
            recipeVariantsFlow,
            selectedRecipeVariantIdFlow,
            activeMeasuredYieldFlow
        ) { recipe, recipeVariants, selectedRecipeVariantId, activeMeasuredYield ->
            val activeYieldGrams = activeMeasuredYield
                ?.yieldGrams
                ?.takeIf { it > 0.0 }

            recipe.copy(
                recipeVariants = recipeVariants,
                selectedRecipeVariantId = selectedRecipeVariantId,
                activeMeasuredYieldGrams = activeYieldGrams,
                activeMeasuredYieldUpdatedAtEpochMs = activeMeasuredYield?.updatedAtEpochMs,
                activeMeasuredYieldNote = activeMeasuredYield?.note,
                recipeGramLoggingAvailable = activeYieldGrams != null,
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
            isTodayPlanLoadingFlow,
            isPlannedMealRelogDialogOpenFlow,
            plannedMealRelogMessageFlow
        ) { isOpen, sections, isLoading, isRelogDialogOpen, relogMessage ->
            TodayPlanUi(
                isOpen = isOpen,
                sections = sections,
                isLoading = isLoading,
                isRelogDialogOpen = isRelogDialogOpen,
                relogMessage = relogMessage
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

                val measuredYieldGramsPerServing: Double? =
                    if (
                        core.selected?.isRecipe == true &&
                        recipe.selectedRecipeVariantId == null
                    ) {
                        val activeYieldGrams = recipe.activeMeasuredYieldGrams
                            ?.takeIf { it > 0.0 }
                        val servingsYield = recipe.recipeServingsYieldDefault
                            ?.takeIf { it > 0.0 }

                        if (activeYieldGrams != null && servingsYield != null) {
                            activeYieldGrams / servingsYield
                        } else {
                            null
                        }
                    } else {
                        null
                    }

                val gramsAmount: Double? = core.selected?.let { food ->
                    computeGramsAmount(
                        food = food,
                        servings = core.servings,
                        inputMode = core.inputMode,
                        inputUnit = core.inputUnit,
                        inputAmount = core.inputAmount,
                        measuredYieldGramsPerServing = measuredYieldGramsPerServing
                    )
                }

                val servingsEquivalent: Double? = core.selected?.let { food ->
                    when (core.inputMode) {
                        InputMode.SERVINGS -> core.servings
                        InputMode.GRAMS,
                        InputMode.SERVING_UNIT -> {
                            val grams = gramsAmount ?: return@let null
                            val gPerServing = measuredYieldGramsPerServing
                                ?: gramsPerServingResolved(food)
                                ?: return@let null
                            if (gPerServing <= 0.0) return@let null
                            grams / gPerServing
                        }
                    }
                }

                val displayServings = servingsEquivalent ?: core.servings

                val servingUnitAmount: Double? = core.selected?.let { food ->
                    when {
                        core.inputMode == InputMode.SERVING_UNIT && core.inputUnit == food.servingUnit -> {
                            core.inputAmount
                        }

                        else -> {
                            displayServings * food.servingSize
                        }
                    }
                }

                val nutrientCautions = buildNutrientCautions(
                    snapshot = core.selectedSnapshot,
                    gramsAmount = gramsAmount
                )

                QuickAddState(
                    query = core.query,
                    results = core.results,
                    mode = core.mode,
                    editingLogId = core.editingLogId,
                    isIdentityLocked = core.isIdentityLocked,
                    selectedFood = core.selected,
                    servings = displayServings,
                    servingsEquivalent = servingsEquivalent,
                    inputUnit = core.inputUnit,
                    inputAmount = core.inputAmount,
                    servingUnitAmount = servingUnitAmount,
                    gramsAmount = gramsAmount,
                    inputMode = core.inputMode,
                    nutrientCautions = nutrientCautions,

                    batches = recipe.batches,
                    selectedBatchId = recipe.selectedBatchId,
                    recipeVariants = recipe.recipeVariants,
                    selectedRecipeVariantId = recipe.selectedRecipeVariantId,
                    activeMeasuredYieldGrams = recipe.activeMeasuredYieldGrams,
                    activeMeasuredYieldUpdatedAtEpochMs = recipe.activeMeasuredYieldUpdatedAtEpochMs,
                    activeMeasuredYieldNote = recipe.activeMeasuredYieldNote,
                    recipeGramLoggingAvailable = recipe.recipeGramLoggingAvailable,
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
                    isPlannedMealRelogDialogOpen = todayPlan.isRelogDialogOpen,
                    plannedMealRelogMessage = todayPlan.relogMessage,
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QuickAddState())
    }

    fun onQueryChange(q: String) {
        if (modeFlow.value == QuickAddMode.EDIT) return
        queryFlow.value = q
    }

    fun onPickedFoodId(foodId: Long) {
        if (modeFlow.value == QuickAddMode.EDIT && isIdentityLockedFlow.value) return

        viewModelScope.launch {
            val food = foodRepository.getById(foodId)
            if (food == null) {
                errorFlow.value = "Picked food not found."
                return@launch
            }

            applySelectedFood(
                food = food,
                selectedBatchIdOverride = null
            )
        }
    }

    fun onFoodSelected(food: Food) {
        if (modeFlow.value == QuickAddMode.EDIT && isIdentityLockedFlow.value) return

        viewModelScope.launch {
            applySelectedFood(
                food = food,
                selectedBatchIdOverride = null
            )
        }
    }

    private suspend fun applySelectedFood(
        food: Food,
        selectedBatchIdOverride: Long?
    ) {
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

        // Kept in sync for UI convenience in SERVINGS mode; not authoritative there.
        inputAmountFlow.value = food.servingSize.coerceAtLeast(0.0)
        errorFlow.value = null
        mealSlotFlow.value = null
        selectedRecipeVariantIdFlow.value = null

        applyRecipeContextForFood(
            food = food,
            selectedBatchIdOverride = selectedBatchIdOverride
        )
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
            selectedRecipeVariantIdFlow.value = null
            return
        }

        val recipe = recipeDao.getByFoodId(food.id)
            ?: recipeDao.getById(food.id)

        if (recipe == null) {
            selectedRecipeIdFlow.value = null
            selectedRecipeStableIdFlow.value = null
            selectedRecipeServingsYieldDefaultFlow.value = null
            selectedBatchIdFlow.value = null
            selectedRecipeVariantIdFlow.value = null
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
        selectedRecipeVariantIdFlow.value = null

        isTodayPlanPickerOpenFlow.value = false
        todayPlanSectionsFlow.value = emptyMap()
        isTodayPlanLoadingFlow.value = false
        dismissPlannedMealRelogDialog()
        stopTodayPlanObservation()

        errorFlow.value = null
    }

    fun onMealSlotChanged(slot: MealSlot?) {
        mealSlotFlow.value = slot
    }

    fun onInputModeChanged(mode: InputMode) {
        inputModeFlow.value = mode
    }

    /**
     * Unit selection no longer auto-converts and rewrites amount state.
     * It only changes the current alternate-input unit.
     *
     * Authoritative quantity should be changed by:
     * - onServingsChanged
     * - onGramsChanged
     * - onServingUnitAmountChanged
     * - onInputAmountChanged (dialog / generic alternate-unit apply path)
     */
    fun onInputUnitChanged(unit: ServingUnit) {
        inputUnitFlow.value = unit
    }

    fun openTodayPlanPicker(logDate: LocalDate) {
        if (modeFlow.value == QuickAddMode.EDIT) return

        val requestedDateIso = logDate.toString()

        isTodayPlanPickerOpenFlow.value = true
        errorFlow.value = null

        if (todayPlanDateIso != requestedDateIso) {
            stopTodayPlanObservation()
            todayPlanSectionsFlow.value = emptyMap()
        }

        if (todayPlanJob != null) return

        todayPlanDateIso = requestedDateIso
        isTodayPlanLoadingFlow.value = true

        todayPlanJob = viewModelScope.launch {
            observeTodayPlannedItemsForQuickAddUseCase(requestedDateIso)
                .collect { sections ->
                    todayPlanSectionsFlow.value = sections
                    isTodayPlanLoadingFlow.value = false
                }
        }
    }

    fun closeTodayPlanPicker() {
        isTodayPlanPickerOpenFlow.value = false
        dismissPlannedMealRelogDialog()
    }

    fun logPlannedMealFromTodayPlan(
        mealId: Long,
        logDate: LocalDate,
        onDone: () -> Unit
    ) {
        if (modeFlow.value == QuickAddMode.EDIT) return
        if (mealId <= 0L) {
            errorFlow.value = "Planned meal is missing."
            return
        }
        if (isSavingFlow.value) return

        val slot = todayPlanSectionsFlow.value
            .asSequence()
            .firstOrNull { (_, candidates) ->
                candidates.any { it.plannedMealId == mealId }
            }
            ?.key

        executePlannedMealLog(
            mealId = mealId,
            logDate = logDate,
            mealSlot = slot,
            allowRelog = false,
            onDone = onDone
        )
    }

    fun dismissPlannedMealRelogDialog() {
        isPlannedMealRelogDialogOpenFlow.value = false
        plannedMealRelogMessageFlow.value = null
        pendingPlannedMealRelog = null
    }

    fun confirmLogPlannedMealAgain(onDone: () -> Unit) {
        val pending = pendingPlannedMealRelog ?: return
        isPlannedMealRelogDialogOpenFlow.value = false
        plannedMealRelogMessageFlow.value = null

        executePlannedMealLog(
            mealId = pending.mealId,
            logDate = pending.logDate,
            mealSlot = pending.mealSlot,
            allowRelog = true,
            onDone = onDone
        )
    }

    private fun executePlannedMealLog(
        mealId: Long,
        logDate: LocalDate,
        mealSlot: MealSlot?,
        allowRelog: Boolean,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            isSavingFlow.value = true
            errorFlow.value = null

            try {
                val timestamp = ZonedDateTime.of(
                    logDate,
                    LocalTime.now(),
                    ZoneId.systemDefault()
                ).toInstant()

                val result = logPlannedMeal.execute(
                    mealId = mealId,
                    timestamp = timestamp,
                    mealSlot = mealSlot,
                    logDateIso = logDate.toString(),
                    allowRelog = allowRelog
                )

                if (!allowRelog && result.needsPlannedMealRelogConfirmation()) {
                    pendingPlannedMealRelog = PendingPlannedMealRelog(
                        mealId = mealId,
                        logDate = logDate,
                        mealSlot = mealSlot
                    )
                    plannedMealRelogMessageFlow.value =
                        "This planned meal was logged before. Log it again only if you ate it again or want another copy in your Day Log."
                    isPlannedMealRelogDialogOpenFlow.value = true
                    return@launch
                }

                val message = buildString {
                    append("Logged ${result.loggedCount}")
                    if (result.blockedCount > 0) append(" • Blocked ${result.blockedCount}")
                    if (result.errorCount > 0) append(" • Errors ${result.errorCount}")
                }

                if (result.errorCount == 0 && result.blockedCount == 0 && result.loggedCount > 0) {
                    requestCaffeineWidgetRefresh()
                    pendingPlannedMealRelog = null
                    isPlannedMealRelogDialogOpenFlow.value = false
                    plannedMealRelogMessageFlow.value = null
                    isTodayPlanPickerOpenFlow.value = false
                    onDone()
                } else {
                    errorFlow.value = message
                }
            } catch (t: Throwable) {
                errorFlow.value = t.message ?: "Failed to log planned meal."
            } finally {
                isSavingFlow.value = false
            }
        }
    }

    private fun LogPlannedMealUseCase.Result.needsPlannedMealRelogConfirmation(): Boolean {
        return loggedCount == 0 &&
                blockedCount == 0 &&
                errorCount == 1 &&
                outcomes.size == 1 &&
                outcomes.firstOrNull()
                    ?.message
                    ?.contains("logged before", ignoreCase = true) == true
    }

    private fun stopTodayPlanObservation() {
        todayPlanJob?.cancel()
        todayPlanJob = null
        todayPlanDateIso = null
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

            val batchOverride =
                if (candidate.type == QuickAddPlannedItemCandidate.Type.RECIPE_BATCH) {
                    candidate.batchId
                } else {
                    null
                }

            applySelectedFood(
                food = food,
                selectedBatchIdOverride = batchOverride
            )

            onMealSlotChanged(candidate.slot)

            /*
             * Preserve planner recipe variant intent for item-level Quick Add.
             *
             * Without this, tapping a planned recipe variant from Today Plan would prefill the
             * base recipe and silently lose the selected variant.
             */
            selectedRecipeVariantIdFlow.value = candidate.recipeVariantId

            if (candidate.recipeVariantId != null) {
                selectedBatchIdFlow.value = null
            }

            candidate.plannedServings?.let { onServingsChanged(it) }
            candidate.plannedGrams?.let { onGramsChanged(it) }

            isTodayPlanPickerOpenFlow.value = false
        }
    }

    /**
     * Generic alternate-unit apply path used by dialogs.
     * This does not back-write servingsFlow; servings are derived in state mapping.
     */
    fun onInputAmountChanged(amount: Double) {
        val food = selectedFoodFlow.value ?: return
        val unit = inputUnitFlow.value
        val a = amount.coerceAtLeast(0.0)

        inputAmountFlow.value = a

        inputModeFlow.value = when {
            unit.asG != null -> InputMode.GRAMS
            unit == food.servingUnit -> InputMode.SERVING_UNIT
            unit.asMl != null -> InputMode.GRAMS
            else -> InputMode.SERVING_UNIT
        }
    }

    /**
     * Servings becomes the active driver.
     * Keep input unit/amount synced for UI convenience, but the authoritative source is servingsFlow.
     */
    fun onServingsChanged(servings: Double) {
        val s = servings.coerceAtLeast(0.0)
        servingsFlow.value = s
        inputModeFlow.value = InputMode.SERVINGS

        selectedFoodFlow.value?.let { food ->
            inputUnitFlow.value = food.servingUnit
            inputAmountFlow.value = s * food.servingSize
        }
    }

    /**
     * Food-serving-unit amount becomes the active driver.
     * Do not delegate to onInputAmountChanged(); set the driver explicitly.
     */
    fun onServingUnitAmountChanged(amount: Double) {
        val food = selectedFoodFlow.value ?: return
        val a = amount.coerceAtLeast(0.0)

        inputUnitFlow.value = food.servingUnit
        inputAmountFlow.value = a
        inputModeFlow.value = InputMode.SERVING_UNIT
    }

    /**
     * Grams becomes the active driver.
     * Do not back-write servingsFlow here; servings are derived in state mapping.
     */
    fun onGramsChanged(grams: Double) {
        val gramsClamped = grams.coerceAtLeast(0.0)

        inputUnitFlow.value = ServingUnit.G
        inputAmountFlow.value = gramsClamped
        inputModeFlow.value = InputMode.GRAMS
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

    fun onRecipeVariantSelected(variantId: Long?) {
        if (modeFlow.value == QuickAddMode.EDIT && isIdentityLockedFlow.value) return

        selectedRecipeVariantIdFlow.value = variantId

        // Variant yield and ingredient rules are separate from cooked batch records for now.
        // Avoid combining the two contexts until variant-specific cooked batches exist.
        if (variantId != null) {
            selectedBatchIdFlow.value = null

            if (inputModeFlow.value == InputMode.GRAMS) {
                onServingsChanged(servingsFlow.value.takeIf { it > 0.0 } ?: 1.0)
            }
        }

        errorFlow.value = null
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
        pendingResolveMass ?: return
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
                        requestCaffeineWidgetRefresh()
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
        inputAmount: Double?,
        measuredYieldGramsPerServing: Double? = null
    ): Double? {
        return when (inputMode) {
            InputMode.SERVINGS -> {
                val gPerServing = measuredYieldGramsPerServing
                    ?: gramsPerServingResolved(food)
                    ?: return null
                if (gPerServing <= 0.0) return null
                servings * gPerServing
            }

            InputMode.GRAMS,
            InputMode.SERVING_UNIT -> {
                val amount = inputAmount ?: return null

                if (
                    inputMode == InputMode.SERVING_UNIT &&
                    measuredYieldGramsPerServing != null &&
                    inputUnit == food.servingUnit
                ) {
                    val servingSize = food.servingSize.takeIf { it > 0.0 } ?: 1.0
                    return (amount / servingSize) * measuredYieldGramsPerServing
                }

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

        val foodMlPerUnit =
            food.mlPerServingUnit?.takeIf { it > 0.0 }
                ?: food.servingUnit.asMl?.takeIf { it > 0.0 }
                ?: return null

        val gPerUnit = food.gramsPerServingUnitResolved() ?: return null
        if (gPerUnit <= 0.0) return null

        val densityGPerMl = gPerUnit / foodMlPerUnit
        val ml = a * inputMlPerUnit
        return ml * densityGPerMl
    }

    private fun activeMeasuredYieldGramsPerServingForSelectedBaseRecipe(): Double? {
        val food = selectedFoodFlow.value
        if (food?.isRecipe != true) return null
        if (selectedRecipeVariantIdFlow.value != null) return null

        val activeYieldGrams = activeMeasuredYieldFlow.value
            ?.yieldGrams
            ?.takeIf { it > 0.0 }
            ?: return null

        val servingsYield = selectedRecipeServingsYieldDefaultFlow.value
            ?.takeIf { it > 0.0 }
            ?: return null

        return activeYieldGrams / servingsYield
    }

    private fun buildAmountInputForSave(): AmountInput? {
        val food = selectedFoodFlow.value ?: return null
        val servings = servingsFlow.value

        val gramsForSave = computeGramsAmount(
            food = food,
            servings = servings,
            inputMode = inputModeFlow.value,
            inputUnit = inputUnitFlow.value,
            inputAmount = inputAmountFlow.value,
            measuredYieldGramsPerServing = activeMeasuredYieldGramsPerServingForSelectedBaseRecipe()
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

                    val selectedVariantId = selectedRecipeVariantIdFlow.value

                    if (selectedVariantId != null) {
                        val variant = recipeVariantRepository.getVariantById(selectedVariantId)
                            ?: run {
                                errorFlow.value = "Recipe variant not found."
                                return@launch
                            }

                        if (inputModeFlow.value == InputMode.GRAMS) {
                            errorFlow.value = "Recipe variants can be logged by servings for now."
                            return@launch
                        }

                        createLogEntry.execute(
                            ref = FoodRef.RecipeVariant(
                                recipeId = recipeId,
                                variantId = selectedVariantId,
                                stableId = stableId,
                                displayName = "${food.name} • ${variant.name}",
                                servingsYieldDefault = variant.servingsYieldOverride
                                    ?: servingsYieldDefault,
                            ),
                            timestamp = timestamp,
                            amountInput = amountInput,
                            recipeBatchId = null,
                            logDateIso = logDate.toString(),
                            mealSlot = mealSlot,
                        )
                    } else {
                        val selectedBatchId = selectedBatchIdFlow.value

                        /*
                         * Base recipe gram logging now uses RecipeMeasuredYield.
                         *
                         * Cooked batches are still supported as a legacy path when a batch id is
                         * already present, but normal Quick Add recipe grams should no longer route
                         * the user toward creating/selecting a cooked batch.
                         */
                        val batchId =
                            when (inputModeFlow.value) {
                                InputMode.GRAMS -> {
                                    if (selectedBatchId != null) {
                                        selectedBatchId
                                    } else {
                                        val activeYieldGrams = activeMeasuredYieldFlow.value
                                            ?.yieldGrams
                                            ?.takeIf { it > 0.0 }

                                        if (activeYieldGrams == null) {
                                            errorFlow.value =
                                                "Set a measured cooked yield for this recipe before logging by grams."
                                            return@launch
                                        }

                                        null
                                    }
                                }

                                else -> selectedBatchId
                            }

                        createLogEntry.execute(
                            ref = FoodRef.Recipe(
                                recipeId = recipeId,
                                stableId = stableId,
                                displayName = food.name,
                                servingsYieldDefault = servingsYieldDefault
                            ),
                            timestamp = timestamp,
                            amountInput = amountInput,
                            recipeBatchId = batchId,
                            logDateIso = logDate.toString(),
                            mealSlot = mealSlot
                        )
                    }
                }

                when (result) {
                    is CreateLogEntryUseCase.Result.Success -> {
                        requestCaffeineWidgetRefresh()
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
                        requestCaffeineWidgetRefresh()
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

    private fun requestCaffeineWidgetRefresh() {
        CaffeineWidgetProvider.requestRefresh(appContext)
    }

    private fun buildNutrientCautions(
        snapshot: FoodNutritionSnapshot?,
        gramsAmount: Double?
    ): List<QuickAddNutrientCaution> {
        val grams = gramsAmount?.takeIf { it > 0.0 } ?: return emptyList()
        val perGram = snapshot?.nutrientsPerGram ?: return emptyList()

        val sodiumMg = perGram[NutrientKey.SODIUM_MG] * grams
        val sugarsG = perGram[NutrientKey.SUGARS_G] * grams

        return buildList {
            if (sodiumMg > QUICK_ADD_SODIUM_CAUTION_MG) {
                add(
                    QuickAddNutrientCaution(
                        label = "Sodium",
                        amountText = "${formatCompact(sodiumMg)} mg",
                        message = "Use caution: this entry has about ${formatCompact(sodiumMg)} mg sodium."
                    )
                )
            }

            if (sugarsG > QUICK_ADD_SUGAR_CAUTION_G) {
                add(
                    QuickAddNutrientCaution(
                        label = "Total sugars",
                        amountText = "${formatCompact(sugarsG)} g",
                        message = "Use caution: this entry has about ${formatCompact(sugarsG)} g total sugars."
                    )
                )
            }
        }
    }

    private companion object {
        private const val QUICK_ADD_SODIUM_CAUTION_MG = 600.0
        private const val QUICK_ADD_SUGAR_CAUTION_G = 15.0
    }
}