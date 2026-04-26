package com.example.adobongkangkong.ui.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.R
import com.example.adobongkangkong.data.local.db.dao.FoodGoalFlagsDao
import com.example.adobongkangkong.data.local.db.entity.FoodGoalFlagsEntity
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.planner.usecase.ObservePlannedFoodNeedsUseCase
import com.example.adobongkangkong.domain.planner.usecase.ObservePlannedFoodTotalsUseCase
import com.example.adobongkangkong.domain.planner.usecase.ObservePlannedRecipeShoppingRequirementsUseCase
import com.example.adobongkangkong.domain.planner.usecase.ObservePlannedRecipeShoppingRequirementsUseCase.RecipeIngredientRequirement
import com.example.adobongkangkong.domain.planner.usecase.ObservePlannedRecipeShoppingRequirementsUseCase.RecipeOccurrenceRequirement
import com.example.adobongkangkong.domain.planner.usecase.ObservePlannedRecipeShoppingRequirementsUseCase.RecipeTotalRequirement
import com.example.adobongkangkong.domain.planner.usecase.PlannedFoodNeed
import com.example.adobongkangkong.domain.planner.usecase.PlannedFoodTotalNeed
import com.example.adobongkangkong.domain.repository.FoodRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ShoppingViewModel @Inject constructor(
    private val observeTotals: ObservePlannedFoodTotalsUseCase,
    private val observeNeeds: ObservePlannedFoodNeedsUseCase,
    private val observeRecipeShopping: ObservePlannedRecipeShoppingRequirementsUseCase,
    private val foodGoalFlagsDao: FoodGoalFlagsDao,
    private val foodRepo: FoodRepository
) : ViewModel() {

    private val startDateFlow = MutableStateFlow(LocalDate.now())
    private val daysFlow = MutableStateFlow(7)
    private val daysTextFlow = MutableStateFlow("7")
    private val checkedFoodIdsFlow = MutableStateFlow<Set<Long>>(emptySet())
    private val unitModeFlow = MutableStateFlow(ShoppingUnitMode.METRIC)

    fun setStartDate(d: LocalDate) {
        startDateFlow.value = d
    }

    fun onDaysTextChanged(v: String) {
        daysTextFlow.value = v
        val parsed = v.toIntOrNull()
        if (parsed != null && parsed > 0) {
            daysFlow.value = parsed.coerceIn(1, 365)
        }
    }

    fun onSimpleItemCheckedChanged(foodId: Long, isChecked: Boolean) {
        checkedFoodIdsFlow.value = if (isChecked) {
            checkedFoodIdsFlow.value + foodId
        } else {
            checkedFoodIdsFlow.value - foodId
        }
    }

    fun onShoppingUnitModeChanged(mode: ShoppingUnitMode) {
        unitModeFlow.value = mode
    }

    private val shoppingWindowFlow: Flow<Pair<LocalDate, Int>> =
        combine(startDateFlow, daysFlow) { start, days -> start to days }

    private val flagsByFoodIdFlow: Flow<Map<Long, FoodGoalFlagsEntity>> =
        foodGoalFlagsDao
            .observeAll()
            .map { list -> list.associateBy { it.foodId } }
            .distinctUntilChanged()

    private val recipeFoodIdsFlow: Flow<Set<Long>> =
        shoppingWindowFlow
            .flatMapLatest { (start, days) -> observeRecipeShopping(start, days) }
            .map { result -> result.totalled.map { it.recipeFoodId }.toSet() }

    private val simpleItemsFlow: Flow<List<ShoppingListItemUi>> =
        combine(
            shoppingWindowFlow.flatMapLatest { (start, days) ->
                observeTotals(startDate = start, days = days)
            },
            shoppingWindowFlow.flatMapLatest { (start, days) ->
                observeRecipeShopping(startDate = start, days = days)
            },
            checkedFoodIdsFlow,
            unitModeFlow
        ) { totals, recipeShopping, checkedFoodIds, unitMode ->
            SimpleShoppingProjectionInputs(
                totals = totals,
                recipeShopping = recipeShopping,
                checkedFoodIds = checkedFoodIds,
                unitMode = unitMode
            )
        }.mapLatest { input ->
            val foodCache = mutableMapOf<Long, Food?>()

            suspend fun getFoodCached(foodId: Long): Food? {
                return foodCache.getOrPut(foodId) {
                    foodRepo.getById(foodId)
                }
            }

            suspend fun convertServingsToBase(
                foodId: Long,
                servings: Double
            ): Pair<Double?, Double?> {
                val food = getFoodCached(foodId) ?: return null to null

                val gramsPerServing =
                    food.gramsPerServingUnit?.let { it * food.servingSize }

                val mlPerServing =
                    food.mlPerServingUnit?.let { it * food.servingSize }

                return when {
                    gramsPerServing != null -> (servings * gramsPerServing) to null
                    mlPerServing != null -> null to (servings * mlPerServing)
                    else -> null to null
                }
            }

            val recipeFoodIds = input.recipeShopping.totalled.map { it.recipeFoodId }.toSet()

            val recipeEarliestDateByRecipeFoodId: Map<Long, LocalDate?> =
                input.recipeShopping.notTotalled
                    .groupBy { it.recipeFoodId }
                    .mapValues { (_, rows) ->
                        rows.mapNotNull { row ->
                            runCatching { LocalDate.parse(row.dateIso) }.getOrNull()
                        }.minOrNull()
                    }

            val aggregatesByFoodId = linkedMapOf<Long, ShoppingListAggregate>()

            input.totals
                .filterNot { it.foodId in recipeFoodIds }
                .forEach { total ->
                    val existing = aggregatesByFoodId[total.foodId]
                    val servings = total.unconvertedServingsTotal ?: 0.0

                    val (convertedGrams, convertedMl) =
                        if (servings > 0.0) {
                            convertServingsToBase(total.foodId, servings)
                        } else {
                            null to null
                        }

                    val addedGrams =
                        (total.gramsTotal ?: 0.0) + (convertedGrams ?: 0.0)

                    val addedMl =
                        (total.mlTotal ?: 0.0) + (convertedMl ?: 0.0)

                    aggregatesByFoodId[total.foodId] = ShoppingListAggregate(
                        foodId = total.foodId,
                        foodName = existing?.foodName ?: total.foodName,
                        earliestDate = minDate(existing?.earliestDate, total.earliestNextPlannedDate),
                        gramsTotal = (existing?.gramsTotal ?: 0.0) + addedGrams,
                        mlTotal = (existing?.mlTotal ?: 0.0) + addedMl,
                        servingsTotal = existing?.servingsTotal ?: 0.0
                    )
                }

            input.recipeShopping.totalled.forEach { recipeTotal ->
                val recipeEarliestDate = recipeEarliestDateByRecipeFoodId[recipeTotal.recipeFoodId]

                recipeTotal.ingredients.forEach { ingredient ->
                    val existing = aggregatesByFoodId[ingredient.foodId]

                    val (convertedGrams, convertedMl) =
                        if (ingredient.source == ObservePlannedRecipeShoppingRequirementsUseCase.IngredientQuantitySource.SERVINGS) {
                            convertServingsToBase(ingredient.foodId, ingredient.amountRequired)
                        } else {
                            null to null
                        }

                    val addedGrams =
                        if (ingredient.source == ObservePlannedRecipeShoppingRequirementsUseCase.IngredientQuantitySource.GRAMS) {
                            ingredient.amountRequired
                        } else {
                            convertedGrams ?: 0.0
                        }

                    val addedMl = convertedMl ?: 0.0

                    aggregatesByFoodId[ingredient.foodId] = ShoppingListAggregate(
                        foodId = ingredient.foodId,
                        foodName = existing?.foodName ?: ingredient.foodName,
                        earliestDate = minDate(existing?.earliestDate, recipeEarliestDate),
                        gramsTotal = (existing?.gramsTotal ?: 0.0) + addedGrams,
                        mlTotal = (existing?.mlTotal ?: 0.0) + addedMl,
                        servingsTotal = existing?.servingsTotal ?: 0.0
                    )
                }
            }

            aggregatesByFoodId
                .values
                .map { aggregate ->
                    val avgPricePer100g = foodRepo.getAveragePricePer100gForFood(aggregate.foodId)
                    val avgPricePer100ml = foodRepo.getAveragePricePer100mlForFood(aggregate.foodId)

                    ShoppingListItemUi(
                        foodId = aggregate.foodId,
                        name = aggregate.foodName,
                        amountText = aggregate.toAmountText(input.unitMode),
                        firstNeededDateText = formatSimpleShoppingDate(aggregate.earliestDate),
                        estimatedCostText = computeEstimatedCostText(
                            gramsTotal = aggregate.gramsTotal.takeIf { it > 0.0 },
                            mlTotal = aggregate.mlTotal.takeIf { it > 0.0 },
                            avgPricePer100g = avgPricePer100g,
                            avgPricePer100ml = avgPricePer100ml
                        )?.removePrefix("est: "),
                        isChecked = aggregate.foodId in input.checkedFoodIds
                    )
                }
                .sortedWith(
                    compareBy<ShoppingListItemUi> { it.isChecked }
                        .thenBy { aggregatesByFoodId[it.foodId]?.earliestDate ?: LocalDate.MAX }
                        .thenBy { it.name.lowercase() }
                        .thenBy { it.foodId }
                )
        }

    private val totalsUiFlow: Flow<List<ShoppingTotalRowUi>> =
        combine(
            shoppingWindowFlow.flatMapLatest { (start, days) ->
                observeTotals(startDate = start, days = days)
            },
            recipeFoodIdsFlow
        ) { totals, recipeIds ->
            totals
                .filterNot { it.foodId in recipeIds }
                .sortedWith(
                    compareBy<PlannedFoodTotalNeed> { it.earliestNextPlannedDate ?: LocalDate.MAX }
                        .thenBy { it.foodName.lowercase() }
                        .thenBy { it.foodId }
                )
        }.mapLatest { totals ->
            totals.map { total ->
                val avgPricePer100g = foodRepo.getAveragePricePer100gForFood(total.foodId)
                val avgPricePer100ml = foodRepo.getAveragePricePer100mlForFood(total.foodId)
                total.toUi(
                    avgPricePer100g = avgPricePer100g,
                    avgPricePer100ml = avgPricePer100ml
                )
            }
        }

    private val needsUiFlow: Flow<List<ShoppingNeedsGroupUi>> =
        combine(
            shoppingWindowFlow.flatMapLatest { (start, days) ->
                observeNeeds(startDate = start, days = days)
            },
            recipeFoodIdsFlow
        ) { list, recipeIds ->
            list
                .filterNot { it.foodId in recipeIds }
                .toGroupedUi()
        }

    private val recipeTotalledUiFlow: Flow<List<ShoppingRecipeTotalGroupUi>> =
        shoppingWindowFlow
            .flatMapLatest { (start, days) -> observeRecipeShopping(startDate = start, days = days) }
            .mapLatest { result ->
                val earliestDateByRecipeFoodId: Map<Long, String> =
                    result.notTotalled
                        .groupBy { it.recipeFoodId }
                        .mapValues { (_, rows) ->
                            val earliestIso = rows.minOfOrNull { it.dateIso }
                            formatShoppingDateFromIso(earliestIso ?: "")
                        }

                result.totalled
                    .sortedWith(
                        compareBy<RecipeTotalRequirement> { it.recipeName.lowercase() }
                            .thenBy { it.recipeFoodId }
                    )
                    .map { total ->
                        total.toTotalledUi(
                            nextDateText = earliestDateByRecipeFoodId[total.recipeFoodId],
                            foodRepo = foodRepo
                        )
                    }
            }

    private val recipeNotTotalledUiFlow: Flow<List<ShoppingRecipeOccurrenceGroupUi>> =
        shoppingWindowFlow
            .flatMapLatest { (start, days) -> observeRecipeShopping(startDate = start, days = days) }
            .mapLatest { result ->
                result.notTotalled
                    .sortedWith(
                        compareBy<RecipeOccurrenceRequirement> { it.dateIso }
                            .thenBy { it.recipeName.lowercase() }
                            .thenBy { it.recipeFoodId }
                    )
                    .map { it.toOccurrenceUi(foodRepo = foodRepo) }
            }

    val state: StateFlow<ShoppingState> =
        combine(
            daysTextFlow,
            totalsUiFlow,
            needsUiFlow,
            flagsByFoodIdFlow,
            simpleItemsFlow
        ) { daysText, totals, groups, flags, simpleItems ->
            BaseShoppingState(
                daysText = daysText,
                totalledRows = totals,
                notTotalledGroups = groups,
                flagsByFoodId = flags,
                simpleItems = simpleItems
            )
        }
            .combine(unitModeFlow) { base, unitMode ->
                base.copy(unitMode = unitMode)
            }
            .combine(recipeTotalledUiFlow) { base, recipeTotals ->
                base.copy(recipeTotalledGroups = recipeTotals)
            }
            .combine(recipeNotTotalledUiFlow) { base, recipeOccurrences ->
                base.copy(recipeNotTotalledGroups = recipeOccurrences)
            }
            .map { base ->
                ShoppingState(
                    daysText = base.daysText,
                    totalledRows = base.totalledRows,
                    notTotalledGroups = base.notTotalledGroups,
                    recipeTotalledGroups = base.recipeTotalledGroups,
                    recipeNotTotalledGroups = base.recipeNotTotalledGroups,
                    flagsByFoodId = base.flagsByFoodId,
                    simpleItems = base.simpleItems,
                    unitMode = base.unitMode
                )
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShoppingState())
}

