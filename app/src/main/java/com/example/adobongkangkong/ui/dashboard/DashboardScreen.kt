package com.example.adobongkangkong.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.max

private val ERROR_LOG_ID = -1L

@Composable
fun DashboardScreen(
    modifier: Modifier  = Modifier,
    vm: DashboardViewModel = hiltViewModel()
) {
    val state = vm.state.collectAsState().value
    val totals = state.totals
    val targets = state.targets

    Column(Modifier.fillMaxSize().padding(16.dp)) {
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
                            Text("${formatTime(item.timestamp)} • ${item.servings.round2()} servings • ${item.caloriesKcal.round0()} kcal")
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
}

@Composable
private fun MacroRow(label: String, value: Double, target: Double, unit: String) {
    val safeTarget = max(target, 1.0)
    val progress = (value / safeTarget).coerceIn(0.0, 1.0).toFloat()

    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
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

private fun Double.round0(): String = "%,.0f".format(this)
private fun Double.round2(): String = "%,.2f".format(this).trimEnd('0').trimEnd('.')