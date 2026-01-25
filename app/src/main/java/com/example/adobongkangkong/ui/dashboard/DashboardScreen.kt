package com.example.adobongkangkong.ui.dashboard

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.adobongkangkong.ui.common.bottomsheet.BlockingBottomSheet
import com.example.adobongkangkong.ui.log.QuickAddBottomSheet
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
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

    // One-shot navigation request
    LaunchedEffect(state.navigateToEditFoodId) {
        val id = state.navigateToEditFoodId ?: return@LaunchedEffect
        onEditFood(id)
        vm.onEditFoodNavigationHandled()
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

    val totals = state.totals
    val targets = state.targets

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AdobongKangkong") },
                actions = {
                    TextButton(onClick = onCreateRecipe) { Text("Recipes") }
                    TextButton(onClick = { onCreateFood("") }) { Text("New Food") }
                    TextButton(onClick = onOpenFoods) { Text("Foods") }
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
            Text("Today", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))

            MacroRow("Calories", totals.caloriesKcal, targets.caloriesKcal, "kcal")
            MacroRow("Protein", totals.proteinG, targets.proteinG, "g")
            MacroRow("Carbs", totals.carbsG, targets.carbsG, "g")
            MacroRow("Fat", totals.fatG, targets.fatG, "g")

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
                                Text(item.foodName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = {
                                Text(
                                    "${formatTime(item.timestamp)} • " +
                                            "${item.servings.round2()} servings • " +
                                            "${item.caloriesKcal.round0()} kcal"
                                )
                            },
                            trailingContent = {
                                IconButton(onClick = { vm.delete(item.logId) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete")
                                }
                            }
                        )
                        HorizontalDivider()
                    }
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
