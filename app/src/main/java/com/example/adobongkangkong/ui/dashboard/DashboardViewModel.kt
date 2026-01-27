package com.example.adobongkangkong.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.domain.export.ExportFoodsAndRecipesUseCase
import com.example.adobongkangkong.domain.export.ImportFoodsAndRecipesUseCase
import com.example.adobongkangkong.domain.logging.CreateLogEntryUseCase
import com.example.adobongkangkong.domain.logging.model.AmountInput
import com.example.adobongkangkong.domain.nutrition.SyncNutrientCatalogUseCase
import com.example.adobongkangkong.domain.usecase.DeleteLogEntryUseCase
import com.example.adobongkangkong.domain.usecase.ObserveTodayLogItemsUseCase
import com.example.adobongkangkong.domain.usecase.ObserveTodayMacrosUseCase
import com.example.adobongkangkong.ui.common.bottomsheet.BlockingSheetModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
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
    private val importFoodsAndRecipes: ImportFoodsAndRecipesUseCase
) : ViewModel() {

    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar = _snackbar.asStateFlow()

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

    /**
     * Called when user confirms logging by servings.
     *
     * If the food is blocked (missing grams-per-serving for volume units),
     * shows a blocking sheet that routes the user to edit the food.
     */
    fun logFoodByServings(foodId: Long, servings: Double) {
        viewModelScope.launch {
            val result = createLogEntry.execute(
                foodId = foodId,
                timestamp = Instant.now(),
                amountInput = AmountInput.ByServings(servings)
            )

            when (result) {
                is CreateLogEntryUseCase.Result.Success -> Unit
                is CreateLogEntryUseCase.Result.Blocked ->
                    showMissingGramsSheet(foodId = foodId, message = result.message)
                is CreateLogEntryUseCase.Result.Error -> {
                    _snackbar.value = "Log failed: ${result.message}"
                }
            }
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
}

private data class DashboardOverlay(
    val blockingSheet: BlockingSheetModel? = null,
    val blockedFoodId: Long? = null,
    val navigateToEditFoodId: Long? = null
)
