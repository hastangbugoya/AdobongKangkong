package com.example.adobongkangkong.ui.shopping

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.adobongkangkong.R
import com.example.adobongkangkong.data.local.db.entity.FoodGoalFlagsEntity
import com.example.adobongkangkong.feature.camera.FoodImageStorage
import com.example.adobongkangkong.ui.camera.generateBlurDerivative
import com.example.adobongkangkong.ui.food.FoodGoalFlagsStrip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

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
                    rows = state.totalledRows,
                    flagsByFoodId = state.flagsByFoodId
                )

                ShoppingTab.NOT_TOTALLED -> NotTotalledTab(
                    groups = state.notTotalledGroups,
                    flagsByFoodId = state.flagsByFoodId
                )
            }
        }
    }
}

@Composable
private fun TotalledTab(
    rows: List<ShoppingTotalRowUi>,
    flagsByFoodId: Map<Long, FoodGoalFlagsEntity>
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(rows, key = { it.foodId }) { row ->
            FoodBannerCardBackground(foodId = row.foodId) {
                // IMPORTANT: Card must be transparent or it will paint over the blur background.
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    ListItem(
                        headlineContent = { Text(row.foodName) },
                        supportingContent = {
                            Column {
                                row.earliestNextPlannedDateText?.let { Text("Next: $it") }
                                row.gramsText?.let { Text(it) }
                                row.mlText?.let { Text(it) }
                                row.unconvertedServingsText?.let { Text(it) }
                            }
                        },
                        trailingContent = {
                            FoodGoalFlagsStrip(flagsByFoodId[row.foodId])
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        // IMPORTANT: no clickable modifier here (no row tap behavior)
                    )
                }
            }
        }
    }
}

@Composable
private fun NotTotalledTab(
    groups: List<ShoppingNeedsGroupUi>,
    flagsByFoodId: Map<Long, FoodGoalFlagsEntity>
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(groups, key = { it.foodId }) { g ->
            FoodBannerCardBackground(foodId = g.foodId) {
                // IMPORTANT: Card must be transparent or it will paint over the blur background.
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(g.foodName, style = MaterialTheme.typography.titleMedium)
                                g.earliestDateText?.let {
                                    Text("Earliest: $it", style = MaterialTheme.typography.bodySmall)
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

/**
 * FoodBannerCardBackground
 *
 * UI-only reusable background wrapper for list rows/cards that should display the banner blur
 * background (if available), matching FoodsListScreen → FoodRow().
 *
 * Notes:
 * - Master banner: filesDir/food_images/{foodId}/banner.jpg
 * - Blur derivative: cacheDir/food_images/{foodId}/banner_blur.webp (safe to regenerate)
 * - This wrapper draws the blur + scrim behind [content].
 *
 * Important:
 * - The child Card/ListItem must use containerColor = Color.Transparent for the blur to be visible.
 */
@Composable
fun FoodBannerCardBackground(
    foodId: Long,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val storage = remember(context) { FoodImageStorage(context) }

    val blurBitmapState = produceState<Bitmap?>(initialValue = null, key1 = foodId) {
        val bannerFile = storage.bannerJpegFile(foodId)
        val blurFile = storage.bannerBlurWebpFile(foodId)

        value = withContext(Dispatchers.IO) {
            if (!blurFile.exists() && bannerFile.exists()) {
                storage.ensureBlurDir(foodId)
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

    Box(modifier = modifier.fillMaxWidth()) {
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

        content()
    }
}