enum class ShoppingUnitMode {
    METRIC,
    GROCERY
}

private data class SimpleShoppingProjectionInputs(
    val totals: List<PlannedFoodTotalNeed>,
    val recipeShopping: ObservePlannedRecipeShoppingRequirementsUseCase.Result,
    val checkedFoodIds: Set<Long>,
    val unitMode: ShoppingUnitMode
)

private data class BaseShoppingState(
    val daysText: String,
    val totalledRows: List<ShoppingTotalRowUi>,
    val notTotalledGroups: List<ShoppingNeedsGroupUi>,
    val recipeTotalledGroups: List<ShoppingRecipeTotalGroupUi> = emptyList(),
    val recipeNotTotalledGroups: List<ShoppingRecipeOccurrenceGroupUi> = emptyList(),
    val flagsByFoodId: Map<Long, FoodGoalFlagsEntity>,
    val simpleItems: List<ShoppingListItemUi> = emptyList(),
    val unitMode: ShoppingUnitMode = ShoppingUnitMode.METRIC
)

data class ShoppingState(
    val daysText: String = "7",
    val totalledRows: List<ShoppingTotalRowUi> = emptyList(),
    val notTotalledGroups: List<ShoppingNeedsGroupUi> = emptyList(),
    val recipeTotalledGroups: List<ShoppingRecipeTotalGroupUi> = emptyList(),
    val recipeNotTotalledGroups: List<ShoppingRecipeOccurrenceGroupUi> = emptyList(),
    val flagsByFoodId: Map<Long, FoodGoalFlagsEntity> = emptyMap(),
    val simpleItems: List<ShoppingListItemUi> = emptyList(),
    val unitMode: ShoppingUnitMode = ShoppingUnitMode.METRIC
)

