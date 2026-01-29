package com.example.adobongkangkong.ui.dashboard

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.adobongkangkong.ui.common.bottomsheet.BlockingBottomSheet
import com.example.adobongkangkong.ui.log.QuickAddBottomSheet
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.max
import androidx.compose.ui.res.painterResource
import com.example.adobongkangkong.R



@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    onEditFood: (foodID: Long) -> Unit,
    onCreateRecipe: () -> Unit,
    onCreateFood: (String) -> Unit,
    onOpenFoods: () -> Unit,
) {
    val vm: DashboardViewModel = hiltViewModel()
    val state by vm.state.collectAsState()

    var showQuickAdd by remember { mutableStateOf(false) }
    val blockingSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarMsg by vm.snackbar.collectAsState()

    val context = LocalContext.current
    var showDevTransferSheet by remember { mutableStateOf(false) }

    val targetDraft by vm.targetDraft.collectAsState()

    val exportLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/zip")
        ) { uri: Uri? ->
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    vm.exportTo(out)
                } ?: run {
                    // keep it simple: VM snackbar handles messaging
                    // but we can also show a local snackbar if you want
                }
            }
        }

    val importLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            if (uri != null) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    vm.importFrom(input)
                }
            }
        }

    // One-shot navigation request
    LaunchedEffect(state.navigateToEditFoodId) {
        val id = state.navigateToEditFoodId ?: return@LaunchedEffect
        onEditFood(id)
        vm.onEditFoodNavigationHandled()
    }

    LaunchedEffect(snackbarMsg) {
        val msg = snackbarMsg ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        vm.snackbarShown()
    }

    // Blocking sheet (shown when CreateLogEntryUseCase.Result.Blocked)
    state.blockingSheet?.let { sheetModel ->
        ModalBottomSheet(
            onDismissRequest = { vm.dismissBlockingSheet() },
            sheetState = blockingSheetState
        ) {
            // IMPORTANT: BlockingBottomSheet calls model.onPrimary internally
            BlockingBottomSheet(
                model = sheetModel,
                onDismiss = { vm.dismissBlockingSheet() }
            )
        }
    }

    val devSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (showDevTransferSheet) {
        ModalBottomSheet(
            onDismissRequest = { showDevTransferSheet = false },
            sheetState = devSheetState
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Data Transfer", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))

                ListItem(
                    headlineContent = { Text("Export foods + recipes") },
                    supportingContent = { Text("Writes a ZIP you can import later.") },
                    modifier = Modifier.clickable {
                        showDevTransferSheet = false
                        exportLauncher.launch("adobongkangkong_export.zip")
                    }
                )
                HorizontalDivider()

                ListItem(
                    headlineContent = { Text("Import foods + recipes") },
                    supportingContent = { Text("Imports a ZIP created by this app.") },
                    modifier = Modifier.clickable {
                        showDevTransferSheet = false
                        importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                    }
                )

                Spacer(Modifier.height(16.dp))
            }
        }
    }

