package com.example.adobongkangkong.ui.recipe

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.model.RecipeDraft
import com.example.adobongkangkong.domain.model.RecipeIngredientDraft
import com.example.adobongkangkong.domain.model.RecipeMacroPreview
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.model.toGrams
import com.example.adobongkangkong.domain.model.toMilliliters
import com.example.adobongkangkong.domain.nutrition.ServingPolicy
import com.example.adobongkangkong.domain.nutrition.gramsPerServingUnitResolved
import com.example.adobongkangkong.domain.repository.FoodGoalFlagsRepository
import com.example.adobongkangkong.domain.repository.FoodRepository
import com.example.adobongkangkong.domain.repository.RecipeIngredientLine
import com.example.adobongkangkong.domain.repository.RecipeRepository
import com.example.adobongkangkong.domain.usecase.CreateRecipeUseCase
import com.example.adobongkangkong.domain.usecase.ObserveRecipeMacroPreviewUseCase
import com.example.adobongkangkong.ui.common.bottomsheet.BlockingSheetModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.max
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import com.example.adobongkangkong.domain.recipes.ComputeRecipeNutritionForSnapshotUseCase
import com.example.adobongkangkong.domain.recipes.RecipeNutritionWarning
import com.example.adobongkangkong.domain.repository.NutrientRepository
import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.model.NutrientCategory
import com.example.adobongkangkong.ui.food.editor.NutrientRowUi
import kotlin.math.abs


