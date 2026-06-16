package com.example.adobongkangkong.ui.recipevariant

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.adobongkangkong.R
import com.example.adobongkangkong.data.local.db.entity.FoodEntity
import com.example.adobongkangkong.domain.model.AssembledRecipeVariantIngredientLine
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.model.RecipeMacroPreview
import com.example.adobongkangkong.domain.model.RecipeVariantIngredientLineSource
import com.example.adobongkangkong.domain.model.RecipeVariantMacroComparison
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.model.RemovedRecipeVariantIngredientLine
import com.example.adobongkangkong.ui.common.food.FoodBannerCardBackground
import com.example.adobongkangkong.ui.common.ingredient.IngredientAmountEditorBottomSheet
import com.example.adobongkangkong.ui.common.nutrition.NutrientCautionCard
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecipeVariantEditorScreen(
    uiState: RecipeVariantEditorUiState,
    onBack: () -> Unit,
    onNameChanged: (String) -> Unit,
    onNotesChanged: (String) -> Unit,
    onVariantServingsYieldTextChanged: (String) -> Unit,
    onApplyVariantServingsYieldOverride: () -> Unit,
    onSave: () -> Unit,
    onClearError: () -> Unit,
    onMarkIngredientRemoved: (Long) -> Unit,
    onRestoreIngredientLine: (Long) -> Unit,
    onAdjustIngredientToGrams: (Long, Double) -> Unit,
    onAdjustIngredientToServings: (Long, Double) -> Unit,
    onClearIngredientAdjustment: (Long) -> Unit,
    onAdjustAddedIngredientToGrams: (Long, Int, Double) -> Unit,
    onAdjustAddedIngredientToServings: (Long, Int, Double) -> Unit,
    onRemoveAddedIngredient: (Long, Int) -> Unit,
    onAddIngredientQueryChanged: (String) -> Unit,
    onPickFoodForIngredient: (Food) -> Unit,
    onClearPickedFoodForIngredient: () -> Unit,
    onPickedServingsChanged: (Double) -> Unit,
    onPickedServingUnitAmountChanged: (Double) -> Unit,
    onPickedGramsChanged: (Double) -> Unit,
    onPickedInputUnitChanged: (ServingUnit) -> Unit,
    onPickedInputAmountChanged: (Double?) -> Unit,
    onPickedPackageClicked: (Double) -> Unit,
    onAddPickedVariantIngredient: () -> Unit,
    onAddPickedVariantIngredientWithAmount: (Double, Double?, Boolean) -> Unit,
    onEditFood: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val canSave = !uiState.isSaving &&
            uiState.name.isNotBlank() &&
            uiState.hasUnsavedChanges

    var editingTarget by remember {
        mutableStateOf<VariantIngredientEditTarget?>(null)
    }

    val editingLine = editingTarget?.let { target ->
        uiState.finalIngredientLines.firstOrNull { line ->
            when (target) {
                is VariantIngredientEditTarget.Base -> {
                    line.baseRecipeIngredientId == target.baseRecipeIngredientId
                }

                is VariantIngredientEditTarget.Added -> {
                    line.source == RecipeVariantIngredientLineSource.ADDED &&
                            line.food.id == target.foodId &&
                            line.sortOrder == target.sortOrder
                }
            }
        }
    }

    uiState.pickedFood?.let { pickedFood ->
        IngredientAmountEditorBottomSheet(
            title = "Add ingredient",
            food = pickedFood,
            initialServings = uiState.pickedServings,
            initialGrams = uiState.pickedGrams,
            initialPreferGrams = uiState.pickedGrams != null,
            primaryButtonLabel = "Add ingredient",
            onDismiss = onClearPickedFoodForIngredient,
            onPrimaryAction = { result ->
                onAddPickedVariantIngredientWithAmount(
                    result.servings,
                    result.grams,
                    result.preferGrams,
                )
            },
            errorMessage = uiState.errorMessage,
            onEditFoodInEditor = onEditFood,
        )
    }

    editingLine?.let { line ->
        IngredientAmountEditorBottomSheet(
            title = "Edit ingredient amount",
            food = line.food.toDomainFood(),
            initialServings = line.servingsForEditor(),
            initialGrams = line.currentGramsForEditor(),
            initialPreferGrams = line.grams != null,
            primaryButtonLabel = "Apply",
            originalAmountLabel = if (line.source == RecipeVariantIngredientLineSource.ADDED) {
                null
            } else {
                "Recipe: ${originalAmountLabel(line)}"
            },
            onDismiss = {
                editingTarget = null
            },
            onPrimaryAction = { result ->
                when (val target = editingTarget) {
                    is VariantIngredientEditTarget.Base -> {
                        if (result.preferGrams && result.grams != null) {
                            onAdjustIngredientToGrams(target.baseRecipeIngredientId, result.grams)
                        } else {
                            onAdjustIngredientToServings(target.baseRecipeIngredientId, result.servings)
                        }
                    }

                    is VariantIngredientEditTarget.Added -> {
                        if (result.preferGrams && result.grams != null) {
                            onAdjustAddedIngredientToGrams(target.foodId, target.sortOrder, result.grams)
                        } else {
                            onAdjustAddedIngredientToServings(target.foodId, target.sortOrder, result.servings)
                        }
                    }

                    null -> Unit
                }

                editingTarget = null
            },
            errorMessage = uiState.errorMessage,
            onEditFoodInEditor = onEditFood,
            onRemove = {
                when (val target = editingTarget) {
                    is VariantIngredientEditTarget.Base -> {
                        onMarkIngredientRemoved(target.baseRecipeIngredientId)
                    }

                    is VariantIngredientEditTarget.Added -> {
                        onRemoveAddedIngredient(target.foodId, target.sortOrder)
                    }

                    null -> Unit
                }

                editingTarget = null
            },
        )
    }

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
                        servingsYieldText = uiState.variantServingsYieldText,
                        onServingsYieldTextChanged = onVariantServingsYieldTextChanged,
                        onApplyServingsYield = onApplyVariantServingsYieldOverride,
                    )
                }
            }

            if (uiState.variantPerServingCautions.isNotEmpty()) {
                item {
                    NutrientCautionCard(
                        cautions = uiState.variantPerServingCautions,
                        title = "Cautions",
                        subtitle = "Based on one variant serving.",
                    )
                }
            }

            item {
                AddVariantIngredientCard(
                    uiState = uiState,
                    onAddIngredientQueryChanged = onAddIngredientQueryChanged,
                    onPickFoodForIngredient = onPickFoodForIngredient,
                    onClearPickedFoodForIngredient = onClearPickedFoodForIngredient,
                    onPickedServingsChanged = onPickedServingsChanged,
                    onPickedServingUnitAmountChanged = onPickedServingUnitAmountChanged,
                    onPickedGramsChanged = onPickedGramsChanged,
                    onPickedInputUnitChanged = onPickedInputUnitChanged,
                    onPickedInputAmountChanged = onPickedInputAmountChanged,
                    onPickedPackageClicked = onPickedPackageClicked,
                    onAddPickedVariantIngredient = onAddPickedVariantIngredient,
                    onAddPickedVariantIngredientWithAmount = onAddPickedVariantIngredientWithAmount,
                    onEditFood = onEditFood,
                )
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
                            onRestoreIngredientLine = onRestoreIngredientLine,
                            onRemoveAddedIngredient = onRemoveAddedIngredient,
                            onEditIngredient = {
                                editingTarget = if (baseRecipeIngredientId != null) {
                                    VariantIngredientEditTarget.Base(baseRecipeIngredientId)
                                } else {
                                    VariantIngredientEditTarget.Added(
                                        foodId = line.food.id,
                                        sortOrder = line.sortOrder,
                                    )
                                }
                            },
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
private fun AddVariantIngredientCard(
    uiState: RecipeVariantEditorUiState,
    onAddIngredientQueryChanged: (String) -> Unit,
    onPickFoodForIngredient: (Food) -> Unit,
    onClearPickedFoodForIngredient: () -> Unit,
    onPickedServingsChanged: (Double) -> Unit,
    onPickedServingUnitAmountChanged: (Double) -> Unit,
    onPickedGramsChanged: (Double) -> Unit,
    onPickedInputUnitChanged: (ServingUnit) -> Unit,
    onPickedInputAmountChanged: (Double?) -> Unit,
    onPickedPackageClicked: (Double) -> Unit,
    onAddPickedVariantIngredient: () -> Unit,
    onAddPickedVariantIngredientWithAmount: (Double, Double?, Boolean) -> Unit,
    onEditFood: ((Long) -> Unit)?,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Add ingredient",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )

                Text(
                    text = "Pick a food, then set the amount in the bottom sheet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = uiState.addIngredientQuery,
                onValueChange = onAddIngredientQueryChanged,
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("Search foods")
                },
                singleLine = true,
            )

            if (uiState.addIngredientResults.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    uiState.addIngredientResults.take(8).forEach { food ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = food.name,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )

                            TextButton(
                                onClick = {
                                    onPickFoodForIngredient(food)
                                },
                            ) {
                                Text("Pick")
                            }
                        }
                    }
                }
            }

            if (uiState.pickedFood != null) {
                Text(
                    text = "Set the amount in the bottom sheet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MacroComparisonCard(
    comparison: RecipeVariantMacroComparison,
    servingsYieldText: String,
    onServingsYieldTextChanged: (String) -> Unit,
    onApplyServingsYield: () -> Unit,
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
                    text = "Live preview. Recipe, variant, and raw difference.",
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

            PerServingMacroComparisonSection(
                comparison = comparison,
                servingsYieldText = servingsYieldText,
                onServingsYieldTextChanged = onServingsYieldTextChanged,
                onApplyServingsYield = onApplyServingsYield,
            )
        }
    }
}

