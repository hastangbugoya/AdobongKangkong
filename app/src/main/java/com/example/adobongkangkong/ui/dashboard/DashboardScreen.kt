package com.example.adobongkangkong.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlin.math.max

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

