package com.example.adobongkangkong.ui.recipe

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.nutrition.gramsPerServingResolved
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeBuilderScreen(
    onBack: () -> Unit,
    vm: RecipeBuilderViewModel = hiltViewModel()
) {
    val focus = LocalFocusManager.current
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Recipe") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        },
        bottomBar = {
            // Sticky action
            Surface(tonalElevation = 2.dp) {
                Column(Modifier.navigationBarsPadding()) {
                    HorizontalDivider()
                    Button(
                        onClick = { vm.save(onDone = onBack) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        enabled = !state.isSaving
                    ) {
                        Text(if (state.isSaving) "Saving…" else "Save recipe")
                    }
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Scrollable content area
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 16.dp, bottom = 16.dp)
            ) {
                // --- everything except the Save button goes here ---

                if (state.errorMessage != null) {
                    AssistChip(
                        onClick = vm::clearError,
                        label = { Text(state.errorMessage!!) }
                    )
                    Spacer(Modifier.height(8.dp))
                }

                OutlinedTextField(
                    value = state.name,
                    onValueChange = vm::onNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Recipe name") },
                    singleLine = true
                )

                Spacer(Modifier.height(10.dp))

                NumberField(
                    label = "Servings yield (batch)",
                    value = state.servingsYield,
                    onValue = vm::onYieldChange
                )

                Spacer(Modifier.height(16.dp))

                Text("Ingredients", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))

                IngredientsList(
                    items = state.ingredients,
                    onRemove = vm::removeIngredientAt
                )

                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                Text("Add ingredient", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = state.query,
                    onValueChange = vm::onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search foods") },
                    singleLine = true
                )

                Spacer(Modifier.height(8.dp))

                if (state.pickedFood == null) {
                    FoodSearchResults(
                        results = state.results,
                        onPick = { vm.pickFood(it) }
                    )
                } else {
                    PickedIngredientPanel(
                        food = state.pickedFood!!,
                        servings = state.pickedServings,
                        grams = state.pickedGrams,
                        onChangeFood = vm::clearPickedFood,
                        onServingsChange = vm::onPickedServingsChange,
                        onGramsChange = vm::onPickedGramsChange,
                        onAdd = vm::addPickedIngredient
                    )
                }
            }
        }
    }

}

@Composable
private fun IngredientsList(
    items: List<RecipeIngredientUi>,
    onRemove: (Int) -> Unit
) {
    if (items.isEmpty()) {
        Text("No ingredients yet.", style = MaterialTheme.typography.bodyMedium)
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 220.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        itemsIndexed(items) { index, item ->
            ListItem(
                headlineContent = {
                    Text(item.foodName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                supportingContent = {
                    Text("${item.servings.clean()} servings")
                },
                trailingContent = {
                    TextButton(onClick = { onRemove(index) }) { Text("Remove") }
                }
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun FoodSearchResults(
    results: List<Food>,
    onPick: (Food) -> Unit
) {
    if (results.isEmpty()) {
        Text("Type to search…", style = MaterialTheme.typography.bodyMedium)
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 260.dp),
        contentPadding = PaddingValues(vertical = 6.dp)
    ) {
        items(items = results, key = { it.id }) { food ->
            ListItem(
                headlineContent = { Text(food.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = {
                    val grams = food.gramsPerServingResolved()
                    val subtitle = buildString {
                        append("${food.servingSize.clean()} ${food.servingUnit.display}")
                        if (grams != null && food.servingUnit.display != "g") append(" (${grams.clean()} g)")
                    }
                    Text(subtitle)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(food) }
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun PickedIngredientPanel(
    food: Food,
    servings: Double,
    grams: Double?,
    onChangeFood: () -> Unit,
    onServingsChange: (Double) -> Unit,
    onGramsChange: (Double) -> Unit,
    onAdd: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text(food.name, style = MaterialTheme.typography.titleSmall)
            Text("${food.servingSize.clean()} ${food.servingUnit.display}", style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = onChangeFood) { Text("Change") }
    }

    Spacer(Modifier.height(10.dp))

    AmountRow(
        label = "Servings",
        value = servings,
        onMinus = { onServingsChange(max(0.0, servings - 0.5)) },
        onPlus = { onServingsChange(servings + 0.5) }
    )

    Spacer(Modifier.height(10.dp))

    // Optional grams input if we can resolve grams per serving (including gram-unit foods)
    if (food.gramsPerServingResolved() != null) {
        NumberField(
            label = "Grams (g)",
            value = grams ?: (servings * food.gramsPerServingResolved()!!),
            onValue = onGramsChange
        )
        Spacer(Modifier.height(10.dp))
    }

    Button(
        onClick = onAdd,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Add ingredient")
    }
}

@Composable
private fun AmountRow(
    label: String,
    value: Double,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onMinus) { Text("–") }
            Text(value.clean(), style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = onPlus) { Text("+") }
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: Double,
    onValue: (Double) -> Unit
) {
    var text by remember(value) { mutableStateOf(value.clean()) }

    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            it.toDoubleOrNull()?.let(onValue)
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true
    )
}

private fun Double.clean(): String =
    if (this % 1.0 == 0.0) this.toInt().toString() else "%,.2f".format(this).trimEnd('0').trimEnd('.')

