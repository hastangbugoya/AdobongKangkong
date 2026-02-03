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
import androidx.compose.material3.AssistChip
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.adobongkangkong.R
import com.example.adobongkangkong.data.local.db.entity.FoodGoalFlagsEntity
import com.example.adobongkangkong.domain.model.Food
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
    val context = LocalContext.current
    //to avoid allocating a new FoodImageStorage on each recomposition
    val storage = remember(context) { FoodImageStorage(context) }

    // Load blurred banner derivative (if it exists). This is app-private cache, not Gallery.
    val blurBitmapState = produceState<Bitmap?>(initialValue = null, key1 = food.id) {
        val storage = FoodImageStorage(context)

        val bannerFile = storage.bannerJpegFile(food.id)
        val blurFile = storage.bannerBlurWebpFile(food.id)

        value = withContext(Dispatchers.IO) {
            // If cache was flushed, recreate blur from the master banner.
            if (!blurFile.exists() && bannerFile.exists()) {
                storage.ensureBlurDir(food.id)

                // reuse the same blur algorithm you already use in BannerCaptureSheet
                // (preferably call the same function; if not accessible, copy that function into FoodRow file)
                generateBlurDerivative(
                    inputJpeg = bannerFile,
                    outputWebp = blurFile,
                    webpQuality = 60,
                    downscaleTargetWidthPx = 96
                )
            }

            if (blurFile.exists()) {
                BitmapFactory.decodeFile(blurFile.absolutePath)
            } else {
                null
            }
        }
    }


    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        // Background (subtle) when available
        blurBitmapState.value?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .matchParentSize(),
                // Banner is already 3:1; for safety we still crop.
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                alpha = 0.22f
            )
            // A gentle scrim for text contrast
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.06f))
            )
        }

        ListItem(
            headlineContent = {
                Text(
                    text = food.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            supportingContent = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Brand (if present)
                    if (!food.brand.isNullOrBlank()) {
                        Text(
                            text = food.brand!!,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }

                    // Type chip-like label
                    AssistChip(
                        onClick = { /* no-op */ },
                        label = { Text(if (food.isRecipe) "Recipe" else "Food") },
                        enabled = false
                    )
                }
            },
            trailingContent = {
                // Gives the trailing icons breathing room and keeps them from feeling vertically cramped
                Column(
                    modifier = Modifier.padding(top = 2.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    FoodGoalFlagsStrip(goalFlags)
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


