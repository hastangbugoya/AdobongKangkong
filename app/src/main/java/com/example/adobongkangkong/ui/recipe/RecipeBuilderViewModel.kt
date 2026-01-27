package com.example.adobongkangkong.ui.recipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.model.RecipeDraft
import com.example.adobongkangkong.domain.model.RecipeIngredientDraft
import com.example.adobongkangkong.domain.model.RecipeMacroPreview
import com.example.adobongkangkong.domain.nutrition.ServingPolicy
import com.example.adobongkangkong.domain.nutrition.gramsPerServingResolved
import com.example.adobongkangkong.domain.repository.FoodRepository
import com.example.adobongkangkong.domain.repository.RecipeIngredientLine
import com.example.adobongkangkong.domain.repository.RecipeRepository
import com.example.adobongkangkong.domain.usecase.CreateRecipeUseCase
import com.example.adobongkangkong.domain.usecase.ObserveRecipeMacroPreviewUseCase
import com.example.adobongkangkong.domain.usecase.SearchFoodsUseCase
import com.example.adobongkangkong.ui.common.bottomsheet.BlockingSheetModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
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
import javax.inject.Inject
import kotlin.math.max

@HiltViewModel
class RecipeBuilderViewModel @Inject constructor(
    private val searchFoods: SearchFoodsUseCase,
    private val createRecipe: CreateRecipeUseCase,
    observeRecipeMacroPreview: ObserveRecipeMacroPreviewUseCase,
    private val recipeRepo: RecipeRepository,
    private val foodRepo: FoodRepository,
) : ViewModel() {

    // -----------------------------
    // Recipe fields (edit/create)
    // -----------------------------

    private val nameFlow = MutableStateFlow("")
    private val servingsYieldFlow = MutableStateFlow(4.0)

    /**
     * Final cooked batch weight in grams (optional).
     * Used for gram-based logging and "per cooked gram" style computations later.
     */
    private val totalYieldGramsFlow = MutableStateFlow<Double?>(null)

    // -----------------------------
    // Add-ingredient flow
    // -----------------------------

    private val queryFlow = MutableStateFlow("")
    private val pickedFoodFlow = MutableStateFlow<Food?>(null)

    /**
     * Canonical numeric servings value used when the user taps "Add".
     */
    private val pickedServingsFlow = MutableStateFlow(1.0)

    /**
     * Raw text backing the Servings TextField.
     *
     * ## Why this exists (future debugging):
     * Binding a numeric TextField directly to `Double.toString()` creates a feedback loop:
     * keyboard input -> parse -> reformat -> set TextField value -> IME tries to “fix” it.
     * Symptoms include cursor jumps and “backspace adds zeros”.
     *
     * Solution: keep the TextField state as **String**, allow partial numeric strings while typing
     * (e.g. "", "1", "1.", "1.2"), and only update the Double when parsing succeeds.
     */
    private val pickedServingsTextFlow = MutableStateFlow("1.0")

    private val ingredientsFlow = MutableStateFlow<List<RecipeIngredientUi>>(emptyList())

    private val isSavingFlow = MutableStateFlow(false)
    private val errorFlow = MutableStateFlow<String?>(null)

    private var editFoodId: Long? = null

    // -----------------------------
    // Overlay (blocking sheet + navigation)
    // -----------------------------

    private val blockingSheetFlow = MutableStateFlow<BlockingSheetModel?>(null)
    private val blockedFoodIdFlow = MutableStateFlow<Long?>(null)
    private val navigateToEditFoodIdFlow = MutableStateFlow<Long?>(null)

    // -----------------------------
    // Search results + preview
    // -----------------------------

    private val resultsFlow: Flow<List<Food>> =
        queryFlow
            .debounce(150)
            .distinctUntilChanged()
            .flatMapLatest { q ->
                if (q.isBlank()) flowOf(emptyList())
                else searchFoods(q, limit = 50)
            }

    private val previewFlow: Flow<RecipeMacroPreview> =
        observeRecipeMacroPreview(
            ingredientsFlow.map { list -> list.map { it.foodId to it.servings } }
        )

    // -----------------------------
    // UI state
    // -----------------------------

    /**
     * Builds [RecipeBuilderState] from internal flows.
     *
     * ## Why we “group” combines (future debugging):
     * Some kotlinx.coroutines versions do not provide typed `combine()` overloads above a certain
     * arity (often 5). When you try `combine(f1,f2,f3,f4,f5,f6) { a,b,c,d,e,f -> ... }`,
     * Kotlin may resolve to the vararg overload:
     *
     *     combine(vararg flows) { values: Array<Any?> -> ... }
     *
     * which causes compile errors like:
     * "SuspendFunction6 ... was expected SuspendFunction1<Array<Any?>,...>"
     *
     * Fix: either use the `Array<Any?>` overload intentionally (leftFlow), or group combines into
     * smaller typed chunks (rightFlow).
     */
    val state: StateFlow<RecipeBuilderState> =
        run {
            // Left: explicitly use the Array<Any?> overload (safe + stable).
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

            // Right: group into 3 + 3 to avoid combine(6) typed overload issues.
            val rightAFlow: Flow<RightA> =
                combine(pickedServingsFlow, pickedServingsTextFlow, ingredientsFlow) { s, sText, ing ->
                    RightA(
                        pickedServings = s,
                        pickedServingsText = sText,
                        ingredients = ing
                    )
                }

            val rightBFlow: Flow<RightB> =
                combine(previewFlow, isSavingFlow, errorFlow) { preview, isSaving, error ->
                    RightB(preview = preview, isSaving = isSaving, error = error)
                }

            val rightFlow: Flow<RightBase> =
                combine(rightAFlow, rightBFlow) { a, b ->
                    RightBase(
                        pickedServings = a.pickedServings,
                        pickedServingsText = a.pickedServingsText,
                        ingredients = a.ingredients,
                        preview = b.preview,
                        isSaving = b.isSaving,
                        error = b.error
                    )
                }

            val overlayFlow: Flow<Overlay> =
                combine(
                    blockingSheetFlow,
                    blockedFoodIdFlow,
                    navigateToEditFoodIdFlow
                ) { blockingSheet, blockedFoodId, navFoodId ->
                    Overlay(
                        blockingSheet = blockingSheet,
                        blockedFoodId = blockedFoodId,
                        navigateToEditFoodId = navFoodId
                    )
                }

            combine(leftFlow, rightFlow, overlayFlow) { left, right, overlay ->
                val pickedGrams =
                    left.pickedFood?.gramsPerServingResolved()?.let { g -> right.pickedServings * g }

                RecipeBuilderState(
                    name = left.name,
                    servingsYield = left.servingsYield,
                    totalYieldGrams = left.totalYieldGrams,

                    query = left.query,
                    results = left.results,

                    pickedFood = left.pickedFood,
                    pickedServings = right.pickedServings,
                    pickedServingsText = right.pickedServingsText,
                    pickedGrams = pickedGrams,

                    ingredients = right.ingredients,

                    isSaving = right.isSaving,
                    errorMessage = right.error,

                    preview = right.preview,

                    blockingSheet = overlay.blockingSheet,
                    blockedFoodId = overlay.blockedFoodId,
                    navigateToEditFoodId = overlay.navigateToEditFoodId
                )
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                RecipeBuilderState()
            )
        }

    // -----------------------------
    // Events
    // -----------------------------

    fun onNameChange(v: String) {
        nameFlow.value = v
    }

    fun onYieldChange(v: Double) {
        servingsYieldFlow.value = v.coerceAtLeast(0.1)
    }

    fun onTotalYieldGramsChanged(value: Double?) {
        totalYieldGramsFlow.value = value?.takeIf { it > 0.0 }
    }

    fun onQueryChange(v: String) {
        queryFlow.value = v
    }

    fun clearError() {
        errorFlow.value = null
    }

    /**
     * TextField handler for the servings input.
     *
     * Accepts partial numeric strings while typing. Updates [pickedServingsFlow] only when parsing
     * succeeds. This is the key to fixing “wonky” numeric input.
     */
    fun onPickedServingsTextChange(raw: String) {
        if (!raw.matches(Regex("""^\d*([.]\d*)?$"""))) return

        pickedServingsTextFlow.value = raw

        raw.toDoubleOrNull()?.let { parsed ->
            // Keep Double in sync when user finishes a valid number.
            pickedServingsFlow.value = max(0.0, parsed)
        }
    }

    fun pickFood(food: Food) {
        pickedFoodFlow.value = food
        pickedServingsFlow.value = 1.0
        pickedServingsTextFlow.value = "1.0"
        errorFlow.value = null
    }

    fun clearPickedFood() {
        pickedFoodFlow.value = null
        pickedServingsFlow.value = 1.0
        pickedServingsTextFlow.value = "1.0"
    }

    /**
     * Used by +/- buttons (non-text input). Keep the text field synced.
     */
    fun onPickedServingsChange(v: Double) {
        val newVal = max(0.0, v)
        pickedServingsFlow.value = newVal
        pickedServingsTextFlow.value = newVal.toString()
    }

    /**
     * If you support grams entry, derive servings when grams-per-serving is known.
     * Keep the text field synced so the UI doesn't “snap back”.
     */
    fun onPickedGramsChange(grams: Double) {
        val food = pickedFoodFlow.value ?: return
        val g = food.gramsPerServingResolved() ?: return
        if (g <= 0.0) return

        val servings = (grams / g).coerceAtLeast(0.0)
        pickedServingsFlow.value = servings
        pickedServingsTextFlow.value = servings.toString()
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

        val next = ingredientsFlow.value.toMutableList()
        next.add(
            RecipeIngredientUi(
                foodId = food.id,
                foodName = food.name,
                servings = servings
            )
        )
        ingredientsFlow.value = next

        // Reset add-ingredient UI
        pickedFoodFlow.value = null
        pickedServingsFlow.value = 1.0
        pickedServingsTextFlow.value = "1.0"
        queryFlow.value = ""
        errorFlow.value = null
    }

    fun removeIngredientAt(index: Int) {
        val list = ingredientsFlow.value.toMutableList()
        if (index !in list.indices) return
        list.removeAt(index)
        ingredientsFlow.value = list
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
                    createRecipe(
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

                    // Keep the recipe "Food" name in sync.
                    foodRepo.getById(editingFoodId)?.let { food ->
                        foodRepo.upsert(food.copy(name = name, isRecipe = true))
                    }
                }

                onDone()
            } catch (t: Throwable) {
                errorFlow.value = t.message ?: "Failed to save recipe."
            } finally {
                isSavingFlow.value = false
            }
        }
    }

    fun loadForEdit(foodId: Long?) {
        if (foodId == null) return

        editFoodId = foodId

        // Reset "add ingredient" UI state
        pickedFoodFlow.value = null
        pickedServingsFlow.value = 1.0
        pickedServingsTextFlow.value = "1.0"
        queryFlow.value = ""

        viewModelScope.launch {
            val recipe = recipeRepo.getRecipeByFoodId(foodId) ?: return@launch
            val ingredients = recipeRepo.getIngredients(recipe.recipeId)

            val food = foodRepo.getById(foodId)

            val ids = ingredients.map { it.ingredientFoodId }.distinct()
            val nameById: Map<Long, String> =
                ids.associateWith { id ->
                    foodRepo.getById(id)?.name ?: "Food $id"
                }

            nameFlow.value = food?.name ?: nameFlow.value
            servingsYieldFlow.value = recipe.servingsYield
            totalYieldGramsFlow.value = recipe.totalYieldGrams

            ingredientsFlow.value = ingredients.map {
                RecipeIngredientUi(
                    foodId = it.ingredientFoodId,
                    foodName = nameById[it.ingredientFoodId] ?: "Food ${it.ingredientFoodId}",
                    servings = it.ingredientServings
                )
            }
        }
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
    val ingredients: List<RecipeIngredientUi>
)

private data class RightB(
    val preview: RecipeMacroPreview,
    val isSaving: Boolean,
    val error: String?
)

private data class RightBase(
    val pickedServings: Double,
    val pickedServingsText: String,
    val ingredients: List<RecipeIngredientUi>,
    val preview: RecipeMacroPreview,
    val isSaving: Boolean,
    val error: String?
)

private data class Overlay(
    val blockingSheet: BlockingSheetModel?,
    val blockedFoodId: Long?,
    val navigateToEditFoodId: Long?
)
