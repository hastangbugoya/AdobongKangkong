package com.example.adobongkangkong.ui.planner.templatepicker

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.adobongkangkong.R
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealTemplatePickerScreen(
    state: StateFlow<MealTemplatePickerUiState>,
    onEvent: (MealTemplatePickerEvent) -> Unit,
    dateIso: String
) {
    val s by state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Pick Template")
                        Text(dateIso, style = MaterialTheme.typography.bodySmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { onEvent(MealTemplatePickerEvent.Back) }) {
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
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextField(
                value = s.query,
                onValueChange = { onEvent(MealTemplatePickerEvent.UpdateQuery(it)) },
                label = { Text("Search templates") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            if (s.templates.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("No templates found.", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Create one from a planned meal first (Save as template).",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = s.templates,
                        key = { it.id }
                    ) { t ->
                        val macros = s.macrosByTemplateId[t.id]
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEvent(MealTemplatePickerEvent.SelectTemplate(t.id)) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    // Names, not slots.
                                    Text(t.name, style = MaterialTheme.typography.titleMedium)

                                    if (macros != null) {
                                        Text(
                                            macrosLine(macros),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                                Text("Pick", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun macrosLine(m: com.example.adobongkangkong.domain.model.MacroTotals): String {
    val kcal = m.caloriesKcal.roundToInt()
    val p = m.proteinG.roundToInt()
    val c = m.carbsG.roundToInt()
    val f = m.fatG.roundToInt()
    return "$kcal kcal • P $p • C $c • F $f"
}
