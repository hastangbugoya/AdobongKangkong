package com.example.adobongkangkong.ui.food

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.adobongkangkong.R
import com.example.adobongkangkong.data.local.db.entity.FoodGoalFlagsEntity
import com.example.adobongkangkong.feature.camera.FoodImageStorage
import com.example.adobongkangkong.ui.camera.generateBlurDerivative
import com.example.adobongkangkong.ui.theme.EatMoreGreen
import com.example.adobongkangkong.ui.theme.FavoriteYellow
import com.example.adobongkangkong.ui.theme.LimitRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    val listState = rememberLazyListState()

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

            Spacer(Modifier.height(8.dp))

            // Sort menu lives here (after the filter row).
            FoodsSortRow(
                sort = state.sort,
                onSortKey = vm::onSortKeyChange,
                onToggleDirection = vm::onSortDirectionToggle
            )

            Spacer(Modifier.height(10.dp))

            LazyColumn(
                Modifier.fillMaxSize(),
                state = listState
            ) {
                items(state.rows, key = { it.foodId }) { row ->
                    FoodRow(
                        row = row,
                        onClick = {
                            if (row.isRecipe) onEditRecipe(row.foodId) else onEditFood(row.foodId)
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
    LaunchedEffect(state.sort.key, state.sort.direction) {
        listState.scrollToItem(0)
    }
}

@Composable
private fun FoodsSortRow(
    sort: FoodSortState,
    onSortKey: (FoodSortKey) -> Unit,
    onToggleDirection: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Sort:",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(end = 8.dp)
        )

        TextButton(onClick = { expanded = true }) {
            Text("${sort.key.label} • ${sort.direction.label}")
        }

        Spacer(Modifier.weight(1f))

        IconButton(onClick = onToggleDirection) {
            Icon(
                painter = painterResource(R.drawable.priority_arrows), // replace if needed
                contentDescription = "Toggle sort direction"
            )
        }
    }

    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        FoodSortKey.entries.forEach { key ->
            DropdownMenuItem(
                text = { Text(key.label) },
                onClick = {
                    onSortKey(key)
                    expanded = false
                }
            )
        }
    }
}

@Composable
private fun FoodRow(
    row: FoodsListRowUiModel,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val storage = remember(context) { FoodImageStorage(context) }

    val blurBitmapState = produceState<Bitmap?>(initialValue = null, key1 = row.foodId) {
        val bannerFile = storage.bannerJpegFile(row.foodId)
        val blurFile = storage.bannerBlurWebpFile(row.foodId)

        value = withContext(Dispatchers.IO) {
            if (!blurFile.exists() && bannerFile.exists()) {
                storage.ensureBlurDir(row.foodId)
                generateBlurDerivative(
                    inputJpeg = bannerFile,
                    outputWebp = blurFile,
                    webpQuality = 60,
                    downscaleTargetWidthPx = 96
                )
            }

            if (blurFile.exists()) BitmapFactory.decodeFile(blurFile.absolutePath) else null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        blurBitmapState.value?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.22f
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.06f))
            )
        }

        ListItem(
            headlineContent = {
                Text(
                    text = row.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            supportingContent = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Brand must always be visible (search candidate).
                    Text(
                        text = row.brandText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(6.dp))

                    // Calories per serving always visible; optional extra metric shown only for macro sort keys.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = row.caloriesPerServingText,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1
                        )

                        row.extraMetricText?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            },
            trailingContent = {
                Column(
                    modifier = Modifier.padding(top = 2.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    // Optional compact type icon when cramped (recipe vs food)
                    Icon(
                        painter = painterResource(
                            if (row.isRecipe) R.drawable.recipe else R.drawable.salad // replace with your icons
                        ),
                        contentDescription = if (row.isRecipe) "Recipe" else "Food",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(8.dp))

                    FoodGoalFlagsStrip(row.goalFlags)
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.fillMaxWidth()
        )
    }
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
//                tint = FavoriteYellow
            )
        }

        if (showEatMore) {
            Icon(
                painter = painterResource(R.drawable.social_network),
                contentDescription = "Eat more",
                modifier = Modifier.size(18.dp),
//                tint = EatMoreGreen,
            )
        }

        if (showLimit) {
            Icon(
                painter = painterResource(R.drawable.triangle_warning),
                contentDescription = "Limit",
                modifier = Modifier.size(18.dp),
//                tint = LimitRed
            )
        }
    }
}
