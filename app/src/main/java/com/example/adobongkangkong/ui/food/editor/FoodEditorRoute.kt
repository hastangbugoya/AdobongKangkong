package com.example.adobongkangkong.ui.food.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.adobongkangkong.ui.camera.BannerCaptureController

/**
 * Route wrapper for FoodEditorScreen.
 *
 * Responsibilities:
 * - Pull nav args (foodId / initialName) from the NavGraph and pass them in.
 * - Call vm.load(...) when args change.
 * - Map VM functions to the UI callbacks.
 *
 * NOTE:
 * - foodId == null means "New Food"
 * - initialName is only meaningful for "New Food" route prefill.
 */
@Composable
fun FoodEditorRoute(
    foodId: Long?,
    initialName: String?,
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: FoodEditorViewModel = hiltViewModel(),
    bannerCaptureController: BannerCaptureController
) {
    val state by viewModel.state.collectAsState()

    val aliasName by viewModel.aliasSheetNutrientName.collectAsState()
    val aliasMessage by viewModel.aliasSheetMessage.collectAsState()
    val aliases by viewModel.selectedAliases.collectAsState()

    // Load / initialize editor state when navigation args change.
    LaunchedEffect(foodId, initialName) {
        viewModel.load(foodId = foodId, initialName = initialName)
    }

    FoodEditorScreen(
        state = state,
        onBack = onBack,

        // Core fields
        onNameChange = viewModel::onNameChange,
        onBrandChange = viewModel::onBrandChange,
        onServingSizeChange = viewModel::onServingSizeChange,
        onServingUnitChange = viewModel::onServingUnitChange,
        onGramsPerServingChange = viewModel::onGramsPerServingChange,
        onServingsPerPackageChange = viewModel::onServingsPerPackageChange,

        // Nutrient rows
        onNutrientAmountChange = viewModel::onNutrientAmountChange,
        onRemoveNutrient = viewModel::removeNutrientRow,

        // Nutrient search/add
        onNutrientSearchQueryChange = viewModel::onNutrientSearchQueryChange,
        onAddNutrientFromSearch = { nutrientId ->
            // Screen provides only the id; VM expects the full UI item.
            val item = state.nutrientSearchResults.firstOrNull { it.id == nutrientId }
                ?: return@FoodEditorScreen
            viewModel.addNutrient(item)
        },

        // Flags (note: your VM names differ from the screen param names)
        onToggleFavorite = viewModel::onFavoriteChange,
        onToggleEatMore = viewModel::onEatMoreChange,
        onToggleLimit = viewModel::onLimitChange,

        // Save/Delete
        onSave = {
            viewModel.save { /*savedId*/ _ ->
                onDone()
            }
        },
        onDeleteFood = null, // You currently don't have a delete() API in this VM.

        // Alias sheet wiring
        aliasSheetNutrientName = aliasName,
        aliasSheetAliases = aliases,
        aliasSheetMessage = aliasMessage,
        onOpenAliasSheet = { id, name -> viewModel.openAliasSheet(id, name) },
        onAddAlias = { viewModel.addAlias(it) },
        onDeleteAlias = { viewModel.deleteAlias(it) },
        onDismissAliasSheet = { viewModel.closeAliasSheet() },
        bannerCaptureController = bannerCaptureController,
    )
}
