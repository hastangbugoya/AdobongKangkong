package com.example.adobongkangkong.ui.food

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.data.local.db.dao.FoodGoalFlagsDao
import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.data.local.db.entity.FoodGoalFlagsEntity
import com.example.adobongkangkong.domain.logging.model.AmountInput
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.nutrition.NutrientBasisScaler
import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.recipes.ComputeRecipeKcalForFoodIdUseCase
import com.example.adobongkangkong.domain.recipes.FoodNutritionSnapshot
import com.example.adobongkangkong.domain.recipes.nutrientsForGrams
import com.example.adobongkangkong.domain.recipes.nutrientsForMilliliters
import com.example.adobongkangkong.domain.repository.FoodNutritionSnapshotRepository
import com.example.adobongkangkong.domain.usage.FoodValidationResult
import com.example.adobongkangkong.domain.usage.UsageContext
import com.example.adobongkangkong.domain.usage.ValidateFoodForUsageUseCase
import com.example.adobongkangkong.domain.usecase.SearchFoodsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlin.math.roundToInt

/**
 * ViewModel for the FoodsList screen.
 *
 * Responsibilities:
 * - Query + filter + sort foods/recipes.
 * - Build lightweight row UI models with:
 *   - per-serving kcal display
 *   - optional extra macro metric (per 100g or per 100mL)
 *   - fix banner message (single-source-of-truth validation)
 *
 * Correctness rules:
 * - Validation uses the domain single source of truth:
 *   [ValidateFoodForUsageUseCase] returning [FoodValidationResult].
 * - Sorting uses canonical per-100 values derived from snapshots:
 *   - Mass foods: PER_100G (snapshot.nutrientsForGrams(100.0))
 *   - Volume foods: PER_100ML (snapshot.nutrientsForMilliliters(100.0))
 * - Never compare grams vs mL values directly (no density guessing).
 *
 * Performance rules:
 * - Keep row construction cheap: compute once per list emission, no heavy composables.
 * - Use snapshot batch fetch to avoid N+1 queries.
 */
