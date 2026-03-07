package com.example.adobongkangkong.ui.meal.editor

import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.adobongkangkong.R
import com.example.adobongkangkong.feature.camera.BannerOwnerRef
import com.example.adobongkangkong.feature.camera.FoodImageStorage
import com.example.adobongkangkong.ui.camera.BannerCaptureController

/**
 * Shared editor screen for both Planned meals and Templates.
 *
 * For developers:
 * - Navigation stays outside this composable.
 * - Template-only actions are injected through [extraActions].
 * - Banner support is optional so planned-meal editor continues to work without banner ownership.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealEditorScreen(
    contract: MealEditorContract,
    onBack: () -> Unit,
    onRequestAddFood: () -> Unit,
    bannerCaptureController: BannerCaptureController? = null,
    bannerOwner: BannerOwnerRef? = null,
    bannerRefreshTick: Int = 0,
    @DrawableRes bannerPlaceholderResId: Int = R.drawable.recipe_banner,
    bannerChangeLabel: String = "Change banner",
    extraActions: (@Composable () -> Unit)? = null
) {
    val state = contract.state.collectAsState().value

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.errorMessage) {
        val msg = state.errorMessage?.trim().orEmpty()
        if (msg.isNotBlank()) snackbarHostState.showSnackbar(message = msg)
    }

    val title = when (state.mode) {
        MealEditorMode.PLANNED -> if (state.mealId == null) "New Planned Meal" else "Edit Planned Meal"
        MealEditorMode.TEMPLATE -> "Edit Meal Template"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painter = painterResource(R.drawable.angle_circle_left), contentDescription = "Back")
                    }
                },
                actions = {
                    extraActions?.invoke()
                    if (state.isSaving) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.height(18.dp))
                        }
                    }
                    Button(
                        onClick = { contract.save() },
                        enabled = state.canSave && !state.isSaving
                    ) {
                        Text("Save")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onRequestAddFood,
                content = { Icon(painter = painterResource(R.drawable.add), contentDescription = "Add food") }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (bannerCaptureController != null) {
                MealEditorBannerSection(
                    bannerOwner = bannerOwner,
                    bannerRefreshTick = bannerRefreshTick,
                    bannerPlaceholderResId = bannerPlaceholderResId,
                    bannerChangeLabel = bannerChangeLabel,
                    bannerCaptureController = bannerCaptureController
                )
            }

            MealEditorHeader(
                state = state,
                onNameChanged = contract::setName,
                onTemplateDefaultSlotChanged = contract::setTemplateDefaultSlot
            )

            Divider()

            if (state.items.isEmpty()) {
                EmptyState(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    onAddFood = onRequestAddFood
                )
                return@Column
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                itemsIndexed(
                    items = state.items,
                    key = { _, item -> item.lineId }
                ) { index, item ->
                    MealEditorItemRow(
                        item = item,
                        onServingsChanged = { text -> contract.updateServings(item.lineId, text) },
                        onGramsChanged = { text -> contract.updateGrams(item.lineId, text) },
                        onMillilitersChanged = { text -> contract.updateMilliliters(item.lineId, text) },
                        onRemove = { contract.removeItem(item.lineId) },
                        onMoveUp = if (index > 0) ({ contract.moveItem(index, index - 1) }) else null,
                        onMoveDown = if (index < state.items.lastIndex) ({ contract.moveItem(index, index + 1) }) else null
                    )
                }
                item { Spacer(modifier = Modifier.height(84.dp)) }
            }
        }
    }
}

@Composable
private fun MealEditorBannerSection(
    bannerOwner: BannerOwnerRef?,
    bannerRefreshTick: Int,
    @DrawableRes bannerPlaceholderResId: Int,
    bannerChangeLabel: String,
    bannerCaptureController: BannerCaptureController
) {
    val context = LocalContext.current
    val storage = remember(context) { FoodImageStorage(context) }
    val canCaptureBanner = bannerOwner != null

    val bannerFile = remember(bannerOwner) {
        bannerOwner?.let { storage.bannerJpegFile(it) }
    }
    val hasStoredBanner = bannerFile?.exists() == true

    val bannerBitmapState = if (bannerOwner != null) {
        produceState<android.graphics.Bitmap?>(
            initialValue = null,
            key1 = bannerOwner,
            key2 = bannerRefreshTick
        ) {
            val file = bannerFile
            value = if (file != null && file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
        }
    } else null

    val bmp = bannerBitmapState?.value

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(20.dp))
                .aspectRatio(3f / 1f)
        ) {
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    alignment = Alignment.Center,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Image(
                    painter = painterResource(bannerPlaceholderResId),
                    contentDescription = null,
                    alignment = Alignment.Center,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop
                )
            }

            if (canCaptureBanner) {
                IconButton(
                    onClick = { bannerOwner?.let { bannerCaptureController.open(it) } },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                ) {
                    Icon(
                        painter = painterResource(android.R.drawable.ic_menu_camera),
                        contentDescription = bannerChangeLabel
                    )
                }
            }

            if (hasStoredBanner && bmp == null) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).size(50.dp),
                    strokeWidth = 2.dp
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                enabled = canCaptureBanner,
                onClick = { bannerOwner?.let { bannerCaptureController.open(it) } },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(text = bannerChangeLabel, style = MaterialTheme.typography.labelSmall)
            }
        }

        if (!canCaptureBanner) {
            Text(
                text = "Save first to enable banner image.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            )
        }
    }
}

@Composable
private fun EmptyState(
    modifier: Modifier = Modifier,
    onAddFood: () -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "No items yet.", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Add a food to start building this meal.", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(onClick = onAddFood) { Text("Add food") }
    }
}

/**
 * Bottom KDoc for future AI assistant.
 *
 * This shared screen intentionally accepts optional banner/action hooks so template-specific
 * behavior can be injected without forking the entire editor UI.
 */
