package com.example.adobongkangkong.ui.recipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.model.RecipeDraft
import com.example.adobongkangkong.domain.model.RecipeIngredientDraft
import com.example.adobongkangkong.domain.model.RecipeMacroPreview
import com.example.adobongkangkong.domain.nutrition.gramsPerServingResolved
import com.example.adobongkangkong.domain.repository.FoodNutrientRepository
import com.example.adobongkangkong.domain.repository.FoodRepository
import com.example.adobongkangkong.domain.repository.RecipeIngredientLine
import com.example.adobongkangkong.domain.repository.RecipeRepository
import com.example.adobongkangkong.domain.usecase.CreateRecipeUseCase
import com.example.adobongkangkong.domain.usecase.ObserveRecipeMacroPreviewUseCase
import com.example.adobongkangkong.domain.usecase.SearchFoodsUseCase
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

    private val nameFlow = MutableStateFlow("")
    private val yieldFlow = MutableStateFlow(4.0)

    private val queryFlow = MutableStateFlow("")
    private val pickedFoodFlow = MutableStateFlow<Food?>(null)
    private val pickedServingsFlow = MutableStateFlow(1.0)

    private val ingredientsFlow = MutableStateFlow<List<RecipeIngredientUi>>(emptyList())
    private val isSavingFlow = MutableStateFlow(false)
    private val errorFlow = MutableStateFlow<String?>(null)

    private var editFoodId: Long? = null

    private val resultsFlow: Flow<List<Food>> =
        queryFlow
            .debounce(150)
            .distinctUntilChanged()
            .flatMapLatest { q ->
                if (q.isBlank()) flowOf(emptyList()) else searchFoods(q, limit = 50)
            }

    private val previewFlow: Flow<RecipeMacroPreview> =
        observeRecipeMacroPreview(
            ingredientsFlow.map { list -> list.map { it.foodId to it.servings } }
        )

    val state: StateFlow<RecipeBuilderState> =
        combine(
            nameFlow,
            yieldFlow,
            queryFlow,
            resultsFlow,
            pickedFoodFlow,
            pickedServingsFlow,
            ingredientsFlow,
            previewFlow,
            isSavingFlow,
            errorFlow,
        ) { arr: Array<Any?> ->
            val name = arr[0] as String
            val servingsYield = arr[1] as Double
            val query = arr[2] as String
            val results = arr[3] as List<Food>
            val pickedFood = arr[4] as Food?
            val pickedServings = arr[5] as Double
            val ingredients = arr[6] as List<RecipeIngredientUi>
            val preview = arr[7] as RecipeMacroPreview
            val isSaving = arr[8] as Boolean
            val error = arr[9] as String?

            val pickedGrams = pickedFood?.gramsPerServingResolved()?.let { g -> pickedServings * g }

            RecipeBuilderState(
                name = name,
                servingsYield = servingsYield,
                query = query,
                results = results,
                pickedFood = pickedFood,
                pickedServings = pickedServings,
                pickedGrams = pickedGrams,
                ingredients = ingredients,
                preview = preview,
                isSaving = isSaving,
                errorMessage = error
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            RecipeBuilderState()
        )

    fun onNameChange(v: String) {
        nameFlow.value = v
    }

    fun onYieldChange(v: Double) {
        yieldFlow.value = v.coerceAtLeast(0.1)
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

    fun addPickedIngredient() {
        val food = pickedFoodFlow.value ?: return
        val servings = pickedServingsFlow.value
        if (servings <= 0.0) {
            errorFlow.value = "Ingredient amount must be > 0."
            return
        }

        val current = ingredientsFlow.value.toMutableList()
        current.add(
            RecipeIngredientUi(
                foodId = food.id,
                foodName = food.name,
                servings = servings
            )
        )
        ingredientsFlow.value = current

        // reset add flow
        pickedFoodFlow.value = null
        pickedServingsFlow.value = 1.0
        queryFlow.value = ""
    }

    fun removeIngredientAt(index: Int) {
        val list = ingredientsFlow.value.toMutableList()
        if (index !in list.indices) return
        list.removeAt(index)
        ingredientsFlow.value = list
    }


// set this inside loadForEdit(foodId): editFoodId = foodId
    fun save(onDone: () -> Unit) {
        val name = nameFlow.value.trim()
        val yield = yieldFlow.value
        val ingredients = ingredientsFlow.value

        if (name.isBlank()) {
            errorFlow.value = "Recipe name is required."
            return
        }
        if (yield <= 0.0) {
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
                            servingsYield = yield,
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
                        servingsYield = yield,
                        ingredients = ingredients.map {
                            RecipeIngredientLine(
                                ingredientFoodId = it.foodId,
                                ingredientServings = it.servings
                            )
                        }
                    )

                    // Optional but recommended: keep the recipe "Food" name in sync.
                    // If you already do this elsewhere, delete this block.
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

        this.editFoodId = foodId

        val editFoodId = foodId
        viewModelScope.launch {
            // Load recipe row by foodId
            val recipe = recipeRepo.getRecipeByFoodId(foodId) ?: return@launch
            val ingredients = recipeRepo.getIngredients(recipe.recipeId)

            // Load the food name (source of truth for display)
            val food = foodRepo.getById(foodId)

            val ids = ingredients.map { it.ingredientFoodId }.distinct()

            val nameById: Map<Long, String> =
                ids.associateWith { id ->
                    foodRepo.getById(id)?.name ?: "Food $id"
                }

            nameFlow.value = food?.name ?: nameFlow.value
            yieldFlow.value = recipe.servingsYield

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

