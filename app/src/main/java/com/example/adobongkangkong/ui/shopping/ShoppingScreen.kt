package com.example.adobongkangkong.ui.shopping

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.adobongkangkong.R
import com.example.adobongkangkong.feature.camera.FoodImageStorage
import com.example.adobongkangkong.data.local.db.entity.FoodGoalFlagsEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingScreen(
    startDate: LocalDate,
    days: Int,
    onBack: () -> Unit,
    vm: ShoppingViewModel = hiltViewModel()
) {
    LaunchedEffect(startDate, days) {
        vm.setRange(startDate = startDate, days = days)
    }

    val state by vm.state.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.size(32.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(
                    painter = painterResource(R.drawable.angle_circle_left),
                    contentDescription = "Back"
                )
            }

            Spacer(Modifier.width(8.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = "Shopping",
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${state.startDate} • next ${state.days} days",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider()

        if (state.rows.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No planned food needs in this range.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items = state.rows, key = { it.foodId }) { row ->
                    ShoppingFoodRow(row = row)
                }
            }
        }
    }
}

/**
 * “As close as possible” to FoodsList FoodRow styling:
 * - blur banner background
 * - ListItem layout
 * - goal flags strip
 *
 * DIFFERENCE (per your request):
 * - NO clickable modifier / NO tap behavior for now
 */
@Composable
private fun ShoppingFoodRow(
    row: ShoppingRowUiModel
) {
    val context = LocalContext.current
    val storage = remember(context) { FoodImageStorage(context) }

    val blurBitmapState = produceState<Bitmap?>(initialValue = null, key1 = row.foodId) {
        val blurFile = storage.bannerBlurWebpFile(row.foodId)
        value = withContext(Dispatchers.IO) {
            if (blurFile.exists()) BitmapFactory.decodeFile(blurFile.absolutePath) else null
        }
    }

    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        blurBitmapState.value?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
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
                            text = row.amountText,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = row.nextDateText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            trailingContent = {
                Column(
                    modifier = Modifier.padding(top = 2.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Spacer(Modifier.height(2.dp))
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
                modifier = Modifier.size(18.dp)
            )
        }

        if (showEatMore) {
            Icon(
                painter = painterResource(R.drawable.social_network),
                contentDescription = "Eat more",
                modifier = Modifier.size(18.dp)
            )
        }

        if (showLimit) {
            Icon(
                painter = painterResource(R.drawable.triangle_warning),
                contentDescription = "Limit",
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
