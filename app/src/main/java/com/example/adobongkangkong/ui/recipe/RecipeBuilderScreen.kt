package com.example.adobongkangkong.ui.recipe

import androidx.compose.material3.AssistChip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.adobongkangkong.R
import com.example.adobongkangkong.ui.common.bottomsheet.BlockingBottomSheet
import com.example.adobongkangkong.ui.format.ui
import androidx.compose.foundation.text.KeyboardOptions

/**
 * Recipe builder / editor screen.
 *
 * ## Important architecture points
 *
 * ### Point-of-use enforcement (data integrity)
 * The importer may allow foods with missing grams-per-serving (nullable). That’s OK.
 * We enforce correctness **right when the user tries to use the food by servings**
 * (adding an ingredient in the recipe builder).
 *
 * The ViewModel signals this condition by setting [RecipeBuilderState.blockingSheet].
 * The UI displays a modal blocking sheet that offers a primary action to edit the food.
 *
 * ### Navigation request pattern (Compose-safe)
 * The ViewModel does NOT navigate directly. Instead it sets a one-shot
 * [RecipeBuilderState.navigateToEditFoodId]. The UI consumes it via [LaunchedEffect],
 * calls [onEditFood], then tells the VM the navigation has been handled so it won’t re-trigger.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeBuilderScreen(
    recipeId: Long? = null,
    editFoodId: Long?,
    onBack: () -> Unit,
    onEditFood: (Long) -> Unit
) {
    val vm: RecipeBuilderViewModel = hiltViewModel()
    val state by vm.state.collectAsState()

    // --- Edit-mode load -------------------------------------------------------
    LaunchedEffect(editFoodId) {
        vm.loadForEdit(editFoodId)
    }

    // --- One-shot navigation request -----------------------------------------
    LaunchedEffect(state.navigateToEditFoodId) {
        val id = state.navigateToEditFoodId ?: return@LaunchedEffect
        onEditFood(id)
        vm.onEditFoodNavigationHandled()
    }

    // --- Blocking sheet UI ----------------------------------------------------
    val blockingSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    state.blockingSheet?.let { sheetModel ->
        ModalBottomSheet(
            onDismissRequest = { vm.dismissBlockingSheet() },
            sheetState = blockingSheetState
        ) {
            BlockingBottomSheet(
                model = sheetModel,
                onDismiss = { vm.dismissBlockingSheet() }
            )
        }
    }

    val canSave = !state.isSaving

    // --- Screen UI ------------------------------------------------------------
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editFoodId == null) "New Recipe" else "Edit Recipe") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.angle_circle_left),
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        bottomBar = {
            // Sticky save so it's always reachable when font is large / screen is short.
            Button(
                onClick = { vm.save(onDone = onBack) },
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(if (state.isSaving) "Saving…" else "Save")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = vm::onNameChange,
                    label = { Text("Recipe name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            item {
                OutlinedTextField(
                    value = state.servingsYield.toString(),
                    onValueChange = { raw -> raw.toDoubleOrNull()?.let(vm::onYieldChange) },
                    label = { Text("Servings yield") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }
            state.errorMessage?.let { err ->
                item {
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(onClick = vm::clearError) { Text("Dismiss") }
                }
            }

            item { Spacer(Modifier.height(4.dp)) }
            item { Text("Add ingredient", style = MaterialTheme.typography.titleMedium) }

            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = vm::onQueryChange,
                    label = { Text("Search foods") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            if (state.results.isNotEmpty()) {
                item {
                    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.results.take(8).forEach { food ->
                            androidx.compose.foundation.layout.Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    food.name,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1
                                )
                                TextButton(onClick = { vm.pickFood(food) }) { Text("Pick") }
                            }
                        }
                    }
                }
            }

            state.pickedFood?.let { pickedFood ->
                item {
                    Spacer(Modifier.height(4.dp))
                    Text("Picked: ${pickedFood.name}", style = MaterialTheme.typography.titleSmall)

                    OutlinedTextField(
                        value = state.pickedServingsText,
                        onValueChange = vm::onPickedServingsTextChange,
                        label = { Text("Servings") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.pickedGramsText,
                        onValueChange = vm::onPickedGramsTextChange,
                        label = { Text("Grams") },
                        enabled = (state.pickedFood?.gramsPerServing != null),
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))

//                    state.pickedGrams?.let { grams ->
//                        Text("≈ ${"%,.1f".format(grams)} g", style = MaterialTheme.typography.bodySmall)
//                    }

                    if (pickedFood.servingsPerPackage != null) {
                        Spacer(Modifier.height(8.dp))

                        Text(
                            "${pickedFood.servingsPerPackage.ui()} servings per package",
                            style = MaterialTheme.typography.bodySmall
                        )

                        Spacer(Modifier.height(8.dp))

                        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(
                                onClick = { vm.onPickedPackage(0.5) },
                                label = { Text("½ package") }
                            )
                            AssistChip(
                                onClick = { vm.onPickedPackage(1.0) },
                                label = { Text("1 package") }
                            )
                        }
                    }

                    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = vm::addPickedIngredient, enabled = !state.isSaving) { Text("Add") }
                        TextButton(onClick = vm::clearPickedFood) { Text("Cancel") }
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
            item { Text("Ingredients", style = MaterialTheme.typography.titleMedium) }

            item {
                if (state.ingredients.isEmpty()) {
                    Text("No ingredients yet.")
                } else {
                    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.ingredients.forEachIndexed { index, ing ->
                            androidx.compose.foundation.layout.Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val unitLabel = ing.servingUnitLabel?.trim().orEmpty()
                                val amountText = if (unitLabel.isNotBlank()) {
                                        "${"%,.2f".format(ing.servings)} $unitLabel"
                                    } else {
                                        "${"%,.2f".format(ing.servings)} servings"
                                    }
                                val gramsText = ing.grams?.let { g -> " (≈ ${"%,.1f".format(g)} g)" }.orEmpty()
                                Text(
                                    "${ing.foodName} • $amountText$gramsText",
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2
                                )

                                IconButton(onClick = { vm.removeIngredientAt(index) }) {
                                    Icon(
                                        painterResource(id = R.drawable.trash),
                                        contentDescription = "Remove"
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
            item { Text("Preview", style = MaterialTheme.typography.titleMedium) }

            item {
                Text("Calories: ${"%,.0f".format(state.preview.totalCalories)} kcal")
                Text("Protein: ${"%,.1f".format(state.preview.totalProteinG)} g")
                Text("Carbs: ${"%,.1f".format(state.preview.totalCarbsG)} g")
                Text("Fat: ${"%,.1f".format(state.preview.totalFatG)} g")
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
