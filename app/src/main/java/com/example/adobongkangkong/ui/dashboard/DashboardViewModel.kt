package com.example.adobongkangkong.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.domain.logging.CreateLogEntryUseCase
import com.example.adobongkangkong.domain.logging.model.AmountInput
import com.example.adobongkangkong.domain.usecase.DeleteLogEntryUseCase
import com.example.adobongkangkong.domain.usecase.ObserveTodayLogItemsUseCase
import com.example.adobongkangkong.domain.usecase.ObserveTodayMacrosUseCase
import com.example.adobongkangkong.ui.common.bottomsheet.BlockingSheetModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    observeTodayMacrosUseCase: ObserveTodayMacrosUseCase,
    observeTodayLogItemsUseCase: ObserveTodayLogItemsUseCase,
    private val deleteLogEntry: DeleteLogEntryUseCase,
    private val createLogEntry: CreateLogEntryUseCase
) : ViewModel() {

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

}

private data class DashboardOverlay(
    val blockingSheet: BlockingSheetModel? = null,
    val blockedFoodId: Long? = null,
    val navigateToEditFoodId: Long? = null
)
