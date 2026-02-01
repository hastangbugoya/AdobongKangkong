package com.example.adobongkangkong.ui.food

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.data.local.db.dao.FoodGoalFlagsDao
import com.example.adobongkangkong.data.local.db.entity.FoodGoalFlagsEntity
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.usecase.SearchFoodsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class FoodsListViewModel @Inject constructor(
    private val searchFoods: SearchFoodsUseCase,
    private val foodGoalFlagsDao: FoodGoalFlagsDao,
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")
    private val filterFlow = MutableStateFlow(FoodsFilter.ALL)

    private val resultsFlow: Flow<List<Food>> =
        queryFlow
            .debounce(150)
            .distinctUntilChanged()
            .flatMapLatest { q ->
                if (q.isBlank()) searchFoods("", limit = 500) else searchFoods(q, limit = 200)
            }

    private val flagsByFoodIdFlow: Flow<Map<Long, FoodGoalFlagsEntity>> =
        foodGoalFlagsDao
            .observeAll()
            .map { list -> list.associateBy { it.foodId } }
            .distinctUntilChanged()

    val state: StateFlow<FoodsListState> =
        combine(queryFlow, filterFlow, resultsFlow, flagsByFoodIdFlow) { q, filter, foods, flagsById ->
            val filteredFoods = when (filter) {
                FoodsFilter.ALL -> foods
                FoodsFilter.FOODS_ONLY -> foods.filter { !it.isRecipe }
                FoodsFilter.RECIPES_ONLY -> foods.filter { it.isRecipe }
            }

            val uiItems = filteredFoods.map { food ->
                FoodListItemUiModel(
                    food = food,
                    goalFlags = flagsById[food.id]
                )
            }

            FoodsListState(
                query = q,
                filter = filter,
                items = uiItems
            )
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoodsListState())

    fun onQueryChange(v: String) { queryFlow.value = v }
    fun onFilterChange(v: FoodsFilter) { filterFlow.value = v }
}


