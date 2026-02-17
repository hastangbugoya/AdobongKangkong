package com.example.adobongkangkong.ui.shopping

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.adobongkangkong.data.local.db.entity.FoodGoalFlagsEntity
import com.example.adobongkangkong.ui.food.FoodGoalFlagsStrip
import java.time.LocalDate
import com.example.adobongkangkong.R

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
                        Icon(painter = painterResource(R.drawable.angle_circle_left), contentDescription = "Back")
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
            Card {
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
                    }
                    // IMPORTANT: no clickable modifier here (no row tap behavior)
                )
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
            Card {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
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