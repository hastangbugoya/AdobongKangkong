package com.example.adobongkangkong.ui.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.R
import com.example.adobongkangkong.data.local.db.dao.FoodGoalFlagsDao
import com.example.adobongkangkong.data.local.db.entity.FoodGoalFlagsEntity
import com.example.adobongkangkong.domain.planner.usecase.ObservePlannedFoodNeedsUseCase
import com.example.adobongkangkong.domain.planner.usecase.ObservePlannedFoodTotalsUseCase
import com.example.adobongkangkong.domain.planner.usecase.ObservePlannedRecipeShoppingRequirementsUseCase
import com.example.adobongkangkong.domain.planner.usecase.PlannedFoodNeed
import com.example.adobongkangkong.domain.planner.usecase.PlannedFoodTotalNeed
import com.example.adobongkangkong.domain.planner.usecase.ObservePlannedRecipeShoppingRequirementsUseCase.RecipeIngredientRequirement
import com.example.adobongkangkong.domain.planner.usecase.ObservePlannedRecipeShoppingRequirementsUseCase.RecipeOccurrenceRequirement
import com.example.adobongkangkong.domain.planner.usecase.ObservePlannedRecipeShoppingRequirementsUseCase.RecipeTotalRequirement
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class ShoppingViewModel @Inject constructor(
    private val observeTotals: ObservePlannedFoodTotalsUseCase,
    private val observeNeeds: ObservePlannedFoodNeedsUseCase,
    private val observeRecipeShopping: ObservePlannedRecipeShoppingRequirementsUseCase,
    private val foodGoalFlagsDao: FoodGoalFlagsDao
) : ViewModel() {

    private val startDateFlow = MutableStateFlow(LocalDate.now())
    private val daysFlow = MutableStateFlow(7)

    private val daysTextFlow = MutableStateFlow("7")

    fun setStartDate(d: LocalDate) { startDateFlow.value = d }

    fun onDaysTextChanged(v: String) {
        daysTextFlow.value = v
        val parsed = v.toIntOrNull()
        if (parsed != null && parsed > 0) {
            daysFlow.value = parsed.coerceIn(1, 365)
        }
    }

    private val flagsByFoodIdFlow: Flow<Map<Long, FoodGoalFlagsEntity>> =
        foodGoalFlagsDao
            .observeAll()
            .map { list -> list.associateBy { it.foodId } }
            .distinctUntilChanged()

    private val recipeFoodIdsFlow: Flow<Set<Long>> =
        combine(startDateFlow, daysFlow) { start, days -> start to days }
            .flatMapLatest { (start, days) -> observeRecipeShopping(start, days) }
            .map { result -> result.totalled.map { it.recipeFoodId }.toSet() }
    private val totalsUiFlow: Flow<List<ShoppingTotalRowUi>> =
        combine(
            combine(startDateFlow, daysFlow) { start, days -> start to days }
                .flatMapLatest { (start, days) -> observeTotals(startDate = start, days = days) },
            recipeFoodIdsFlow
        ) { totals, recipeIds ->

            totals
                .filterNot { it.foodId in recipeIds } // 🔥 KEY FIX
                .sortedWith(
                    compareBy<PlannedFoodTotalNeed> { it.earliestNextPlannedDate ?: LocalDate.MAX }
                        .thenBy { it.foodName.lowercase() }
                        .thenBy { it.foodId }
                )
                .map { it.toUi() }
        }

    private val needsUiFlow: Flow<List<ShoppingNeedsGroupUi>> =
        combine(
            combine(startDateFlow, daysFlow) { start, days -> start to days }
                .flatMapLatest { (start, days) -> observeNeeds(startDate = start, days = days) },
            recipeFoodIdsFlow
        ) { list, recipeIds ->
            list
                .filterNot { it.foodId in recipeIds } // 🔥 SAME RULE
                .toGroupedUi()
        }

    private val recipeTotalledUiFlow: Flow<List<ShoppingRecipeTotalGroupUi>> =
        combine(startDateFlow, daysFlow) { start, days -> start to days }
            .flatMapLatest { (start, days) -> observeRecipeShopping(startDate = start, days = days) }
            .mapLatest { result ->
                result.totalled
                    .sortedWith(
                        compareBy<RecipeTotalRequirement> { it.recipeName.lowercase() }
                            .thenBy { it.recipeFoodId }
                    )
                    .map { it.toTotalledUi() }
            }

    private val recipeNotTotalledUiFlow: Flow<List<ShoppingRecipeOccurrenceGroupUi>> =
        combine(startDateFlow, daysFlow) { start, days -> start to days }
            .flatMapLatest { (start, days) -> observeRecipeShopping(startDate = start, days = days) }
            .mapLatest { result ->
                result.notTotalled
                    .sortedWith(
                        compareBy<RecipeOccurrenceRequirement> { it.dateIso }
                            .thenBy { it.recipeName.lowercase() }
                            .thenBy { it.recipeFoodId }
                    )
                    .map { it.toOccurrenceUi() }
            }


    val state: StateFlow<ShoppingState> =
        combine(
            daysTextFlow,
            totalsUiFlow,
            needsUiFlow,
            flagsByFoodIdFlow
        ) { daysText, totals, groups, flags ->
            BaseShoppingState(
                daysText = daysText,
                totalledRows = totals,
                notTotalledGroups = groups,
                flagsByFoodId = flags
            )
        }
            .combine(recipeTotalledUiFlow) { base, recipeTotals ->
                base.copy(
                    recipeTotalledGroups = recipeTotals
                )
            }
            .combine(recipeNotTotalledUiFlow) { base, recipeOccurrences ->
                base.copy(
                    recipeNotTotalledGroups = recipeOccurrences
                )
            }
            .map { base ->
                ShoppingState(
                    daysText = base.daysText,
                    totalledRows = base.totalledRows,
                    notTotalledGroups = base.notTotalledGroups,
                    recipeTotalledGroups = base.recipeTotalledGroups,
                    recipeNotTotalledGroups = base.recipeNotTotalledGroups,
                    flagsByFoodId = base.flagsByFoodId
                )
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShoppingState())
}

