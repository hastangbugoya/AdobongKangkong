package com.example.adobongkangkong.ui.recipe
// ⚠️ Default null servings to 1.0.
// UI layer allows nullable servings during editing, but the domain draft
// requires a non-null Double for nutrition scaling and planner expansion.
// We intentionally default to 1.0 (NOT 0.0) to preserve recipe math integrity
// and make unexpected null states visible in the UI.
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
import com.example.adobongkangkong.domain.nutrition.gramsPerServingUnitResolved
import com.example.adobongkangkong.domain.repository.FoodGoalFlagsRepository
import com.example.adobongkangkong.domain.repository.FoodCategoryRepository
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
import com.example.adobongkangkong.ui.food.editor.FoodCategoryUi
import com.example.adobongkangkong.ui.food.editor.NutrientRowUi
import kotlin.math.abs


@HiltViewModel
class RecipeBuilderViewModel @Inject constructor(
    private val foodRepo: FoodRepository,
    private val recipeRepo: RecipeRepository,
    private val createRecipe: CreateRecipeUseCase,
    private val flagsRepository: FoodGoalFlagsRepository,
    private val foodCategoryRepo: FoodCategoryRepository,
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

    private val categoriesFlow = MutableStateFlow<List<FoodCategoryUi>>(emptyList())
    private val selectedCategoryIdsFlow = MutableStateFlow<Set<Long>>(emptySet())
    private val newCategoryNameFlow = MutableStateFlow("")

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
                list.map { it.foodId to (it.servings ?: 0.0) }
            }
        ).stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            RecipeMacroPreview()
        )

    private var didAutoPrefillTotalYieldGrams: Boolean = false
    private var didUserEditTotalYieldGrams: Boolean = false
    private val pickedInputUnitFlow = MutableStateFlow(ServingUnit.G)
    private val pickedInputAmountTextFlow = MutableStateFlow("")

    init {
        loadCategoriesForEditor()
    }

    private fun maybeAutoPrefillTotalYieldGramsFromIngredients() {
        if (didUserEditTotalYieldGrams) return

        val grams = ingredientsFlow.value.sumOf { it.grams ?: 0.0 }
        if (grams <= 0.0) return

        totalYieldGramsFlow.value = grams
        didAutoPrefillTotalYieldGrams = true
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

    private data class CategoryState(
        val categories: List<FoodCategoryUi>,
        val selectedCategoryIds: Set<Long>,
        val newCategoryName: String,
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

            val categoryFlow = combine(
                categoriesFlow,
                selectedCategoryIdsFlow,
                newCategoryNameFlow
            ) { categories, selectedCategoryIds, newCategoryName ->
                CategoryState(
                    categories = categories,
                    selectedCategoryIds = selectedCategoryIds,
                    newCategoryName = newCategoryName,
                )
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

            combine(leftFlow, rightFlow, overlayFlow, categoryFlow) { left, right, overlay, categoryState ->
                val pickedGrams =
                    left.pickedFood?.gramsPerServingUnitResolved()
                        ?.let { g -> right.pickedServings * g }

                RecipeBuilderState(
                    name = left.name,
                    servingsYield = left.servingsYield,
                    totalYieldGrams = left.totalYieldGrams,
                    categories = categoryState.categories,
                    selectedCategoryIds = categoryState.selectedCategoryIds,
                    newCategoryName = categoryState.newCategoryName,
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

    private fun loadCategoriesForEditor() {
        viewModelScope.launch {
            try {
                categoriesFlow.value = foodCategoryRepo.getAll()
                    .sortedBy { it.name.lowercase() }
                    .map { category ->
                        FoodCategoryUi(
                            id = category.id,
                            name = category.name,
                            isSystem = category.isSystem,
                        )
                    }
            } catch (_: Throwable) {
            }
        }
    }

    fun onCategoryCheckedChange(categoryId: Long, checked: Boolean) {
        val nextIds = selectedCategoryIdsFlow.value.toMutableSet().apply {
            if (checked) add(categoryId) else remove(categoryId)
        }
        selectedCategoryIdsFlow.value = nextIds
        markDirty()
    }

    fun onNewCategoryNameChange(v: String) {
        newCategoryNameFlow.value = v
    }

    fun createCategory() {
        val rawName = newCategoryNameFlow.value.trim()
        if (rawName.isBlank()) {
            errorFlow.value = "Category name is required."
            return
        }

        viewModelScope.launch {
            try {
                val created = foodCategoryRepo.getOrCreateByName(rawName)
                categoriesFlow.value = (categoriesFlow.value + FoodCategoryUi(
                    id = created.id,
                    name = created.name,
                    isSystem = created.isSystem,
                )).distinctBy { it.id }.sortedBy { it.name.lowercase() }
                selectedCategoryIdsFlow.value = selectedCategoryIdsFlow.value + created.id
                newCategoryNameFlow.value = ""
                errorFlow.value = null
                markDirty()
            } catch (t: Throwable) {
                errorFlow.value = t.message ?: "Failed to create category."
            }
        }
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
                val fallbackTotalYieldGrams = totalYieldGramsFlow.value
                    ?: lines.sumOf { it.grams ?: 0.0 }
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
                    totalYieldGrams = fallbackTotalYieldGrams
                )

                val result = computeRecipeNutrition(recipe)

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
                ).toList()

                nutrientTallyRowsFlow.value = rows

                val warn = result.warnings.firstOrNull { w ->
                    w is RecipeNutritionWarning.MissingFood ||
                            w is RecipeNutritionWarning.MissingGramsPerServing ||
                            w is RecipeNutritionWarning.MissingMlPerServing ||
                            w is RecipeNutritionWarning.MissingNutrientsPerGram ||
                            w is RecipeNutritionWarning.MissingNutrientsPerMilliliter
                }
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
            is RecipeNutritionWarning.MissingMlPerServing -> "Missing ml-per-serving for foodId=${w.foodId}"
            is RecipeNutritionWarning.MissingNutrientsPerGram -> "Missing per-gram nutrients for foodId=${w.foodId}"
            is RecipeNutritionWarning.MissingNutrientsPerMilliliter -> "Missing per-milliliter nutrients for foodId=${w.foodId}"
            is RecipeNutritionWarning.IngredientServingsNonPositive -> "Ingredient servings must be > 0 (foodId=${w.foodId})"
        }

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
        val s = "%.3f".format(v)
        return s.trimEnd('0').trimEnd('.')
    }

    fun onPickedGramsTextChange(raw: String) {
        if (!raw.matches(Regex("^\\d*([.]\\d*)?$"))) return

        isEditingGrams = true
        pickedGramsTextFlow.value = raw

        if (raw.isBlank() || raw == ".") {
            pickedServingsFlow.value = 0.0
            pickedServingsTextFlow.value = "0"
            return
        }

        val grams = raw.toDoubleOrNull() ?: return
        onPickedGramsChange(grams)
    }

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
            isEditingGrams = true
            pickedGramsTextFlow.value = ""
            pickedServingsFlow.value = 0.0
            pickedServingsTextFlow.value = "0"
            return
        }

        val amount = raw.toDoubleOrNull() ?: return
        val unit = pickedInputUnitFlow.value

        val grams = computePickedInputGrams(food = food, amount = amount, unit = unit) ?: return

        isEditingGrams = true
        pickedGramsTextFlow.value = formatTo2Decimals(grams)
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

        unit.toGrams(a)?.let { grams ->
            return grams
        }

        val mlInput = unit.toMilliliters(a) ?: return null

        val gPerServing = food.gramsPerServingUnitResolved() ?: return null
        if (gPerServing <= 0.0) return null

        val mlPerFoodServing = food.servingUnit.toMilliliters(food.servingSize) ?: return null
        if (mlPerFoodServing <= 0.0) return null

        val densityGPerMl = gPerServing / mlPerFoodServing
        return mlInput * densityGPerMl
    }

    fun onFoodSelected(food: Food) = pickFood(food)
    fun clearSelection() = clearPickedFood()
    fun onServingsChanged(servings: Double) = onPickedServingsChange(servings)

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

    fun onPickedServingsChange(v: Double) {
        val newVal = max(0.0, v)
        pickedServingsFlow.value = newVal
        pickedServingsTextFlow.value = newVal.toString()
        syncPickedGramsTextFromServings()
    }

    fun onPickedGramsChange(grams: Double) {
        val food = pickedFoodFlow.value ?: return
        val g = food.gramsPerServingUnitResolved() ?: return
        if (g <= 0.0) return

        val servingsRaw = (grams / g).coerceAtLeast(0.0)
        pickedServingsFlow.value = servingsRaw
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

        val gramsBasis = food.gramsPerServingUnitResolved()
        val gramsForLine: Double?
        val isApproximateWeight: Boolean

        if (gramsBasis != null) {
            gramsForLine = (servings * gramsBasis).coerceAtLeast(0.0)
            isApproximateWeight = false
        } else {
            val mlPerServing = food.mlPerServingUnit
                ?: food.servingUnit.toMilliliters(food.servingSize)
            gramsForLine = mlPerServing?.let { perServingMl ->
                (servings * perServingMl).coerceAtLeast(0.0)
            }
            isApproximateWeight = gramsForLine != null
        }

        val rawEntered = pickedInputAmountTextFlow.value.trim()
        val enteredAmount: Double
        val enteredUnitLabel: String

        if (rawEntered.isNotBlank() && rawEntered != ".") {
            enteredAmount = rawEntered.toDoubleOrNull() ?: servings
            enteredUnitLabel = pickedInputUnitFlow.value.toString()
        } else {
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
                isApproximateWeight = isApproximateWeight,
                enteredAmount = enteredAmount,
                enteredUnitLabel = enteredUnitLabel
            )
        )
        ingredientsFlow.value = next
        recomputeNutrientTally()
        maybeAutoPrefillTotalYieldGramsFromIngredients()
        markDirty()
        isEditingGrams = false
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
                Log.d("Meow", "SAVE RECIPE totalYieldGrams=${totalYieldGramsFlow.value} ingredientSum=${ingredientsFlow.value.sumOf { it.grams ?: 0.0 }}")
                val savedRecipeFoodId = if (editingFoodId == null) {
                    val newFoodId = createRecipe(
                        RecipeDraft(
                            name = name,
                            servingsYield = servingsYield,
                            totalYieldGrams = totalYieldGrams,
                            ingredients = ingredients.map {
                                RecipeIngredientDraft(
                                    foodId = it.foodId,
                                    ingredientServings = it.servings ?: 1.0
                                )
                            }
                        )
                    )

                    flagsRepository.setFlags(
                        foodId = newFoodId,
                        favorite = favoriteFlow.value,
                        eatMore = eatMoreFlow.value,
                        limit = limitFlow.value
                    )
                    newFoodId
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

                    flagsRepository.setFlags(
                        foodId = editingFoodId,
                        favorite = favoriteFlow.value,
                        eatMore = eatMoreFlow.value,
                        limit = limitFlow.value
                    )
                    editingFoodId
                }

                recipeRepo.getRecipeByFoodId(savedRecipeFoodId)?.let { recipe ->
                    foodCategoryRepo.replaceForRecipe(
                        recipeId = recipe.recipeId,
                        categoryIds = selectedCategoryIdsFlow.value,
                    )
                }

                hasUnsavedChangesFlow.value = false
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

            val flags = flagsRepository.get(foodId)
            favoriteFlow.value = flags?.favorite ?: false
            eatMoreFlow.value = flags?.eatMore ?: false
            limitFlow.value = flags?.limit ?: false

            categoriesFlow.value = foodCategoryRepo.getAll()
                .sortedBy { it.name.lowercase() }
                .map { category ->
                    FoodCategoryUi(
                        id = category.id,
                        name = category.name,
                        isSystem = category.isSystem,
                    )
                }
            selectedCategoryIdsFlow.value = foodCategoryRepo.getForRecipe(recipe.recipeId).map { it.id }.toSet()
            newCategoryNameFlow.value = ""

            val ids = ingredients.map { it.ingredientFoodId }.distinct()
            val foodById: Map<Long, Food?> =
                ids.associateWith { id -> foodRepo.getById(id) }

            nameFlow.value = food?.name ?: nameFlow.value
            servingsYieldFlow.value = recipe.servingsYield
            totalYieldGramsFlow.value = recipe.totalYieldGrams

            ingredientsFlow.value = ingredients.map { line ->
                val ingFood = foodById[line.ingredientFoodId]

                val gramsBasis = ingFood?.gramsPerServingUnitResolved()
                val gramsForLine: Double?
                val isApproximateWeight: Boolean

                if (gramsBasis != null) {
                    gramsForLine = line.ingredientServings?.let { servings ->
                        (servings * gramsBasis).coerceAtLeast(0.0)
                    }
                    isApproximateWeight = false
                } else {
                    val mlPerServing = ingFood?.mlPerServingUnit
                        ?: ingFood?.servingUnit?.toMilliliters(ingFood.servingSize)
                    gramsForLine = line.ingredientServings?.let { servings ->
                        mlPerServing?.let { perServingMl ->
                            (servings * perServingMl).coerceAtLeast(0.0)
                        }
                    }
                    isApproximateWeight = gramsForLine != null
                }

                RecipeIngredientUi(
                    foodId = line.ingredientFoodId,
                    foodName = ingFood?.name ?: "Food ${line.ingredientFoodId}",
                    servings = line.ingredientServings,
                    servingUnitLabel = ingFood?.servingUnit?.toString(),
                    grams = gramsForLine,
                    isApproximateWeight = isApproximateWeight
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