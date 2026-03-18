package com.example.adobongkangkong.ui.food.editor

import android.util.Log
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.adobongkangkong.ui.camera.BannerCaptureController
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodEditorRoute(
    foodId: Long?,
    initialName: String?,
    initialBarcode: String? = null,
    bannerRefreshTick: Int,
    mergePickedFoodId: Long? = null,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onAssignBarcodeToExisting: (String) -> Unit = {},
    onOpenMergeFoodPicker: (Long) -> Unit = {},
    onMergePickedConsumed: () -> Unit = {},
    viewModel: FoodEditorViewModel = hiltViewModel(),
    bannerCaptureController: BannerCaptureController,
    onOpenFoodEditor: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val didDelete by viewModel.didDelete.collectAsState()
    val didMerge by viewModel.didMerge.collectAsState()
    val assignExistingBarcode by viewModel.assignBarcodeToExistingBarcode.collectAsState()
    val openFoodEditorRequest by viewModel.openFoodEditorRequest.collectAsState()

    LaunchedEffect(Unit) {
        Log.d("Meow", "FOOD_EDITOR_ROUTE vm=${System.identityHashCode(viewModel)}")
    }

    val aliasName by viewModel.aliasSheetNutrientName.collectAsState()
    val aliasMessage by viewModel.aliasSheetMessage.collectAsState()
    val aliases by viewModel.selectedAliases.collectAsState()

    val didApplyInitialBarcode = remember(foodId, initialName, initialBarcode) {
        mutableStateOf(false)
    }

    LaunchedEffect(foodId, initialName, initialBarcode) {
        viewModel.load(foodId = foodId, initialName = initialName, force = true)

        val code = initialBarcode?.trim().orEmpty()
        if (code.isBlank()) return@LaunchedEffect
        if (didApplyInitialBarcode.value) return@LaunchedEffect
        didApplyInitialBarcode.value = true

        if (foodId == null) {
            viewModel.onBarcodeScanned(code)
        } else {
            snapshotFlow { state.foodId }
                .filter { it == foodId }
                .first()

            viewModel.assignBarcodeToCurrentFood(code)
        }
    }

    LaunchedEffect(didDelete) {
        if (didDelete) onBack()
    }

    LaunchedEffect(didMerge) {
        if (didMerge) onBack()
    }

    LaunchedEffect(assignExistingBarcode) {
        val code = assignExistingBarcode?.trim().orEmpty()
        if (code.isNotBlank()) {
            onAssignBarcodeToExisting(code)
            viewModel.consumeAssignBarcodeToExistingRequest()
        }
    }

    LaunchedEffect(openFoodEditorRequest) {
        val targetFoodId = openFoodEditorRequest ?: return@LaunchedEffect
        onOpenFoodEditor(targetFoodId)
        viewModel.consumeOpenFoodEditorRequest()
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
        onMergeFood = {
            val currentFoodId = state.foodId ?: return@FoodEditorScreen
            onOpenMergeFoodPicker(currentFoodId)
        },

        onCategoryCheckedChange = viewModel::onCategoryCheckedChange,
        onNewCategoryNameChange = viewModel::onNewCategoryNameChange,
        onCreateCategory = viewModel::createCategory,
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
        onUnassignBarcode = viewModel::unassignBarcode,

        onPickBasisType = viewModel::onPickBasisType,
        onDismissGroundingDialog = viewModel::closeGroundingDialog,

        mlPerServingUnit = state.mlPerServingUnit,
        onMlPerServingChange = viewModel::onMlPerServingChange,
        basisType = state.basisType,

        onDismissBarcodeFallback = viewModel::dismissBarcodeFallback,
        onBarcodeFallbackAssignExisting = viewModel::barcodeFallbackAssignExisting,
        onBarcodeFallbackCreateNameChange = viewModel::onBarcodeFallbackCreateNameChange,
        onBarcodeFallbackCreateMinimal = viewModel::barcodeFallbackCreateMinimalFood,
        onConfirmBarcodeRemap = viewModel::onConfirmBarcodeRemap,

        onBarcodeFallbackOpenAssignedFood = { targetFoodId ->
            viewModel.dismissBarcodeFallback()
            onOpenFoodEditor(targetFoodId)
        },

        onOpenFoodEditor = { targetFoodId ->
            viewModel.dismissBarcodeFallback()
            onOpenFoodEditor(targetFoodId)
        },

        onDismissBarcodeCollision = viewModel::dismissBarcodeCollision,
        onOpenExistingFromCollision = viewModel::openExistingFromCollision,
        onRemapFromCollisionProceedImport = viewModel::remapFromCollisionProceedImport,
        onResolveBarcodeCollision = { action ->
            when (action) {
                BarcodeCollisionAction.Cancel -> viewModel.dismissBarcodeCollision()
                BarcodeCollisionAction.OpenExisting -> viewModel.openExistingFromCollision()
                BarcodeCollisionAction.Replace -> viewModel.remapFromCollisionProceedImport()
            }
        },

        onOpenBarcodePackageEditor = viewModel::openBarcodePackageEditor,
        onDismissBarcodePackageEditor = viewModel::dismissBarcodePackageEditor,
        onBarcodePackageOverrideServingsPerPackageChange = viewModel::onBarcodePackageOverrideServingsPerPackageChange,
        onBarcodePackageOverrideHouseholdServingTextChange = viewModel::onBarcodePackageOverrideHouseholdServingTextChange,
        onBarcodePackageOverrideServingSizeChange = viewModel::onBarcodePackageOverrideServingSizeChange,
        onBarcodePackageOverrideServingUnitChange = viewModel::onBarcodePackageOverrideServingUnitChange,
        onSaveBarcodePackageOverrides = viewModel::saveBarcodePackageOverrides,

        onConfirmUsdaInterpretationPrompt = viewModel::confirmUsdaInterpretationPrompt,
        onDismissUsdaInterpretationPrompt = viewModel::dismissUsdaInterpretationPrompt,

        onConfirmUsdaBackfillPrompt = viewModel::confirmUsdaBackfillPrompt,
        onDismissUsdaBackfillPrompt = viewModel::dismissUsdaBackfillPrompt,
        onDismissUsdaBackfillMessage = viewModel::dismissUsdaBackfillMessage,

        onDismissNeedsFixBanner = viewModel::dismissNeedsFixBanner
    )

    LaunchedEffect(mergePickedFoodId) {
        val canonicalFoodId = mergePickedFoodId ?: return@LaunchedEffect
        viewModel.mergeIntoFood(canonicalFoodId)
        onMergePickedConsumed()
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (state.isBarcodeScannerOpen && state.barcodePickItems.size <= 1) {
        ModalBottomSheet(
            onDismissRequest = viewModel::closeBarcodeScanner,
            sheetState = sheetState
        ) {
            BarcodeScannerSheet(
                onClose = viewModel::closeBarcodeScanner,
                onBarcode = { code ->
                    val cleaned = code.trim()
                    if (cleaned.isBlank()) return@BarcodeScannerSheet

                    if (state.foodId != null) {
                        viewModel.assignBarcodeToCurrentFood(cleaned)
                    } else {
                        viewModel.onBarcodeScanned(cleaned)
                    }
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
 *   - loads the food/editor data based on route args
 *   - owns navigation side-effects (done/back/open-other-food/merge picker bridge)
 *
 * Important architecture rule:
 * - FoodEditorScreen stays stateless and navigation-agnostic.
 * - FoodEditorViewModel decides intent.
 * - FoodEditorRoute performs navigation.
 *
 * Barcode override flow:
 * - Editing existing food + scan barcode:
 *   - new barcode -> attach to current food -> open package override editor
 *   - same-food barcode -> open package override editor
 *   - different-food barcode -> route opens that already-assigned food
 *
 * USDA interpretation flow:
 * - After a USDA food is imported and the nutrient semantics are ambiguous,
 *   the ViewModel may expose pendingUsdaInterpretationPrompt.
 * - Route only forwards confirm/dismiss callbacks between screen and ViewModel.
 * - Do not move USDA interpretation business logic into this route.
 *
 * USDA nutrient backfill flow:
 * - After USDA package adoption into an existing food, the ViewModel may expose
 *   pendingUsdaBackfillPrompt.
 * - Route simply forwards prompt/result callbacks between screen and ViewModel.
 * - Do not move nutrient backfill business logic into this route.
 *
 * Merge wiring:
 * - Keep merge completion bridge at route level.
 * - Do not duplicate merge-result handling in both route and VM.
 */