package com.example.adobongkangkong.ui.recipevariant

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun RecipeVariantEditorRoute(
    onBack: () -> Unit,
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
    )
}
