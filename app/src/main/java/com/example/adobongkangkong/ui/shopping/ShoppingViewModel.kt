package com.example.adobongkangkong.ui.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.data.local.db.dao.FoodGoalFlagsDao
import com.example.adobongkangkong.data.local.db.entity.FoodGoalFlagsEntity
import com.example.adobongkangkong.domain.planner.usecase.ObservePlannedFoodTotalsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class ShoppingViewModel @Inject constructor(
    private val observeTotals: ObservePlannedFoodTotalsUseCase,
    private val foodGoalFlagsDao: FoodGoalFlagsDao
) : ViewModel() {

    private val startDateFlow = MutableStateFlow(LocalDate.now())
    private val daysFlow = MutableStateFlow(7)

    fun setRange(startDate: LocalDate, days: Int) {
        if (days <= 0) return
        startDateFlow.value = startDate
        daysFlow.value = days
    }

    private val flagsByFoodIdFlow: Flow<Map<Long, FoodGoalFlagsEntity>> =
        foodGoalFlagsDao
            .observeAll()
            .map { list -> list.associateBy { it.foodId } }
            .distinctUntilChanged()

    val state: StateFlow<ShoppingState> =
        combine(startDateFlow, daysFlow) { start, days -> start to days }
            .flatMapLatest { (start, days) ->
                observeTotals(startDate = start, days = days)
                    .combine(flagsByFoodIdFlow) { totals, flagsById ->
                        val rows = totals.map { t ->
                            ShoppingRowUiModel(
                                foodId = t.foodId,
                                name = t.foodName,
                                brandText = " ", // we’ll fill brand later when you decide to join Food table here
                                amountText = formatAmount(t.gramsTotal, t.mlTotal, t.unconvertedServingsTotal),
                                nextDateText = t.earliestNextPlannedDate?.toString() ?: "",
                                goalFlags = flagsById[t.foodId]
                            )
                        }

                        ShoppingState(
                            startDate = start,
                            days = days,
                            rows = rows
                        )
                    }
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShoppingState())

    private fun formatAmount(
        grams: Double?,
        ml: Double?,
        servings: Double?
    ): String {
        val parts = ArrayList<String>(3)
        grams?.let { parts += "${it.roundSmart()} g" }
        ml?.let { parts += "${it.roundSmart()} mL" }
        servings?.let { parts += "${it.roundSmart()} serv" }
        return if (parts.isEmpty()) "—" else parts.joinToString(" + ")
    }

    private fun Double.roundSmart(): String {
        val r1 = (this * 10.0).roundToInt() / 10.0
        return if (r1 == r1.toInt().toDouble()) r1.toInt().toString() else "%.1f".format(r1)
    }
}

data class ShoppingState(
    val startDate: LocalDate = LocalDate.now(),
    val days: Int = 7,
    val rows: List<ShoppingRowUiModel> = emptyList()
)

data class ShoppingRowUiModel(
    val foodId: Long,
    val name: String,
    val brandText: String,
    val amountText: String,
    val nextDateText: String,
    val goalFlags: FoodGoalFlagsEntity?
)