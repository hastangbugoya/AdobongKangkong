package com.example.adobongkangkong.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.recipes.FoodNutritionSnapshot
import com.example.adobongkangkong.domain.repository.FoodNutritionSnapshotRepository
import com.example.adobongkangkong.domain.usecase.SearchFoodsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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

/**
 * Small picker state holder for choosing one of the three caffeine widget quick-log foods.
 *
 * This intentionally does not log anything and does not own widget state.
 * It only searches foods and marks whether each result has positive caffeine data
 * in its normal nutrition snapshot.
 *
 * Caffeine truth source:
 * - [FoodNutritionSnapshot.nutrientsPerGram]
 * - [FoodNutritionSnapshot.nutrientsPerMilliliter]
 *
 * Missing caffeine data is treated as non-counting, not guessed.
 */
@HiltViewModel
class CaffeineWidgetFoodPickerViewModel @Inject constructor(
    private val searchFoods: SearchFoodsUseCase,
    private val snapshotRepository: FoodNutritionSnapshotRepository,
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")
    private val onlyFoodsWithCaffeineFlow = MutableStateFlow(true)

    private val resultsFlow =
        combine(
            queryFlow
                .debounce(150)
                .distinctUntilChanged(),
            onlyFoodsWithCaffeineFlow
        ) { query, onlyFoodsWithCaffeine ->
            PickerQuery(
                query = query.trim(),
                onlyFoodsWithCaffeine = onlyFoodsWithCaffeine
            )
        }.flatMapLatest { pickerQuery ->
            if (pickerQuery.query.isBlank()) {
                flowOf(emptyList())
            } else {
                searchFoods(
                    query = pickerQuery.query,
                    limit = 50
                ).map { foods ->
                    val snapshotsByFoodId =
                        snapshotRepository.getSnapshots(
                            foodIds = foods.map { it.id }.toSet()
                        )

                    foods.map { food ->
                        val hasCaffeine =
                            snapshotsByFoodId[food.id].hasPositiveCaffeine()

                        CaffeineWidgetFoodPickerFoodUiModel(
                            food = food,
                            hasCaffeine = hasCaffeine
                        )
                    }.filter { item ->
                        !pickerQuery.onlyFoodsWithCaffeine || item.hasCaffeine
                    }
                }
            }
        }

    val state: StateFlow<CaffeineWidgetFoodPickerState> =
        combine(
            queryFlow,
            onlyFoodsWithCaffeineFlow,
            resultsFlow
        ) { query, onlyFoodsWithCaffeine, results ->
            CaffeineWidgetFoodPickerState(
                query = query,
                onlyFoodsWithCaffeine = onlyFoodsWithCaffeine,
                results = results
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CaffeineWidgetFoodPickerState()
        )

    fun onQueryChange(query: String) {
        queryFlow.value = query
    }

    fun onOnlyFoodsWithCaffeineChange(onlyFoodsWithCaffeine: Boolean) {
        onlyFoodsWithCaffeineFlow.value = onlyFoodsWithCaffeine
    }

    private data class PickerQuery(
        val query: String,
        val onlyFoodsWithCaffeine: Boolean
    )
}

data class CaffeineWidgetFoodPickerState(
    val query: String = "",
    val onlyFoodsWithCaffeine: Boolean = true,
    val results: List<CaffeineWidgetFoodPickerFoodUiModel> = emptyList()
)

data class CaffeineWidgetFoodPickerFoodUiModel(
    val food: Food,
    val hasCaffeine: Boolean
)

private fun FoodNutritionSnapshot?.hasPositiveCaffeine(): Boolean {
    if (this == null) return false

    val caffeinePerGram =
        nutrientsPerGram?.get(NutrientKey.CAFFEINE_MG) ?: 0.0

    val caffeinePerMilliliter =
        nutrientsPerMilliliter?.get(NutrientKey.CAFFEINE_MG) ?: 0.0

    return caffeinePerGram > 0.0 || caffeinePerMilliliter > 0.0
}