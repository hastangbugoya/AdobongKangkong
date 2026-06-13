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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.adobongkangkong.R
import com.example.adobongkangkong.domain.model.AssembledRecipeVariantIngredientLine
import com.example.adobongkangkong.domain.model.RecipeMacroPreview
import com.example.adobongkangkong.domain.model.RecipeVariantIngredientLineSource
import com.example.adobongkangkong.domain.model.RecipeVariantMacroComparison
import com.example.adobongkangkong.domain.model.RemovedRecipeVariantIngredientLine
import com.example.adobongkangkong.ui.common.food.FoodBannerCardBackground
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecipeVariantEditorScreen(
    uiState: RecipeVariantEditorUiState,
    onBack: () -> Unit,
    onNameChanged: (String) -> Unit,
    onNotesChanged: (String) -> Unit,
    onSave: () -> Unit,
    onClearError: () -> Unit,
    onMarkIngredientRemoved: (Long) -> Unit,
    onRestoreIngredientLine: (Long) -> Unit,
    onAdjustIngredientToGrams: (Long, Double) -> Unit,
    onAdjustIngredientToServings: (Long, Double) -> Unit,
    onClearIngredientAdjustment: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val canSave = !uiState.isSaving &&
            uiState.name.isNotBlank() &&
            uiState.hasUnsavedChanges

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Recipe variant",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ms_arrow_back),
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
        bottomBar = {
            Button(
                onClick = onSave,
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    if (uiState.isSaving) {
                        "Saving…"
                    } else if (uiState.hasUnsavedChanges) {
                        "Save variant"
                    } else {
                        "Saved"
                    }
                )
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
            }

            item {
                RecipeHeaderCard(uiState = uiState)
            }

            if (uiState.errorMessage != null) {
                item {
                    ErrorCard(
                        message = uiState.errorMessage,
                        onClearError = onClearError,
                    )
                }
            }

            item {
                VariantDetailsCard(
                    name = uiState.name,
                    notes = uiState.notes,
                    onNameChanged = onNameChanged,
                    onNotesChanged = onNotesChanged,
                )
            }

            uiState.macroComparison?.let { comparison ->
                item {
                    MacroComparisonCard(
                        comparison = comparison,
                        isStale = uiState.hasUnsavedChanges,
                    )
                }
            }

            if (uiState.warnings.isNotEmpty()) {
                item {
                    WarningCard(warnings = uiState.warnings)
                }
            }

            item {
                SectionHeader(
                    title = "Ingredients",
                    subtitle = when {
                        uiState.isLoading -> "Loading variant ingredients…"
                        uiState.finalIngredientLines.isEmpty() -> "No ingredients to show."
                        else -> "${uiState.finalIngredientLines.size} final ingredient${if (uiState.finalIngredientLines.size == 1) "" else "s"}"
                    },
                )
            }

            if (uiState.isLoading) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "Loading variant…",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else if (uiState.finalIngredientLines.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "No final ingredients to show.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(
                    items = uiState.finalIngredientLines,
                    key = { line ->
                        "${line.source}-${line.baseRecipeIngredientId ?: line.food.id}-${line.sortOrder}"
                    },
                ) { line ->
                    val baseRecipeIngredientId = line.baseRecipeIngredientId
                    val isMarkedRemoved = baseRecipeIngredientId != null &&
                            baseRecipeIngredientId in uiState.removedBaseIngredientIds
                    val isMarkedAdjusted = baseRecipeIngredientId != null &&
                            baseRecipeIngredientId in uiState.adjustedBaseIngredientIds

                    FoodBannerCardBackground(foodId = line.food.id) {
                        FinalIngredientLineCard(
                            line = line,
                            isMarkedRemoved = isMarkedRemoved,
                            isMarkedAdjusted = isMarkedAdjusted,
                            onMarkIngredientRemoved = onMarkIngredientRemoved,
                            onRestoreIngredientLine = onRestoreIngredientLine,
                            onAdjustIngredientToGrams = onAdjustIngredientToGrams,
                            onAdjustIngredientToServings = onAdjustIngredientToServings,
                            onClearIngredientAdjustment = onClearIngredientAdjustment,
                        )
                    }
                }
            }

            if (uiState.removedIngredientLines.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Removed ingredients",
                        subtitle = "${uiState.removedIngredientLines.size} pending removal${if (uiState.removedIngredientLines.size == 1) "" else "s"}",
                    )
                }

                items(
                    items = uiState.removedIngredientLines,
                    key = { it.baseRecipeIngredientId },
                ) { line ->
                    val food = line.food
                    if (food != null) {
                        FoodBannerCardBackground(foodId = food.id) {
                            RemovedIngredientLineCard(
                                line = line,
                                onRestoreIngredientLine = onRestoreIngredientLine,
                            )
                        }
                    } else {
                        RemovedIngredientLineCard(
                            line = line,
                            onRestoreIngredientLine = onRestoreIngredientLine,
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(88.dp))
            }
        }
    }
}

