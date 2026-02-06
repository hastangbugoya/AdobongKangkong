package com.example.adobongkangkong.ui.food

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.data.local.db.dao.FoodGoalFlagsDao
import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.data.local.db.entity.FoodGoalFlagsEntity
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.nutrition.NutrientBasisScaler
import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.recipes.nutrientsForGrams
import com.example.adobongkangkong.domain.repository.FoodNutritionSnapshotRepository
import com.example.adobongkangkong.domain.usecase.SearchFoodsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.roundToInt
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
    private val sortFlow = MutableStateFlow(FoodSortState())

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

    /** Apply All/Foods/Recipes filter to raw search results. */
    private val filteredFoodsFlow: Flow<List<Food>> =
        combine(filterFlow, resultsFlow) { filter, foods ->
            when (filter) {
                FoodsFilter.ALL -> foods
                FoodsFilter.FOODS_ONLY -> foods.filter { !it.isRecipe }
                FoodsFilter.RECIPES_ONLY -> foods.filter { it.isRecipe }
            }
        }

    /**
     * Canonical per-100g macro cache for the current visible list.
     *
     * Note: snapshotRepo yields normalized maps; calling nutrientsForGrams(100.0) gives us
     * a canonical apples-to-apples basis for sorting (per 100g).
     */
    private val per100gByFoodIdFlow: Flow<Map<Long, Per100gMacros>> =
        filteredFoodsFlow.mapLatest { foods ->
            val ids = foods.map { it.id }.toSet()
            if (ids.isEmpty()) return@mapLatest emptyMap()

            val snapshotsById = snapshotRepo.getSnapshots(ids)

            val out = HashMap<Long, Per100gMacros>(foods.size)
            for (food in foods) {
                val snapshot = snapshotsById[food.id] ?: continue
                val per100g = snapshot.nutrientsForGrams(100.0)
                val present = per100g.keys().map { it.value }.toHashSet()

                fun pick(code: String): Double? {
                    return if (present.contains(code)) per100g[NutrientKey(code)] else null
                }

                out[food.id] = Per100gMacros(
                    kcal = pick(NutrientKey.CALORIES_KCAL.value),
                    protein = pick(NutrientKey.PROTEIN_G.value),
                    carbs = pick(NutrientKey.CARBS_G.value),
                    fat = pick(NutrientKey.FAT_G.value),
                    sugar = pick(NutrientKey.SUGAR_G.value)
                )
            }
            out
        }

    private data class Partial(
        val q: String,
        val filter: FoodsFilter,
        val sort: FoodSortState,
        val foods: List<Food>,
        val flagsById: Map<Long, FoodGoalFlagsEntity>,
    )

    val state: StateFlow<FoodsListState> =
        combine(
            queryFlow,
            filterFlow,
            sortFlow,
            filteredFoodsFlow,
            flagsByFoodIdFlow
        ) { q, filter, sort, foods, flagsById ->
            Partial(
                q = q,
                filter = filter,
                sort = sort,
                foods = foods,
                flagsById = flagsById
            )
        }
            .combine(per100gByFoodIdFlow) { partial, per100gById ->
                val (q, filter, sort, foods, flagsById) = partial

                val rowsUnsorted = foods.map { food ->
                    val macros100 = per100gById[food.id]

                    val kcalPerServingText =
                        caloriesPerServingText(
                            food = food,
                            kcalPer100g = macros100?.kcal
                        )

                    val extraMetricText =
                        if (sort.showExtraMetricOnRow) {
                            val v = when (sort.key) {
                                FoodSortKey.PROTEIN -> macros100?.protein
                                FoodSortKey.CARBS -> macros100?.carbs
                                FoodSortKey.FAT -> macros100?.fat
                                FoodSortKey.SUGAR -> macros100?.sugar
                                else -> null
                            }

                            when (sort.key) {
                                FoodSortKey.PROTEIN -> formatExtra("Protein", v)
                                FoodSortKey.CARBS -> formatExtra("Carbs", v)
                                FoodSortKey.FAT -> formatExtra("Fat", v)
                                FoodSortKey.SUGAR -> formatExtra("Sugar", v)
                                else -> null
                            }
                        } else null

                    FoodsListRowUiModel(
                        foodId = food.id,
                        name = food.name,
                        brandText = food.brand?.takeIf { it.isNotBlank() } ?: "Generic",
                        caloriesPerServingText = kcalPerServingText,
                        extraMetricText = extraMetricText,
                        isRecipe = food.isRecipe,
                        goalFlags = flagsById[food.id]
                    )
                }

                val sortedRows = sortRows(
                    rows = rowsUnsorted,
                    sort = sort,
                    per100gById = per100gById
                )

                FoodsListState(
                    query = q,
                    filter = filter,
                    sort = sort,
                    rows = sortedRows
                )

            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoodsListState())

    fun onQueryChange(v: String) { queryFlow.value = v }
    fun onFilterChange(v: FoodsFilter) { filterFlow.value = v }

    fun onSortKeyChange(key: FoodSortKey) {
        sortFlow.value = sortFlow.value.copy(key = key)
    }

    fun onSortDirectionToggle() {
        val cur = sortFlow.value
        sortFlow.value = cur.copy(direction = if (cur.direction == SortDirection.ASC) SortDirection.DESC else SortDirection.ASC)
    }

    fun onSortDirectionChange(dir: SortDirection) {
        sortFlow.value = sortFlow.value.copy(direction = dir)
    }

    private fun caloriesPerServingText(
        food: Food,
        kcalPer100g: Double?
    ): String {
        if (kcalPer100g == null) return "— kcal"

        val label = servingLabel(food.servingSize, food.servingUnit)

        // If we know grams-per-unit, we can always compute kcal manually
        val gramsPerUnit = food.gramsPerServingUnit
        if (gramsPerUnit != null && gramsPerUnit > 0.0) {
            val grams =
                if (food.servingUnit == ServingUnit.G)
                    food.servingSize
                else
                    food.servingSize * gramsPerUnit

            val kcal = (kcalPer100g * grams / 100.0).roundToInt()
            return "$kcal kcal/$label"
        }

        // Fallback: try canonical scaler (for safety / future units)
        val result = NutrientBasisScaler.canonicalToDisplayPerServing(
            storedAmount = kcalPer100g,
            storedBasis = BasisType.PER_100G,
            servingSize = food.servingSize,
            gramsPerServingUnit = gramsPerUnit
        )

        return if (result.didScale) {
            "${result.amount.roundToInt()} kcal/$label"
        } else {
            "— kcal/$label"
        }
    }



    private fun servingLabel(servingSize: Double, servingUnit: ServingUnit): String {
        val sizeText =
            if (servingSize == servingSize.toInt().toDouble())
                servingSize.toInt().toString()
            else
                "%.2f".format(servingSize).trimEnd('0').trimEnd('.')

        return "$sizeText ${servingUnit.display}"
    }

    private fun formatExtra(label: String, v: Double?): String {
        return if (v == null) "— $label/100g" else "${v.format1()} g $label/100g"
    }

    private fun Double.format1(): String = "%,.1f".format(this).replace(",", "")

    private data class Per100gMacros(
        val kcal: Double?,
        val protein: Double?,
        val carbs: Double?,
        val fat: Double?,
        val sugar: Double?,
    )

    private fun sortRows(
        rows: List<FoodsListRowUiModel>,
        sort: FoodSortState,
        per100gById: Map<Long, Per100gMacros>
    ): List<FoodsListRowUiModel> {
        if (rows.isEmpty()) return rows

        fun nameCmp(a: String, b: String, dir: SortDirection): Int {
            val cmp = a.lowercase().compareTo(b.lowercase())
            return if (dir == SortDirection.ASC) cmp else -cmp
        }

        fun compareNullableLast(a: Double?, b: Double?, dir: SortDirection): Int {
            val aNull = a == null
            val bNull = b == null
            if (aNull && bNull) return 0
            if (aNull) return 1   // always bottom
            if (bNull) return -1  // always bottom

            val cmp = a!!.compareTo(b!!)
            return if (dir == SortDirection.ASC) cmp else -cmp
        }

        fun metric(row: FoodsListRowUiModel): Double? {
            val m = per100gById[row.foodId]
            return when (sort.key) {
                FoodSortKey.NAME -> null
                FoodSortKey.CALORIES -> m?.kcal
                FoodSortKey.PROTEIN -> m?.protein
                FoodSortKey.CARBS -> m?.carbs
                FoodSortKey.FAT -> m?.fat
                FoodSortKey.SUGAR -> m?.sugar
            }
        }

        return rows.sortedWith { a, b ->
            when (sort.key) {
                FoodSortKey.NAME -> {
                    val primary = nameCmp(a.name, b.name, sort.direction)
                    if (primary != 0) primary else nameCmp(a.brandText, b.brandText, SortDirection.ASC)
                }
                else -> {
                    val av = metric(a)
                    val bv = metric(b)

                    val primary = compareNullableLast(av, bv, sort.direction)
                    if (primary != 0) primary
                    else {
                        val t1 = nameCmp(a.name, b.name, SortDirection.ASC)
                        if (t1 != 0) t1 else nameCmp(a.brandText, b.brandText, SortDirection.ASC)
                    }
                }
            }
        }
    }
}

/**
 * FUTURE-YOU NOTE (2026-02-06):
 *
 * - Sorting must ALWAYS use canonical apples-to-apples values (PER_100G), never per-serving,
 *   because serving sizes are arbitrary and make "kcal per serving" meaningless for ordering.
 * - Missing selected macro values must ALWAYS sort to the bottom for both ASC and DESC.
 * - Display is intentionally different: rows show kcal/serv (and optionally one macro/100g when sorting by that macro).
 * - Keep scaling out of UI. Use NutrientBasisScaler for per-serving display amounts.
 * - If servingUnit == G, treat gramsPerServingUnit as 1.0 so canonical scaling works (servingSize is grams).
 * - Keep rows lightweight (no chips). Heavy row composables caused scroll jank before.
 */
