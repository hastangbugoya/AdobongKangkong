package com.example.adobongkangkong.ui.food.editor

import android.util.Log
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.adobongkangkong.ui.camera.BannerCaptureController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodEditorRoute(
    foodId: Long?,
    initialName: String?,
    initialBarcode: String? = null,
    bannerRefreshTick: Int,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onAssignBarcodeToExisting: (String) -> Unit = {},
    viewModel: FoodEditorViewModel = hiltViewModel(),
    bannerCaptureController: BannerCaptureController,
) {
    val state by viewModel.state.collectAsState()
    val didDelete by viewModel.didDelete.collectAsState()
    LaunchedEffect(Unit) {
        Log.d("Meow", "FOOD_EDITOR_ROUTE vm=${System.identityHashCode(viewModel)}")
    }

    val aliasName by viewModel.aliasSheetNutrientName.collectAsState()
    val aliasMessage by viewModel.aliasSheetMessage.collectAsState()
    val aliases by viewModel.selectedAliases.collectAsState()

    LaunchedEffect(foodId, initialName) {
        viewModel.load(foodId = foodId, initialName = initialName)
    }

    // NEW (minimal): if route was opened with a barcode, reuse the existing scan handler.
    LaunchedEffect(initialBarcode) {
        initialBarcode
            ?.takeIf { it.isNotBlank() }
            ?.let { code ->
                viewModel.onBarcodeScanned(code)
            }
    }

    LaunchedEffect(didDelete) {
        if (didDelete) onBack()
    }

    FoodEditorScreen(
        state = state,
        onBack = onBack,

        onNameChange = viewModel::onNameChange,
        onBrandChange = viewModel::onBrandChange,
        onServingSizeChange = viewModel::onServingSizeChange,
        onServingUnitChange = viewModel::onServingUnitChange,
        onGramsPerServingChange = viewModel::onGramsPerServingChange,
        onServingsPerPackageChange = viewModel::onServingsPerPackageChange,

        onNutrientAmountChange = viewModel::onNutrientAmountChange,
        onRemoveNutrient = viewModel::removeNutrientRow,

        onNutrientSearchQueryChange = viewModel::onNutrientSearchQueryChange,
        onAddNutrientFromSearch = { nutrientId ->
            val item = state.nutrientSearchResults.firstOrNull { it.id == nutrientId }
                ?: return@FoodEditorScreen
            viewModel.addNutrient(item)
        },

        onDeleteFood = { viewModel.deleteFood() },
        onHardDeleteFood = { viewModel.hardDeleteFoodPermanently() },

        onToggleFavorite = viewModel::onFavoriteChange,
        onToggleEatMore = viewModel::onEatMoreChange,
        onToggleLimit = viewModel::onLimitChange,

        onSave = {
            viewModel.save { _ -> onDone() }
        },

        aliasSheetNutrientName = aliasName,
        aliasSheetAliases = aliases,
        aliasSheetMessage = aliasMessage,
        onOpenAliasSheet = { id, name -> viewModel.openAliasSheet(id, name) },
        onAddAlias = { viewModel.addAlias(it) },
        onDeleteAlias = { viewModel.deleteAlias(it) },
        onDismissAliasSheet = { viewModel.closeAliasSheet() },

        bannerCaptureController = bannerCaptureController,
        bannerRefreshTick = bannerRefreshTick,
        onOpenBarcodeScanner = viewModel::openBarcodeScanner,
        onCloseBarcodeScanner = viewModel::closeBarcodeScanner,
        onBarcodeScanned = viewModel::onBarcodeScanned,
        onPickBarcodeCandidate = viewModel::onPickBarcodeCandidate,
        onPickBasisType = viewModel::onPickBasisType,
        onDismissGroundingDialog = viewModel::closeGroundingDialog,
        mlPerServingUnit = state.mlPerServingUnit,
        onMlPerServingChange = viewModel::onMlPerServingChange,
        basisType = state.basisType,
    )

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (state.isBarcodeScannerOpen && state.barcodePickItems.size <= 1) {
        ModalBottomSheet(
            onDismissRequest = viewModel::closeBarcodeScanner,
            sheetState = sheetState
        ) {
            BarcodeScannerSheet(
                onClose = viewModel::closeBarcodeScanner,
                onBarcode = { code ->
                    viewModel.onBarcodeScanned(code)
                }
            )
        }
    }
}

/**
 * FOR-FUTURE-ME (FoodEditorRoute)
 *
 * Purpose:
 * - Route-level wrapper:
 *   - owns FoodEditorViewModel via hiltViewModel()
 *   - loads the food/editor data based on
 */
