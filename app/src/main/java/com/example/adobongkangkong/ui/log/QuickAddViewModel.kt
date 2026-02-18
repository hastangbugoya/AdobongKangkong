package com.example.adobongkangkong.ui.log

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.core.log.MeowLog
import com.example.adobongkangkong.data.local.db.dao.FoodGoalFlagsDao
import com.example.adobongkangkong.data.local.db.dao.RecipeBatchDao
import com.example.adobongkangkong.data.local.db.dao.RecipeDao
import com.example.adobongkangkong.data.local.db.entity.RecipeBatchEntity
import com.example.adobongkangkong.domain.logging.CreateLogEntryUseCase
import com.example.adobongkangkong.domain.logging.model.AmountInput
import com.example.adobongkangkong.domain.logging.model.BatchSummary
import com.example.adobongkangkong.domain.logging.model.FoodRef
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.model.gPerUnit
import com.example.adobongkangkong.domain.model.gPerUnitOrNull
import com.example.adobongkangkong.domain.nutrition.gramsPerServingUnitResolved
import com.example.adobongkangkong.domain.recipes.CreateSnapshotFoodFromRecipeUseCase
import com.example.adobongkangkong.domain.repository.FoodRepository
import com.example.adobongkangkong.domain.usecase.SearchFoodsUseCase
import com.example.adobongkangkong.ui.food.FoodListItemUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject

