package com.example.adobongkangkong.ui.recipevariant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.adobongkangkong.data.local.db.entity.RecipeVariantEntity

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecipeVariantListScreen(
    uiState: RecipeVariantListUiState,
    onBack: () -> Unit,
    onFilterChanged: (RecipeVariantFilter) -> Unit,
    onCreateClicked: () -> Unit,
    onCreateDismissed: () -> Unit,
    onNewVariantNameChanged: (String) -> Unit,
    onNewVariantNotesChanged: (String) -> Unit,
    onCreateConfirmed: () -> Unit,
    onArchiveVariant: (Long) -> Unit,
    onRestoreVariant: (Long) -> Unit,
    onOpenVariantEditor: (Long) -> Unit,
    onDeleteArchivedVariantClicked: (Long) -> Unit,
    onDeleteArchivedVariantDismissed: () -> Unit,
    onDeleteArchivedVariantConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text("Recipe variants")
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateClicked,
            ) {
                Text("+")
            }
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Variants belong to this recipe only.",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )

                        Text(
                            text = "They will not appear as separate foods in the main picker. Quick Add can later show them only after this recipe is selected.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    VariantFilterChip(
                        label = "Current",
                        selected = uiState.filter == RecipeVariantFilter.CURRENT,
                        onClick = { onFilterChanged(RecipeVariantFilter.CURRENT) },
                    )

                    VariantFilterChip(
                        label = "Archived",
                        selected = uiState.filter == RecipeVariantFilter.ARCHIVED,
                        onClick = { onFilterChanged(RecipeVariantFilter.ARCHIVED) },
                    )

                    VariantFilterChip(
                        label = "All",
                        selected = uiState.filter == RecipeVariantFilter.ALL,
                        onClick = { onFilterChanged(RecipeVariantFilter.ALL) },
                    )
                }
            }

            if (uiState.errorMessage != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = uiState.errorMessage,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            if (uiState.visibleVariants.isEmpty()) {
                item {
                    EmptyVariantsCard(
                        filter = uiState.filter,
                    )
                }
            } else {
                items(
                    items = uiState.visibleVariants,
                    key = { it.id },
                ) { variant ->
                    RecipeVariantCard(
                        variant = variant,
                        onArchiveVariant = onArchiveVariant,
                        onRestoreVariant = onRestoreVariant,
                        onOpenVariantEditor = onOpenVariantEditor,
                        onDeleteArchivedVariantClicked = onDeleteArchivedVariantClicked,
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    if (uiState.isCreateDialogOpen) {
        CreateRecipeVariantDialog(
            name = uiState.newVariantName,
            notes = uiState.newVariantNotes,
            errorMessage = uiState.errorMessage,
            onNameChanged = onNewVariantNameChanged,
            onNotesChanged = onNewVariantNotesChanged,
            onDismiss = onCreateDismissed,
            onConfirm = onCreateConfirmed,
        )
    }

    uiState.pendingDeleteVariant?.let { variant ->
        DeleteArchivedVariantDialog(
            variantName = variant.name,
            onDismiss = onDeleteArchivedVariantDismissed,
            onConfirm = onDeleteArchivedVariantConfirmed,
        )
    }
}

@Composable
private fun VariantFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(label)
        },
    )
}

@Composable
private fun EmptyVariantsCard(
    filter: RecipeVariantFilter,
) {
    val message = when (filter) {
        RecipeVariantFilter.CURRENT -> "No current variants yet."
        RecipeVariantFilter.ARCHIVED -> "No archived variants."
        RecipeVariantFilter.ALL -> "No variants yet."
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecipeVariantCard(
    variant: RecipeVariantEntity,
    onArchiveVariant: (Long) -> Unit,
    onRestoreVariant: (Long) -> Unit,
    onOpenVariantEditor: (Long) -> Unit,
    onDeleteArchivedVariantClicked: (Long) -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = variant.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )

                    if (!variant.notes.isNullOrBlank()) {
                        Text(
                            text = variant.notes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            if (variant.isArchived) {
                                "Archived"
                            } else {
                                "Current"
                            }
                        )
                    },
                )
            }

            Text(
                text = "Basis: original recipe",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    space = 8.dp,
                    alignment = Alignment.End,
                ),
            ) {
                if (variant.isArchived) {
                    OutlinedButton(
                        onClick = { onDeleteArchivedVariantClicked(variant.id) },
                    ) {
                        Text("Delete")
                    }


                    Button(
                        onClick = { onRestoreVariant(variant.id) },
                    ) {
                        Text("Restore")
                    }
                } else {
                    Button(
                        onClick = { onOpenVariantEditor(variant.id) },
                    ) {
                        Text("Edit")
                    }

                    OutlinedButton(
                        onClick = { onArchiveVariant(variant.id) },
                    ) {
                        Text("Archive")
                    }
                }
            }
        }
    }
}

@Composable
private fun DeleteArchivedVariantDialog(
    variantName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Delete archived variant?")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = variantName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )

                Text(
                    text = "This permanently deletes this variant and its ingredient changes. The original recipe and food logs are not deleted.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
            ) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun CreateRecipeVariantDialog(
    name: String,
    notes: String,
    errorMessage: String?,
    onNameChanged: (String) -> Unit,
    onNotesChanged: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Create variant")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "This variant starts from the original recipe. Later, you can add ingredient changes from the variant editor.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text("Variant name")
                    },
                    singleLine = true,
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = onNotesChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text("Notes")
                    },
                    minLines = 2,
                )

                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = name.isNotBlank(),
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
            ) {
                Text("Cancel")
            }
        },
    )
}