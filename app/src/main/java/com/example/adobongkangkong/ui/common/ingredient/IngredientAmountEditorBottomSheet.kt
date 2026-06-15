package com.example.adobongkangkong.ui.common.ingredient

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.model.toGrams
import com.example.adobongkangkong.domain.model.toMilliliters
import com.example.adobongkangkong.domain.nutrition.gramsPerServingResolved
import com.example.adobongkangkong.ui.food.SelectedFoodPanel
import kotlin.math.max

data class IngredientAmountEditorResult(
    val servings: Double,
    val grams: Double?,
    val preferGrams: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IngredientAmountEditorBottomSheet(
    title: String,
    food: Food,
    initialServings: Double,
    initialGrams: Double?,
    initialPreferGrams: Boolean,
    primaryButtonLabel: String,
    onDismiss: () -> Unit,
    onPrimaryAction: (IngredientAmountEditorResult) -> Unit,
    modifier: Modifier = Modifier,
    originalAmountLabel: String? = null,
    errorMessage: String? = null,
    onEditFoodInEditor: ((Long) -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    normalizedPriceDisplay: String? = null,
    servingPriceDisplay: String? = null,
    ingredientCostDisplay: String? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var servings by rememberSaveable(food.id, initialServings) {
        mutableDoubleStateOf(initialServings.takeIf { it > 0.0 } ?: 1.0)
    }
    var servingUnitAmount by rememberSaveable(food.id, initialServings) {
        mutableDoubleStateOf(servings * food.servingSize)
    }
    var gramsAmount by rememberSaveable(food.id, initialServings, initialGrams) {
        mutableStateOf(initialGrams ?: food.gramsPerServingResolved()?.let { gramsPerServing ->
            servings * gramsPerServing
        })
    }
    var inputUnit by rememberSaveable(food.id) {
        mutableStateOf(ServingUnit.G)
    }
    var inputAmount by rememberSaveable(food.id) {
        mutableStateOf<Double?>(null)
    }
    var preferGrams by rememberSaveable(food.id, initialPreferGrams) {
        mutableStateOf(initialPreferGrams && initialGrams != null)
    }

    LaunchedEffect(servings, food.id) {
        servingUnitAmount = servings * food.servingSize
        if (!preferGrams) {
            gramsAmount = food.gramsPerServingResolved()?.let { gramsPerServing ->
                servings * gramsPerServing
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                )

                Text(
                    text = food.name,
                    style = MaterialTheme.typography.titleMedium,
                )

                food.brand?.takeIf { it.isNotBlank() }?.let { brand ->
                    Text(
                        text = brand,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                originalAmountLabel?.takeIf { it.isNotBlank() }?.let { original ->
                    Text(
                        text = original,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider()

            SelectedFoodPanel(
                food = food,
                servings = servings,
                servingUnitAmount = servingUnitAmount,
                gramsAmount = gramsAmount,
                inputUnit = inputUnit,
                inputAmount = inputAmount,
                errorMessage = errorMessage,
                onBack = onDismiss,
                onServingsChanged = { value ->
                    preferGrams = false
                    servings = max(0.0, value)
                    servingUnitAmount = servings * food.servingSize
                    gramsAmount = food.gramsPerServingResolved()?.let { gramsPerServing ->
                        servings * gramsPerServing
                    }
                },
                onServingUnitAmountChanged = { amount ->
                    preferGrams = false
                    servingUnitAmount = max(0.0, amount)
                    val servingSize = food.servingSize.takeIf { it > 0.0 } ?: 1.0
                    servings = servingUnitAmount / servingSize
                    gramsAmount = food.gramsPerServingResolved()?.let { gramsPerServing ->
                        servings * gramsPerServing
                    }
                },
                onGramsChanged = { grams ->
                    preferGrams = true
                    val safeGrams = max(0.0, grams)
                    gramsAmount = safeGrams
                    val gramsPerServing = food.gramsPerServingResolved()
                    if (gramsPerServing != null && gramsPerServing > 0.0) {
                        servings = safeGrams / gramsPerServing
                        servingUnitAmount = servings * food.servingSize
                    }
                },
                onInputUnitChanged = { unit ->
                    inputUnit = unit
                    val amount = inputAmount
                    if (amount != null && amount > 0.0) {
                        val grams = computeInputGrams(food, amount, unit)
                        if (grams != null) {
                            preferGrams = true
                            gramsAmount = grams
                            val gramsPerServing = food.gramsPerServingResolved()
                            if (gramsPerServing != null && gramsPerServing > 0.0) {
                                servings = grams / gramsPerServing
                                servingUnitAmount = servings * food.servingSize
                            }
                        }
                    }
                },
                onInputAmountChanged = { amount ->
                    inputAmount = amount
                    if (amount != null && amount > 0.0) {
                        val grams = computeInputGrams(food, amount, inputUnit)
                        if (grams != null) {
                            preferGrams = true
                            gramsAmount = grams
                            val gramsPerServing = food.gramsPerServingResolved()
                            if (gramsPerServing != null && gramsPerServing > 0.0) {
                                servings = grams / gramsPerServing
                                servingUnitAmount = servings * food.servingSize
                            }
                        }
                    }
                },
                onPackage = { multiplier ->
                    val servingsPerPackage = food.servingsPerPackage

                    if (servingsPerPackage != null && servingsPerPackage > 0.0) {
                        preferGrams = false
                        servings = (servingsPerPackage * multiplier).coerceAtLeast(0.0)
                        servingUnitAmount = servings * food.servingSize
                        gramsAmount = food.gramsPerServingResolved()?.let { gramsPerServing ->
                            servings * gramsPerServing
                        }
                    }
                },
                onEditFoodInEditor = {
                    onEditFoodInEditor?.invoke(food.id)
                },
                primaryButtonLabel = primaryButtonLabel,
                onPrimaryAction = {
                    onPrimaryAction(
                        IngredientAmountEditorResult(
                            servings = servings,
                            grams = gramsAmount,
                            preferGrams = preferGrams && gramsAmount != null,
                        )
                    )
                },
                normalizedPriceDisplay = normalizedPriceDisplay,
                servingPriceDisplay = servingPriceDisplay,
                ingredientCostDisplay = ingredientCostDisplay,
            )

            if (onRemove != null) {
                Spacer(Modifier.height(4.dp))

                OutlinedButton(
                    onClick = onRemove,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Remove")
                }
            }
        }
    }
}

private fun computeInputGrams(
    food: Food,
    amount: Double,
    unit: ServingUnit,
): Double? {
    val safeAmount = amount.coerceAtLeast(0.0)

    unit.toGrams(safeAmount)?.let { grams ->
        return grams
    }

    val inputMl = unit.toMilliliters(safeAmount) ?: return null
    val gramsPerServing = food.gramsPerServingResolved() ?: return null
    val mlPerServing = food.millilitersPerCurrentServingResolved() ?: return null

    if (gramsPerServing <= 0.0 || mlPerServing <= 0.0) {
        return null
    }

    val densityGPerMl = gramsPerServing / mlPerServing
    return inputMl * densityGPerMl
}

private fun Food.millilitersPerCurrentServingResolved(): Double? {
    val directVolume = servingUnit.toMilliliters(servingSize)
    if (directVolume != null && directVolume > 0.0) {
        return directVolume
    }

    val mlPerOneUnit = mlPerServingUnit
    if (mlPerOneUnit != null && mlPerOneUnit > 0.0 && servingSize > 0.0) {
        return servingSize * mlPerOneUnit
    }

    return null
}