data class ShoppingListItemUi(
    val foodId: Long,
    val name: String,
    val amountText: String?,
    val firstNeededDateText: String?,
    val estimatedCostText: String?,
    val isChecked: Boolean
)

data class ShoppingTotalRowUi(
    val foodId: Long,
    val foodName: String,
    val earliestNextPlannedDateText: String?,
    val gramsText: String?,
    val mlText: String?,
    val unconvertedServingsText: String?,
    val avgPricePer100gText: String?,
    val avgPricePer100mlText: String?,
    val estimatedCostText: String?
)

data class ShoppingNeedsRowUi(
    val dateText: String,
    val gramsText: String,
    val servingsText: String
)

data class ShoppingNeedsGroupUi(
    val foodId: Long,
    val foodName: String,
    val earliestDateText: String?,
    val rows: List<ShoppingNeedsRowUi>
)

data class ShoppingRecipeIngredientRowUi(
    val foodId: Long,
    val foodName: String,
    val amountText: String,
    val duplicateIconRes: Int?,
    val avgPriceText: String?,
    val estimatedCostText: String?
)

data class ShoppingRecipeTotalGroupUi(
    val recipeFoodId: Long,
    val recipeName: String,
    val nextDateText: String?,
    val servingsText: String,
    val batchesText: String,
    val ingredients: List<ShoppingRecipeIngredientRowUi>
)