//    val targetsByCode = state.targetsByCode
//
//    val caloriesTarget = targetsByCode["calories_kcal"]?.targetPerDay ?: 2000.0
//    val proteinTarget = targetsByCode["protein_g"]?.targetPerDay ?: 150.0
//    val carbsTarget = targetsByCode["carbs_g"]?.targetPerDay ?: 200.0
//    val fatTarget = targetsByCode["fat_g"]?.targetPerDay ?: 77.0

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "AdobongKangkong",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.combinedClickable(
                            onClick = {},
                            onLongClick = { showDevTransferSheet = true }
                        )
                    )
                },
                actions = {
                    IconButton(onClick = vm::onSettingsClicked) {
                        Icon(painter = painterResource(id = R.drawable.settings), contentDescription = "Dashboard settings")
                    }
                }
            )


        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showQuickAdd = true }) { Text("+") }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(onClick = onCreateRecipe) { Text("Recipes") }
                TextButton(onClick = { onCreateFood("") }) { Text("New Food") }
                TextButton(onClick = onOpenFoods) { Text("Foods") }
            }
            Text(
                "Today",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.combinedClickable(
                    onClick = {},
                    onLongClick = { vm.devSyncNutrients() }
                )
            )
            Spacer(Modifier.height(16.dp))


            state.nutrientCards.forEach { card ->
                Log.d("Meow", "NutrientCard ${card.displayName} > max:${card.maxPerDay} min:${card.minPerDay} target:${card.targetPerDay}")
                DashboardNutrientCardRow(card)
            }

            Spacer(Modifier.height(16.dp))
            Text("Logged Today", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            if (state.todayItems.isEmpty()) {
                Text("Nothing logged yet.", style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(state.todayItems, key = { it.logId }) { item ->
                        ListItem(
                            headlineContent = {
                                Text(item.itemName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = {
                                Text(
                                    "${formatTime(item.timestamp)} • " +
                                            "${item.caloriesKcal.round0()} kcal"
                                )
                            },
                            trailingContent = {
                                IconButton(onClick = { vm.delete(item.logId) }) {
                                    Icon(painterResource(id = R.drawable.trash), contentDescription = "Delete")
                                }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }

            if (state.settingsSheetOpen) {
                val settingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

                ModalBottomSheet(
                    onDismissRequest = { vm.onDismissSettingsSheet() },
                    sheetState = settingsSheetState
                ) {
                    DashboardSettingsSheet(
                        pinnedKeys = state.pinnedKeys,
                        monitoredCards = state.nutrientCards,
                        targetDraft = targetDraft,

                        onDismiss = vm::onDismissSettingsSheet,

                        onApplyPins = vm::applyPinnedCodes,

                        onStartTargetEdit = vm::startTargetEdit,
                        onDraftMinChange = vm::updateTargetDraftMin,
                        onDraftTargetChange = vm::updateTargetDraftTarget,
                        onDraftMaxChange = vm::updateTargetDraftMax,
                        onCancelTargetEdit = vm::cancelTargetEdit,
                        onSaveTargetDraft = vm::saveTargetDraft,

                        onSync = vm::devSyncNutrients,
                        onExport = { exportLauncher.launch("adobongkangkong_export.zip") },
                        onImport = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }
                    )
                }
            }



        }

        if (showQuickAdd) {
            QuickAddBottomSheet(
                onDismiss = { showQuickAdd = false },
                onCreateFood = { query ->
                    showQuickAdd = false
                    onCreateFood(query)
                }
            )
        }
    }
}

@Composable
private fun MacroRow(label: String, value: Double, target: Double, unit: String) {
    val safeTarget = max(target, 1.0)
    val progress = (value / safeTarget).coerceIn(0.0, 1.0).toFloat()

    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text("${value.round1()} / ${target.round1()} $unit", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
    }
}

private fun Double.round1(): String = "%,.1f".format(this)

private fun formatTime(instant: java.time.Instant): String {
    val zdt = ZonedDateTime.ofInstant(instant, ZoneId.systemDefault())
    return zdt.format(DateTimeFormatter.ofPattern("h:mm a"))
}

internal fun Double.round0(): String = "%,.0f".format(this)
internal fun Double.round2(): String = "%,.2f".format(this).trimEnd('0').trimEnd('.')

@Composable
private fun DashboardNutrientCardRow(
    card: com.example.adobongkangkong.domain.trend.model.DashboardNutrientCard
) {
    val unit = card.unit.orEmpty()

    val display = remember(card) { card.toDisplay(unit) }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(card.displayName, style = MaterialTheme.typography.titleMedium)
            Text(display.rightText, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(progress = display.progress, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(4.dp))
        Text(
            text = "Status: ${card.status.name}",
            style = MaterialTheme.typography.bodySmall
        )

        card.rollingAverage?.let { avg ->
            Text(
                text = "Avg: ${avg.round1()} $unit",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private data class NutrientCardDisplay(
    val rightText: String,
    val progress: Float
)

private fun com.example.adobongkangkong.domain.trend.model.DashboardNutrientCard.toDisplay(
    unit: String
): NutrientCardDisplay {
    val consumed = consumedToday

    val target = targetPerDay
    val min = minPerDay
    val max = maxPerDay

    // Priority: target > min > max (matches your UX expectation)
    return when {
        target != null && target > 0.0 -> {
            val p = (consumed / target).coerceIn(0.0, 1.0).toFloat()
            NutrientCardDisplay(
                rightText = "${consumed.round1()} / ${target.round1()} $unit",
                progress = p
            )
        }

        min != null && min > 0.0 -> {
            // Below min -> fill up toward 100%. At/above min -> cap at 100%.
            val p = (consumed / min).coerceIn(0.0, 1.0).toFloat()
            NutrientCardDisplay(
                rightText = "${consumed.round1()} / ≥${min.round1()} $unit",
                progress = p
            )
        }

        max != null && max > 0.0 -> {
            // Under max -> progress approaches 100%. Over max -> show full (you already convey HIGH via status).
            val p = (consumed / max).coerceIn(0.0, 1.0).toFloat()
            NutrientCardDisplay(
                rightText = "${consumed.round1()} / ≤${max.round1()} $unit",
                progress = p
            )
        }

        else -> {
            NutrientCardDisplay(
                rightText = "${consumed.round1()} $unit",
                progress = 0f
            )
        }
    }
}