private data class BaseShoppingState(
    val daysText: String,
    val totalledRows: List<ShoppingTotalRowUi>,
    val notTotalledGroups: List<ShoppingNeedsGroupUi>,
    val recipeTotalledGroups: List<ShoppingRecipeTotalGroupUi> = emptyList(),
    val recipeNotTotalledGroups: List<ShoppingRecipeOccurrenceGroupUi> = emptyList(),
    val flagsByFoodId: Map<Long, FoodGoalFlagsEntity>
)

data class ShoppingState(
    val daysText: String = "7",
    val totalledRows: List<ShoppingTotalRowUi> = emptyList(),
    val notTotalledGroups: List<ShoppingNeedsGroupUi> = emptyList(),
    val recipeTotalledGroups: List<ShoppingRecipeTotalGroupUi> = emptyList(),
    val recipeNotTotalledGroups: List<ShoppingRecipeOccurrenceGroupUi> = emptyList(),
    val flagsByFoodId: Map<Long, FoodGoalFlagsEntity> = emptyMap()
)

data class ShoppingTotalRowUi(
    val foodId: Long,
    val foodName: String,
    val earliestNextPlannedDateText: String?,
    val gramsText: String?,
    val mlText: String?,
    val unconvertedServingsText: String?
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
    val duplicateIconRes: Int?
)

data class ShoppingRecipeTotalGroupUi(
    val recipeFoodId: Long,
    val recipeName: String,
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

private fun PlannedFoodTotalNeed.toUi(): ShoppingTotalRowUi {
    fun fmtDouble(v: Double): String =
        if (v == v.roundToInt().toDouble()) v.roundToInt().toString() else "%.2f".format(v)

    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd, EEEE", Locale.getDefault())

    return ShoppingTotalRowUi(
        foodId = foodId,
        foodName = foodName,
        earliestNextPlannedDateText = earliestNextPlannedDate?.format(formatter),
        gramsText = gramsTotal?.let { "g: ${fmtDouble(it)}" },
        mlText = mlTotal?.let { "mL: ${fmtDouble(it)}" },
        unconvertedServingsText = unconvertedServingsTotal?.let { "servings: ${fmtDouble(it)} (unconverted)" }
    )
}

/**
 * Prompt rules:
 * - Group by foodId
 * - Groups ordered by earliest date ASC
 * - Within group: rows ordered by date ASC
 */
private fun List<PlannedFoodNeed>.toGroupedUi(): List<ShoppingNeedsGroupUi> {
    val grouped = this.groupBy { it.foodId }
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd, EEEE", Locale.getDefault())

    val groups = grouped.map { (foodId, rows) ->
        val foodName = rows.firstOrNull()?.foodName ?: "Food #$foodId"
        val sortedRows = rows.sortedBy { it.date }

        ShoppingNeedsGroupUi(
            foodId = foodId,
            foodName = foodName,
            earliestDateText = sortedRows.firstOrNull()?.date?.let {
                it.format(formatter)
            } ?: "",
            rows = sortedRows.map { r ->
                ShoppingNeedsRowUi(
                    dateText = r.date.toString(),
                    gramsText = r.grams?.toString() ?: "-",
                    servingsText = r.servings?.toString() ?: "-"
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

private fun RecipeTotalRequirement.toTotalledUi(): ShoppingRecipeTotalGroupUi {
    return ShoppingRecipeTotalGroupUi(
        recipeFoodId = recipeFoodId,
        recipeName = recipeName,
        servingsText = "servings: ${fmtShoppingDouble(totalRequiredServings)}",
        batchesText = "batches: ${fmtShoppingDouble(batchesRequired)}",
        ingredients = ingredients
            .sortedWith(
                compareBy<RecipeIngredientRequirement> { it.foodName.lowercase() }
                    .thenBy { it.foodId }
            )
            .map { it.toUi() }
    )
}

private fun RecipeOccurrenceRequirement.toOccurrenceUi(): ShoppingRecipeOccurrenceGroupUi {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd, EEEE", Locale.getDefault())
    val parsedDate = runCatching { LocalDate.parse(dateIso) }.getOrNull()

    return ShoppingRecipeOccurrenceGroupUi(
        recipeFoodId = recipeFoodId,
        recipeName = recipeName,
        dateText = parsedDate?.format(formatter) ?: dateIso,
        servingsText = "servings: ${fmtShoppingDouble(requiredServings)}",
        batchesText = "batches: ${fmtShoppingDouble(batchesRequired)}",
        ingredients = ingredients
            .sortedWith(
                compareBy<RecipeIngredientRequirement> { it.foodName.lowercase() }
                    .thenBy { it.foodId }
            )
            .map { it.toUi() }
    )
}

private fun RecipeIngredientRequirement.toUi(): ShoppingRecipeIngredientRowUi {
    return ShoppingRecipeIngredientRowUi(
        foodId = foodId,
        foodName = foodName,
        amountText = "${fmtShoppingDouble(amountRequired)} ${unit.display}",
        duplicateIconRes = if (isDuplicateAcrossRecipes) R.drawable.layers else null
    )
}

private fun fmtShoppingDouble(v: Double): String {
    return if (v == v.roundToInt().toDouble()) {
        v.roundToInt().toString()
    } else {
        "%.2f".format(v)
    }
}