@HiltViewModel
class QuickAddViewModel @Inject constructor(
    private val searchFoods: SearchFoodsUseCase,
    private val createLogEntry: CreateLogEntryUseCase,
    private val recipeDao: RecipeDao,
    private val recipeBatchDao: RecipeBatchDao,
    private val createBatchFoodFromRecipeUseCase: CreateSnapshotFoodFromRecipeUseCase,
    private val foodGoalFlagsDao: FoodGoalFlagsDao,
    private val foodRepository: FoodRepository,

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

    private val isSavingFlow = MutableStateFlow(false)
    private val errorFlow = MutableStateFlow<String?>(null)

    private val isResolveMassDialogOpenFlow = MutableStateFlow(false)
    private val gramsPerServingTextFlow = MutableStateFlow("")

    private data class PendingResolveMass(
        val food: Food,
        val timestamp: Instant,
        val amountInput: AmountInput
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

    /**
     * Aggregates all internal StateFlows into a single [QuickAddState] for UI consumption.
     *
     * ## Why flows are grouped instead of using index-based `combine`
     * The vararg version of `combine(flows...) { values -> ... }` exposes its inputs as
     * `Array<Any?>`, forcing index-based access (e.g., `values[3]`, `values[7]`).
     *
     * This approach is fragile:
     * - Adding, removing, or reordering flows silently shifts indices.
     * - Bugs introduced this way compile successfully but produce incorrect UI state.
     * - Refactors become high-risk and difficult to reason about.
     *
     * To avoid these pitfalls, flows are grouped into small, typed combine blocks
     * ([CoreInputs], [RecipeInputs], [UiFlags]) and then recombined.
     *
     * This keeps the mapping:
     * - Type-safe
     * - Self-documenting
     * - Refactor-friendly
     * - Resistant to subtle ordering bugs
     *
     * This pattern is especially valuable here because logging UX evolves frequently
     * (e.g., servings vs grams, recipe batches, yield tracking).
     */

    private data class CoreInputs(
        val query: String,
        val results: List<FoodListItemUiModel>,
        val selected: Food?,
        val servings: Double,
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

    val state: StateFlow<QuickAddState> = run {
        /**
         * Why this is structured in groups:
         * Some coroutines versions do not provide typed `combine` overloads for large arity
         * (e.g., 13 flows). Grouping keeps everything type-safe and avoids `Array<Any?>` indices.
         */
        data class CoreA(
            val query: String,
            val results: List<FoodListItemUiModel>,
            val selected: Food?,
            val servings: Double
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
            servingsFlow
        ) { query, results, selected, servings ->
            CoreA(
                query = query,
                results = results,
                selected = selected,
                servings = servings
            )
        }

        val coreBFlow = combine(
            inputModeFlow,
            inputUnitFlow,
            inputAmountFlow
        ) { inputMode, inputUnit, inputAmount ->
            CoreB(
                inputMode = inputMode,
                inputUnit = inputUnit,
                inputAmount = inputAmount
            )
        }

        val coreFlow = combine(coreAFlow, coreBFlow) { a, b ->
            CoreInputs(
                query = a.query,
                results = a.results,
                selected = a.selected,
                servings = a.servings,
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

        combine(coreFlow, recipeFlow, flagsFlow) { core, recipe, flags ->

            val gramsAmount: Double? = core.selected?.let { food ->
                computeGramsAmount(
                    food = food,
                    servings = core.servings,
                    inputMode = core.inputMode,
                    inputUnit = core.inputUnit,
                    inputAmount = core.inputAmount
                )
            }

            // Serving-equivalent is only computable when we have grams-per-serving (or when user is in SERVINGS mode).
            val servingsEquivalent: Double? = core.selected?.let { food ->
                when (core.inputMode) {
                    InputMode.SERVINGS -> core.servings
                    else -> {
                        val grams = gramsAmount ?: return@let null
                        val gPerServing = food.gramsPerServingUnitResolved() ?: return@let null
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
                yieldGramsText = recipe.yieldGramsText,
                servingsYieldText = recipe.servingsYieldText,

                isCreateBatchDialogOpen = flags.isDialogOpen,
                isSaving = flags.isSaving,
                errorMessage = flags.error,
                isResolveMassDialogOpen = flags.isResolveMassDialogOpen,   // ✅ ADD
                gramsPerServingText = flags.gramsPerServingText            // ✅ ADD
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QuickAddState())
    }

    fun onQueryChange(q: String) {
        queryFlow.value = q
    }

    fun onFoodSelected(food: Food) {
        selectedFoodFlow.value = food
        servingsFlow.value = 1.0
        inputModeFlow.value = InputMode.SERVINGS
        Log.d("Meow", "QuickAdd init foodId=${food.id} name=${food.name} gramsPerServing=${food.gramsPerServingUnitResolved()}")

        // Default input unit: prefer the food serving unit when it is convertible, otherwise grams.
        inputUnitFlow.value = when (food.servingUnit) {
            ServingUnit.MG, ServingUnit.G, ServingUnit.KG, ServingUnit.OZ, ServingUnit.LB,
            ServingUnit.ML, ServingUnit.L,
            ServingUnit.TSP_US, ServingUnit.TBSP_US, ServingUnit.FL_OZ_US, ServingUnit.CUP_US, ServingUnit.PINT_US, ServingUnit.QUART_US, ServingUnit.GALLON_US,
            ServingUnit.CUP_METRIC, ServingUnit.CUP_JP, ServingUnit.RCCUP,
            ServingUnit.FL_OZ_IMP, ServingUnit.PINT_IMP, ServingUnit.QUART_IMP, ServingUnit.GALLON_IMP,
            ServingUnit.TSP, ServingUnit.TBSP, ServingUnit.CUP, ServingUnit.QUART,
            ServingUnit.SERVING -> food.servingUnit
            else -> ServingUnit.G
        }
        // For 1 serving, the natural amount shown is the serving size in its serving unit.
        inputAmountFlow.value = food.servingSize.coerceAtLeast(0.0)

        errorFlow.value = null

        // Resolve recipe context if this Food is a recipe “proxy”
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
                // Recipe “Food” exists but Recipe row missing: keep UI usable, but disable recipe-specific logging.
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

            // If we already have batches, default-select newest (first) if none selected.
            // (The batchesFlow will emit shortly; this is just a safe initial state.)
            selectedBatchIdFlow.value = null
        }
    }

    fun clearSelection() {
        selectedFoodFlow.value = null
        servingsFlow.value = 1.0
        inputModeFlow.value = InputMode.SERVINGS

        selectedRecipeIdFlow.value = null
        selectedRecipeStableIdFlow.value = null
        selectedRecipeServingsYieldDefaultFlow.value = null
        selectedBatchIdFlow.value = null

        errorFlow.value = null
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

        // Keep grams constant when possible; otherwise leave amount as-is.
        if (currentGrams != null) {
            // If new unit is mass, rewrite amount accordingly.
            unit.gPerUnitOrNull()?.let { gPerUnit ->
                inputAmountFlow.value = currentGrams / gPerUnit
                inputModeFlow.value = InputMode.GRAMS
                return
            }
            // If new unit is volume, rewrite amount accordingly (only if density is available).
            unit.mlPerUnitOrNull()?.let { mlPerUnit ->
                val grams = currentGrams
                val foodMlPerUnit = food.servingUnit.mlPerUnitOrNull()
                val gPerServing = food.gramsPerServingUnitResolved()
                if (foodMlPerUnit != null && gPerServing != null && gPerServing > 0.0 && food.servingSize > 0.0) {
                    val density = gPerServing / (food.servingSize * foodMlPerUnit)
                    if (density > 0.0) {
                        val ml = grams / density
                        inputAmountFlow.value = ml / mlPerUnit
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

        // Decide mode based on unit kind.
        val grams = computeGramsFromAmountAndUnit(food, a, unit)

        if (grams != null) {
            // If we can compute grams, sync servings when possible.
            val gPerServing = food.gramsPerServingUnitResolved()
            if (gPerServing != null && gPerServing > 0.0) {
                servingsFlow.value = (grams / gPerServing).coerceAtLeast(0.0)
            }
            inputModeFlow.value = if (unit.gPerUnitOrNull() != null) InputMode.GRAMS else InputMode.SERVING_UNIT
        } else {
            // Fallback: if user picked the food's serving unit, we can still treat it as serving-unit input.
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

        // Keep the amount field aligned with the food's serving unit when possible.
        selectedFoodFlow.value?.let { food ->
            inputUnitFlow.value = food.servingUnit
            inputAmountFlow.value = s * food.servingSize
        }
    }

    fun onServingUnitAmountChanged(amount: Double) {
        val food = selectedFoodFlow.value ?: return
        // Legacy behavior: amount in the food's own serving unit.
        inputUnitFlow.value = food.servingUnit
        onInputAmountChanged(amount)
    }

    fun onGramsChanged(grams: Double) {
        val food = selectedFoodFlow.value ?: return
        val gramsClamped = grams.coerceAtLeast(0.0)

        inputUnitFlow.value = ServingUnit.G
        inputAmountFlow.value = gramsClamped
        inputModeFlow.value = InputMode.GRAMS

        // If grams-per-serving is known, keep servings stepper in sync.
        val gPerServing = food.gramsPerServingUnitResolved()
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
            val gramsPerServing = food.gramsPerServingUnitResolved()

            // Prefill servings used with the recipe default servingsYield (editable)
            servingsYieldTextFlow.value =
                servingsDefault?.let { formatCompact(it) }.orEmpty()

            // Prefill cooked yield grams with total-ingredient-weight estimate (editable)
            yieldGramsTextFlow.value =
                if (servingsDefault != null && gramsPerServing != null) {
                    formatCompact(servingsDefault * gramsPerServing)
                } else {
                    ""
                }
        } else {
            // Non-recipe: keep current behavior (blank)
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
            try {


                val result = createBatchFoodFromRecipeUseCase.execute(
                    recipeId = recipeId,
                    cookedYieldGrams = cookedYieldGrams,
                    servingsYieldUsed = servingsYieldUsed
                )

                val batchFoodId = result.batchFoodId

                // 2️⃣ Create batch referencing snapshot food
                val batchId = recipeBatchDao.insert(
                    RecipeBatchEntity(
                        recipeId = recipeId,
                        batchFoodId = batchFoodId,
                        cookedYieldGrams = cookedYieldGrams,
                        servingsYieldUsed = servingsYieldUsed
                    )
                )

                selectedBatchIdFlow.value = batchId
                isCreateBatchDialogOpenFlow.value = false
                yieldGramsTextFlow.value = ""
                servingsYieldTextFlow.value = ""
                errorFlow.value = null
            } catch (t: Throwable) {
                errorFlow.value = t.message ?: "Failed to create batch."
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
        val ml = pending.food.mlPerServingUnit ?: return

        retryPendingLog(overrideGpsu = ml, persistGpsu = null)
    }

    fun useEstimateAlways() {
        val pending = pendingResolveMass ?: return
        val ml = pending.food.mlPerServingUnit ?: return

        retryPendingLog(overrideGpsu = null, persistGpsu = ml)
    }

    fun confirmEnteredGramsPerServing() {
        val pending = pendingResolveMass ?: return
        val grams = gramsPerServingTextFlow.value.toDoubleOrNull()
            ?.takeIf { it > 0.0 } ?: return

        retryPendingLog(overrideGpsu = null, persistGpsu = grams)
    }

    private fun retryPendingLog(
        overrideGpsu: Double?,
        persistGpsu: Double?
    ) {
        val pending = pendingResolveMass ?: return

        viewModelScope.launch {
            try {
                // Persist if requested (Option A)
                if (persistGpsu != null) {
                    foodRepository.upsert(
                        pending.food.copy(gramsPerServingUnit = persistGpsu)
                    )
                    // refresh local selected food so UI math updates
                    selectedFoodFlow.value = pending.food.copy(gramsPerServingUnit = persistGpsu)
                }

                val result = createLogEntry.execute(
                    ref = FoodRef.Food(pending.food.id),
                    timestamp = pending.timestamp,
                    amountInput = pending.amountInput,
                    overrideGramsPerServingUnit = overrideGpsu
                )
                Log.d("Meow", "QuickAddViewModel > createLogEntry result = $result")
                when (result) {
                    is CreateLogEntryUseCase.Result.Success -> {
                        closeResolveMassDialog()
                        clearSelection()
                        queryFlow.value = ""
                        // NOTE: call onDone() from original save flow if needed;
                        // easiest is to store it in pending too, if you want auto-dismiss.
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
    // Unit conversion helpers (QuickAdd)
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
                val gPerServing = food.gramsPerServingUnitResolved() ?: return null
                if (gPerServing <= 0.0) return null
                servings * gPerServing
            }
            // User typed an amount in some unit (mass or volume or count-ish).
            InputMode.GRAMS, InputMode.SERVING_UNIT -> {
                val amount = inputAmount ?: return null
                computeGramsFromAmountAndUnit(food, amount, inputUnit)
            }
        }
    }

    private fun computeGramsFromAmountAndUnit(food: Food, amount: Double, unit: ServingUnit): Double? {
        val a = amount.coerceAtLeast(0.0)

        // Mass input: direct conversion to grams.
        unit.gPerUnitOrNull()?.let { gPerUnit ->
            return a * gPerUnit
        }

        // Special case: user chooses "SERVING" explicitly (means servings-count).
        if (unit == ServingUnit.SERVING) {
            val gPerServing = food.gramsPerServingUnitResolved() ?: return null
            if (gPerServing <= 0.0) return null
            return a * gPerServing
        }

        // If user picks the food's own serving unit (piece/cup/etc.), treat as a serving-unit amount.
        if (unit == food.servingUnit) {
            if (food.servingSize <= 0.0) return null
            val servings = a / food.servingSize
            val gPerServing = food.gramsPerServingUnitResolved() ?: return null
            if (gPerServing <= 0.0) return null
            return servings * gPerServing
        }

        // Volume input: needs density derived from the food's serving definition.
        val inputMlPerUnit = unit.mlPerUnitOrNull() ?: return null
        val foodMlPerUnit = food.servingUnit.mlPerUnitOrNull() ?: return null
        if (food.servingSize <= 0.0) return null

        val gPerServing = food.gramsPerServingUnitResolved() ?: return null
        if (gPerServing <= 0.0) return null

        // gramsPerServingUnit corresponds to (servingSize * foodServingUnit) volume.
        val mlPerServing = food.servingSize * foodMlPerUnit
        if (mlPerServing <= 0.0) return null

        val densityGPerMl = gPerServing / mlPerServing
        val ml = a * inputMlPerUnit
        return ml * densityGPerMl
    }

    private fun ServingUnit.mlPerUnitOrNull(): Double? = when (this) {
        // Metric
        ServingUnit.ML -> 1.0
        ServingUnit.L -> 1000.0

        // US nutrition-standard set (internally consistent)
        ServingUnit.CUP_US -> 240.0
        ServingUnit.TBSP_US -> 240.0 / 16.0
        ServingUnit.TSP_US -> (240.0 / 16.0) / 3.0
        ServingUnit.FL_OZ_US -> 240.0 / 8.0
        ServingUnit.PINT_US -> 240.0 * 2.0
        ServingUnit.QUART_US -> 240.0 * 4.0
        ServingUnit.GALLON_US -> 240.0 * 16.0

        // Cup variants
        ServingUnit.CUP_METRIC -> 250.0
        ServingUnit.CUP_JP -> 200.0

        // Rice cooker cup (international)
        ServingUnit.RCCUP -> 180.0

        // Imperial (exact)
        ServingUnit.FL_OZ_IMP -> 28.4130625
        ServingUnit.PINT_IMP -> 568.26125
        ServingUnit.QUART_IMP -> 1136.5225
        ServingUnit.GALLON_IMP -> 4546.09

        // Legacy aliases: treat as US for compatibility
        ServingUnit.CUP -> ServingUnit.CUP_US.mlPerUnitOrNull()
        ServingUnit.TBSP -> ServingUnit.TBSP_US.mlPerUnitOrNull()
        ServingUnit.TSP -> ServingUnit.TSP_US.mlPerUnitOrNull()
        ServingUnit.QUART -> ServingUnit.QUART_US.mlPerUnitOrNull()

        else -> null
    }
// -----------------------------
    // Save (log entry)
    // -----------------------------

    fun save(onDone: () -> Unit, logDate: LocalDate) {
        val food = selectedFoodFlow.value ?: return
        val servings = servingsFlow.value
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
        Log.d("Meow","QuickAddViewModel > save START amountInput=$amountInput selected=${selectedFoodFlow.value?.name}")
        viewModelScope.launch {
            isSavingFlow.value = true
            errorFlow.value = null

            try {
                val now = Instant.now()
                val timestamp = ZonedDateTime.of(logDate, LocalTime.now(), ZoneId.systemDefault()).toInstant()
                // ✅ logs to the viewed date, at current clock time
                val result = if (!food.isRecipe) {
                    // Regular food
                    createLogEntry.execute(
                        ref = FoodRef.Food(
                            foodId = food.id,
                        ),
                        timestamp = timestamp,
                        amountInput = amountInput,
                        recipeBatchId = null
                    )
                } else {
                    // Recipe (Food is a proxy row; resolve recipeId)
                    val recipeId = selectedRecipeIdFlow.value
                        ?: run {
                            errorFlow.value = "Recipe data missing for this item."
                            isSavingFlow.value = false
                            return@launch
                        }

                    val stableId = selectedRecipeStableIdFlow.value ?: food.stableId
                    val servingsYieldDefault =
                        selectedRecipeServingsYieldDefaultFlow.value ?: 1.0

                    val batchId =
                        when (inputModeFlow.value) {
                            InputMode.GRAMS -> selectedBatchIdFlow.value
                                ?: run {
                                    // Logging by cooked grams MUST have a batch/yield context.
                                    // (That’s the whole point of RecipeBatchEntity.) :contentReference[oaicite:11]{index=11}
                                    errorFlow.value = "Select or create a cooked batch (yield grams) to log by grams."
                                    isSavingFlow.value = false
                                    return@launch
                                }
                            else -> selectedBatchIdFlow.value // optional for servings-based logging
                        }

                    createLogEntry.execute(
                        ref = FoodRef.Recipe(
                            recipeId = recipeId,
                            stableId = stableId,
                            displayName = food.name,
                            servingsYieldDefault = servingsYieldDefault
                        ),
                        timestamp = now,
                        amountInput = amountInput,
                        recipeBatchId = batchId
                    )
                }

                when (result) {
                    is CreateLogEntryUseCase.Result.Success -> {
                        // reset for speed logging
                        clearSelection()
                        queryFlow.value = ""
                        onDone()
                    }
                    is CreateLogEntryUseCase.Result.Blocked ->{
                        val selected = selectedFoodFlow.value
                        Log.d("Meow","QuickAddViewModel > selected.servingUnit ${selected?.servingUnit} selected.gramsPerServingUnit:${selected?.gramsPerServingUnit} selected.mlPerServingUnit:${selected?.mlPerServingUnit} amountInput:${amountInput.toString()}")
                        val isMissingGpsuBlock =
                            result.message.contains("grams-per-serving", ignoreCase = true)

                        val shouldOfferMlEstimate =
                            isMissingGpsuBlock &&
                                    selected != null &&
                                    amountInput is AmountInput.ByServings &&
                                    selected.gramsPerServingUnit == null &&
                                    selected.mlPerServingUnit != null

                        if (shouldOfferMlEstimate) {
                            Log.d("Meow", "QuickAddViewModel > opening resolve-mass dialog ml=${selected.mlPerServingUnit}")
                            pendingResolveMass = PendingResolveMass(
                                food = selected,
                                timestamp = timestamp,
                                amountInput = amountInput
                            )
                            gramsPerServingTextFlow.value = ""
                            isResolveMassDialogOpenFlow.value = true
                            errorFlow.value = null
                            return@launch
                        }
                        errorFlow.value = result.message
                    }
                    is CreateLogEntryUseCase.Result.Error -> {
                        errorFlow.value = result.message
                    }
                }
            } finally {
                Log.d("Meow","QuickAddViewModel > save FINALLY (clearing?)")
                isSavingFlow.value = false
            }
        }
    }
}



