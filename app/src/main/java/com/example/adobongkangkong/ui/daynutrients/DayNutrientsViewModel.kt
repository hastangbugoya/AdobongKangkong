package com.example.adobongkangkong.ui.daynutrients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.domain.model.Nutrient
import com.example.adobongkangkong.domain.model.NutrientCategory
import com.example.adobongkangkong.domain.repository.NutrientRepository
import com.example.adobongkangkong.domain.usecase.ObserveDailyNutritionTotalsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel for the reusable Day Nutrients screen.
 *
 * This screen is date-driven and can be opened from:
 * - Day Log
 * - Dashboard
 * - any future calendar/day detail surface
 *
 * Nutrition truth source:
 * - Uses [ObserveDailyNutritionTotalsUseCase].
 * - That use case aggregates immutable logged nutrient snapshots for a single logDateIso day.
 * - This ViewModel only resolves metadata and prepares grouped UI rows.
 *
 * Important:
 * - Do not recompute from current Food or Recipe rows here.
 * - Do not query logs directly here.
 * - Do not use timestamps to determine day membership.
 */
@HiltViewModel
class DayNutrientsViewModel @Inject constructor(
    private val observeDailyNutritionTotals: ObserveDailyNutritionTotalsUseCase,
    private val nutrientRepository: NutrientRepository,
    private val zoneId: ZoneId,
) : ViewModel() {

    private val selectedDateFlow = MutableStateFlow<LocalDate?>(null)

    private val totalsFlow =
        selectedDateFlow
            .filterNotNull()
            .flatMapLatest { date ->
                observeDailyNutritionTotals(
                    date = date,
                    zoneId = zoneId
                )
            }

    private val nutrientsByCodeFlow: Flow<Map<String, Nutrient>> =
        nutrientRepository
            .observeAllNutrients()
            .map { nutrients ->
                nutrients.associateBy { it.code }
            }

    val state =
        combine(
            selectedDateFlow.filterNotNull(),
            totalsFlow,
            nutrientsByCodeFlow
        ) { date, totals, nutrientsByCode ->
            val rows =
                totals.totalsByCode
                    .entries()
                    .mapNotNull { entry ->
                        val key = entry.key
                        val amount = entry.value

                        if (amount.isEffectivelyZero()) {
                            return@mapNotNull null
                        }

                        val nutrient = nutrientsByCode[key.value]

                        DayNutrientRowUiModel(
                            code = key.value,
                            displayName = nutrient?.displayName ?: key.value,
                            amountText = formatNutrientAmount(amount),
                            unitSymbol = nutrient?.unit?.symbol.orEmpty(),
                            amount = amount,
                            category = nutrient?.category ?: NutrientCategory.OTHER
                        )
                    }
                    .sortedWith(dayNutrientRowComparator)

            val sections =
                rows
                    .groupBy { it.category }
                    .toSortedMap(compareBy<NutrientCategory> { it.sortOrder }.thenBy { it.displayName })
                    .map { (category, sectionRows) ->
                        DayNutrientSectionUiModel(
                            title = category.displayName,
                            sortOrder = category.sortOrder,
                            rows = sectionRows
                        )
                    }

            DayNutrientsState(
                date = date,
                sections = sections,
                isEmpty = rows.isEmpty()
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DayNutrientsState()
        )

    fun load(date: LocalDate) {
        selectedDateFlow.value = date
    }

    private fun Double.isEffectivelyZero(): Boolean =
        abs(this) < ZERO_EPSILON

    private companion object {
        const val ZERO_EPSILON = 0.000_001
    }
}

data class DayNutrientsState(
    val date: LocalDate = LocalDate.now(),
    val sections: List<DayNutrientSectionUiModel> = emptyList(),
    val isEmpty: Boolean = true
)

data class DayNutrientSectionUiModel(
    val title: String,
    val sortOrder: Int,
    val rows: List<DayNutrientRowUiModel>
)

data class DayNutrientRowUiModel(
    val code: String,
    val displayName: String,
    val amountText: String,
    val unitSymbol: String,
    val amount: Double,
    val category: NutrientCategory
) {
    val amountWithUnit: String
        get() = if (unitSymbol.isBlank()) {
            amountText
        } else {
            "$amountText $unitSymbol"
        }
}

private val priorityByCode: Map<String, Int> =
    mapOf(
        "CALORIES_KCAL" to 0,
        "PROTEIN_G" to 1,
        "CARBS_G" to 2,
        "FAT_G" to 3,
        "CAFFEINE_MG" to 10,
        "SODIUM_MG" to 11,
        "SUGARS_G" to 12,
        "FIBER_G" to 13
    )

private val dayNutrientRowComparator: Comparator<DayNutrientRowUiModel> =
    compareBy<DayNutrientRowUiModel> { it.category.sortOrder }
        .thenBy { priorityByCode[it.code] ?: 1_000 }
        .thenBy { it.displayName.lowercase() }
        .thenBy { it.code }

private fun formatNutrientAmount(value: Double): String =
    when {
        abs(value) >= 100.0 -> "%,.0f".format(value)
        abs(value) >= 10.0 -> "%,.1f".format(value).trimTrailingZeros()
        else -> "%,.2f".format(value).trimTrailingZeros()
    }

private fun String.trimTrailingZeros(): String =
    trimEnd('0').trimEnd('.')