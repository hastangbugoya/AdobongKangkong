package com.example.adobongkangkong.ui.recipevariant

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun RecipeVariantListRoute(
    onBack: () -> Unit,
    onOpenVariantEditor: (recipeFoodId: Long, variantId: Long) -> Unit,
    viewModel: RecipeVariantListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is RecipeVariantListViewModel.Effect.OpenVariantEditor -> {
                    onOpenVariantEditor(
                        effect.recipeFoodId,
                        effect.variantId,
                    )
                }
            }
        }
    }

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
        onOpenVariant = viewModel::openVariantEditor,
    )
}