data class ShoppingRecipeOccurrenceGroupUi(
    val recipeFoodId: Long,
    val recipeName: String,
    val dateText: String,
    val servingsText: String,
    val batchesText: String,
    val ingredients: List<ShoppingRecipeIngredientRowUi>
)

private data class ShoppingListAggregate(
    val foodId: Long,
    val foodName: String,
    val earliestDate: LocalDate?,
    val gramsTotal: Double,
    val mlTotal: Double,
    val servingsTotal: Double
) {
    fun toAmountText(unitMode: ShoppingUnitMode): String? {
        return when {
            gramsTotal > 0.0 -> formatShoppingGrams(gramsTotal, unitMode)
            mlTotal > 0.0 -> "${fmtShoppingDouble(mlTotal)} mL"
            else -> null
        }
    }
}

private fun PlannedFoodTotalNeed.toUi(
    avgPricePer100g: Double?,
    avgPricePer100ml: Double?
): ShoppingTotalRowUi {
    return ShoppingTotalRowUi(
        foodId = foodId,
        foodName = foodName,
        earliestNextPlannedDateText = formatShoppingDate(earliestNextPlannedDate),
        gramsText = gramsTotal?.let { "g: ${fmtShoppingDouble(it)}" },
        mlText = mlTotal?.let { "mL: ${fmtShoppingDouble(it)}" },
        unconvertedServingsText = unconvertedServingsTotal?.let {
            "servings: ${fmtShoppingDouble(it)} (unconverted)"
        },
        avgPricePer100gText = avgPricePer100g?.let { "avg: ${formatCurrency(it)} /100g" },
        avgPricePer100mlText = avgPricePer100ml?.let { "avg: ${formatCurrency(it)} /100mL" },
        estimatedCostText = computeEstimatedCostText(
            gramsTotal = gramsTotal,
            mlTotal = mlTotal,
            avgPricePer100g = avgPricePer100g,
            avgPricePer100ml = avgPricePer100ml
        )
    )
}

