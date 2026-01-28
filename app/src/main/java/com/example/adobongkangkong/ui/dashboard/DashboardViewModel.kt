package com.example.adobongkangkong.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.domain.export.ExportFoodsAndRecipesUseCase
import com.example.adobongkangkong.domain.export.ImportFoodsAndRecipesUseCase
import com.example.adobongkangkong.domain.logging.CreateLogEntryUseCase
import com.example.adobongkangkong.domain.logging.model.AmountInput
import com.example.adobongkangkong.domain.model.DailyNutritionSummary
import com.example.adobongkangkong.domain.model.DailyNutritionTotals
import com.example.adobongkangkong.domain.nutrition.SyncNutrientCatalogUseCase
import com.example.adobongkangkong.domain.usecase.BootstrapDomainUseCase
import com.example.adobongkangkong.domain.usecase.DeleteLogEntryUseCase
import com.example.adobongkangkong.domain.usecase.ObserveDailyNutritionSummaryUseCase
import com.example.adobongkangkong.domain.usecase.ObserveTodayLogItemsUseCase
import com.example.adobongkangkong.domain.usecase.ObserveTodayMacrosUseCase
import com.example.adobongkangkong.ui.common.bottomsheet.BlockingSheetModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Dashboard screen state holder.
 *
 * Responsibilities:
 * - Exposes today's macro totals and logged items as a single [DashboardState] stream.
 * - Handles user intents for logging/deleting entries.
 * - Runs dev-only actions (nutrient sync) and import/export flows, reporting results via snackbar text.
 *
 * Domain rules honored here:
 * - Import is lax (warnings returned), but correctness is enforced at point-of-use.
 * - Logging by servings can be blocked when a food requires grams-per-serving (volume-based entry).
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    observeTodayMacrosUseCase: ObserveTodayMacrosUseCase,
    observeTodayLogItemsUseCase: ObserveTodayLogItemsUseCase,
    private val deleteLogEntry: DeleteLogEntryUseCase,
    private val createLogEntry: CreateLogEntryUseCase,
    private val syncNutrientCatalog: SyncNutrientCatalogUseCase,
    private val exportFoodsAndRecipes: ExportFoodsAndRecipesUseCase,
    private val importFoodsAndRecipes: ImportFoodsAndRecipesUseCase,
    private val bootstrapDomain: BootstrapDomainUseCase,
    private val observeDailyNutritionSummary: ObserveDailyNutritionSummaryUseCase,
) : ViewModel() {

    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar = _snackbar.asStateFlow()

    private val selectedDateFlow =
        MutableStateFlow(LocalDate.now())


    private val _overlay = MutableStateFlow(DashboardOverlay())

    val state: StateFlow<DashboardState> =
        combine(
            observeTodayMacrosUseCase(),
            observeTodayLogItemsUseCase(),
            _overlay
        ) { totals, items, overlay ->
            DashboardState(
                totals = totals,
                todayItems = items,
                blockingSheet = overlay.blockingSheet,
                blockedFoodId = overlay.blockedFoodId,
                navigateToEditFoodId = overlay.navigateToEditFoodId
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            DashboardState()
        )

    fun snackbarShown() {
        _snackbar.value = null
    }

    fun delete(logId: Long) {
        viewModelScope.launch { deleteLogEntry(logId) }
    }

    fun dismissBlockingSheet() {
        _overlay.update { it.copy(blockingSheet = null, blockedFoodId = null) }
    }

    fun onEditFoodNavigationHandled() {
        _overlay.update { it.copy(navigateToEditFoodId = null) }
    }

    fun showPreviousDay() {
        selectedDateFlow.value =
            selectedDateFlow.value.minusDays(1)
    }

    fun showNextDay() {
        val next = selectedDateFlow.value.plusDays(1)
        if (next <= LocalDate.now()) {
            selectedDateFlow.value = next
        }
    }

    fun resetToToday() {
        selectedDateFlow.value = LocalDate.now()
    }


    /**
     * Dev-only: sync nutrient catalog + aliases (triggered via long press).
     */
    fun devSyncNutrients() {
        viewModelScope.launch {
            val r = syncNutrientCatalog()
            _snackbar.value =
                "Synced nutrients: +${r.inserted} inserted, ${r.updated} updated, ${r.aliasesUpserted} aliases"
        }
    }

    private fun showMissingGramsSheet(foodId: Long, message: String) {
        _overlay.update {
            it.copy(
                blockedFoodId = foodId,
                blockingSheet = BlockingSheetModel(
                    title = "Needs grams-per-serving",
                    message = message,
                    primaryButtonText = "Edit food",
                    secondaryButtonText = "Dismiss",
                    onPrimary = {
                        _overlay.update { o ->
                            o.copy(
                                navigateToEditFoodId = foodId,
                                blockingSheet = null
                            )
                        }
                    },
                    onSecondary = { dismissBlockingSheet() }
                )
            )
        }
    }

    fun importFrom(input: InputStream) {
        viewModelScope.launch {
            try {
                val r = importFoodsAndRecipes(input)
                val warnSuffix = if (r.warnings.isNotEmpty()) " (${r.warnings.size} warnings)" else ""
                _snackbar.value =
                    "Imported: ${r.foodsInserted} foods (+${r.foodsUpdated} updated), " +
                            "${r.recipesInserted} recipes (+${r.recipesUpdated} updated)$warnSuffix"
            } catch (e: Exception) {
                _snackbar.value = "Import failed: ${e.message ?: "unknown error"}"
            }
        }
    }

    fun exportTo(out: OutputStream) {
        viewModelScope.launch {
            try {
                val r = exportFoodsAndRecipes(out)
                _snackbar.value = "Exported ${r.foodsExported} foods, ${r.recipesExported} recipes"
            } catch (e: Exception) {
                _snackbar.value = "Export failed: ${e.message ?: "unknown error"}"
            }
        }
    }

    init {
        viewModelScope.launch {
            bootstrapDomain()
        }
        viewModelScope.launch {
            val dailySummary: StateFlow<DailyNutritionSummary> =
                selectedDateFlow
                    .flatMapLatest { date ->
                        observeDailyNutritionSummary(
                            date,
                            ZoneId.systemDefault()
                        )
                    }
                    .stateIn(
                        viewModelScope,
                        SharingStarted.WhileSubscribed(5_000),
                        DailyNutritionSummary(
                            totals = DailyNutritionTotals(
                                date = selectedDateFlow.value,
                                totalsByCode = emptyMap()
                            ),
                            statuses = emptyList()
                        )
                    )
        }

    }



}

private data class DashboardOverlay(
    val blockingSheet: BlockingSheetModel? = null,
    val blockedFoodId: Long? = null,
    val navigateToEditFoodId: Long? = null
)