@HiltViewModel
class FoodsListViewModel @Inject constructor(
    private val searchFoods: SearchFoodsUseCase,
    private val foodGoalFlagsDao: FoodGoalFlagsDao,
    private val snapshotRepo: FoodNutritionSnapshotRepository,
    private val computeRecipeKcalForFoodId: ComputeRecipeKcalForFoodIdUseCase,
    private val validateFood: ValidateFoodForUsageUseCase,
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")
    private val filterFlow = MutableStateFlow(FoodsFilter.ALL)
    private val sortFlow = MutableStateFlow(FoodSortState())

    val query: StateFlow<String> = queryFlow

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
     * Snapshots for the current visible list.
     *
     * Used for:
     * - building per-100 caches
     * - determining if a food has nutrients (snapshot present)
     * - driving validation fix messages (single source of truth)
     */
    private val snapshotsByFoodIdFlow: Flow<Map<Long, FoodNutritionSnapshot>> =
        filteredFoodsFlow.mapLatest { foods ->
            val ids = foods.map { it.id }.toSet()
            if (ids.isEmpty()) emptyMap() else snapshotRepo.getSnapshots(ids)
        }

    /**
     * Canonical per-100 macro cache for the current visible list.
     *
     * snapshotRepo yields normalized maps; calling nutrientsForGrams(100.0) / nutrientsForMilliliters(100.0)
     * gives canonical per-100 basis for sorting.
     *
     * - Mass foods: per 100g
     * - Volume foods: per 100mL
     *
     * NOTE: grams and mL are not comparable without density; we never guess density.
     */
    private val per100ByFoodIdFlow: Flow<Map<Long, Per100Macros>> =
        snapshotsByFoodIdFlow.mapLatest { snapshotsById ->
            if (snapshotsById.isEmpty()) return@mapLatest emptyMap()

            val out = HashMap<Long, Per100Macros>(snapshotsById.size)
            for ((foodId, snapshot) in snapshotsById) {

                val (basis, per100) = when {
                    // Mass-grounded snapshot
                    snapshot.nutrientsPerGram != null ->
                        BasisType.PER_100G to snapshot.nutrientsForGrams(100.0)

                    // Volume-grounded snapshot
                    snapshot.nutrientsPerMilliliter != null ->
                        BasisType.PER_100ML to snapshot.nutrientsForMilliliters(100.0)

                    else -> null to null
                }

                val present = per100?.keys()?.map { it.value }?.toHashSet() ?: emptySet()

                fun pick(code: String): Double? {
                    return if (per100 != null && present.contains(code)) per100[NutrientKey(code)] else null
                }

                out[foodId] = Per100Macros(
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
                partial to per100ById
            }
            .combine(snapshotsByFoodIdFlow) { (partial, per100ById), snapshotsById ->

                val (q, filter, sort, foods, flagsById) = partial

                val rowsUnsorted = ArrayList<FoodsListRowUiModel>(foods.size)
                for (food in foods) {

                    val fixMessageForFood: String? =
                        if (food.isRecipe) {
                            null
                        } else {
                            val result = validateFood.execute(
                                ValidateFoodForUsageUseCase.PersistedInput(
                                    servingUnit = food.servingUnit,
                                    gramsPerServingUnit = food.gramsPerServingUnit,
                                    mlPerServingUnit = food.mlPerServingUnit,
                                    amountInput = AmountInput.ByServings(1.0),
                                    context = UsageContext.LOGGING,
                                    snapshot = snapshotsById[food.id] // may be null
                                )
                            )

                            // Recommended: list shows "fix" only for truly blocked foods.
                            when (result) {
                                FoodValidationResult.Ok -> null
                                is FoodValidationResult.Warning -> null
                                is FoodValidationResult.Blocked -> result.message
                            }
                        }

                    // ---- Recipes (special-case row content) ----
                    if (food.isRecipe) {
                        val kcalText =
                            when (val r = computeRecipeKcalForFoodId(food.id)) {
                                is ComputeRecipeKcalForFoodIdUseCase.Result.Success ->
                                    "B=${r.totalKcal}/S=${r.perServingKcal} kcal"
                                else -> "B=—/S=— kcal"
                            }

                        rowsUnsorted.add(
                            FoodsListRowUiModel(
                                foodId = food.id,
                                name = food.name,
                                brandText = food.brand?.takeIf { it.isNotBlank() } ?: "Generic",
                                caloriesPerServingText = kcalText,
                                extraMetricText = null,
                                isRecipe = true,
                                goalFlags = flagsById[food.id],
                                fixMessage = null
                            )
                        )
                        continue
                    }

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

                    rowsUnsorted.add(
                        FoodsListRowUiModel(
                            foodId = food.id,
                            name = food.name,
                            brandText = food.brand?.takeIf { it.isNotBlank() } ?: "Generic",
                            caloriesPerServingText = kcalPerServingText,
                            extraMetricText = extraMetricText,
                            isRecipe = false,
                            goalFlags = flagsById[food.id],
                            fixMessage = fixMessageForFood
                        )
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
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoodsListState())

    fun onQueryChange(v: String) {
        queryFlow.value = v
    }

    fun onFilterChange(v: FoodsFilter) {
        filterFlow.value = v
    }

    fun onSortKeyChange(key: FoodSortKey) {
        sortFlow.value = sortFlow.value.copy(key = key)
    }

    fun onSortDirectionToggle() {
        val cur = sortFlow.value
        sortFlow.value =
            cur.copy(direction = if (cur.direction == SortDirection.ASC) SortDirection.DESC else SortDirection.ASC)
    }

    fun onSortDirectionChange(dir: SortDirection) {
        sortFlow.value = sortFlow.value.copy(direction = dir)
    }

    private fun caloriesPerServingText(
        food: Food,
        basis: BasisType?,
        kcalPer100: Double?
    ): String {

        // NOTE: recipes render calories differently (batch/per serving).
        if (food.isRecipe) {
            val servingKcal: Int? = when (basis) {
                BasisType.PER_100G -> {
                    val gramsPerUnit = food.gramsPerServingUnit?.takeIf { it > 0.0 }
                    val grams: Double? =
                        if (food.servingUnit == ServingUnit.G) food.servingSize
                        else gramsPerUnit?.let { food.servingSize * it }

                    if (grams != null && kcalPer100 != null) (kcalPer100 * grams / 100.0).roundToInt()
                    else {
                        val result = NutrientBasisScaler.canonicalToDisplayPerServing(
                            storedAmount = kcalPer100 ?: return "B=—/S=— kcal",
                            storedBasis = BasisType.PER_100G,
                            servingSize = food.servingSize,
                            gramsPerServingUnit = gramsPerUnit
                        )
                        if (result.didScale) result.amount.roundToInt() else null
                    }
                }

                BasisType.PER_100ML -> {
                    val mlPerUnit = food.mlPerServingUnit?.takeIf { it > 0.0 }
                    val ml: Double? =
                        if (food.servingUnit == ServingUnit.ML) food.servingSize
                        else mlPerUnit?.let { food.servingSize * it }

                    if (ml != null && kcalPer100 != null) (kcalPer100 * ml / 100.0).roundToInt() else null
                }

                else -> null
            }

            val s = servingKcal
            val batchServings = food.servingsPerPackage?.takeIf { it > 0.0 }
            val b = if (s != null && batchServings != null) (s * batchServings).roundToInt() else null

            val bText = b?.toString() ?: "—"
            val sText = s?.toString() ?: "—"
            return "B=$bText/S=$sText kcal"
        }

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
 * =============================================================================
 * FUTURE-YOU / FUTURE AI NOTES — DO NOT DELETE
 * =============================================================================
 *
 * Validation single source of truth
 * - Foods list must NEVER re-introduce duplicate “fix message” rules.
 * - Always call ValidateFoodForUsageUseCase and only display fixMessage for Blocked.
 *
 * Sorting invariants
 * - Always use canonical per-100 snapshot values:
 *   - Mass foods: PER_100G (snapshot.nutrientsForGrams(100.0))
 *   - Volume foods: PER_100ML (snapshot.nutrientsForMilliliters(100.0))
 * - Never compare grams vs mL numbers directly. No density guessing.
 *
 * Logging-by-mL reminder (planned future work)
 * - This screen currently validates list rows assuming AmountInput.ByServings(1.0).
 * - When logging adds AmountInput.ByMilliliters:
 *     - Extend row validation checks to validate for that amount mode where appropriate.
 *     - Consider showing different fix banners depending on user’s last-used logging mode.
 *
 * IMPORTANT TO FUTURE AI:
 * - These reminders are intentional guardrails.
 * - Do NOT delete or “clean up” this bottom KDoc during refactors.
 */