@Composable
private fun RecipeHeaderCard(
    uiState: RecipeVariantEditorUiState,
) {
    FoodBannerCardBackground(foodId = uiState.recipeFoodId) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Recipe",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )

                Text(
                    text = uiState.recipeName.ifBlank { "Recipe" },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text = "Variant: ${uiState.name.ifBlank { "Unnamed variant" }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(
    message: String,
    onClearError: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )

            TextButton(onClick = onClearError) {
                Text("Dismiss")
            }
        }
    }
}

@Composable
private fun VariantDetailsCard(
    name: String,
    notes: String,
    onNameChanged: (String) -> Unit,
    onNotesChanged: (String) -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Variant details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
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
        }
    }
}

@Composable
private fun MacroComparisonCard(
    comparison: RecipeVariantMacroComparison,
    isStale: Boolean,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Macro comparison",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )

                Text(
                    text = if (isStale) {
                        "Saved comparison. Save variant to refresh."
                    } else {
                        "Recipe, variant, and raw difference."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            MacroComparisonHeaderRow()

            MacroComparisonRow(
                label = "Calories",
                recipeValue = comparison.recipe.totalCalories,
                variantValue = comparison.variant.totalCalories,
                deltaValue = comparison.delta.totalCalories,
                suffix = "",
                decimals = 0,
            )

            MacroComparisonRow(
                label = "Protein",
                recipeValue = comparison.recipe.totalProteinG,
                variantValue = comparison.variant.totalProteinG,
                deltaValue = comparison.delta.totalProteinG,
                suffix = "g",
                decimals = 1,
            )

            MacroComparisonRow(
                label = "Carbs",
                recipeValue = comparison.recipe.totalCarbsG,
                variantValue = comparison.variant.totalCarbsG,
                deltaValue = comparison.delta.totalCarbsG,
                suffix = "g",
                decimals = 1,
            )

            MacroComparisonRow(
                label = "Fat",
                recipeValue = comparison.recipe.totalFatG,
                variantValue = comparison.variant.totalFatG,
                deltaValue = comparison.delta.totalFatG,
                suffix = "g",
                decimals = 1,
            )
        }
    }
}

@Composable
private fun MacroComparisonHeaderRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "",
            modifier = Modifier.weight(1.1f),
        )

        Text(
            text = "Recipe",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
        )

        Text(
            text = "Variant",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
        )

        Text(
            text = "Δ",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun MacroComparisonRow(
    label: String,
    recipeValue: Double,
    variantValue: Double,
    deltaValue: Double,
    suffix: String,
    decimals: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1.1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )

        Text(
            text = formatMacroValue(
                value = recipeValue,
                suffix = suffix,
                decimals = decimals,
                showPlus = false,
            ),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
        )

        Text(
            text = formatMacroValue(
                value = variantValue,
                suffix = suffix,
                decimals = decimals,
                showPlus = false,
            ),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
        )

        Text(
            text = formatMacroValue(
                value = deltaValue,
                suffix = suffix,
                decimals = decimals,
                showPlus = true,
            ),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
        )
    }
}

