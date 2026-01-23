package com.example.adobongkangkong.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.nutrition.gramsPerServingResolved
import com.example.adobongkangkong.domain.usecase.LogFoodUseCase
import com.example.adobongkangkong.domain.usecase.SearchFoodsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QuickAddViewModel @Inject constructor(
    private val searchFoods: SearchFoodsUseCase,
    private val logFood: LogFoodUseCase
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")

    private val selectedFoodFlow = MutableStateFlow<Food?>(null)
    private val servingsFlow = MutableStateFlow(1.0)

    val state: StateFlow<QuickAddState> = combine(
        queryFlow,
        queryFlow
            .debounce(150)
            .distinctUntilChanged()
            .flatMapLatest { q ->
                if (q.isBlank()) flowOf(emptyList())
                else searchFoods(q, limit = 50)
            },
        selectedFoodFlow,
        servingsFlow
    ) { query, results, selected, servings ->

        val servingUnitAmount = selected?.let { servings * it.servingSize }
        val gramsAmount = selected?.gramsPerServing?.let { g -> servings * g }

        QuickAddState(
            query = query,
            results = results,
            selectedFood = selected,
            servings = servings,
            servingUnitAmount = servingUnitAmount,
            gramsAmount = gramsAmount
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QuickAddState())

    fun onQueryChange(q: String) {
        queryFlow.value = q
    }

    fun onFoodSelected(food: Food) {
        selectedFoodFlow.value = food
        servingsFlow.value = 1.0
    }

    fun clearSelection() {
        selectedFoodFlow.value = null
        servingsFlow.value = 1.0
    }

    fun onServingsChanged(servings: Double) {
        servingsFlow.value = servings.coerceAtLeast(0.0)
    }

    fun onServingUnitAmountChanged(amount: Double) {
        val food = selectedFoodFlow.value ?: return
        if (food.servingSize <= 0.0) return
        servingsFlow.value = (amount / food.servingSize).coerceAtLeast(0.0)
    }

    fun onGramsChanged(grams: Double) {
        val food = selectedFoodFlow.value ?: return
        val g = food.gramsPerServingResolved() ?: return
        if (g <= 0.0) return
        servingsFlow.value = (grams / g).coerceAtLeast(0.0)
    }

    fun onPackageClicked(multiplier: Double = 1.0) {
        val food = selectedFoodFlow.value ?: return
        val spp = food.servingsPerPackage ?: return
        servingsFlow.value = (spp * multiplier).coerceAtLeast(0.0)
    }

    fun save(onDone: () -> Unit) {
        val food = selectedFoodFlow.value ?: return
        val servings = servingsFlow.value
        if (servings <= 0.0) return

        viewModelScope.launch {
            logFood(foodId = food.id, servings = servings)
            // reset for speed logging
            selectedFoodFlow.value = null
            servingsFlow.value = 1.0
            queryFlow.value = ""
            onDone()
        }
    }
}