@HiltViewModel
class RecipeBuilderViewModel @Inject constructor(
    private val foodRepo: FoodRepository,
    private val recipeRepo: RecipeRepository,
    private val createRecipe: CreateRecipeUseCase,
    private val flagsRepository: FoodGoalFlagsRepository,
    private val computeRecipeNutrition: ComputeRecipeNutritionForSnapshotUseCase,
    private val nutrientRepo: NutrientRepository,
    observePreview: ObserveRecipeMacroPreviewUseCase,
) : ViewModel() {

    private var editFoodId: Long? = null
    private val hasUnsavedChangesFlow = MutableStateFlow(false)
    private val nameFlow = MutableStateFlow("")
    private val servingsYieldFlow = MutableStateFlow(5.0)
    private val totalYieldGramsFlow = MutableStateFlow<Double?>(null)
    private val queryFlow = MutableStateFlow("")
    private val resultsFlow: StateFlow<List<Food>> =
        queryFlow
            .debounce(150)
            .map { it.trim() }
            .distinctUntilChanged()
            .flatMapLatest { q: String ->
                if (q.isBlank()) {
                    flowOf(emptyList<Food>())
                } else {
                    // TEMP placeholder until we wire the real search call:
                    foodRepo.search(q, limit = 50)
                }
            }
            .catch { t: Throwable ->
                errorFlow.value = t.message ?: "Search failed."
                emit(emptyList<Food>())
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                emptyList()
            )
    private var isEditingGrams: Boolean = false

    private val pickedFoodFlow = MutableStateFlow<Food?>(null)
    private val pickedServingsFlow = MutableStateFlow(1.0)
    private val pickedServingsTextFlow = MutableStateFlow("1.0")
    private val pickedGramsTextFlow = MutableStateFlow("")
    private val ingredientsFlow = MutableStateFlow<List<RecipeIngredientUi>>(emptyList())

    private val favoriteFlow = MutableStateFlow(false)
    private val eatMoreFlow = MutableStateFlow(false)
    private val limitFlow = MutableStateFlow(false)

    val ingredientTotalGrams: StateFlow<Double> =
        ingredientsFlow
            .map { lines -> lines.sumOf { it.grams ?: 0.0 } }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                0.0
            )

    private val isSavingFlow = MutableStateFlow(false)
    private val errorFlow = MutableStateFlow<String?>(null)

    private val blockingSheetFlow = MutableStateFlow<BlockingSheetModel?>(null)
    private val blockedFoodIdFlow = MutableStateFlow<Long?>(null)
    private val navigateToEditFoodIdFlow = MutableStateFlow<Long?>(null)


    private val nutrientTallyRowsFlow = MutableStateFlow<List<NutrientRowUi>>(emptyList())
    private val nutrientTallyLoadingFlow = MutableStateFlow(false)
    private val nutrientTallyErrorFlow = MutableStateFlow<String?>(null)

    private val previewFlow: StateFlow<RecipeMacroPreview> =
        observePreview(
            ingredients = ingredientsFlow.map { list ->
                list.map { it.foodId to (it.grams ?: 0.0) }
            }
        ).stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            RecipeMacroPreview()
        )

    // totalYieldGrams create and update
    private var didAutoPrefillTotalYieldGrams: Boolean = false
    private var didUserEditTotalYieldGrams: Boolean = false
    // SelectedFoodPanel
    private val pickedInputUnitFlow = MutableStateFlow(ServingUnit.G)
    private val pickedInputAmountTextFlow = MutableStateFlow("")

    // -----------------------------
    // State wiring
    // -----------------------------

    private fun maybeAutoPrefillTotalYieldGramsFromIngredients() {
        if (didAutoPrefillTotalYieldGrams) return
        if (didUserEditTotalYieldGrams) return
        if (totalYieldGramsFlow.value != null) return

        val grams = ingredientsFlow.value.sumOf { it.grams ?: 0.0 }
        if (grams > 0.0) {
            totalYieldGramsFlow.value = grams
            didAutoPrefillTotalYieldGrams = true
        }
    }

    private data class Left(
        val name: String,
        val servingsYield: Double,
        val totalYieldGrams: Double?,
        val query: String,
        val results: List<Food>,
        val pickedFood: Food?
    )

    private data class RightA(
        val pickedServings: Double,
        val pickedServingsText: String,
        val pickedGramsText: String,
        val ingredients: List<RecipeIngredientUi>
    )

    private data class RightB(
        val preview: RecipeMacroPreview,
        val isSaving: Boolean,
        val error: String?,
        val nutrientTallyRows: List<NutrientRowUi>,
        val nutrientTallyLoading: Boolean,
        val nutrientTallyErrorMessage: String?
    )

    private data class RightBase(
        val pickedServings: Double,
        val pickedServingsText: String,
        val pickedGramsText: String,
        val ingredients: List<RecipeIngredientUi>,
        val preview: RecipeMacroPreview,
        val isSaving: Boolean,
        val error: String?,
        val nutrientTallyRows: List<NutrientRowUi>,
        val nutrientTallyLoading: Boolean,
        val nutrientTallyErrorMessage: String?
    )

    private data class Overlay(
        val blockingSheet: BlockingSheetModel?,
        val blockedFoodId: Long?,
        val navigateToEditFoodId: Long?,
        val hasUnsavedChanges: Boolean,
        val favorite: Boolean,
        val eatMore: Boolean,
        val limit: Boolean
    )

    val state: StateFlow<RecipeBuilderState> =
        run {
            val leftFlow: Flow<Left> =
                combine(
                    nameFlow,
                    servingsYieldFlow,
                    totalYieldGramsFlow,
                    queryFlow,
                    resultsFlow,
                    pickedFoodFlow
                ) { arr: Array<Any?> ->
                    val name = arr[0] as String
                    val servingsYield = arr[1] as Double
                    val totalYieldGrams = arr[2] as Double?
                    val query = arr[3] as String

                    @Suppress("UNCHECKED_CAST")
                    val results = arr[4] as List<Food>
                    val pickedFood = arr[5] as Food?

                    Left(
                        name = name,
                        servingsYield = servingsYield,
                        totalYieldGrams = totalYieldGrams,
                        query = query,
                        results = results,
                        pickedFood = pickedFood
                    )
                }

            val rightAFlow: Flow<RightA> =
                combine(
                    pickedServingsFlow,
                    pickedServingsTextFlow,
                    pickedGramsTextFlow,
                    ingredientsFlow
                ) { s, sText, gText, ing ->
                    RightA(
                        pickedServings = s,
                        pickedServingsText = sText,
                        pickedGramsText = gText,
                        ingredients = ing
                    )
                }

            val rightBFlow: Flow<RightB> =
                combine(previewFlow, isSavingFlow) { preview, isSaving ->
                    preview to isSaving
                }.combine(errorFlow) { (preview, isSaving), error ->
                    Triple(preview, isSaving, error)
                }.combine(nutrientTallyRowsFlow) { (preview, isSaving, error), rows ->
                    listOf(preview, isSaving, error, rows)
                }.combine(nutrientTallyLoadingFlow) { list, loading ->
                    list + loading
                }.combine(nutrientTallyErrorFlow) { list, tallyError ->
                    val preview = list[0] as com.example.adobongkangkong.domain.model.RecipeMacroPreview
                    val isSaving = list[1] as Boolean
                    val error = list[2] as String?
                    val rows = list[3] as List<NutrientRowUi>
                    val loading = list[4] as Boolean

                    RightB(
                        preview = preview,
                        isSaving = isSaving,
                        error = error,
                        nutrientTallyRows = rows,
                        nutrientTallyLoading = loading,
                        nutrientTallyErrorMessage = tallyError
                    )
                }


            val rightFlow: Flow<RightBase> =
                combine(rightAFlow, rightBFlow) { a, b ->
                    RightBase(
                        pickedServings = a.pickedServings,
                        pickedServingsText = a.pickedServingsText,
                        pickedGramsText = a.pickedGramsText,
                        ingredients = a.ingredients,
                        preview = b.preview,
                        isSaving = b.isSaving,
                        error = b.error,
                        nutrientTallyRows = b.nutrientTallyRows,
                        nutrientTallyLoading = b.nutrientTallyLoading,
                        nutrientTallyErrorMessage = b.nutrientTallyErrorMessage
                    )
                }

            val flagsFlow = combine(favoriteFlow, eatMoreFlow, limitFlow) { f, e, l ->
                Triple(f, e, l)
            }

            val overlayFlow = combine(
                blockingSheetFlow,
                blockedFoodIdFlow,
                navigateToEditFoodIdFlow,
                hasUnsavedChangesFlow,
                flagsFlow
            ) { blockingSheet, blockedFoodId, navFoodId, hasUnsavedChanges, flags ->
                Overlay(
                    blockingSheet = blockingSheet,
                    blockedFoodId = blockedFoodId,
                    navigateToEditFoodId = navFoodId,
                    hasUnsavedChanges = hasUnsavedChanges,
                    favorite = flags.first,
                    eatMore = flags.second,
                    limit = flags.third
                )
            }

            combine(leftFlow, rightFlow, overlayFlow) { left, right, overlay ->
                val pickedGrams =
                    left.pickedFood?.gramsPerServingUnitResolved()
                        ?.let { g -> right.pickedServings * g }

                RecipeBuilderState(
                    name = left.name,
                    servingsYield = left.servingsYield,
                    totalYieldGrams = left.totalYieldGrams,

                    query = left.query,
                    results = left.results,

                    pickedFood = left.pickedFood,
                    pickedServings = right.pickedServings,
                    pickedServingsText = right.pickedServingsText,
                    pickedGramsText = right.pickedGramsText,
                    pickedGrams = pickedGrams,

                    ingredients = right.ingredients,

                    isSaving = right.isSaving,
                    errorMessage = right.error,

                    preview = right.preview,

                    nutrientTallyRows = right.nutrientTallyRows,
                    nutrientTallyLoading = right.nutrientTallyLoading,
                    nutrientTallyErrorMessage = right.nutrientTallyErrorMessage,

                    blockingSheet = overlay.blockingSheet,
                    blockedFoodId = overlay.blockedFoodId,
                    navigateToEditFoodId = overlay.navigateToEditFoodId,
                    hasUnsavedChanges = overlay.hasUnsavedChanges,
                    favorite = overlay.favorite,
                    eatMore = overlay.eatMore,
                    limit = overlay.limit,

                )
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                RecipeBuilderState()
            )
        }

    fun onFavoriteChange(v: Boolean) {
        favoriteFlow.value = v
        markDirty()
    }

    fun onEatMoreChange(v: Boolean) {
        eatMoreFlow.value = v
        markDirty()
    }

    fun onLimitChange(v: Boolean) {
        limitFlow.value = v
        markDirty()
    }

    private fun markDirty() {
        hasUnsavedChangesFlow.value = true
    }


    // -----------------------------
    // Nutrient tally (read-only)
    // -----------------------------
    private fun recomputeNutrientTally() {
        Log.d("Meow","RecipeBuilderViewModel> recomputeNutrientTally > Ingredients count:${ingredientsFlow.value.size}")
        viewModelScope.launch {
            val lines = ingredientsFlow.value
            if (lines.isEmpty()) {
                nutrientTallyRowsFlow.value = emptyList()
                nutrientTallyErrorFlow.value = null
                nutrientTallyLoadingFlow.value = false
                return@launch
            }

            lines.forEach {
                Log.d("Meow","RecipeBuilderViewModel> recomputeNutrientTally> Ingredient: ${it.foodName}")
            }

            nutrientTallyLoadingFlow.value = true
            nutrientTallyErrorFlow.value = null

            try {
                // Build a minimal domain Recipe for nutrition computation.
                val recipe = com.example.adobongkangkong.domain.recipes.Recipe(
                    id = 0L,
                    name = nameFlow.value,
                    ingredients = lines.map { line ->
                        com.example.adobongkangkong.domain.recipes.RecipeIngredient(
                            foodId = line.foodId,
                            servings = line.servings
                        )
                    },
                    servingsYield = servingsYieldFlow.value,
                    totalYieldGrams = totalYieldGramsFlow.value ?: 0.0
                )

                val result = computeRecipeNutrition(recipe)

                // Convert totals (NutrientKey -> amount) into FoodEditor-style rows.
                val rows = buildList {
                    for ((key, v) in result.totals.entries()) {
                        if (abs(v) <= 0.0) continue

                        val nutrient = nutrientRepo.getByCode(key.value) ?: continue
                        Log.d("Meow","RecipeBuilderViewModel> recomputeNutrientTally> adding ${nutrient.displayName}")
                        add(
                            NutrientRowUi(
                                nutrientId = nutrient.id,
                                name = nutrient.displayName,
                                aliases = nutrient.aliases,
                                unit = nutrient.unit,
                                category = nutrient.category,
                                amount = formatTallyAmount(v)
                            )
                        )
                    }
                }.sortedWith(
                    compareBy<NutrientRowUi> { it.category.sortOrder }
                        .thenBy { it.name.lowercase() }
                )

                    .toList()

                nutrientTallyRowsFlow.value = rows

                val warn = result.warnings.firstOrNull()
                nutrientTallyErrorFlow.value = warn?.let { warningToMessage(it) }
            } catch (t: Throwable) {
                nutrientTallyErrorFlow.value = t.message ?: "Failed to compute nutrient tally."
            } finally {
                nutrientTallyLoadingFlow.value = false
            }
        }
    }

    private fun formatTallyAmount(v: Double): String {
        val absV = abs(v)
        return when {
            absV >= 100 -> "%,.0f".format(v)
            absV >= 10 -> "%,.1f".format(v)
            else -> "%,.2f".format(v)
        }
    }

    private fun warningToMessage(w: RecipeNutritionWarning): String =
        when (w) {
            RecipeNutritionWarning.MissingServingsYield -> "Missing servings-yield."
            RecipeNutritionWarning.MissingTotalYieldGrams -> "Missing total-yield grams."
            is RecipeNutritionWarning.InvalidServingsYield -> "Invalid servings-yield: ${w.value}"
            is RecipeNutritionWarning.InvalidTotalYieldGrams -> "Invalid total-yield grams: ${w.value}"
            is RecipeNutritionWarning.MissingFood -> "Missing food for foodId=${w.foodId}"
            is RecipeNutritionWarning.MissingGramsPerServing -> "Missing grams-per-serving for foodId=${w.foodId}"
            is RecipeNutritionWarning.MissingNutrientsPerGram -> "Missing per-gram nutrients for foodId=${w.foodId}"
            is RecipeNutritionWarning.IngredientServingsNonPositive -> "Ingredient servings must be > 0 (foodId=${w.foodId})"
        }


    // -----------------------------
    // Events
    // -----------------------------
    fun onNameChange(v: String) {
        nameFlow.value = v
        markDirty()
    }

    fun onYieldChange(v: Double) {
        servingsYieldFlow.value = v.coerceAtLeast(0.1)
        markDirty()
    }

    fun onTotalYieldGramsChanged(value: Double?) {
        didUserEditTotalYieldGrams = true
        totalYieldGramsFlow.value = value?.takeIf { it > 0.0 }
        markDirty()
    }

    fun onQueryChange(v: String) {
        queryFlow.value = v
    }

    fun clearError() {
        errorFlow.value = null
    }

    private fun syncPickedGramsTextFromServings() {
        if (isEditingGrams) return

        val food = pickedFoodFlow.value
        val g = food?.gramsPerServingUnitResolved()
        if (g == null || g <= 0.0) {
            pickedGramsTextFlow.value = ""
            return
        }
        val grams = (pickedServingsFlow.value * g).coerceAtLeast(0.0)
        pickedGramsTextFlow.value = formatNumberForInput(grams)
    }

    private fun formatNumberForInput(v: Double): String {
        // Keep it compact for TextField (avoid scientific notation, trim trailing zeros).
        val s = "%.3f".format(v)
        return s.trimEnd('0').trimEnd('.')
    }

    /**
     * TextField handler for grams input.
     *
     * Accepts partial numeric strings while typing. When parsing succeeds and grams-per-serving
     * is known, we derive servings and keep the servings TextField in sync.
     */
    fun onPickedGramsTextChange(raw: String) {
        if (!raw.matches(Regex("^\\d*([.]\\d*)?$"))) return

        isEditingGrams = true
        pickedGramsTextFlow.value = raw

        // If user cleared grams (or is mid-typing '.'), don't backfill from servings.
        if (raw.isBlank() || raw == ".") {
            pickedServingsFlow.value = 0.0
            pickedServingsTextFlow.value = "0"
            return
        }

        val grams = raw.toDoubleOrNull() ?: return
        onPickedGramsChange(grams)
    }

    /**
     * TextField handler for the servings input.
     *
     * Accepts partial numeric strings while typing. Updates [pickedServingsFlow] only when parsing
     * succeeds. This is the key to fixing “wonky” numeric input.
     */
    fun onPickedServingsTextChange(raw: String) {
        if (!raw.matches(Regex("""^\d*([.]\d*)?$"""))) return

        isEditingGrams = false
        pickedServingsTextFlow.value = raw

        raw.toDoubleOrNull()?.let { parsed ->
            pickedServingsFlow.value = max(0.0, parsed)
            syncPickedGramsTextFromServings()
        }
    }

    fun onPickedInputAmountTextChange(raw: String) {
        if (!raw.matches(Regex("""^\d*([.]\d*)?$"""))) return

        pickedInputAmountTextFlow.value = raw

        val food = pickedFoodFlow.value ?: return

        if (raw.isBlank() || raw == ".") {
            // Don’t force 0.0; just clear derived fields similarly to grams typing.
            isEditingGrams = true
            pickedGramsTextFlow.value = ""
            pickedServingsFlow.value = 0.0
            pickedServingsTextFlow.value = "0"
            return
        }

        val amount = raw.toDoubleOrNull() ?: return
        val unit = pickedInputUnitFlow.value

        val grams = computePickedInputGrams(food = food, amount = amount, unit = unit) ?: return

        // Push through your existing grams→servings sync
        isEditingGrams = true
        pickedGramsTextFlow.value = formatTo2Decimals(grams) // optional: keep text in sync
        onPickedGramsChange(grams)
    }

    fun onPickedInputUnitChange(unit: ServingUnit) {
        pickedInputUnitFlow.value = unit

        val food = pickedFoodFlow.value ?: return
        val raw = pickedInputAmountTextFlow.value.trim()
        if (raw.isBlank() || raw == ".") return

        val amount = raw.toDoubleOrNull() ?: return
        val grams = computePickedInputGrams(food = food, amount = amount, unit = unit) ?: return

        isEditingGrams = true
        pickedGramsTextFlow.value = formatTo2Decimals(grams)
        onPickedGramsChange(grams)
    }

    private fun computePickedInputGrams(
        food: Food,
        amount: Double,
        unit: ServingUnit
    ): Double? {
        val a = amount.coerceAtLeast(0.0)

        // Mass → grams (deterministic)
        unit.toGrams(a)?.let { grams ->
            return grams
        }

        // Volume → grams requires density derived from food's serving definition
        val mlInput = unit.toMilliliters(a) ?: return null

        val gPerServing = food.gramsPerServingUnitResolved() ?: return null
        if (gPerServing <= 0.0) return null

        // mL per ONE food serving (servingSize + servingUnit)
        val mlPerFoodServing = food.servingUnit.toMilliliters(food.servingSize) ?: return null
        if (mlPerFoodServing <= 0.0) return null

        val densityGPerMl = gPerServing / mlPerFoodServing // g/mL
        return mlInput * densityGPerMl
    }



    // --------------------------------------------------------------------
