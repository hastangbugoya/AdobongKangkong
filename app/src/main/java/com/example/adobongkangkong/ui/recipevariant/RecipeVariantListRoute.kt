package com.example.adobongkangkong.ui.recipevariant

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue

@Composable
fun RecipeVariantListRoute(
    onBack: () -> Unit,
    viewModel: RecipeVariantListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    RecipeVariantListScreen(
        uiState = uiState,
        onBack = onBack,
        onFilterChanged = viewModel::setFilter,
        onCreateClicked = viewModel::openCreateDialog,
        onCreateDismissed = viewModel::closeCreateDialog,
        onNewVariantNameChanged = viewModel::updateNewVariantName,
        onNewVariantNotesChanged = viewModel::updateNewVariantNotes,
        onCreateConfirmed = viewModel::createVariant,
        onArchiveVariant = viewModel::archiveVariant,
        onRestoreVariant = viewModel::restoreVariant,
    )
}