private fun formatMacroValue(
    value: Double,
    suffix: String,
    decimals: Int,
    showPlus: Boolean,
): String {
    val normalized = if (abs(value) < 0.0001) 0.0 else value
    val prefix = if (showPlus && normalized > 0.0) "+" else ""
    val number = if (decimals <= 0) {
        String.format(Locale.US, "%,.0f", normalized)
    } else {
        String.format(Locale.US, "%,.${decimals}f", normalized)
            .trimEnd('0')
            .trimEnd('.')
    }

    return "$prefix$number$suffix"
}

@Composable
private fun WarningCard(
    warnings: List<String>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Warnings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error,
            )

            warnings.forEach { warning ->
                Text(
                    text = "• $warning",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FinalIngredientLineCard(
    line: AssembledRecipeVariantIngredientLine,
    isMarkedRemoved: Boolean,
    isMarkedAdjusted: Boolean,
    onMarkIngredientRemoved: (Long) -> Unit,
    onRestoreIngredientLine: (Long) -> Unit,
    onAdjustIngredientToGrams: (Long, Double) -> Unit,
    onAdjustIngredientToServings: (Long, Double) -> Unit,
    onClearIngredientAdjustment: (Long) -> Unit,
) {
    val baseRecipeIngredientId = line.baseRecipeIngredientId

    var adjustModeName by rememberSaveable(
        baseRecipeIngredientId,
        line.source.name,
        line.servings,
        line.grams,
    ) {
        mutableStateOf(defaultAdjustMode(line).name)
    }

    val adjustMode = VariantAdjustMode.valueOf(adjustModeName)

    var adjustAmountText by rememberSaveable(
        baseRecipeIngredientId,
        line.source.name,
        line.servings,
        line.grams,
    ) {
        mutableStateOf(defaultAdjustText(line, defaultAdjustMode(line)))
    }

    val parsedAmount = adjustAmountText.toDoubleOrNull()
    val defaultAmountText = defaultAdjustText(line, adjustMode)
    val canApply = baseRecipeIngredientId != null &&
            !isMarkedRemoved &&
            parsedAmount != null &&
            parsedAmount > 0.0 &&
            adjustAmountText != defaultAmountText

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        text = line.food.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    line.food.brand?.takeIf { it.isNotBlank() }?.let { brand ->
                        Text(
                            text = brand,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                IngredientLineStatusIcon(
                    source = line.source,
                    isMarkedRemoved = isMarkedRemoved,
                    isMarkedAdjusted = isMarkedAdjusted,
                )
            }

            line.note?.takeIf { it.isNotBlank() }?.let { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (baseRecipeIngredientId != null) {
                if (isMarkedRemoved) {
                    OutlinedButton(
                        onClick = {
                            onRestoreIngredientLine(baseRecipeIngredientId)
                        },
                    ) {
                        Text("Restore")
                    }
                } else {
                    CompactIngredientAmountEditor(
                        line = line,
                        adjustMode = adjustMode,
                        adjustAmountText = adjustAmountText,
                        canApply = canApply,
                        isMarkedAdjusted = isMarkedAdjusted,
                        onAdjustModeChanged = { newMode ->
                            adjustModeName = newMode.name
                            adjustAmountText = defaultAdjustText(line, newMode)
                        },
                        onAdjustAmountTextChanged = { newValue ->
                            adjustAmountText = newValue.filter { character ->
                                character.isDigit() || character == '.'
                            }
                        },
                        onApply = {
                            parsedAmount?.let { amount ->
                                when (adjustMode) {
                                    VariantAdjustMode.SERVINGS -> {
                                        onAdjustIngredientToServings(
                                            baseRecipeIngredientId,
                                            servingsFromDisplayAmount(
                                                displayAmount = amount,
                                                servingSize = line.food.servingSize,
                                            ),
                                        )
                                    }

                                    VariantAdjustMode.GRAMS -> {
                                        onAdjustIngredientToGrams(baseRecipeIngredientId, amount)
                                    }
                                }
                            }
                        },
                        onClearAdjustment = {
                            adjustModeName = defaultAdjustMode(line).name
                            adjustAmountText = defaultAdjustText(line, defaultAdjustMode(line))
                            onClearIngredientAdjustment(baseRecipeIngredientId)
                        },
                        onRemove = {
                            onMarkIngredientRemoved(baseRecipeIngredientId)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun IngredientLineStatusIcon(
    source: RecipeVariantIngredientLineSource,
    isMarkedRemoved: Boolean,
    isMarkedAdjusted: Boolean,
) {
    val iconRes = when {
        isMarkedRemoved -> android.R.drawable.ic_menu_delete
        isMarkedAdjusted || source == RecipeVariantIngredientLineSource.ADJUSTED -> android.R.drawable.ic_menu_edit
        source == RecipeVariantIngredientLineSource.ADDED -> android.R.drawable.ic_input_add
        else -> android.R.drawable.ic_menu_info_details
    }

    val description = when {
        isMarkedRemoved -> "Pending removal"
        isMarkedAdjusted || source == RecipeVariantIngredientLineSource.ADJUSTED -> "Adjusted ingredient"
        source == RecipeVariantIngredientLineSource.ADDED -> "Added ingredient"
        else -> "Original ingredient"
    }

    val tint = when {
        isMarkedRemoved -> MaterialTheme.colorScheme.error
        isMarkedAdjusted || source == RecipeVariantIngredientLineSource.ADJUSTED -> MaterialTheme.colorScheme.primary
        source == RecipeVariantIngredientLineSource.ADDED -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Icon(
        painter = painterResource(iconRes),
        contentDescription = description,
        tint = tint,
        modifier = Modifier
            .padding(top = 1.dp)
            .size(20.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompactIngredientAmountEditor(
    line: AssembledRecipeVariantIngredientLine,
    adjustMode: VariantAdjustMode,
    adjustAmountText: String,
    canApply: Boolean,
    isMarkedAdjusted: Boolean,
    onAdjustModeChanged: (VariantAdjustMode) -> Unit,
    onAdjustAmountTextChanged: (String) -> Unit,
    onApply: () -> Unit,
    onClearAdjustment: () -> Unit,
    onRemove: () -> Unit,
) {
    val canAdjustByGrams = canAdjustByGrams(line)

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FilterChip(
                selected = adjustMode == VariantAdjustMode.SERVINGS,
                onClick = {
                    onAdjustModeChanged(VariantAdjustMode.SERVINGS)
                },
                label = {
                    Text(line.food.servingUnit.display)
                },
            )

            if (canAdjustByGrams) {
                FilterChip(
                    selected = adjustMode == VariantAdjustMode.GRAMS,
                    onClick = {
                        onAdjustModeChanged(VariantAdjustMode.GRAMS)
                    },
                    label = {
                        Text("g")
                    },
                )
            }
        }

        OutlinedTextField(
            value = adjustAmountText,
            onValueChange = onAdjustAmountTextChanged,
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text(
                    text = "Recipe: ${originalAmountLabel(line, adjustMode)}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
            ),
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Button(
                onClick = onApply,
                enabled = canApply,
            ) {
                Text("Apply")
            }

            if (line.source == RecipeVariantIngredientLineSource.ADJUSTED || isMarkedAdjusted) {
                OutlinedButton(
                    onClick = onClearAdjustment,
                ) {
                    Text("Clear")
                }
            }

            TextButton(
                onClick = onRemove,
            ) {
                Text("Remove")
            }
        }
    }
}

@Composable
private fun RemovedIngredientLineCard(
    line: RemovedRecipeVariantIngredientLine,
    onRestoreIngredientLine: (Long) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (line.food != null) {
                Color.Transparent
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = line.food?.name ?: "Missing food",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = "Removed • ${
                    formatVariantAmount(
                        servings = line.servings,
                        grams = line.grams,
                        servingSize = line.food?.servingSize ?: 1.0,
                        servingUnitLabel = line.food?.servingUnit?.display ?: "serving",
                    )
                }",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            line.note?.takeIf { it.isNotBlank() }?.let { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedButton(
                onClick = {
                    onRestoreIngredientLine(line.baseRecipeIngredientId)
                },
            ) {
                Text("Restore")
            }
        }
    }
}

private enum class VariantAdjustMode {
    SERVINGS,
    GRAMS,
}

private val RecipeVariantIngredientLineSource.label: String
    get() = when (this) {
        RecipeVariantIngredientLineSource.ORIGINAL -> "Original"
        RecipeVariantIngredientLineSource.ADJUSTED -> "Adjusted"
        RecipeVariantIngredientLineSource.ADDED -> "Added"
    }

private fun formatVariantAmount(
    servings: Double?,
    grams: Double?,
    servingSize: Double,
    servingUnitLabel: String,
): String {
    return when {
        grams != null -> "${formatAmountNumber(grams)} g"
        servings != null -> {
            val amount = servings * servingSize
            "${formatAmountNumber(amount)} $servingUnitLabel"
        }
        else -> "No amount"
    }
}

private fun defaultAdjustMode(
    line: AssembledRecipeVariantIngredientLine,
): VariantAdjustMode {
    return if (line.grams != null) {
        VariantAdjustMode.GRAMS
    } else {
        VariantAdjustMode.SERVINGS
    }
}

private fun defaultAdjustText(
    line: AssembledRecipeVariantIngredientLine,
    mode: VariantAdjustMode,
): String {
    val value = when (mode) {
        VariantAdjustMode.SERVINGS -> displayAmountFromServings(
            servings = line.servings,
            servingSize = line.food.servingSize,
        ) ?: 1.0

        VariantAdjustMode.GRAMS -> currentGramsOrNull(line) ?: 1.0
    }

    return formatAmountNumber(value)
}

private fun canAdjustByGrams(
    line: AssembledRecipeVariantIngredientLine,
): Boolean {
    return line.grams != null ||
            (line.servings != null && line.food.gramsPerServingUnit != null)
}

private fun currentGramsOrNull(
    line: AssembledRecipeVariantIngredientLine,
): Double? {
    return line.grams
        ?: line.servings
            ?.let { servings ->
                line.food.gramsPerServingUnit?.let { gramsPerServing ->
                    servings * gramsPerServing
                }
            }
}


private fun originalAmountLabel(
    line: AssembledRecipeVariantIngredientLine,
    mode: VariantAdjustMode,
): String {
    if (line.source == RecipeVariantIngredientLineSource.ADDED) {
        return "new ingredient"
    }

    return when (mode) {
        VariantAdjustMode.SERVINGS -> {
            formatVariantAmount(
                servings = line.originalServings,
                grams = line.originalGrams,
                servingSize = line.food.servingSize,
                servingUnitLabel = line.food.servingUnit.display,
            )
        }

        VariantAdjustMode.GRAMS -> {
            originalGramsOrNull(line)?.let { grams ->
                "${formatAmountNumber(grams)} g"
            } ?: formatVariantAmount(
                servings = line.originalServings,
                grams = line.originalGrams,
                servingSize = line.food.servingSize,
                servingUnitLabel = line.food.servingUnit.display,
            )
        }
    }
}

private fun originalGramsOrNull(
    line: AssembledRecipeVariantIngredientLine,
): Double? {
    return line.originalGrams
        ?: line.originalServings
            ?.let { servings ->
                line.food.gramsPerServingUnit?.let { gramsPerServing ->
                    servings * gramsPerServing
                } ?: displayAmountFromServings(
                    servings = servings,
                    servingSize = line.food.servingSize,
                )
            }
}

private fun displayAmountFromServings(
    servings: Double?,
    servingSize: Double,
): Double? {
    val safeServingSize = servingSize.takeIf { it > 0.0 } ?: 1.0
    return servings?.times(safeServingSize)
}

private fun servingsFromDisplayAmount(
    displayAmount: Double,
    servingSize: Double,
): Double {
    val safeServingSize = servingSize.takeIf { it > 0.0 } ?: 1.0
    return displayAmount / safeServingSize
}

private fun formatAmountNumber(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        "%,.2f".format(value).trimEnd('0').trimEnd('.')
    }
}
