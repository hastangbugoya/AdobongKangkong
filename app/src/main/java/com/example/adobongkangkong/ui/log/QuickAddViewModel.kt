package com.example.adobongkangkong.ui.log

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.data.local.db.dao.FoodGoalFlagsDao
import com.example.adobongkangkong.data.local.db.dao.RecipeBatchDao
import com.example.adobongkangkong.data.local.db.dao.RecipeDao
import com.example.adobongkangkong.data.local.db.entity.MealSlot
import com.example.adobongkangkong.domain.logging.CreateLogEntryUseCase
import com.example.adobongkangkong.domain.logging.model.AmountInput
import com.example.adobongkangkong.domain.logging.model.BatchSummary
import com.example.adobongkangkong.domain.logging.model.FoodRef
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.nutrition.gramsPerServingUnitResolved
import com.example.adobongkangkong.domain.recipes.CreateSnapshotFoodFromRecipeUseCase
import com.example.adobongkangkong.domain.planner.usecase.CreateIouUseCase
import com.example.adobongkangkong.domain.repository.FoodBarcodeRepository
import com.example.adobongkangkong.domain.repository.FoodNutritionSnapshotRepository
import com.example.adobongkangkong.domain.repository.FoodRepository
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

@HiltViewModel
class QuickAddViewModel @Inject constructor(
    private val searchFoods: SearchFoodsUseCase,
    private val createLogEntry: CreateLogEntryUseCase,
    private val recipeDao: RecipeDao,
    private val recipeBatchDao: RecipeBatchDao,
    private val createBatchFoodFromRecipeUseCase: CreateSnapshotFoodFromRecipeUseCase,
    private val foodGoalFlagsDao: FoodGoalFlagsDao,
    private val foodRepository: FoodRepository,

    // validation + snapshot access for preflight gating
    private val snapshotRepo: FoodNutritionSnapshotRepository,
    private val validateFoodForUsage: ValidateFoodForUsageUseCase,
    private val foodBarcodeRepository: FoodBarcodeRepository,

    // IOUs (planner narrative placeholders)
    private val createPlannerIou: CreateIouUseCase,
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")
    private val selectedFoodFlow = MutableStateFlow<Food?>(null)

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

    private data class PendingResolveMass(
        val food: Food,
        val timestamp: Instant,
        val amountInput: AmountInput,
        val logDateIso: String
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
    private val isSavingIouFlow = MutableStateFlow(false)
    private val iouErrorFlow = MutableStateFlow<String?>(null)

    fun openIouDialog() {
        iouDescriptionFlow.value = ""
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

    fun saveIou(logDate: LocalDate, onSaved: () -> Unit) {
        if (isSavingIouFlow.value) return

        val desc = iouDescriptionFlow.value.trim()
        if (desc.isBlank()) {
            iouErrorFlow.value = "Description is required."
            return
        }

        isSavingIouFlow.value = true
        iouErrorFlow.value = null

        viewModelScope.launch {
            try {
                createPlannerIou(
                    dateIso = logDate.toString(),
                    description = desc
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
        // Keep digits only (scanner/labels often include whitespace).
        return raw.trim().filter { it.isDigit() }
    }

    /**
     * Called by the scanner UI when a barcode is read.
     */
    fun onBarcodeScanned(barcode: String) {
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

    // -----------------------------
    // Navigation events (optional)
    // -----------------------------

    sealed class QuickAddNavEvent {
        data class Navigate(val route: String) : QuickAddNavEvent()
    }

    private val navEventFlow = MutableStateFlow<QuickAddNavEvent?>(null)
    val navEvent: StateFlow<QuickAddNavEvent?> = navEventFlow.asStateFlow()

    fun consumeNavEvent() {
        navEventFlow.value = null
    }

    // -----------------------------
    // State combine (typed, grouped)
    // -----------------------------

    private data class CoreInputs(
        val query: String,
        val results: List<FoodListItemUiModel>,
        val selected: Food?,
        val servings: Double,
        val mealSlot: MealSlot?,
        val inputMode: InputMode,
        val inputUnit: ServingUnit,
        val inputAmount: Double?
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
    )

    private data class BarcodeUi(
        val isScannerOpen: Boolean,
        val found: FoundBarcodeDialogState?,
        val notFound: NotFoundBarcodeDialogState?
    )

    private data class IouUi(
        val isOpen: Boolean,
        val description: String,
        val isSaving: Boolean,
        val error: String?
    )

    val state: StateFlow<QuickAddState> = run {
        data class CoreA(
            val query: String,
            val results: List<FoodListItemUiModel>,
            val selected: Food?,
            val servings: Double,
            val mealSlot: MealSlot?
        )

        data class CoreB(
            val inputMode: InputMode,
            val inputUnit: ServingUnit,
            val inputAmount: Double?
        )

        val coreAFlow = combine(
            queryFlow,
            resultsUiFlow,
            selectedFoodFlow,
            servingsFlow,
            mealSlotFlow
        ) { query, results, selected, servings, mealSlot ->
            CoreA(query, results, selected, servings, mealSlot)
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
                inputAmount = b.inputAmount
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
            isCreateBatchDialogOpenFlow,
            isSavingFlow,
            errorFlow,
            isResolveMassDialogOpenFlow,
            gramsPerServingTextFlow
        ) { isDialogOpen, isSaving, error, isResolveMassDialogOpen, gramsPerServingText ->
            UiFlags(
                isDialogOpen = isDialogOpen,
                isSaving = isSaving,
                error = error,
                isResolveMassDialogOpen = isResolveMassDialogOpen,
                gramsPerServingText = gramsPerServingText
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
            isIouDialogOpenFlow,
            iouDescriptionFlow,
            isSavingIouFlow,
            iouErrorFlow
        ) { isOpen, description, isSaving, error ->
            IouUi(
                isOpen = isOpen,
                description = description,
                isSaving = isSaving,
                error = error
            )
        }

        combine(coreFlow, recipeFlow, flagsFlow, barcodeFlow, iouFlow) { core, recipe, flags, barcode, iou ->

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

                isScannerOpen = barcode.isScannerOpen,
                foundBarcodeDialogFood = barcode.found?.food,
                foundBarcodeDialogBarcode = barcode.found?.barcode,
                notFoundBarcodeDialogBarcode = barcode.notFound?.barcode,

                isIouDialogOpen = iou.isOpen,
                iouDescription = iou.description,
                isSavingIou = iou.isSaving,
                iouErrorMessage = iou.error,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QuickAddState())
    }

    // -----------------------------
    // Inputs
    // -----------------------------

    fun onQueryChange(q: String) {
        queryFlow.value = q
    }

    fun onFoodSelected(food: Food) {
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
            if (!food.isRecipe) {
                selectedRecipeIdFlow.value = null
                selectedRecipeStableIdFlow.value = null
                selectedRecipeServingsYieldDefaultFlow.value = null
                selectedBatchIdFlow.value = null
                return@launch
            }

            val recipe = recipeDao.getByFoodId(food.id)
            if (recipe == null) {
                selectedRecipeIdFlow.value = null
                selectedRecipeStableIdFlow.value = null
                selectedRecipeServingsYieldDefaultFlow.value = null
                selectedBatchIdFlow.value = null
                errorFlow.value = "Recipe data missing for this item."
                return@launch
            }

            selectedRecipeIdFlow.value = recipe.id
            selectedRecipeStableIdFlow.value = recipe.stableId
            selectedRecipeServingsYieldDefaultFlow.value = recipe.servingsYield
            selectedBatchIdFlow.value = null
        }
    }

    fun clearSelection() {
        selectedFoodFlow.value = null
        servingsFlow.value = 1.0
        inputModeFlow.value = InputMode.SERVINGS

        mealSlotFlow.value = null

        selectedRecipeIdFlow.value = null
        selectedRecipeStableIdFlow.value = null
        selectedRecipeServingsYieldDefaultFlow.value = null
        selectedBatchIdFlow.value = null

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
                    // Density derived from "per-unit" bridges:
                    // gPerUnit / mlPerUnit (servingSize cancels out).
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

    // -----------------------------
    // Recipe batch UI
    // -----------------------------

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

    // -----------------------------
    // Resolve-mass dialog
    // -----------------------------

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
                    // Note: persistGpsu here is interpreted as grams-per-1-serving (consistent with the resolve dialog).
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

    // -----------------------------
    // Unit conversion helpers (ServingUnit.asG / asMl)
    // -----------------------------

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
            // User entered an amount in the food’s servingUnit (e.g., TBSP).
            // gramsPerServingUnitResolved() behaves like grams-per-1-unit here.
            val gPerUnit = food.gramsPerServingUnitResolved() ?: return null
            if (gPerUnit <= 0.0) return null
            return a * gPerUnit
        }

        val inputMlPerUnit = unit.asMl ?: return null
        val foodMlPerUnit = food.servingUnit.asMl ?: return null
        if (foodMlPerUnit <= 0.0) return null

        val gPerUnit = food.gramsPerServingUnitResolved() ?: return null
        if (gPerUnit <= 0.0) return null

        // Density derived from "per-unit" bridges: gPerUnit / mlPerUnit.
        val densityGPerMl = gPerUnit / foodMlPerUnit
        val ml = a * inputMlPerUnit
        return ml * densityGPerMl
    }

    // -----------------------------
    // Save (log entry)
    // -----------------------------

    fun save(onDone: () -> Unit, logDate: LocalDate) {
        val food = selectedFoodFlow.value ?: return
        val servings = servingsFlow.value
        val mealSlot = mealSlotFlow.value

        val gramsForSave = computeGramsAmount(
            food = food,
            servings = servings,
            inputMode = inputModeFlow.value,
            inputUnit = inputUnitFlow.value,
            inputAmount = inputAmountFlow.value
        )

        val amountInput =
            if (gramsForSave != null && gramsForSave > 0.0 && inputModeFlow.value != InputMode.SERVINGS) {
                AmountInput.ByGrams(gramsForSave)
            } else {
                if (servings <= 0.0) return
                AmountInput.ByServings(servings)
            }

        Log.d("Meow", "QuickAddViewModel > save START amountInput=$amountInput selected=${selectedFoodFlow.value?.name}")

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
                        ref = FoodRef.Food(
                            foodId = food.id,
                        ),
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
                    val servingsYieldDefault =
                        selectedRecipeServingsYieldDefaultFlow.value ?: 1.0

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
}