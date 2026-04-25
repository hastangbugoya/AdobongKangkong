package com.example.adobongkangkong.ui.daylog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.adobongkangkong.R
import com.example.adobongkangkong.data.local.db.entity.MealSlot
import com.example.adobongkangkong.ui.daylog.model.DayLogRow
import com.example.adobongkangkong.ui.log.QuickAddBottomSheet
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayLogScreen(
    date: LocalDate,
    onBack: () -> Unit,
    onCreateFood: (String) -> Unit,
    onCreateFoodWithBarcode: (String) -> Unit = {},
    onOpenFoodEditor: (Long) -> Unit = {},
    onOpenQuickAddFavorites: () -> Unit = {},
    onDelete: ((Long) -> Unit)? = null,
    vm: DayLogViewModel = hiltViewModel(),
    pickedQuickAddFoodId: Long? = null,
    onPickedQuickAddFoodConsumed: () -> Unit = {},
) {
    val entries by vm.entries.collectAsState()
    val ious by vm.ious.collectAsState()
    val totals by vm.totals.collectAsState()
    val message by vm.message.collectAsState()
    val pendingLogAgainTodayConfirmation by vm.pendingLogAgainTodayConfirmation.collectAsState()

    val delete = onDelete ?: { id -> vm.deleteEntry(id) }

    var showQuickAdd by rememberSaveable { mutableStateOf(false) }
    var editingLogId by rememberSaveable { mutableStateOf<Long?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(date) {
        vm.load(date)
    }

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        vm.consumeMessage()
    }

    if (showQuickAdd) {
        QuickAddBottomSheet(
            onDismiss = {
                showQuickAdd = false
                editingLogId = null
            },
            onCreateFood = onCreateFood,
            onCreateFoodWithBarcode = onCreateFoodWithBarcode,
            onOpenFoodEditor = onOpenFoodEditor,
            onOpenFavorites = onOpenQuickAddFavorites,
            logDate = date,
            editingLogId = editingLogId,
            pickedFoodId = pickedQuickAddFoodId,
            onPickedFoodConsumed = onPickedQuickAddFoodConsumed
        )
    }

    pendingLogAgainTodayConfirmation?.let { pending ->
        AlertDialog(
            onDismissRequest = { vm.dismissLogAgainTodayConfirmation() },
            title = { Text("Log again today") },
            text = { Text(pending.message) },
            confirmButton = {
                TextButton(
                    onClick = { vm.confirmLogAgainToday(pending.logId) }
                ) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { vm.dismissLogAgainTodayConfirmation() }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    val groupedEntries = buildDayLogSections(entries)

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        topBar = {
            TopAppBar(
                title = { Text(date.toString()) },
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
                            editingLogId = null
                            showQuickAdd = true
                        }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.add),
                            contentDescription = "Quick add"
                        )
                    }
                }
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {

            totals?.let { DayTotalsCard(it) }

            LazyColumn(
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = 4.dp,
                    bottom = 16.dp
                )
            ) {

                groupedEntries.forEach { section ->

                    item(key = "slot_header_${section.key}") {
                        Text(
                            text = section.title,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                    }

                    items(
                        items = section.rows,
                        key = { it.logId }
                    ) { row ->

                        DayLogRowCard(
                            row = row,
                            onClick = {
                                editingLogId = row.logId
                                showQuickAdd = true
                            },
                            onLogAgainToday = {
                                vm.logAgainToday(row.logId)
                            },
                            onDelete = { delete(row.logId) }
                        )

                        HorizontalDivider()
                    }
                }

                if (ious.isNotEmpty()) {

                    item(key = "iou_header") {
                        Text(
                            text = "IOUs",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                    }

                    items(
                        items = ious,
                        key = { "iou_${it.iouId}" }
                    ) { row ->

                        DayLogIouRowCard(
                            row = row,
                            onDelete = { vm.deleteIou(row.iouId) }
                        )

                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

private data class DayLogSection(
    val key: String,
    val title: String,
    val rows: List<DayLogRow>,
)

private fun buildDayLogSections(
    entries: List<DayLogRow>
): List<DayLogSection> {

    if (entries.isEmpty()) return emptyList()

    val grouped = entries.groupBy { it.mealSlot }
    val sections = mutableListOf<DayLogSection>()

    MealSlot.entries.forEach { slot ->

        val rows = grouped[slot].orEmpty()

        if (rows.isNotEmpty()) {
            sections += DayLogSection(
                key = slot.name,
                title = slot.display,
                rows = rows
            )
        }
    }

    val unslotted = grouped[null].orEmpty()

    if (unslotted.isNotEmpty()) {
        sections += DayLogSection(
            key = "UNSLOTTED",
            title = "Unslotted",
            rows = unslotted
        )
    }

    return sections
}