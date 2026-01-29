package com.example.adobongkangkong.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.domain.export.ExportFoodsAndRecipesUseCase
import com.example.adobongkangkong.domain.export.ImportFoodsAndRecipesUseCase
import com.example.adobongkangkong.domain.logging.CreateLogEntryUseCase
import com.example.adobongkangkong.domain.model.TargetDraft
import com.example.adobongkangkong.domain.model.TargetEdit
import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.nutrition.SyncNutrientCatalogUseCase
import com.example.adobongkangkong.domain.repository.UserPinnedNutrientRepository
import com.example.adobongkangkong.domain.trend.model.RollingNutritionAverages
import com.example.adobongkangkong.domain.trend.model.RollingNutritionStats
import com.example.adobongkangkong.domain.trend.usecase.ObserveDashboardNutrientCardsUseCase
import com.example.adobongkangkong.domain.trend.usecase.ObserveRollingNutritionStatsUseCase
import com.example.adobongkangkong.domain.trend.usecase.SetPinnedDashboardNutrientsUseCase
import com.example.adobongkangkong.domain.usecase.BootstrapDomainUseCase
import com.example.adobongkangkong.domain.usecase.DeleteLogEntryUseCase
import com.example.adobongkangkong.domain.usecase.LogFoodUseCase
import com.example.adobongkangkong.domain.usecase.ObserveTodayLogItemsUseCase
import com.example.adobongkangkong.domain.usecase.ObserveTodayMacrosUseCase
import com.example.adobongkangkong.domain.usecase.UpsertUserNutrientTargetUseCase
import com.example.adobongkangkong.ui.common.bottomsheet.BlockingSheetModel
import com.example.adobongkangkong.ui.dashboard.pinned.model.DashboardPinOption
import com.example.adobongkangkong.ui.dashboard.pinned.model.NutrientOption
import com.example.adobongkangkong.ui.dashboard.pinned.usecase.ObserveDashboardPinOptionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Dashboard screen state holder.
 *
 * Responsibilities:
 * - Exposes today's totals + log items + dashboard nutrient cards as one [DashboardState] stream.
 * - Handles dashboard actions:
 *   - Delete log entries
 *   - Log foods (servings/grams) with domain validation
 *   - Edit pinned nutrients + per-day nutrient targets (min/max/target)
 *   - Import/Export + nutrient catalog sync
 *
 * Notes / common pitfall:
 * - The Dashboard layer should NOT construct FoodRef objects or build nutrient snapshots.
 * - Logging requires domain validation + snapshotting, so Dashboard calls [LogFoodUseCase].
 * - [LogFoodUseCase] delegates to [CreateLogEntryUseCase], which:
 *   - loads the food/recipe data
 *   - enforces “grams-per-serving required when logging by servings”
 *   - snapshots nutrients into the persisted log entry
 *
 * If the user attempts to log a food that is missing grams-per-serving, the domain returns
 * [CreateLogEntryUseCase.Result.Blocked]. The ViewModel converts that into a blocking sheet with
 * an "Edit food" CTA that navigates to the food editor.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val observeTodayMacrosUseCase: ObserveTodayMacrosUseCase,
    private val observeTodayLogItemsUseCase: ObserveTodayLogItemsUseCase,
    private val deleteLogEntry: DeleteLogEntryUseCase,

    private val logFood: LogFoodUseCase,
    private val observeDashboardPinOptions: ObserveDashboardPinOptionsUseCase,


    private val syncNutrientCatalog: SyncNutrientCatalogUseCase,
    private val exportFoodsAndRecipes: ExportFoodsAndRecipesUseCase,
    private val importFoodsAndRecipes: ImportFoodsAndRecipesUseCase,

    private val bootstrapDomain: BootstrapDomainUseCase,

    private val observeRollingNutritionStatsUseCase: ObserveRollingNutritionStatsUseCase,
    private val observeDashboardNutrientCardsUseCase: ObserveDashboardNutrientCardsUseCase,

    private val userPinnedNutrientRepository: UserPinnedNutrientRepository,
    private val setPinnedDashboardNutrientsUseCase: SetPinnedDashboardNutrientsUseCase,
    private val upsertUserNutrientTargetUseCase: UpsertUserNutrientTargetUseCase
) : ViewModel() {

    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar = _snackbar.asStateFlow()

    private val selectedDateFlow = MutableStateFlow(LocalDate.now())
    private val rollingDaysFlow = MutableStateFlow(7)

    private val cardsFlow =
        combine(selectedDateFlow, rollingDaysFlow) { date, days -> date to days }
            .flatMapLatest { (date, days) ->
                observeDashboardNutrientCardsUseCase(
                    date = date,
                    rollingDays = days,
                    zoneId = ZoneId.systemDefault()
                )
            }

    val pinOptions: StateFlow<List<DashboardPinOption>> =
        observeDashboardPinOptions()
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                emptyList()
            )


    private val pinnedKeysFlow: StateFlow<List<NutrientKey>> =
        userPinnedNutrientRepository.observePinnedKeys()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val rollingStats: StateFlow<RollingNutritionStats> =
        combine(selectedDateFlow, rollingDaysFlow) { date, days -> date to days }
            .flatMapLatest { (date, days) ->
                observeRollingNutritionStatsUseCase(
                    endDate = date,
                    days = days,
                    zoneId = ZoneId.systemDefault()
                )
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                RollingNutritionStats(
                    averages = RollingNutritionAverages(
                        endDate = selectedDateFlow.value,
                        days = rollingDaysFlow.value,
                        averageByCode = emptyMap()
                    ),
                    okStreaks = emptyList()
                )
            )

    fun setRollingDays(days: Int) {
        rollingDaysFlow.value = days.coerceAtLeast(1)
    }

    fun useWeek() = setRollingDays(7)
    fun useTwoWeeks() = setRollingDays(14)
    fun useMonth() = setRollingDays(30)

    private val _overlay = MutableStateFlow(DashboardOverlay())

    /**
     * UI can read the current target draft (and show inputs/errors/saving) without changing DashboardState.
     * This avoids forcing you to edit DashboardState right now.
     */
    val targetDraft: StateFlow<TargetDraft?> =
        _overlay
            .map { it.targetDraft }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val state: StateFlow<DashboardState> =
        combine(
            observeTodayMacrosUseCase(),
            observeTodayLogItemsUseCase(),
            cardsFlow,
            pinnedKeysFlow,
            _overlay
        ) { totals, items, cards, pinnedKeys, overlay ->
            DashboardState(
                totals = totals,
                todayItems = items,
                nutrientCards = cards,
                pinnedKeys = pinnedKeys,
                blockingSheet = overlay.blockingSheet,
                blockedFoodId = overlay.blockedFoodId,
                navigateToEditFoodId = overlay.navigateToEditFoodId,
                settingsSheetOpen = overlay.settingsSheetOpen
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            DashboardState()
        )




    // region UI toggles

    fun onSettingsClicked() {
        _overlay.update { it.copy(settingsSheetOpen = true) }
    }

    fun onDismissSettingsSheet() {
        // Only closes the sheet; draft is left intact unless UI calls cancelTargetEdit().
        _overlay.update { it.copy(settingsSheetOpen = false) }
    }

    fun snackbarShown() {
        _snackbar.value = null
    }

    // endregion

    // region Date navigation

    fun showPreviousDay() {
        selectedDateFlow.value = selectedDateFlow.value.minusDays(1)
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

    // endregion

    // region Logging / deleting

    fun delete(logId: Long) {
        viewModelScope.launch { deleteLogEntry(logId) }
    }

    fun logFoodByServings(foodId: Long, servings: Double = 1.0) {
        viewModelScope.launch {
            val result = logFood.logFoodByServings(foodId, servings)
            handleLogResult(foodId, result)
        }
    }

    fun logFoodByGrams(foodId: Long, grams: Double) {
        viewModelScope.launch {
            val result = logFood.logFoodByGrams(foodId, grams)
            handleLogResult(foodId, result)
        }
    }

    private fun handleLogResult(
        foodId: Long,
        result: CreateLogEntryUseCase.Result
    ) {
        when (result) {
            is CreateLogEntryUseCase.Result.Success -> Unit

            is CreateLogEntryUseCase.Result.Blocked ->
                showMissingGramsSheet(foodId, result.message)

            is CreateLogEntryUseCase.Result.Error ->
                _snackbar.value = result.message
        }
    }

    // endregion

    // region Blocking sheet + navigation

    fun dismissBlockingSheet() {
        _overlay.update { it.copy(blockingSheet = null, blockedFoodId = null) }
    }

    fun onEditFoodNavigationHandled() {
        _overlay.update { it.copy(navigateToEditFoodId = null) }
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

    // endregion

    // region Import / Export / Sync

    fun devSyncNutrients() {
        viewModelScope.launch {
            val r = syncNutrientCatalog()
            _snackbar.value =
                "Synced nutrients: +${r.inserted} inserted, ${r.updated} updated, ${r.aliasesUpserted} aliases"
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

    // endregion

    // region Dashboard personalization

    fun applyPinnedCodes(slot0: String?, slot1: String?) {
        viewModelScope.launch {
            setPinnedDashboardNutrientsUseCase(
                slot0 = slot0.toKeyOrNull(),
                slot1 = slot1.toKeyOrNull()
            )
        }
    }

    fun saveTargetEdit(edit: TargetEdit) {
        viewModelScope.launch { upsertUserNutrientTargetUseCase(edit) }
    }

    /**
     * Start editing a target for the given nutrient.
     * Draft values start empty; your sheet can pre-fill by reading existing target values if you expose them later.
     */
    fun startTargetEdit(key: NutrientKey) {
        _overlay.update {
            it.copy(
                targetDraft = TargetDraft(
                    key = key,
                    min = "",
                    target = "",
                    max = ""
                ),
                settingsSheetOpen = true
            )
        }
    }

    fun updateTargetDraftMin(value: String) {
        _overlay.update { overlay ->
            val draft = overlay.targetDraft ?: return@update overlay
            overlay.copy(
                targetDraft = draft.copy(
                    min = value,
                    isDirty = true,
                    error = null
                )
            )
        }
    }

    fun updateTargetDraftTarget(value: String) {
        _overlay.update { overlay ->
            val draft = overlay.targetDraft ?: return@update overlay
            overlay.copy(
                targetDraft = draft.copy(
                    target = value,
                    isDirty = true,
                    error = null
                )
            )
        }
    }

    fun updateTargetDraftMax(value: String) {
        _overlay.update { overlay ->
            val draft = overlay.targetDraft ?: return@update overlay
            overlay.copy(
                targetDraft = draft.copy(
                    max = value,
                    isDirty = true,
                    error = null
                )
            )
        }
    }

    fun cancelTargetEdit() {
        _overlay.update { it.copy(targetDraft = null) }
    }

    fun saveTargetDraft() {
        val draft = _overlay.value.targetDraft ?: return

        val min = draft.min.toDoubleOrNull()
        val target = draft.target.toDoubleOrNull()
        val max = draft.max.toDoubleOrNull()

        // Require at least one number; empty fields mean "unset".
        if (min == null && target == null && max == null) {
            _overlay.update { overlay ->
                overlay.copy(
                    targetDraft = draft.copy(error = "Enter at least one value")
                )
            }
            return
        }

        // Optional ordering validation (light UI guard; domain can also enforce):
        // If all 3 are present, require min <= target <= max.
        val orderingOk =
            (min == null || target == null || min <= target) &&
                    (target == null || max == null || target <= max) &&
                    (min == null || max == null || min <= max)

        if (!orderingOk) {
            _overlay.update { overlay ->
                overlay.copy(
                    targetDraft = draft.copy(error = "Check ordering: min ≤ target ≤ max")
                )
            }
            return
        }

        _overlay.update { overlay ->
            overlay.copy(
                targetDraft = draft.copy(isSaving = true, error = null)
            )
        }

        viewModelScope.launch {
            try {
                upsertUserNutrientTargetUseCase(
                    TargetEdit(
                        key = draft.key,
                        min = min,
                        target = target,
                        max = max
                    )
                )
                _overlay.update { it.copy(targetDraft = null) }
            } catch (e: Exception) {
                _overlay.update { overlay ->
                    val current = overlay.targetDraft
                    // Preserve whatever draft is currently present (user might have typed more while saving)
                    if (current == null) overlay
                    else overlay.copy(
                        targetDraft = current.copy(
                            isSaving = false,
                            error = e.message ?: "Save failed"
                        )
                    )
                }
            }
        }
    }

    private fun String?.toKeyOrNull(): NutrientKey? =
        this?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { NutrientKey(it.uppercase()) }

    fun startTargetEditPrefilled(
        key: NutrientKey,
        min: String,
        target: String,
        max: String
    ) {
        _overlay.update {
            it.copy(
                targetDraft = TargetDraft(
                    key = key,
                    min = min,
                    target = target,
                    max = max
                ),
                settingsSheetOpen = true
            )
        }
    }

    // endregion

    init {
        viewModelScope.launch { bootstrapDomain() }
    }
}

private data class DashboardOverlay(
    val blockingSheet: BlockingSheetModel? = null,
    val blockedFoodId: Long? = null,
    val navigateToEditFoodId: Long? = null,
    val settingsSheetOpen: Boolean = false,
    val targetDraft: TargetDraft? = null
)

