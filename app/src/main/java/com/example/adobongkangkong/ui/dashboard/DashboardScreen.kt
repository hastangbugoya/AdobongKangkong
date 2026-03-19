package com.example.adobongkangkong.ui.dashboard

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.adobongkangkong.R
import com.example.adobongkangkong.domain.trend.model.DashboardNutrientCard
import com.example.adobongkangkong.domain.trend.model.TargetStatus
import com.example.adobongkangkong.ui.common.bottomsheet.BlockingBottomSheet
import com.example.adobongkangkong.ui.log.QuickAddBottomSheet
import com.example.adobongkangkong.ui.theme.AppIconSize
import com.example.adobongkangkong.ui.theme.EatMoreGreen
import com.example.adobongkangkong.ui.theme.LimitRed
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onEditFood: (foodID: Long) -> Unit,
    onCreateRecipe: () -> Unit,
    onCreateFood: (String) -> Unit,
    onOpenFoods: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenDayLog: (LocalDate) -> Unit = {},
    onOpenMeowLogs: () -> Unit,
    onOpenPlanner: () -> Unit,
    initialDate: LocalDate? = null,
    onOpenBackup: () -> Unit,
    onCreateFoodWithBarcode: (String) -> Unit,
    onOpenQuickAddFavorites: () -> Unit = {},
    pickedQuickAddFoodId: Long? = null,
    onPickedQuickAddFoodConsumed: () -> Unit = {},
    showBackButton: Boolean = false,
    onBack: () -> Unit = {},
) {
    val vm: DashboardViewModel = hiltViewModel()
    val state by vm.state.collectAsState()

    var showQuickAdd by rememberSaveable { mutableStateOf(false) }
    val blockingSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarMsg by vm.snackbar.collectAsState()

    var showDevTransferSheet by remember { mutableStateOf(false) }

    val targetDraft by vm.targetDraft.collectAsState()
    val pinOptions by vm.pinOptions.collectAsState()

    val selectedDate = state.date
    val today = remember { LocalDate.now() }
    val isToday = selectedDate == today

    val exportLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/zip")
        ) { uri: Uri? ->
            if (uri != null) {
                vm.exportTo(uri)
            }
        }

    val importLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            if (uri != null) {
                vm.onImportZipPicked(uri)
            }
        }

    LaunchedEffect(initialDate) {
        initialDate?.let { vm.setDate(it) }
    }

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

    LaunchedEffect(pickedQuickAddFoodId) {
        if (pickedQuickAddFoodId != null) {
            showQuickAdd = true
        }
    }

    state.blockingSheet?.let { sheetModel ->
        ModalBottomSheet(
            onDismissRequest = { vm.dismissBlockingSheet() },
            sheetState = blockingSheetState
        ) {
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

                Spacer(Modifier.height(2.dp))
            }
        }
    }

    val settingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    if (state.settingsSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { vm.onDismissSettingsSheet() },
            sheetState = settingsSheetState
        ) {
            DashboardSettingsSheet(
                pinnedKeys = state.pinnedKeys,
                monitoredCards = state.nutrientCards,
                targetDraft = targetDraft,
                pinOptions = pinOptions,
                nutrientPreferences = vm.nutrientPreferences.collectAsState().value,
                onDismiss = vm::onDismissSettingsSheet,
                onApplyPins = vm::applyPinnedCodes,
                onPinnedChange = vm::setPinnedFromSettings,
                onCriticalChange = vm::setCriticalFromSettings,
                onDraftMinChange = vm::updateTargetDraftMin,
                onDraftTargetChange = vm::updateTargetDraftTarget,
                onDraftMaxChange = vm::updateTargetDraftMax,
                onCancelTargetEdit = vm::cancelTargetEdit,
                onSaveTargetDraft = vm::saveTargetDraft,
                onStartTargetEditPrefilled = vm::startTargetEditPrefilled,
                onSync = vm::devSyncNutrients,
                onExport = { exportLauncher.launch("adobongkangkong_export.zip") },
                onImport = {
                    importLauncher.launch(
                        arrayOf(
                            "application/zip",
                            "application/octet-stream"
                        )
                    )
                },
                onOpenMeowLogs = onOpenMeowLogs,
                onOpenPlanner = onOpenPlanner,
                onOpenBackup = onOpenBackup,
                onDebugReset = vm::runDebugReset
            )
        }
    }

    if (showQuickAdd) {
        QuickAddBottomSheet(
            onDismiss = { showQuickAdd = false },
            onCreateFood = onCreateFood,
            onCreateFoodWithBarcode = onCreateFoodWithBarcode,
            onOpenFoodEditor = { foodId ->
                onEditFood(foodId)
            },
            onOpenFavorites = {
                onOpenQuickAddFavorites()
            },
            logDate = state.date,
            pickedFoodId = pickedQuickAddFoodId
        )

        LaunchedEffect(pickedQuickAddFoodId, showQuickAdd) {
            if (showQuickAdd && pickedQuickAddFoodId != null) {
                onPickedQuickAddFoodConsumed()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(R.drawable.angle_circle_left),
                                contentDescription = "Back"
                            )
                        }
                    }
                },
                title = {
                    Text(
                        text = "Adobong Kangkong",
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
                        Icon(
                            painter = painterResource(id = R.drawable.settings),
                            contentDescription = "Dashboard settings"
                        )
                    }
                }
            )
        },
        bottomBar = {
            DashboardBottomActionBar(
                onOpenFoods = onOpenFoods,
                onQuickAdd = { showQuickAdd = true },
                onOpenCalendar = onOpenCalendar
            )
        }
    ) { padding ->
        val showTopNavButtons = false

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = 32.dp
            ),
        ) {
            if (showTopNavButtons) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TextButton(onClick = onCreateRecipe) { Text("Recipes") }
                        TextButton(onClick = { onCreateFood("") }) { Text("New Food") }
                        TextButton(onClick = onOpenFoods) { Text("Foods") }
                        TextButton(onClick = onOpenCalendar) { Text("Calendar") }
                    }
                }
            }

            item {
                val formatter = DateTimeFormatter.ofPattern(
                    "EEE, MMM/d/yyyy",
                    Locale.getDefault()
                )

                val dateLabel = state.date.format(formatter)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = vm::showPreviousDay) {
                        Icon(
                            painter = painterResource(R.drawable.angle_small_left),
                            contentDescription = "Previous day",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = if (isToday) "Today" else dateLabel,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    IconButton(
                        onClick = vm::showNextDay,
                        enabled = state.date < LocalDate.now()
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.angle_small_right),
                            contentDescription = "Next day",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            items(
                items = state.nutrientCards,
                key = { it.code }
            ) { card ->
                DashboardNutrientCardRow(card)
            }

            item {
                val itemCount = state.todayItems.size
                val summaryText = if (itemCount == 0) {
                    "No items logged today"
                } else {
                    "$itemCount items logged today"
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = summaryText,
                        style = MaterialTheme.typography.titleMedium
                    )

                    IconButton(
                        onClick = { onOpenDayLog(state.date) }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.overview),
                            contentDescription = "Open day log"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardBottomActionBar(
    onOpenFoods: () -> Unit,
    onQuickAdd: () -> Unit,
    onOpenCalendar: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onOpenFoods,
                modifier = Modifier.weight(1f))
            {
                Icon(
                    painter = painterResource(R.drawable.list),
                    contentDescription = "Open Foods",
                )
            }
            IconButton(
                onClick = onQuickAdd,
                modifier = Modifier.weight(1f))
            {
                Icon(
                    painter = painterResource(R.drawable.add),
                    contentDescription = "Quick Log",
                )
            }
            IconButton(
                onClick = onOpenCalendar,
                modifier = Modifier.weight(1f))
            {
                Icon(
                    painter = painterResource(R.drawable.calendar_days),
                    contentDescription = "Open Calendar",
                )
            }
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

private fun formatTime(instant: Instant): String {
    val zdt = ZonedDateTime.ofInstant(instant, ZoneId.systemDefault())
    return zdt.format(DateTimeFormatter.ofPattern("h:mm a"))
}

internal fun Double.round0(): String = "%,.0f".format(this)
internal fun Double.round2(): String = "%,.2f".format(this).trimEnd('0').trimEnd('.')

@Composable
private fun DashboardNutrientCardRow(
    card: DashboardNutrientCard
) {
    val unit = card.unit.orEmpty()
    val display = remember(card) { card.toDisplay(unit) }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                card.displayName,
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(Modifier.weight(1f))

            Text(
                display.rightText,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.width(6.dp))

            val icon = when (card.status) {
                TargetStatus.OK -> R.drawable.check_circle__1_
                TargetStatus.LOW -> R.drawable.exclamation
                TargetStatus.HIGH -> R.drawable.exclamation
                TargetStatus.NO_TARGET -> R.drawable.empty_set
            }

            val tint = when (card.status) {
                TargetStatus.OK -> EatMoreGreen
                TargetStatus.LOW, TargetStatus.HIGH -> LimitRed
                TargetStatus.NO_TARGET -> LocalContentColor.current
            }

            Icon(
                painter = painterResource(icon),
                contentDescription = card.status.name,
                tint = tint,
                modifier = Modifier.size(AppIconSize.Inline),
            )
        }

        Spacer(Modifier.height(6.dp))

        LinearProgressIndicator(
            progress = display.progress,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Status: ${card.status.name}",
                style = MaterialTheme.typography.bodySmall
            )

            card.iouEstimate?.takeIf { it > 0.0 }?.let { iou ->
                Text(
                    text = "IOU: +${iou.round1()} $unit",
                    style = MaterialTheme.typography.bodySmall,
                    color = LimitRed
                )
            }
        }

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

private fun DashboardNutrientCard.toDisplay(
    unit: String
): NutrientCardDisplay {
    val consumed = consumedToday

    val target = targetPerDay
    val min = minPerDay
    val max = maxPerDay

    return when {
        target != null && target > 0.0 -> {
            val p = (consumed / target).coerceIn(0.0, 1.0).toFloat()
            NutrientCardDisplay(
                rightText = "${consumed.round1()} / ${target.round1()} $unit",
                progress = p
            )
        }

        min != null && min > 0.0 -> {
            val p = (consumed / min).coerceIn(0.0, 1.0).toFloat()
            NutrientCardDisplay(
                rightText = "${consumed.round1()} / ≥${min.round1()} $unit",
                progress = p
            )
        }

        max != null && max > 0.0 -> {
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