private fun List<PlannedFoodNeed>.toGroupedUi(): List<ShoppingNeedsGroupUi> {
    val grouped = this.groupBy { it.foodId }

    val groups = grouped.map { (foodId, rows) ->
        val foodName = rows.firstOrNull()?.foodName ?: "Food #$foodId"
        val sortedRows = rows.sortedBy { it.date }

        ShoppingNeedsGroupUi(
            foodId = foodId,
            foodName = foodName,
            earliestDateText = formatShoppingDate(sortedRows.firstOrNull()?.date) ?: "",
            rows = sortedRows.map { r ->
                ShoppingNeedsRowUi(
                    dateText = formatShoppingDate(r.date) ?: "",
                    gramsText = r.grams?.let(::fmtShoppingDouble) ?: "-",
                    servingsText = r.servings?.let(::fmtShoppingDouble) ?: "-"
                )
            }
        )
    }

    return groups.sortedWith(
        compareBy<ShoppingNeedsGroupUi> { it.earliestDateText ?: "9999-12-31" }
            .thenBy { it.foodName.lowercase() }
            .thenBy { it.foodId }
    )
}

private suspend fun RecipeTotalRequirement.toTotalledUi(
    nextDateText: String?,
    foodRepo: FoodRepository
): ShoppingRecipeTotalGroupUi {
    return ShoppingRecipeTotalGroupUi(
        recipeFoodId = recipeFoodId,
        recipeName = recipeName,
        nextDateText = nextDateText,
        servingsText = "servings: ${fmtShoppingDouble(totalRequiredServings)}",
        batchesText = "batches: ${fmtShoppingDouble(batchesRequired)}",
        ingredients = ingredients
            .sortedWith(
                compareBy<RecipeIngredientRequirement> { it.foodName.lowercase() }
                    .thenBy { it.foodId }
            )
            .map { it.toUi(foodRepo) }
    )
}

private suspend fun RecipeOccurrenceRequirement.toOccurrenceUi(
    foodRepo: FoodRepository
): ShoppingRecipeOccurrenceGroupUi {
    return ShoppingRecipeOccurrenceGroupUi(
        recipeFoodId = recipeFoodId,
        recipeName = recipeName,
        dateText = formatShoppingDateFromIso(dateIso),
        servingsText = "servings: ${fmtShoppingDouble(requiredServings)}",
        batchesText = "batches: ${fmtShoppingDouble(batchesRequired)}",
        ingredients = ingredients
            .sortedWith(
                compareBy<RecipeIngredientRequirement> { it.foodName.lowercase() }
                    .thenBy { it.foodId }
            )
            .map { it.toUi(foodRepo) }
    )
}

