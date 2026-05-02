package com.example.adobongkangkong.ui.productcheck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adobongkangkong.domain.productcheck.EvaluateProductNutritionUseCase
import com.example.adobongkangkong.domain.productcheck.GetUsdaFoodByBarcodeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ProductCheckViewModel @Inject constructor(
    private val getUsdaFoodByBarcode: GetUsdaFoodByBarcodeUseCase,
    private val evaluateProductNutrition: EvaluateProductNutritionUseCase
) : ViewModel() {

    data class UiState(
        val isScannerOpen: Boolean = false,
        val isLoading: Boolean = false,
        val barcode: String? = null,
        val evaluation: EvaluateProductNutritionUseCase.Result? = null,
        val errorMessage: String? = null
    )

    private val stateFlow = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = stateFlow.asStateFlow()

    fun openScanner() {
        stateFlow.value = stateFlow.value.copy(
            isScannerOpen = true,
            errorMessage = null
        )
    }

    fun closeScanner() {
        stateFlow.value = stateFlow.value.copy(isScannerOpen = false)
    }

    fun clearResult() {
        stateFlow.value = UiState()
    }

    fun onBarcodeScanned(rawBarcode: String) {
        val barcode = normalizeBarcode(rawBarcode)

        if (barcode.isBlank()) {
            stateFlow.value = stateFlow.value.copy(
                isScannerOpen = false,
                errorMessage = "Barcode was empty."
            )
            return
        }

        stateFlow.value = stateFlow.value.copy(
            isScannerOpen = false,
            isLoading = true,
            barcode = barcode,
            evaluation = null,
            errorMessage = null
        )

        viewModelScope.launch {
            try {
                val usdaFood = getUsdaFoodByBarcode.execute(barcode)

                if (usdaFood == null) {
                    stateFlow.value = stateFlow.value.copy(
                        isLoading = false,
                        evaluation = null,
                        errorMessage = "No USDA food found for this barcode."
                    )
                    return@launch
                }

                val evaluation = evaluateProductNutrition.execute(
                    foodName = usdaFood.name,
                    brand = usdaFood.brand,
                    servingText = usdaFood.servingText,
                    sodiumMg = usdaFood.sodiumMg,
                    sugarG = usdaFood.sugarG
                )

                stateFlow.value = stateFlow.value.copy(
                    isLoading = false,
                    evaluation = evaluation,
                    errorMessage = null
                )
            } catch (t: Throwable) {
                stateFlow.value = stateFlow.value.copy(
                    isLoading = false,
                    evaluation = null,
                    errorMessage = t.message ?: "Product check failed."
                )
            }
        }
    }

    private fun normalizeBarcode(raw: String): String {
        return raw.trim().filter { it.isDigit() }
    }
}