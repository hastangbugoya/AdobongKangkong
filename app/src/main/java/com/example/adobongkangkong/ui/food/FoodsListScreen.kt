package com.example.adobongkangkong.ui.food

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.adobongkangkong.domain.model.Food

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodsListScreen(
    onBack: () -> Unit,
    onEditFood: (Long) -> Unit,
    onEditRecipe: (Long) -> Unit,
    onCreateFood: () -> Unit,
    vm: FoodsListViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Foods") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = { TextButton(onClick = onCreateFood) { Text("New") } }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search") },
                singleLine = true
            )

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.filter == FoodsFilter.ALL,
                    onClick = { vm.onFilterChange(FoodsFilter.ALL) },
                    label = { Text("All") }
                )
                FilterChip(
                    selected = state.filter == FoodsFilter.FOODS_ONLY,
                    onClick = { vm.onFilterChange(FoodsFilter.FOODS_ONLY) },
                    label = { Text("Foods") }
                )
                FilterChip(
                    selected = state.filter == FoodsFilter.RECIPES_ONLY,
                    onClick = { vm.onFilterChange(FoodsFilter.RECIPES_ONLY) },
                    label = { Text("Recipes") }
                )
            }

            Spacer(Modifier.height(12.dp))

            LazyColumn(Modifier.fillMaxSize()) {
                items(state.items, key = { it.id }) { food ->
                    FoodRow(food) {
                        if (food.isRecipe) onEditRecipe(food.id) else onEditFood(food.id)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun FoodRow(food: Food, onClick: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(food.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(if (food.isRecipe) "Recipe" else "Food")
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    )
}

