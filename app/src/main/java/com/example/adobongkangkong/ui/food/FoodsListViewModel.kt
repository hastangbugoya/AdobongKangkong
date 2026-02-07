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
import com.example.adobongkangkong.domain.recipes.nutrientsForMilliliters
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
     * Canonical per-100 macro cache for the current visible list.
     *
     * Note: snapshotRepo yields normalized maps; calling nutrientsForGrams(100.0) / nutrientsForMilliliters(100.0)
     * gives us a canonical per-100 basis for sorting.
     * - Mass foods: per 100g
     * - Volume foods: per 100mL
     *
     * Note: grams and mL are not comparable without density; we never guess density.
     */
    private val per100ByFoodIdFlow: Flow<Map<Long, Per100Macros>> =
        filteredFoodsFlow.mapLatest { foods ->
            val ids = foods.map { it.id }.toSet()
            if (ids.isEmpty()) return@mapLatest emptyMap()

            val snapshotsById = snapshotRepo.getSnapshots(ids)

            val out = HashMap<Long, Per100Macros>(foods.size)
            for (food in foods) {
                val snapshot = snapshotsById[food.id] ?: continue

                val (basis, per100) = when {
                    // Mass-grounded snapshot
                    snapshot.nutrientsPerGram != null -> BasisType.PER_100G to snapshot.nutrientsForGrams(100.0)

                    // Volume-grounded snapshot
                    snapshot.nutrientsPerMilliliter != null -> BasisType.PER_100ML to snapshot.nutrientsForMilliliters(100.0)

                    else -> null to null
                }

                val present = per100?.keys()?.map { it.value }?.toHashSet() ?: emptySet()

                fun pick(code: String): Double? {
                    return if (per100 != null && present.contains(code)) per100[NutrientKey(code)] else null
                }

                out[food.id] = Per100Macros(
                    basis = basis,
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
            .combine(per100ByFoodIdFlow) { partial, per100ById ->
                val (q, filter, sort, foods, flagsById) = partial

                val rowsUnsorted = foods.map { food ->
                    val macros100 = per100ById[food.id]

                    val kcalPerServingText =
                        caloriesPerServingText(
                            food = food,
                            basis = macros100?.basis,
                            kcalPer100 = macros100?.kcal
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
                                FoodSortKey.PROTEIN -> formatExtra("Protein", v, macros100?.basis)
                                FoodSortKey.CARBS -> formatExtra("Carbs", v, macros100?.basis)
                                FoodSortKey.FAT -> formatExtra("Fat", v, macros100?.basis)
                                FoodSortKey.SUGAR -> formatExtra("Sugar", v, macros100?.basis)
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
                    per100ById = per100ById
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
        basis: BasisType?,
        kcalPer100: Double?
    ): String {
        if (basis == null || kcalPer100 == null) return "— kcal"

        val label = servingLabel(food.servingSize, food.servingUnit)

        return when (basis) {
            BasisType.PER_100G -> {
                val gramsPerUnit = food.gramsPerServingUnit?.takeIf { it > 0.0 }

                val grams: Double? =
                    if (food.servingUnit == ServingUnit.G) {
                        food.servingSize
                    } else {
                        gramsPerUnit?.let { food.servingSize * it }
                    }

                if (grams != null) {
                    val kcal = (kcalPer100 * grams / 100.0).roundToInt()
                    "$kcal kcal/$label"
                } else {
                    // Fallback: try canonical scaler (keeps math out of UI)
                    val result = NutrientBasisScaler.canonicalToDisplayPerServing(
                        storedAmount = kcalPer100,
                        storedBasis = BasisType.PER_100G,
                        servingSize = food.servingSize,
                        gramsPerServingUnit = gramsPerUnit
                    )

                    if (result.didScale) "${result.amount.roundToInt()} kcal/$label" else "— kcal/$label"
                }
            }

            BasisType.PER_100ML -> {
                val mlPerUnit = food.mlPerServingUnit?.takeIf { it > 0.0 }

                val ml: Double? =
                    if (food.servingUnit == ServingUnit.ML) {
                        food.servingSize
                    } else {
                        mlPerUnit?.let { food.servingSize * it }
                    }

                if (ml != null) {
                    val kcal = (kcalPer100 * ml / 100.0).roundToInt()
                    "$kcal kcal/$label"
                } else {
                    "— kcal/$label"
                }
            }

            else -> "— kcal/$label"
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

    private fun formatExtra(label: String, v: Double?, basis: BasisType?): String {
        val suffix = when (basis) {
            BasisType.PER_100ML -> "/100mL"
            else -> "/100g"
        }
        return if (v == null) "— $label$suffix" else "${v.format1()} g $label$suffix"
    }

    private fun Double.format1(): String = "%,.1f".format(this).replace(",", "")

    private data class Per100Macros(
        val basis: BasisType?,
        val kcal: Double?,
        val protein: Double?,
        val carbs: Double?,
        val fat: Double?,
        val sugar: Double?,
    )

    private fun sortRows(
        rows: List<FoodsListRowUiModel>,
        sort: FoodSortState,
        per100ById: Map<Long, Per100Macros>
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

        fun metric(row: FoodsListRowUiModel): Pair<BasisType?, Double?> {
            val m = per100ById[row.foodId]
            val v = when (sort.key) {
                FoodSortKey.NAME -> null
                FoodSortKey.CALORIES -> m?.kcal
                FoodSortKey.PROTEIN -> m?.protein
                FoodSortKey.CARBS -> m?.carbs
                FoodSortKey.FAT -> m?.fat
                FoodSortKey.SUGAR -> m?.sugar
            }
            return m?.basis to v
        }

        fun basisRank(basis: BasisType?): Int =
            when (basis) {
                BasisType.PER_100G -> 0
                BasisType.PER_100ML -> 1
                else -> 2
            }

        return rows.sortedWith { a, b ->
            when (sort.key) {
                FoodSortKey.NAME -> {
                    val primary = nameCmp(a.name, b.name, sort.direction)
                    if (primary != 0) primary else nameCmp(a.brandText, b.brandText, SortDirection.ASC)
                }
                else -> {
                    val (ab, av) = metric(a)
                    val (bb, bv) = metric(b)

                    // Never directly compare grams vs mL values; group by basis first.
                    val basisCmp = basisRank(ab).compareTo(basisRank(bb))
                    if (basisCmp != 0) basisCmp
                    else {
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
}

/**
 * FUTURE-YOU NOTE (2026-02-06):
 *
 * - Sorting must ALWAYS use canonical per-100 values, never per-serving:
 *     - Mass foods: PER_100G (use snapshot.nutrientsForGrams(100.0))
 *     - Volume foods: PER_100ML (use snapshot.nutrientsForMilliliters(100.0))
 *
 * - Grams and mL are NOT comparable without density. Do not guess density.
 *   When sorting by a macro, group by basis first (PER_100G rows before PER_100ML) and sort within that group.
 *
 * - Missing selected macro values must ALWAYS sort to the bottom for both ASC and DESC.
 *
 * - Display is intentionally different:
 *     - rows show kcal/serv
 *     - optional macro shows per 100g OR per 100mL (matching the food’s canonical basis)
 *
 * - Keep scaling out of UI. Use NutrientBasisScaler for PER_100G per-serving display amounts.
 *   For PER_100ML, compute per-serving from mlPerServingUnit/ServingUnit.ML only (still no density).
 *
 * - Keep rows lightweight. Heavy row composables caused scroll jank before.
 */


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
