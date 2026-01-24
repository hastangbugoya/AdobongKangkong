package com.example.adobongkangkong.ui.food

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.usecase.SearchFoodsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class FoodsListViewModel @Inject constructor(
    private val searchFoods: SearchFoodsUseCase
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

    val state: StateFlow<FoodsListState> =
        combine(queryFlow, filterFlow, resultsFlow) { q, filter, items ->
            val filtered = when (filter) {
                FoodsFilter.ALL -> items
                FoodsFilter.FOODS_ONLY -> items.filter { !it.isRecipe }
                FoodsFilter.RECIPES_ONLY -> items.filter { it.isRecipe }
            }
            FoodsListState(query = q, filter = filter, items = filtered)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoodsListState())

    fun onQueryChange(v: String) { queryFlow.value = v }
    fun onFilterChange(v: FoodsFilter) { filterFlow.value = v }
}