private suspend fun RecipeIngredientRequirement.toUi(
    foodRepo: FoodRepository
): ShoppingRecipeIngredientRowUi {
    val avgPricePer100g = foodRepo.getAveragePricePer100gForFood(foodId)
    val avgPricePer100ml = foodRepo.getAveragePricePer100mlForFood(foodId)

    val amountText = when (source) {
        ObservePlannedRecipeShoppingRequirementsUseCase.IngredientQuantitySource.SERVINGS ->
            "${fmtShoppingDouble(amountRequired)} servings"

        ObservePlannedRecipeShoppingRequirementsUseCase.IngredientQuantitySource.GRAMS ->
            "${fmtShoppingDouble(amountRequired)} g"
    }

    val avgPriceText = when {
        avgPricePer100g != null ->
            "avg: ${formatCurrency(avgPricePer100g)} /100g"

        avgPricePer100ml != null ->
            "avg: ${formatCurrency(avgPricePer100ml)} /100mL"

        else -> null
    }

    val estimatedCostText = when {
        source == ObservePlannedRecipeShoppingRequirementsUseCase.IngredientQuantitySource.GRAMS &&
                avgPricePer100g != null -> {
            "est: ${formatCurrency((amountRequired / 100.0) * avgPricePer100g)}"
        }

        else -> null
    }

    return ShoppingRecipeIngredientRowUi(
        foodId = foodId,
        foodName = foodName,
        amountText = amountText,
        duplicateIconRes = if (isDuplicateAcrossRecipes) R.drawable.layers else null,
        avgPriceText = avgPriceText,
        estimatedCostText = estimatedCostText
    )
}

private fun computeEstimatedCostText(
    gramsTotal: Double?,
    mlTotal: Double?,
    avgPricePer100g: Double?,
    avgPricePer100ml: Double?
): String? {
    val estimated = when {
        gramsTotal != null && avgPricePer100g != null ->
            (gramsTotal / 100.0) * avgPricePer100g

        mlTotal != null && avgPricePer100ml != null ->
            (mlTotal / 100.0) * avgPricePer100ml

        else -> null
    }

    return estimated?.let { "est: ${formatCurrency(it)}" }
}

private fun formatShoppingGrams(
    grams: Double,
    unitMode: ShoppingUnitMode
): String {
    return when (unitMode) {
        ShoppingUnitMode.METRIC -> "${fmtShoppingDouble(grams)} g"
        ShoppingUnitMode.GROCERY -> "${fmtShoppingPounds(grams / GRAMS_PER_POUND)} lb"
    }
}

private fun fmtShoppingPounds(pounds: Double): String {
    return when {
        pounds >= 10.0 -> pounds.roundToInt().toString()
        pounds >= 1.0 -> "%.1f".format(pounds)
        else -> "%.2f".format(pounds)
    }
}

private fun fmtShoppingDouble(v: Double): String {
    return if (v == v.roundToInt().toDouble()) {
        v.roundToInt().toString()
    } else {
        "%.2f".format(v)
    }
}

private fun formatCurrency(v: Double): String {
    return "$" + "%.2f".format(v)
}

private fun formatShoppingDate(date: LocalDate?): String? {
    if (date == null) return null
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd, EEEE", Locale.getDefault())
    val base = date.format(formatter)
    return if (date == LocalDate.now()) "$base • Today" else base
}

private fun formatSimpleShoppingDate(date: LocalDate?): String? {
    if (date == null) return null
    if (date == LocalDate.now()) return "Today"
    return date.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))
}

private fun formatShoppingDateFromIso(dateIso: String): String {
    val parsedDate = runCatching { LocalDate.parse(dateIso) }.getOrNull()
    return formatShoppingDate(parsedDate) ?: dateIso
}

private fun minDate(a: LocalDate?, b: LocalDate?): LocalDate? {
    return when {
        a == null -> b
        b == null -> a
        a <= b -> a
        else -> b
    }
}

private const val GRAMS_PER_POUND = 453.59237