package com.example.adobongkangkong.ui.food

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.adobongkangkong.data.local.db.entity.FoodGoalFlagsEntity
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.model.FoodGoalFlags
import com.example.adobongkangkong.R
import com.example.adobongkangkong.ui.theme.EatMoreGreen
import com.example.adobongkangkong.ui.theme.FavoriteYellow
import com.example.adobongkangkong.ui.theme.LimitRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodsListScreen(
    onBack: () -> Unit,
    onEditFood: (Long) -> Unit,
    onEditRecipe: (Long) -> Unit,
    onCreateFood: () -> Unit,
    onCreateRecipe: () -> Unit,
    vm: FoodsListViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Foods") },

                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.angle_circle_left),
                            contentDescription = "Back"
                        )
                    }
                },

                actions = {
                    IconButton(
                        onClick = {
                            when (state.filter) {
                                FoodsFilter.FOODS_ONLY -> onCreateFood()
                                FoodsFilter.RECIPES_ONLY -> onCreateRecipe()
                                FoodsFilter.ALL -> onCreateFood()
                            }
                        }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.add),
                            contentDescription = "Add"
                        )
                    }
                }
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
                items(state.items, key = { it.food.id }) { item ->
                    FoodRow(
                        food = item.food,
                        goalFlags = item.goalFlags
                    ) {
                        if (item.food.isRecipe) onEditRecipe(item.food.id) else onEditFood(item.food.id)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun FoodRow(
    food: Food,
    goalFlags: FoodGoalFlagsEntity?,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                food.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            food.brand?.let {
                Text(text = food.brand)
            }
            Text(if (food.isRecipe) "Recipe" else "Food")
        },
        trailingContent = {
            FoodGoalFlagsStrip(goalFlags)
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    )
}

@Composable
internal fun FoodGoalFlagsStrip(flags: FoodGoalFlagsEntity?) {
    if (flags == null) return

    val showFavorite = flags.favorite
    val showEatMore = flags.eatMore
    val showLimit = flags.limit

    if (!showFavorite && !showEatMore && !showLimit) return

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showFavorite) {
            Icon(
                painter = painterResource(R.drawable.star),
                contentDescription = "Favorite",
                modifier = Modifier.size(18.dp),
                tint = FavoriteYellow
            )
        }

        if (showEatMore) {
            Icon(
                painter = painterResource(R.drawable.social_network),
                contentDescription = "Eat more",
                modifier = Modifier.size(18.dp),
                tint = EatMoreGreen,
            )
        }

        if (showLimit) {
            Icon(
                painter = painterResource(R.drawable.triangle_warning),
                contentDescription = "Limit",
                modifier = Modifier.size(18.dp),
                tint = LimitRed
            )
        }
    }
}


