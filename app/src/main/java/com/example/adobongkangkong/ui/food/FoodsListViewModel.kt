package com.example.adobongkangkong.ui.food

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.data.local.db.dao.FoodGoalFlagsDao
import com.example.adobongkangkong.data.local.db.entity.FoodGoalFlagsEntity
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.recipes.nutrientsForGrams
import com.example.adobongkangkong.domain.repository.FoodNutritionSnapshotRepository
import com.example.adobongkangkong.domain.usecase.SearchFoodsUseCase
import com.example.adobongkangkong.ui.heatmap.NutrientSelector
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class FoodsListViewModel @Inject constructor(
    private val searchFoods: SearchFoodsUseCase,
    private val foodGoalFlagsDao: FoodGoalFlagsDao,
    private val snapshotRepo: FoodNutritionSnapshotRepository,
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

    /** Apply filter to raw search results. */
    private val filteredFoodsFlow: Flow<List<Food>> =
        combine(filterFlow, resultsFlow) { filter, foods ->
            when (filter) {
                FoodsFilter.ALL -> foods
                FoodsFilter.FOODS_ONLY -> foods.filter { !it.isRecipe }
                FoodsFilter.RECIPES_ONLY -> foods.filter { it.isRecipe }
            }
        }

    /**
     * Computes per-current-serving macro previews for foods currently visible in the list.
     *
     * DO NOT TOUCH THIS (future-you note):
     * - Always compute previews from FoodNutritionSnapshot (normalized per-gram maps).
     * - Avoid re-implementing scaling in UI.
     */
    private val macroPreviewByFoodIdFlow: Flow<Map<Long, FoodMacroPreviewUi>> =
        filteredFoodsFlow
            .mapLatest { foods ->
                val ids = foods.map { it.id }.toSet()
                if (ids.isEmpty()) return@mapLatest emptyMap()

                val snapshotsById = snapshotRepo.getSnapshots(ids)

                val out = HashMap<Long, FoodMacroPreviewUi>(foods.size)
                for (food in foods) {
                    val gramsForServing = gramsForCurrentServing(food) ?: continue
                    val snapshot = snapshotsById[food.id] ?: continue

                    val map = snapshot.nutrientsForGrams(gramsForServing)
                    val preview = map.toMacroPreviewOrNull(foodId = food.id)
                    if (preview != null) out[food.id] = preview
                }

                if (out.isEmpty() && foods.isNotEmpty()) {
                    // Debug hint: helpful when codes mismatch (all zeros) or grams missing.
                    Log.d("Meow", "FoodsList macros empty. foods=${foods.size} snapshots=${snapshotsById.size}")
                }

                out
            }

    val state: StateFlow<FoodsListState> =
        combine(
            queryFlow,
            filterFlow,
            filteredFoodsFlow,
            flagsByFoodIdFlow,
            macroPreviewByFoodIdFlow
        ) { q, filter, foods, flagsById, macrosById ->

            val uiItems = foods.map { food ->
                FoodListItemUiModel(
                    food = food,
                    goalFlags = flagsById[food.id]
                )
            }

            FoodsListState(
                query = q,
                filter = filter,
                items = uiItems,
                macroPreviewByFoodId = macrosById
            )
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoodsListState())

    fun onQueryChange(v: String) { queryFlow.value = v }
    fun onFilterChange(v: FoodsFilter) { filterFlow.value = v }

    private fun gramsForCurrentServing(food: Food): Double? {
        val size = food.servingSize
        if (size <= 0.0) return null

        return when (food.servingUnit) {
            ServingUnit.G -> size
            else -> {
                val gPerUnit = food.gramsPerServingUnit
                if (gPerUnit == null || gPerUnit <= 0.0) null else size * gPerUnit
            }
        }
    }

    private fun com.example.adobongkangkong.domain.nutrition.NutrientMap.toMacroPreviewOrNull(
        foodId: Long
    ): FoodMacroPreviewUi? {
        // Prefer key presence checks rather than "0 means missing".
        val present = this.keys().map { it.value }.toHashSet()

        fun pick(vararg codes: String): Double? {
            for (c in codes) {
                if (present.contains(c)) return this[NutrientKey(c)]
            }
            return null
        }

        // Our canonical CSV/DB codes come from NutrientCodes (e.g. CALORIES_KCAL),
        // but keep some legacy fallbacks for safety.
        val kcal = pick(NutrientKey.CALORIES_KCAL.value)
        val protein = pick(NutrientKey.PROTEIN_G.value)
        val carbs = pick(NutrientKey.CARBS_G.value)
        val fat = pick(NutrientKey.FAT_G.value)
        val sugars = pick(NutrientKey.SUGAR_G.value)

        // If nothing relevant is present, treat as missing (don’t show chips).
        if (kcal == null && protein == null && carbs == null && fat == null) {
            Log.d("Meow", "No macro codes present for foodId=$foodId keys=$present")
            return null
        }

        return FoodMacroPreviewUi(
            caloriesKcal = kcal ?: 0.0,
            proteinG = protein ?: 0.0,
            carbsG = carbs ?: 0.0,
            fatG = fat ?: 0.0,
            sugarsG = sugars
        )
    }
}