@Composable
private fun PerServingMacroComparisonSection(
    comparison: RecipeVariantMacroComparison,
    servingsYieldText: String,
    onServingsYieldTextChanged: (String) -> Unit,
    onApplyServingsYield: () -> Unit,
) {
    var expanded by rememberSaveable(comparison.hasServingYieldOverride) {
        mutableStateOf(comparison.hasServingYieldOverride)
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Per-serving comparison",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )

                Text(
                    text = perServingSummary(comparison),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            TextButton(
                onClick = {
                    expanded = !expanded
                },
            ) {
                Text(if (expanded) "Hide" else "Show")
            }
        }

        Text(
            text = "ⓘ Serving count only divides the final batch. It does not scale ingredient amounts.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = servingsYieldText,
                onValueChange = onServingsYieldTextChanged,
                modifier = Modifier.weight(1f),
                label = {
                    Text("Variant makes (servings)")
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                ),
            )

            Button(
                onClick = onApplyServingsYield,
            ) {
                Text("Apply")
            }
        }

        if (comparison.baseServingsYield != null && comparison.variantServingsYield != null) {
            Text(
                text = "Recipe makes ${formatAmountNumber(comparison.baseServingsYield)} serving${if (comparison.baseServingsYield == 1.0) "" else "s"} • Variant makes ${formatAmountNumber(comparison.variantServingsYield)} serving${if (comparison.variantServingsYield == 1.0) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (expanded) {
            MacroComparisonHeaderRow()

            MacroComparisonRow(
                label = "Calories",
                recipeValue = comparison.recipePerServing.totalCalories,
                variantValue = comparison.variantPerServing.totalCalories,
                deltaValue = comparison.perServingDelta.totalCalories,
                suffix = "",
                decimals = 0,
            )

            MacroComparisonRow(
                label = "Protein",
                recipeValue = comparison.recipePerServing.totalProteinG,
                variantValue = comparison.variantPerServing.totalProteinG,
                deltaValue = comparison.perServingDelta.totalProteinG,
                suffix = "g",
                decimals = 1,
            )

            MacroComparisonRow(
                label = "Carbs",
                recipeValue = comparison.recipePerServing.totalCarbsG,
                variantValue = comparison.variantPerServing.totalCarbsG,
                deltaValue = comparison.perServingDelta.totalCarbsG,
                suffix = "g",
                decimals = 1,
            )

            MacroComparisonRow(
                label = "Fat",
                recipeValue = comparison.recipePerServing.totalFatG,
                variantValue = comparison.variantPerServing.totalFatG,
                deltaValue = comparison.perServingDelta.totalFatG,
                suffix = "g",
                decimals = 1,
            )
        }
    }
}

