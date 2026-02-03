package com.example.adobongkangkong.ui.food.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.adobongkangkong.ui.camera.BannerCaptureController

@Composable
fun FoodEditorRoute(
    foodId: Long?,
    initialName: String?,
    onBack: () -> Unit,
    onDone: () -> Unit,
    bannerRefreshTick: Int = 0,
    viewModel: FoodEditorViewModel = hiltViewModel(),
    bannerCaptureController: BannerCaptureController,
) {
    val state by viewModel.state.collectAsState()

    val aliasName by viewModel.aliasSheetNutrientName.collectAsState()
    val aliasMessage by viewModel.aliasSheetMessage.collectAsState()
    val aliases by viewModel.selectedAliases.collectAsState()

    LaunchedEffect(foodId, initialName) {
        viewModel.load(foodId = foodId, initialName = initialName)
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

        onToggleFavorite = viewModel::onFavoriteChange,
        onToggleEatMore = viewModel::onEatMoreChange,
        onToggleLimit = viewModel::onLimitChange,

        onSave = {
            viewModel.save { _ -> onDone() }
        },
        onDeleteFood = null,

        aliasSheetNutrientName = aliasName,
        aliasSheetAliases = aliases,
        aliasSheetMessage = aliasMessage,
        onOpenAliasSheet = { id, name -> viewModel.openAliasSheet(id, name) },
        onAddAlias = { viewModel.addAlias(it) },
        onDeleteAlias = { viewModel.deleteAlias(it) },
        onDismissAliasSheet = { viewModel.closeAliasSheet() },

        bannerRefreshTick = bannerRefreshTick,
        bannerCaptureController = bannerCaptureController,
    )
}
