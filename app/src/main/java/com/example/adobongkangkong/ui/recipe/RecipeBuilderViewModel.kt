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
     * Used for gram-based logging and "per 100g" preview.
     */
    private val totalYieldGramsFlow = MutableStateFlow<Double?>(null)

    // -----------------------------
    // Add-ingredient flow
    // -----------------------------

    private val queryFlow = MutableStateFlow("")
    private val pickedFoodFlow = MutableStateFlow<Food?>(null)
    private val pickedServingsFlow = MutableStateFlow(1.0)

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

            val rightFlow: Flow<RightBase> =
                combine(
                    pickedServingsFlow,
                    ingredientsFlow,
                    previewFlow,
                    isSavingFlow,
                    errorFlow
                ) { pickedServings, ingredients, preview, isSaving, error ->
                    RightBase(
                        pickedServings = pickedServings,
                        ingredients = ingredients,
                        preview = preview,
                        isSaving = isSaving,
                        error = error
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
        // Lax: allow null/blank. Clamp negatives to null.
        totalYieldGramsFlow.value = value?.takeIf { it > 0.0 }
    }

    fun onQueryChange(v: String) {
        queryFlow.value = v
    }

    fun clearError() {
        errorFlow.value = null
    }

    fun pickFood(food: Food) {
        pickedFoodFlow.value = food
        pickedServingsFlow.value = 1.0
        errorFlow.value = null
    }

    fun clearPickedFood() {
        pickedFoodFlow.value = null
        pickedServingsFlow.value = 1.0
    }

    fun onPickedServingsChange(v: Double) {
        pickedServingsFlow.value = max(0.0, v)
    }

    fun onPickedGramsChange(grams: Double) {
        val food = pickedFoodFlow.value ?: return
        val g = food.gramsPerServingResolved() ?: return
        if (g <= 0.0) return
        pickedServingsFlow.value = (grams / g).coerceAtLeast(0.0)
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
     * If the user is trying to add an ingredient using servings/volume units but the food lacks
     * grams-per-serving, we block and send them to Food Editor instead of guessing.
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

        // ✅ Actually add the ingredient
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
                    // ---- Create ----
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
                    // ---- Edit ----
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

private data class RightBase(
    val pickedServings: Double,
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
