package com.example.adobongkangkong.ui.shopping

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.adobongkangkong.R
import com.example.adobongkangkong.data.local.db.entity.FoodGoalFlagsEntity
import com.example.adobongkangkong.ui.common.food.FoodBannerCardBackground
import com.example.adobongkangkong.ui.food.FoodGoalFlagsStrip
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private enum class ShoppingTab { TOTALLED, NOT_TOTALLED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingScreen(
    startDate: LocalDate,
    onBack: () -> Unit,
    vm: ShoppingViewModel = hiltViewModel()
) {
    LaunchedEffect(startDate) {
        vm.setStartDate(startDate)
    }

    val state by vm.state.collectAsState()
    var tab by remember { mutableStateOf(ShoppingTab.TOTALLED) }
    val startDateLabel = remember(startDate) {
        startDate.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy"))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shopping") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.angle_circle_left),
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(12.dp)
        ) {
            Text(
                text = "Needs as of $startDateLabel",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = state.daysText,
                onValueChange = vm::onDaysTextChanged,
                label = { Text("Next N days") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            TabRow(selectedTabIndex = if (tab == ShoppingTab.TOTALLED) 0 else 1) {
                Tab(
                    selected = tab == ShoppingTab.TOTALLED,
                    onClick = { tab = ShoppingTab.TOTALLED },
                    text = { Text("Totalled") }
                )
                Tab(
                    selected = tab == ShoppingTab.NOT_TOTALLED,
                    onClick = { tab = ShoppingTab.NOT_TOTALLED },
                    text = { Text("Not totalled") }
                )
            }

            Spacer(Modifier.height(12.dp))

            when (tab) {
                ShoppingTab.TOTALLED -> TotalledTab(
                    recipeGroups = state.recipeTotalledGroups,
                    foodRows = state.totalledRows,
                    flagsByFoodId = state.flagsByFoodId
                )

                ShoppingTab.NOT_TOTALLED -> NotTotalledTab(
                    recipeGroups = state.recipeNotTotalledGroups,
                    foodGroups = state.notTotalledGroups,
                    flagsByFoodId = state.flagsByFoodId
                )
            }
        }
    }
}

@Composable
private fun TotalledTab(
    recipeGroups: List<ShoppingRecipeTotalGroupUi>,
    foodRows: List<ShoppingTotalRowUi>,
    flagsByFoodId: Map<Long, FoodGoalFlagsEntity>
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(recipeGroups, key = { "recipe_${it.recipeFoodId}" }) { group ->
            RecipeTotalledCard(group = group, flagsByFoodId = flagsByFoodId)
        }

        items(foodRows, key = { "food_${it.foodId}" }) { row ->
            FoodBannerCardBackground(foodId = row.foodId) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = row.foodName,
                                style = MaterialTheme.typography.titleMedium
                            )
                            row.earliestNextPlannedDateText?.let {
                                Text(
                                    text = "Next: $it",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            row.gramsText?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            row.mlText?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            row.unconvertedServingsText?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            row.avgPricePer100gText?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            row.avgPricePer100mlText?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            row.estimatedCostText?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        FoodGoalFlagsStrip(flagsByFoodId[row.foodId])
                    }
                }
            }
        }
    }
}

@Composable
private fun NotTotalledTab(
    recipeGroups: List<ShoppingRecipeOccurrenceGroupUi>,
    foodGroups: List<ShoppingNeedsGroupUi>,
    flagsByFoodId: Map<Long, FoodGoalFlagsEntity>
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(recipeGroups, key = { "recipe_${it.recipeFoodId}_${it.dateText}_${it.recipeName}" }) { group ->
            RecipeOccurrenceCard(group = group, flagsByFoodId = flagsByFoodId)
        }

        items(foodGroups, key = { "food_${it.foodId}" }) { g ->
            FoodBannerCardBackground(foodId = g.foodId) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = g.foodName,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                g.earliestDateText?.let {
                                    Text(
                                        text = "Earliest: $it",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            FoodGoalFlagsStrip(flagsByFoodId[g.foodId])
                        }

                        Spacer(Modifier.height(8.dp))

                        g.rows.forEach { r ->
                            Text(
                                text = "${r.dateText} • g=${r.gramsText} • s=${r.servingsText}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecipeTotalledCard(
    group: ShoppingRecipeTotalGroupUi,
    flagsByFoodId: Map<Long, FoodGoalFlagsEntity>
) {
    FoodBannerCardBackground(foodId = group.recipeFoodId) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = group.recipeName,
                            style = MaterialTheme.typography.titleMedium
                        )
                        group.nextDateText?.let {
                            Text(
                                text = "Next: $it",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text(
                            text = group.servingsText,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = group.batchesText,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    FoodGoalFlagsStrip(flagsByFoodId[group.recipeFoodId])
                }

                if (group.ingredients.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                }

                group.ingredients.forEach { ingredient ->
                    RecipeIngredientRow(ingredient = ingredient)
                }
            }
        }
    }
}

@Composable
private fun RecipeOccurrenceCard(
    group: ShoppingRecipeOccurrenceGroupUi,
    flagsByFoodId: Map<Long, FoodGoalFlagsEntity>
) {
    FoodBannerCardBackground(foodId = group.recipeFoodId) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = group.recipeName,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = group.dateText,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = group.servingsText,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = group.batchesText,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    FoodGoalFlagsStrip(flagsByFoodId[group.recipeFoodId])
                }

                if (group.ingredients.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                }

                group.ingredients.forEach { ingredient ->
                    RecipeIngredientRow(ingredient = ingredient)
                }
            }
        }
    }
}

@Composable
private fun RecipeIngredientRow(
    ingredient: ShoppingRecipeIngredientRowUi
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = ingredient.foodName,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (ingredient.duplicateIconRes != null) {
                    Spacer(Modifier.padding(start = 6.dp))
                    Icon(
                        painter = painterResource(ingredient.duplicateIconRes),
                        contentDescription = "Ingredient appears in multiple recipes",
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            ingredient.avgPriceText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            ingredient.estimatedCostText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Text(
            text = ingredient.amountText,
            style = MaterialTheme.typography.bodySmall
        )
    }
}