private fun perServingSummary(
    comparison: RecipeVariantMacroComparison,
): String {
    val calorieDelta = formatMacroValue(
        value = comparison.perServingDelta.totalCalories,
        suffix = "",
        decimals = 0,
        showPlus = true,
    )

    val proteinDelta = formatMacroValue(
        value = comparison.perServingDelta.totalProteinG,
        suffix = "g",
        decimals = 1,
        showPlus = true,
    )

    return "Variant serving: $calorieDelta kcal, $proteinDelta protein vs recipe serving."
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

@Composable
private fun FinalIngredientLineCard(
    line: AssembledRecipeVariantIngredientLine,
    isMarkedRemoved: Boolean,
    isMarkedAdjusted: Boolean,
    onRestoreIngredientLine: (Long) -> Unit,
    onRemoveAddedIngredient: (Long, Int) -> Unit,
    onEditIngredient: () -> Unit,
) {
    val baseRecipeIngredientId = line.baseRecipeIngredientId

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
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

            Text(
                text = "Variant: ${
                    formatVariantAmount(
                        servings = line.servings,
                        grams = line.grams,
                        servingSize = line.food.servingSize,
                        servingUnitLabel = line.food.servingUnit.display,
                    )
                }",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (line.source != RecipeVariantIngredientLineSource.ADDED) {
                Text(
                    text = "Recipe: ${originalAmountLabel(line)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            line.note?.takeIf { it.isNotBlank() }?.let { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (baseRecipeIngredientId != null && isMarkedRemoved) {
                    OutlinedButton(
                        onClick = {
                            onRestoreIngredientLine(baseRecipeIngredientId)
                        },
                    ) {
                        Text("Restore")
                    }
                } else {
                    OutlinedButton(
                        onClick = onEditIngredient,
                    ) {
                        Text("Edit amount")
                    }

                    TextButton(
                        onClick = {
                            if (baseRecipeIngredientId == null) {
                                onRemoveAddedIngredient(
                                    line.food.id,
                                    line.sortOrder,
                                )
                            } else {
                                onEditIngredient()
                            }
                        },
                    ) {
                        Text("Remove")
                    }
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
                    text = if (line.source == RecipeVariantIngredientLineSource.ADDED) {
                        "Variant amount"
                    } else {
                        "Recipe: ${originalAmountLabel(line, adjustMode)}"
                    },
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

private sealed interface VariantIngredientEditTarget {
    data class Base(
        val baseRecipeIngredientId: Long,
    ) : VariantIngredientEditTarget

    data class Added(
        val foodId: Long,
        val sortOrder: Int,
    ) : VariantIngredientEditTarget
}

private fun FoodEntity.toDomainFood(): Food {
    return Food(
        id = id,
        stableId = stableId,
        name = name,
        brand = brand,
        servingSize = servingSize,
        servingUnit = servingUnit,
        gramsPerServingUnit = gramsPerServingUnit,
        mlPerServingUnit = mlPerServingUnit,
        servingsPerPackage = servingsPerPackage,
        isRecipe = isRecipe,
        isLowSodium = isLowSodium,
        usdaFdcId = usdaFdcId,
        usdaGtinUpc = usdaGtinUpc,
        usdaPublishedDate = usdaPublishedDate,
        usdaModifiedDate = usdaModifiedDate,
        usdaServingSize = usdaServingSize,
        usdaServingUnit = usdaServingUnit,
        householdServingText = householdServingText,
        mergedIntoFoodId = mergedIntoFoodId,
        mergedAtEpochMs = mergedAtEpochMs,
        isDeleted = isDeleted,
        deletedAtEpochMs = deletedAtEpochMs,
        mergeChildCount = mergeChildCount,
    )
}

private fun AssembledRecipeVariantIngredientLine.servingsForEditor(): Double {
    servings?.takeIf { it > 0.0 }?.let { return it }

    val gramsValue = grams?.takeIf { it > 0.0 }
    val gramsPerServing = currentGramsForOneServing()

    if (gramsValue != null && gramsPerServing != null && gramsPerServing > 0.0) {
        return gramsValue / gramsPerServing
    }

    return 1.0
}

private fun AssembledRecipeVariantIngredientLine.currentGramsForEditor(): Double? {
    grams?.takeIf { it > 0.0 }?.let { return it }

    val servingsValue = servings?.takeIf { it > 0.0 } ?: return null
    val gramsPerServing = currentGramsForOneServing() ?: return null

    return servingsValue * gramsPerServing
}

private fun AssembledRecipeVariantIngredientLine.currentGramsForOneServing(): Double? {
    lineFoodDirectMassGramsPerServing(food)?.let { return it }

    val gramsPerOneUnit = food.gramsPerServingUnit
    if (gramsPerOneUnit != null && gramsPerOneUnit > 0.0 && food.servingSize > 0.0) {
        return food.servingSize * gramsPerOneUnit
    }

    return null
}

private fun lineFoodDirectMassGramsPerServing(
    food: FoodEntity,
): Double? {
    return food.servingUnit.asG
        ?.takeIf { it > 0.0 }
        ?.let { gramsPerUnit ->
            food.servingSize * gramsPerUnit
        }
}

private fun originalAmountLabel(
    line: AssembledRecipeVariantIngredientLine,
): String {
    return formatVariantAmount(
        servings = line.originalServings,
        grams = line.originalGrams,
        servingSize = line.food.servingSize,
        servingUnitLabel = line.food.servingUnit.display,
    )
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