// QuickAdd-compatible API (RecipeBuilder should behave the same way)
// --------------------------------------------------------------------

    fun onFoodSelected(food: Food) = pickFood(food)

    fun clearSelection() = clearPickedFood()

    fun onServingsChanged(servings: Double) = onPickedServingsChange(servings)

    /**
     * Amount in the food's serving unit (e.g., CUP_US, TBSP_US, etc).
     * Mirrors QuickAdd behavior: servings = amount / servingSize.
     */
    fun onServingUnitAmountChanged(amountInServingUnit: Double) {
        val food = pickedFoodFlow.value ?: return
        val servingSize = food.servingSize
        if (servingSize <= 0.0) return

        val servings = (amountInServingUnit / servingSize).coerceAtLeast(0.0)
        onPickedServingsChange(servings)
    }

    fun onGramsChanged(grams: Double) = onPickedGramsChange(grams)

    fun onInputUnitChanged(unit: ServingUnit) = onPickedInputUnitChange(unit)

    fun onInputAmountChanged(amount: Double?) {
        val food = pickedFoodFlow.value ?: return
        val unit = pickedInputUnitFlow.value

        if (amount == null || amount <= 0.0) return

        val grams = computePickedInputGrams(food = food, amount = amount, unit = unit) ?: return
        isEditingGrams = true
        pickedGramsTextFlow.value = formatTo2Decimals(grams)
        onPickedGramsChange(grams)
    }

    fun onPackageClicked(multiplier: Double) = onPickedPackage(multiplier)

    fun pickFood(food: Food) {
        isEditingGrams = false
        pickedFoodFlow.value = food
        pickedServingsFlow.value = 1.0
        pickedServingsTextFlow.value = "1.0"
        syncPickedGramsTextFromServings()
        errorFlow.value = null
    }

    fun clearPickedFood() {
        pickedFoodFlow.value = null
        pickedServingsFlow.value = 1.0
        pickedServingsTextFlow.value = "1.0"
        pickedGramsTextFlow.value = ""
    }

    /**
     * Used by +/- buttons (non-text input). Keep the text field synced.
     */
    fun onPickedServingsChange(v: Double) {
        val newVal = max(0.0, v)
        pickedServingsFlow.value = newVal
        pickedServingsTextFlow.value = newVal.toString()
        syncPickedGramsTextFromServings()
    }

    /**
     * If you support grams entry, derive servings when grams-per-serving is known.
     * Keep the text field synced so the UI doesn't “snap back”.
     */
    fun onPickedGramsChange(grams: Double) {
        val food = pickedFoodFlow.value ?: return
        val g = food.gramsPerServingUnitResolved() ?: return
        if (g <= 0.0) return

        val servingsRaw = (grams / g).coerceAtLeast(0.0)

        // ✅ full precision stored
        pickedServingsFlow.value = servingsRaw

        // ✅ UI text only: limit to 2 decimals (no data loss)
        pickedServingsTextFlow.value = formatTo2Decimals(servingsRaw)
    }


    private fun formatTo2Decimals(value: Double): String {
        val s = "%.2f".format(value)
        return s.trimEnd('0').trimEnd('.')
    }

    fun dismissBlockingSheet() {
        blockingSheetFlow.value = null
        blockedFoodIdFlow.value = null
    }

    fun onEditFoodNavigationHandled() {
        navigateToEditFoodIdFlow.value = null
    }

    /**
     * Point-of-use enforcement:
     * If the food lacks grams-per-serving and user is working in "servings" units,
     * route them to Food Editor rather than guessing.
     */
    fun addPickedIngredient() {
        val food = pickedFoodFlow.value ?: return
        val servings = pickedServingsFlow.value

        if (servings <= 0.0) {
            errorFlow.value = "Ingredient amount must be > 0."
            return
        }

        if (!ServingPolicy.canUseServings(food)) {
            blockedFoodIdFlow.value = food.id
            blockingSheetFlow.value = BlockingSheetModel(
                title = "Needs grams-per-serving",
                message = ServingPolicy.blockingReason(food),
                primaryButtonText = "Edit food",
                secondaryButtonText = "Dismiss",
                onPrimary = {
                    navigateToEditFoodIdFlow.value = food.id
                    blockingSheetFlow.value = null
                },
                onSecondary = { dismissBlockingSheet() }
            )
            return
        }

        val gramsForLine = food.gramsPerServingUnitResolved()?.let { gPerServing ->
            (servings * gPerServing).coerceAtLeast(0.0)
        }

        val rawEntered = pickedInputAmountTextFlow.value.trim()
        val enteredAmount: Double
        val enteredUnitLabel: String

        if (rawEntered.isNotBlank() && rawEntered != ".") {
            enteredAmount = rawEntered.toDoubleOrNull() ?: servings
            enteredUnitLabel = pickedInputUnitFlow.value.toString()
        } else {
            // User likely used the servings +/- controls, not the freeform input picker.
            enteredAmount = servings
            enteredUnitLabel = food.servingUnit.toString()
        }

        val next = ingredientsFlow.value.toMutableList()
        next.add(
            RecipeIngredientUi(
                foodId = food.id,
                foodName = food.name,
                servings = servings,
                servingUnitLabel = food.servingUnit.toString(),
                grams = gramsForLine,
                enteredAmount = enteredAmount,
                enteredUnitLabel = enteredUnitLabel
            )
        )
        ingredientsFlow.value = next
        recomputeNutrientTally()
        maybeAutoPrefillTotalYieldGramsFromIngredients()
        markDirty()
        isEditingGrams = false
        // Reset add-ingredient UI
        pickedFoodFlow.value = null
        pickedServingsFlow.value = 1.0
        pickedServingsTextFlow.value = "1.0"
        pickedGramsTextFlow.value = ""
        queryFlow.value = ""
        errorFlow.value = null
    }

    fun removeIngredientAt(index: Int) {
        val list = ingredientsFlow.value.toMutableList()
        if (index !in list.indices) return
        list.removeAt(index)
        ingredientsFlow.value = list
        maybeAutoPrefillTotalYieldGramsFromIngredients()
        recomputeNutrientTally()
        markDirty()
    }

    fun save(onDone: () -> Unit) {
        val name = nameFlow.value.trim()
        val servingsYield = servingsYieldFlow.value
        val totalYieldGrams = totalYieldGramsFlow.value
        val ingredients = ingredientsFlow.value

        if (name.isBlank()) {
            errorFlow.value = "Recipe name is required."
            return
        }
        if (servingsYield <= 0.0) {
            errorFlow.value = "Servings yield must be > 0."
            return
        }
        if (ingredients.isEmpty()) {
            errorFlow.value = "Add at least 1 ingredient."
            return
        }

        viewModelScope.launch {
            isSavingFlow.value = true
            errorFlow.value = null
            try {
                val editingFoodId = editFoodId

                if (editingFoodId == null) {
                    val newFoodId = createRecipe(
                        RecipeDraft(
                            name = name,
                            servingsYield = servingsYield,
                            totalYieldGrams = totalYieldGrams,
                            ingredients = ingredients.map {
                                RecipeIngredientDraft(
                                    foodId = it.foodId,
                                    ingredientServings = it.servings
                                )
                            }
                        )
                    )

                    // Save flags separately (keyed by foodId, including recipes).
                    flagsRepository.setFlags(
                        foodId = newFoodId,
                        favorite = favoriteFlow.value,
                        eatMore = eatMoreFlow.value,
                        limit = limitFlow.value
                    )
                } else {
                    recipeRepo.updateRecipeByFoodId(
                        foodId = editingFoodId,
                        servingsYield = servingsYield,
                        totalYieldGrams = totalYieldGrams,
                        ingredients = ingredients.map {
                            RecipeIngredientLine(
                                ingredientFoodId = it.foodId,
                                ingredientServings = it.servings
                            )
                        }
                    )

                    foodRepo.getById(editingFoodId)?.let { food ->
                        foodRepo.upsert(food.copy(name = name, isRecipe = true))
                    }

                    // Save flags separately (keyed by foodId, including recipes).
                    flagsRepository.setFlags(
                        foodId = editingFoodId,
                        favorite = favoriteFlow.value,
                        eatMore = eatMoreFlow.value,
                        limit = limitFlow.value
                    )

                }

                onDone()
            } catch (t: Throwable) {
                errorFlow.value = t.message ?: "Failed to save recipe."
            } finally {
                isSavingFlow.value = false
            }
        }
    }

    fun onPickedPackage(multiplier: Double) {
        val food = pickedFoodFlow.value ?: return
        val spp = food.servingsPerPackage ?: return
        if (spp <= 0.0) return

        val newServings = (spp * multiplier).coerceAtLeast(0.0)
        pickedServingsFlow.value = newServings
        pickedServingsTextFlow.value = newServings.toString()
        syncPickedGramsTextFromServings()
    }

    fun loadForEdit(foodId: Long?) {
        if (foodId == null) return
        hasUnsavedChangesFlow.value = false
        editFoodId = foodId

        didAutoPrefillTotalYieldGrams = false
        didUserEditTotalYieldGrams = false

        // Reset "add ingredient" UI state
        isEditingGrams = false
        pickedFoodFlow.value = null
        pickedServingsFlow.value = 1.0
        pickedServingsTextFlow.value = "1.0"
        pickedGramsTextFlow.value = ""
        queryFlow.value = ""

        viewModelScope.launch {
            val recipe = recipeRepo.getRecipeByFoodId(foodId) ?: return@launch
            val ingredients = recipeRepo.getIngredients(recipe.recipeId)

            val food = foodRepo.getById(foodId)

            // ✅ Load goal flags for this recipe (recipes are foods; flags are keyed by foodId)
            val flags = flagsRepository.get(foodId)
            favoriteFlow.value = flags?.favorite ?: false
            eatMoreFlow.value = flags?.eatMore ?: false
            limitFlow.value = flags?.limit ?: false

            // Build lookup once so we can get both name + unit + gramsPerServingUnit
            val ids = ingredients.map { it.ingredientFoodId }.distinct()
            val foodById: Map<Long, Food?> =
                ids.associateWith { id -> foodRepo.getById(id) }

            nameFlow.value = food?.name ?: nameFlow.value
            servingsYieldFlow.value = recipe.servingsYield
            totalYieldGramsFlow.value = recipe.totalYieldGrams

            ingredientsFlow.value = ingredients.map { line ->
                val ingFood = foodById[line.ingredientFoodId]
                val gramsForLine = ingFood?.gramsPerServingUnitResolved()?.let { gPerServing ->
                    (line.ingredientServings * gPerServing).coerceAtLeast(0.0)
                }

                RecipeIngredientUi(
                    foodId = line.ingredientFoodId,
                    foodName = ingFood?.name ?: "Food ${line.ingredientFoodId}",
                    servings = line.ingredientServings,
                    servingUnitLabel = ingFood?.servingUnit?.toString(),
                    grams = gramsForLine
                )
            }
            recomputeNutrientTally()
            if (totalYieldGramsFlow.value == null) {
                maybeAutoPrefillTotalYieldGramsFromIngredients()
            } else {
                didAutoPrefillTotalYieldGrams = true
            }
            hasUnsavedChangesFlow.value = false
        }
    }
}
/** 2025-02-05
 * ViewModel for creating and editing recipes.
 *
 * ⚠️ READ THIS BEFORE MODIFYING ⚠️
 *
 * ## Core model rules (locked in)
 * - A **Recipe is represented by a Food row** where `Food.isRecipe = true`.
 * - All recipe identity in the app ultimately flows through **foodId**, not recipeId.
 * - `FoodGoalFlagsEntity` (favorite / eatMore / limit) is **keyed by foodId**.
 * - Therefore, recipe goal flags MUST ALWAYS be saved and loaded using the
 *   recipe's **foodId**.
 *
 * ## Create vs Edit semantics
 * - **Create**
 *   - `CreateRecipeUseCase` (Option A) returns the newly created recipe’s **foodId**.
 *   - That returned `foodId` must be used immediately to persist `FoodGoalFlags`.
 *
 * - **Edit**
 *   - `editFoodId` is already known.
 *   - Flags must be updated using that same `foodId`.
 *
 * 🚫 Do NOT invent identifiers.
 * 🚫 Do NOT key flags by recipeId.
 *
 * ## Flag persistence contract
 * This ViewModel mirrors `FoodEditorViewModel` behavior exactly:
 * - Flags are persisted via `FoodGoalFlagsRepository.setFlags(foodId, ...)`
 * - Flags are loaded via `FoodGoalFlagsRepository.get(foodId)`
 *
 * ## loadForEdit()
 * - Must load goal flags using the recipe’s **foodId**
 * - Must populate:
 *   - `favoriteFlow`
 *   - `eatMoreFlow`
 *   - `limitFlow`
 *
 * If this step is skipped, editing an existing recipe will silently reset flags
 * on the next save.
 *
 * ## Common foot-guns (don’t repeat)
 * - ❌ Using recipeId for anything user-facing
 * - ❌ Forgetting to persist flags after create
 * - ❌ Assuming recipes have a separate flag model
 * - ❌ Treating recipes differently from foods for goal flags
 *
 * When in doubt: **recipes ARE foods** — follow the food path.
 */