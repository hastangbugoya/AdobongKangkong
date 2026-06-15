package com.example.adobongkangkong.ui.recipevariant

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun RecipeVariantEditorRoute(
    onBack: () -> Unit,
    onEditFood: ((Long) -> Unit)? = null,
    viewModel: RecipeVariantEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    RecipeVariantEditorScreen(
        uiState = uiState,
        onBack = onBack,
        onNameChanged = viewModel::onNameChanged,
        onNotesChanged = viewModel::onNotesChanged,
        onSave = viewModel::save,
        onClearError = viewModel::clearError,
        onMarkIngredientRemoved = viewModel::markIngredientRemoved,
        onRestoreIngredientLine = viewModel::restoreIngredientLine,
        onAdjustIngredientToGrams = viewModel::adjustIngredientToGrams,
        onAdjustIngredientToServings = viewModel::adjustIngredientToServings,
        onClearIngredientAdjustment = viewModel::clearIngredientAdjustment,
        onAdjustAddedIngredientToGrams = viewModel::adjustAddedIngredientToGrams,
        onAdjustAddedIngredientToServings = viewModel::adjustAddedIngredientToServings,
        onRemoveAddedIngredient = viewModel::removeAddedIngredient,
        onAddIngredientQueryChanged = viewModel::onAddIngredientQueryChanged,
        onPickFoodForIngredient = viewModel::pickFoodForIngredient,
        onClearPickedFoodForIngredient = viewModel::clearPickedFoodForIngredient,
        onPickedServingsChanged = viewModel::onPickedServingsChanged,
        onPickedServingUnitAmountChanged = viewModel::onPickedServingUnitAmountChanged,
        onPickedGramsChanged = viewModel::onPickedGramsChanged,
        onPickedInputUnitChanged = viewModel::onPickedInputUnitChanged,
        onPickedInputAmountChanged = viewModel::onPickedInputAmountChanged,
        onPickedPackageClicked = viewModel::onPickedPackageClicked,
        onAddPickedVariantIngredient = viewModel::addPickedVariantIngredient,
        onAddPickedVariantIngredientWithAmount = viewModel::addPickedVariantIngredientWithAmount,
        onEditFood = onEditFood,
    )
}
