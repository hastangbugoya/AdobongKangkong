package com.example.adobongkangkong.ui.dashboard

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.data.local.db.dao.FoodNutrientDao
import com.example.adobongkangkong.data.local.db.dao.NutrientDao
import com.example.adobongkangkong.data.repository.FoodNutritionSnapshotRepositoryImpl
import com.example.adobongkangkong.domain.export.ExportFoodsAndRecipesUseCase
import com.example.adobongkangkong.domain.export.ImportFoodsAndRecipesUseCase
import com.example.adobongkangkong.domain.logging.CreateLogEntryUseCase
import com.example.adobongkangkong.domain.logging.model.AmountInput
import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.nutrition.SyncNutrientCatalogUseCase
import com.example.adobongkangkong.domain.repository.FoodNutritionSnapshotRepository
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

@HiltViewModel
class DashboardViewModel @Inject constructor(
    observeTodayMacrosUseCase: ObserveTodayMacrosUseCase,
    observeTodayLogItemsUseCase: ObserveTodayLogItemsUseCase,
    private val deleteLogEntry: DeleteLogEntryUseCase,
    private val createLogEntry: CreateLogEntryUseCase,
    private val syncNutrientCatalog: SyncNutrientCatalogUseCase,
    private val exportFoodsAndRecipes: ExportFoodsAndRecipesUseCase,
    private val importFoodsAndRecipes: ImportFoodsAndRecipesUseCase,
    private val snapshotRepo: FoodNutritionSnapshotRepository,
    private val foodNutrientDao: FoodNutrientDao,
    private val nutrientDao: NutrientDao
) : ViewModel() {
    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar = _snackbar.asStateFlow()

    init {
        viewModelScope.launch {
            val chicken = snapshotRepo.getSnapshot(1001L)
            val yogurt = snapshotRepo.getSnapshot(1005L)

            val chickenCalPerG = chicken?.nutrientsPerGram?.get(NutrientKey("CALORIES"))
            val yogurtProteinPerG = yogurt?.nutrientsPerGram?.get(NutrientKey("PROTEIN_G"))

            Log.d("NUTRI_DEBUG", "Chicken CAL per gram = $chickenCalPerG")
            Log.d("NUTRI_DEBUG", "Yogurt PRO per gram = $yogurtProteinPerG")

            val chicken2 = snapshotRepo.getSnapshot(1001L)
            Log.d("NUTRI_DEBUG", "chicken exists=${chicken2 != null} gramsPerServing=${chicken2?.gramsPerServing}")

            val keys = chicken2?.nutrientsPerGram?.keys()?.take(20)
            Log.d("NUTRI_DEBUG", "chicken keys (first 20)=$keys")

            val caloriesKey = NutrientKey("CALORIES")
            Log.d("NUTRI_DEBUG", "CALORIES per g = ${chicken2?.nutrientsPerGram?.get(caloriesKey)}")

            val codes =
                (snapshotRepo as? FoodNutritionSnapshotRepositoryImpl)
                    ?.debugListNutrientCodes()

            Log.d("NUTRI_DEBUG", "nutrient codes = $codes")

            val chickenRows = snapshotRepo.getSnapshot(1001L)
            Log.d("NUTRI_DEBUG", "chicken full keys = ${chickenRows?.nutrientsPerGram?.keys()}")

            val rows = foodNutrientDao.debugRowsForFood(1001L)
            Log.d("NUTRI_DEBUG", "Food 1001 DB nutrients = ${rows.joinToString { "${it.code}=${it.amount}(${it.basisType})" }}")

            val caloriesId = nutrientDao.getIdByCode("CALORIES")
            Log.d("NUTRI_DEBUG", "CALORIES nutrientId=$caloriesId")

            val yogurt2 = snapshotRepo.getSnapshot(1005L)
            Log.d("NUTRI_DEBUG", "yogurt keys=${yogurt2?.nutrientsPerGram?.keys()}")
            Log.d("NUTRI_DEBUG", "Yogurt PRO per gram = ${yogurt2?.nutrientsPerGram?.get(NutrientKey("PROTEIN"))}")
        }
    }


    fun devSyncNutrients() {
        viewModelScope.launch {
            val r = syncNutrientCatalog()
            _snackbar.value = "Synced nutrients: +${r.inserted} inserted, ${r.updated} updated, ${r.aliasesUpserted} aliases"
        }
    }

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
     * Called by your Quick Add / Log UI when user confirms logging by servings.
     * If the food is blocked (missing grams-per-serving for volume units),
     * we show a blocking sheet instead of creating a log entry.
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
                    // TODO: snackbar/toast if you have an app-level message system
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
