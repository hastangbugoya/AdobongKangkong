package com.example.adobongkangkong.ui.food

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.adobongkangkong.ui.theme.AppIconSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodsListScreen(
    onBack: () -> Unit,
    onEditFood: (Long) -> Unit,
    onEditRecipe: (Long) -> Unit,
    onCreateFood: () -> Unit,
    onCreateRecipe: () -> Unit,
    onHeaderLongPress: () -> Unit = {},
    /**
     * Optional picker-mode callback.
     *
     * When non-null, tapping a row returns the selected foodId instead of opening the editor.
     * Recipe rows return their recipe foodId through the same callback because recipes are foods in this app.
     */
    onPickFood: ((Long) -> Unit)? = null,
    initialFavoritesOnly: Boolean = false,
    vm: FoodsListViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    val listState = rememberLazyListState()
    val query = state.query
    val favoritesOnly by vm.favoritesOnly.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(initialFavoritesOnly) {
        vm.setFavoritesOnly(initialFavoritesOnly)
    }

    val alphabet = remember { ('A'..'Z').map { it.toString() } }

    fun indexForLetter(letter: String): Int? {
        val target = letter.firstOrNull() ?: return null
        return state.rows.indexOfFirst { row ->
            row.name.trim().firstOrNull()?.uppercaseChar() == target
        }.takeIf { it >= 0 }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Foods",
                        modifier = Modifier.combinedClickable(
                            onClick = {},
                            onLongClick = onHeaderLongPress
                        )
                    )
                },
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .padding(end = if (state.sort.key == FoodSortKey.NAME) 20.dp else 0.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = vm::onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search") },
                    singleLine = true,
                    trailingIcon = {
                        if (query.isNotBlank()) {
                            IconButton(onClick = { vm.onQueryChange("") }) {
                                Icon(
                                    painter = painterResource(
                                        R.drawable.cross_small,
                                    ),
                                    contentDescription = "Clear search",
                                )
                            }
                        }
                    },
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
                    FilterChip(
                        selected = favoritesOnly,
                        onClick = { vm.onFavoritesOnlyChange(!favoritesOnly) },
                        label = { Text("Favorites") }
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = state.selectedCategoryId == null,
                        onClick = { vm.onSelectedCategoryChange(null) },
                        label = { Text("All categories") }
                    )

                    state.categories.forEach { category ->
                        FilterChip(
                            selected = state.selectedCategoryId == category.id,
                            onClick = { vm.onSelectedCategoryChange(category.id) },
                            label = { Text(category.name) }
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                FoodsSortRow(
                    sort = state.sort,
                    onSortKey = vm::onSortKeyChange,
                    onToggleDirection = vm::onSortDirectionToggle
                )

                Spacer(Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState
                ) {
                    items(state.rows, key = { it.foodId }) { row ->
                        FoodRow(
                            row = row,
                            onClick = {
                                if (onPickFood != null) {
                                    onPickFood(row.foodId)
                                } else {
                                    if (row.isRecipe) onEditRecipe(row.foodId) else onEditFood(row.foodId)
                                }
                            }
                        )
                        HorizontalDivider()
                    }

                    item {
                        Text(state.toString(), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (state.sort.key == FoodSortKey.NAME && state.rows.isNotEmpty()) {
                AlphabetIndexRail(
                    letters = alphabet,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 4.dp),
                    onLetterTap = { letter ->
                        val index = indexForLetter(letter) ?: return@AlphabetIndexRail
                        coroutineScope.launch {
                            listState.animateScrollToItem(index)
                        }
                    }
                )
            }
        }
    }

    LaunchedEffect(state.sort.key, state.sort.direction, favoritesOnly) {
        listState.scrollToItem(0)
    }
}

@Composable
private fun AlphabetIndexRail(
    letters: List<String>,
    modifier: Modifier = Modifier,
    onLetterTap: (String) -> Unit
) {
    Surface(
        modifier = modifier,
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            letters.forEach { letter ->
                Text(
                    text = letter,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clickable { onLetterTap(letter) }
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
    }
}

@Composable
private fun FoodsSortRow(
    sort: FoodSortState,
    onSortKey: (FoodSortKey) -> Unit,
    onToggleDirection: () -> Unit,
) {
    val expanded = remember { androidx.compose.runtime.mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Sort:",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(end = 8.dp)
        )

        TextButton(onClick = { expanded.value = true }) {
            Text("${sort.key.label} • ${sort.direction.label}")
        }

        Spacer(Modifier.weight(1f))

        IconButton(onClick = onToggleDirection) {
            Icon(
                painter = painterResource(R.drawable.priority_arrows),
                contentDescription = "Toggle sort direction",
                modifier = Modifier.size(AppIconSize.CardAction)
            )
        }
    }

    DropdownMenu(expanded = expanded.value, onDismissRequest = { expanded.value = false }) {
        FoodSortKey.entries.forEach { key ->
            DropdownMenuItem(
                text = { Text(key.label) },
                onClick = {
                    onSortKey(key)
                    expanded.value = false
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = row.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (row.isMergeFallback) {
                        Icon(
                            painter = painterResource(R.drawable.layers),
                            contentDescription = "Default merge fallback food",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            supportingContent = {
                Column(modifier = Modifier.fillMaxWidth()) {

                    if (!row.fixMessage.isNullOrBlank()) {
                        NeedsFixBanner(
                            message = row.fixMessage,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    Text(
                        text = row.brandText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(6.dp))

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
                    Icon(
                        painter = painterResource(
                            if (row.isRecipe) R.drawable.recipe else R.drawable.salad
                        ),
                        contentDescription = if (row.isRecipe) "Recipe" else "Food",
                        modifier = Modifier.size(AppIconSize.CardAction),
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
private fun NeedsFixBanner(
    message: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            painter = painterResource(R.drawable.triangle_warning),
            contentDescription = "Needs fix",
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(AppIconSize.CardAction)
        )
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun FoodGoalFlagsStrip(flags: FoodGoalFlagsEntity?) {
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
                modifier = Modifier.size(AppIconSize.CardAction),
            )
        }

        if (showEatMore) {
            Icon(
                painter = painterResource(R.drawable.social_network),
                contentDescription = "Eat more",
                modifier = Modifier.size(AppIconSize.CardAction),
            )
        }

        if (showLimit) {
            Icon(
                painter = painterResource(R.drawable.triangle_warning),
                contentDescription = "Limit",
                modifier = Modifier.size(AppIconSize.CardAction),
            )
        }
    }
}