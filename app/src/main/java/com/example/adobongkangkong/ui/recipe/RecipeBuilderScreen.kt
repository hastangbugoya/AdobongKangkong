package com.example.adobongkangkong.ui.recipe

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.adobongkangkong.ui.common.bottomsheet.BlockingBottomSheet

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
    /**
     * The sheet content is fully driven by the ViewModel via [BlockingSheetModel].
     * This keeps the UI simple: render model, provide only dismissal wiring.
     */
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

    // --- Screen UI ------------------------------------------------------------
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editFoodId == null) "New Recipe" else "Edit Recipe") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                },
                actions = {
                    TextButton(
                        onClick = { vm.save(onDone = onBack) },
                        enabled = !state.isSaving
                    ) { Text("Save") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = vm::onNameChange,
                label = { Text("Recipe name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = state.servingsYield.toString(),
                onValueChange = { raw -> raw.toDoubleOrNull()?.let(vm::onYieldChange) },
                label = { Text("Servings yield") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )

            state.errorMessage?.let { err ->
                Text(
                    text = err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                TextButton(onClick = vm::clearError) { Text("Dismiss") }
            }

            Spacer(Modifier.height(4.dp))
            Text("Add ingredient", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = state.query,
                onValueChange = vm::onQueryChange,
                label = { Text("Search foods") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            if (state.results.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.results.take(8).forEach { food ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(food.name, modifier = Modifier.weight(1f))
                            TextButton(onClick = { vm.pickFood(food) }) { Text("Pick") }
                        }
                    }
                }
            }

            state.pickedFood?.let { picked ->
                Spacer(Modifier.height(4.dp))
                Text("Picked: ${picked.name}", style = MaterialTheme.typography.titleSmall)

                OutlinedTextField(
                    value = state.pickedServingsText,
                    onValueChange ={ vm::onPickedServingsChange },
                    label = { Text("Servings") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                state.pickedGrams?.let { grams ->
                    Text("≈ ${"%,.1f".format(grams)} g", style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.width(8.dp))
                state.pickedFood?.servingsPerPackage?.let { spp ->
                    Text("$spp servings per package", style = MaterialTheme.typography.bodySmall)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = vm::addPickedIngredient, enabled = !state.isSaving) { Text("Add") }
                    TextButton(onClick = vm::clearPickedFood) { Text("Cancel") }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("Ingredients", style = MaterialTheme.typography.titleMedium)

            if (state.ingredients.isEmpty()) {
                Text("No ingredients yet.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.ingredients.forEachIndexed { index, ing ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${ing.foodName} • ${"%,.2f".format(ing.servings)} servings")
                            IconButton(onClick = { vm.removeIngredientAt(index) }) {
                                Icon(Icons.Default.Close, contentDescription = "Remove")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("Preview", style = MaterialTheme.typography.titleMedium)
            Text("Calories: ${"%,.0f".format(state.preview.totalCalories)} kcal")
            Text("Protein: ${"%,.1f".format(state.preview.totalProteinG)} g")
            Text("Carbs: ${"%,.1f".format(state.preview.totalCarbsG)} g")
            Text("Fat: ${"%,.1f".format(state.preview.totalFatG)} g")

            Spacer(Modifier.height(16.dp))
        }
    }
}
