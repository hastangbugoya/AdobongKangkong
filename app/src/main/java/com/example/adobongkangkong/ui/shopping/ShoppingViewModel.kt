package com.example.adobongkangkong.ui.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.data.local.db.dao.FoodGoalFlagsDao
import com.example.adobongkangkong.data.local.db.entity.FoodGoalFlagsEntity
import com.example.adobongkangkong.domain.planner.usecase.ObservePlannedFoodNeedsUseCase
import com.example.adobongkangkong.domain.planner.usecase.ObservePlannedFoodTotalsUseCase
import com.example.adobongkangkong.domain.planner.usecase.PlannedFoodNeed
import com.example.adobongkangkong.domain.planner.usecase.PlannedFoodTotalNeed
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

    private val totalsUiFlow: Flow<List<ShoppingTotalRowUi>> =
        combine(startDateFlow, daysFlow) { start, days -> start to days }
            .flatMapLatest { (start, days) -> observeTotals(startDate = start, days = days) }
            .mapLatest { list ->
                // ✅ Prompt rule: earliestNextPlannedDate ASC, then foodName ASC, then foodId ASC
                list.sortedWith(
                    compareBy<PlannedFoodTotalNeed> { it.earliestNextPlannedDate ?: LocalDate.MAX }
                        .thenBy { it.foodName.lowercase() }
                        .thenBy { it.foodId }
                ).map { it.toUi() }
            }

    private val needsUiFlow: Flow<List<ShoppingNeedsGroupUi>> =
        combine(startDateFlow, daysFlow) { start, days -> start to days }
            .flatMapLatest { (start, days) -> observeNeeds(startDate = start, days = days) }
            .mapLatest { list -> list.toGroupedUi() }

    val state: StateFlow<ShoppingState> =
        combine(daysTextFlow, totalsUiFlow, needsUiFlow, flagsByFoodIdFlow) { daysText, totals, groups, flags ->
            ShoppingState(
                daysText = daysText,
                totalledRows = totals,
                notTotalledGroups = groups,
                flagsByFoodId = flags
            )
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShoppingState())
}

data class ShoppingState(
    val daysText: String = "7",
    val totalledRows: List<ShoppingTotalRowUi> = emptyList(),
    val notTotalledGroups: List<ShoppingNeedsGroupUi> = emptyList(),
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