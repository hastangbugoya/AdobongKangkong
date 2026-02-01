package com.example.adobongkangkong.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.data.local.db.dao.FoodGoalFlagsDao
import com.example.adobongkangkong.data.local.db.dao.RecipeBatchDao
import com.example.adobongkangkong.data.local.db.dao.RecipeDao
import com.example.adobongkangkong.data.local.db.entity.RecipeBatchEntity
import com.example.adobongkangkong.domain.logging.CreateLogEntryUseCase
import com.example.adobongkangkong.domain.logging.model.AmountInput
import com.example.adobongkangkong.domain.logging.model.BatchSummary
import com.example.adobongkangkong.domain.logging.model.FoodRef
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.nutrition.gramsPerServingResolved
import com.example.adobongkangkong.domain.recipes.CreateSnapshotFoodFromRecipeUseCase
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
import javax.inject.Inject

@HiltViewModel
class QuickAddViewModel @Inject constructor(
    private val searchFoods: SearchFoodsUseCase,
    private val createLogEntry: CreateLogEntryUseCase,
    private val recipeDao: RecipeDao,
    private val recipeBatchDao: RecipeBatchDao,
    private val createBatchFoodFromRecipeUseCase: CreateSnapshotFoodFromRecipeUseCase,
    private val foodGoalFlagsDao: FoodGoalFlagsDao,
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")
    private val selectedFoodFlow = MutableStateFlow<Food?>(null)

    // canonical amount
    private val servingsFlow = MutableStateFlow(1.0)
    private val inputModeFlow = MutableStateFlow(InputMode.SERVINGS)

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

    private val gramsTypedFlow = MutableStateFlow<Double?>(null)

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
        val inputMode: InputMode
    )

    private data class RecipeInputs(
        val gramsTyped: Double?,
        val batches: List<BatchSummary>,
        val selectedBatchId: Long?,
        val yieldGramsText: String,
        val servingsYieldText: String
    )

    private data class UiFlags(
        val isDialogOpen: Boolean,
        val isSaving: Boolean,
        val error: String?
    )

    val state: StateFlow<QuickAddState> = run {
        /**
         * Why this is structured in groups:
         * Some coroutines versions do not provide typed `combine` overloads for large arity
         * (e.g., 13 flows). Grouping keeps everything type-safe and avoids `Array<Any?>` indices.
         */
        val coreFlow = combine(
            queryFlow,
            resultsUiFlow,
            selectedFoodFlow,
            servingsFlow,
            inputModeFlow
        ) { query, results, selected, servings, inputMode ->
            CoreInputs(
                query = query,
                results = results,
                selected = selected,
                servings = servings,
                inputMode = inputMode
            )
        }

        val recipeFlow = combine(
            gramsTypedFlow,
            batchesFlow,
            selectedBatchIdFlow,
            yieldGramsTextFlow,
            servingsYieldTextFlow
        ) { gramsTyped, batches, selectedBatchId, yieldGramsText, servingsYieldText ->
            RecipeInputs(
                gramsTyped = gramsTyped,
                batches = batches,
                selectedBatchId = selectedBatchId,
                yieldGramsText = yieldGramsText,
                servingsYieldText = servingsYieldText
            )
        }

        val flagsFlow = combine(
            isCreateBatchDialogOpenFlow,
            isSavingFlow,
            errorFlow
        ) { isDialogOpen, isSaving, error ->
            UiFlags(
                isDialogOpen = isDialogOpen,
                isSaving = isSaving,
                error = error
            )
        }

        combine(coreFlow, recipeFlow, flagsFlow) { core, recipe, flags ->

            val servingUnitAmount = core.selected?.let { core.servings * it.servingSize }

            // Input-mode-driven grams:
            val gramsAmount = when (core.inputMode) {
                InputMode.GRAMS -> recipe.gramsTyped
                else -> core.selected?.gramsPerServingResolved()?.let { g -> core.servings * g }
            }

            QuickAddState(
                query = core.query,
                results = core.results,
                selectedFood = core.selected,
                servings = core.servings,
                servingUnitAmount = servingUnitAmount,
                gramsAmount = gramsAmount,
                inputMode = core.inputMode,

                batches = recipe.batches,
                selectedBatchId = recipe.selectedBatchId,
                yieldGramsText = recipe.yieldGramsText,
                servingsYieldText = recipe.servingsYieldText,

                isCreateBatchDialogOpen = flags.isDialogOpen,
                isSaving = flags.isSaving,
                errorMessage = flags.error
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

    fun onServingsChanged(servings: Double) {
        gramsTypedFlow.value = null
        servingsFlow.value = servings.coerceAtLeast(0.0)
        inputModeFlow.value = InputMode.SERVINGS
    }

    fun onServingUnitAmountChanged(amount: Double) {
        gramsTypedFlow.value = null
        val food = selectedFoodFlow.value ?: return
        if (food.servingSize <= 0.0) return
        servingsFlow.value = (amount / food.servingSize).coerceAtLeast(0.0)
        inputModeFlow.value = InputMode.SERVING_UNIT
    }

    fun onGramsChanged(grams: Double) {
        val food = selectedFoodFlow.value ?: return
        val g = food.gramsPerServingResolved() ?: return
        if (g <= 0.0) return

        gramsTypedFlow.value = grams.coerceAtLeast(0.0)
        servingsFlow.value = (grams / g).coerceAtLeast(0.0)
        inputModeFlow.value = InputMode.GRAMS
    }

    fun onPackageClicked(multiplier: Double = 1.0) {
        val food = selectedFoodFlow.value ?: return
        val spp = food.servingsPerPackage ?: return
        servingsFlow.value = (spp * multiplier).coerceAtLeast(0.0)
        inputModeFlow.value = InputMode.SERVINGS
    }

    // -----------------------------
    // Recipe batch UI
    // -----------------------------

    fun onBatchSelected(batchId: Long?) {
        selectedBatchIdFlow.value = batchId
    }

    fun openCreateBatchDialog() {
        isCreateBatchDialogOpenFlow.value = true
        yieldGramsTextFlow.value = ""
        servingsYieldTextFlow.value = ""
        errorFlow.value = null
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

    // -----------------------------
    // Save (log entry)
    // -----------------------------

    fun save(onDone: () -> Unit) {
        val food = selectedFoodFlow.value ?: return
        val servings = servingsFlow.value
        if (servings <= 0.0) return

        val amountInput =
            when (inputModeFlow.value) {
                InputMode.GRAMS -> {
                    val grams = gramsTypedFlow.value
                        ?: run {
                            errorFlow.value = "Enter grams."
                            return
                        }
                    AmountInput.ByGrams(grams)
                }
                InputMode.SERVINGS, InputMode.SERVING_UNIT -> {
                    AmountInput.ByServings(servings)
                }
            }

        viewModelScope.launch {
            isSavingFlow.value = true
            errorFlow.value = null

            try {
                val now = Instant.now()

                val result = if (!food.isRecipe) {
                    // Regular food
                    createLogEntry.execute(
                        ref = FoodRef.Food(
                            foodId = food.id,
                        ),
                        timestamp = now,
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
                    is CreateLogEntryUseCase.Result.Blocked -> {
                        errorFlow.value = result.message
                    }
                    is CreateLogEntryUseCase.Result.Error -> {
                        errorFlow.value = result.message
                    }
                }
            } finally {
                isSavingFlow.value = false
            }
        }
    }
}
