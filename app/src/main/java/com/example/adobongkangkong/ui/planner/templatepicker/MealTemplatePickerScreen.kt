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
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.adobongkangkong.R
import com.example.adobongkangkong.ui.common.template.MealTemplateBannerCardBackground
import kotlinx.coroutines.flow.StateFlow

/**
 * Renders the planner meal template picker.
 *
 * - Shows searchable template rows.
 * - Keeps all nutrition aggregation outside the composable.
 * - Reads already-shaped picker state from [MealTemplatePickerUiState].
 *
 * ## Notes for devs
 * - This screen should remain render-only.
 * - Query filtering belongs in the ViewModel.
 * - Template macro aggregation belongs upstream.
 * - Keep row rendering lightweight for smooth LazyColumn scrolling.
 * - This file intentionally stays close to the original picker implementation because
 *   the meal template list macro work should not silently rewrite picker behavior.
 */
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
                        Text(
                            text = dateIso,
                            style = MaterialTheme.typography.bodySmall
                        )
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

            if (s.rows.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "No templates found.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Create one from a planned meal first (Save as template).",
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
                        items = s.rows,
                        key = { row -> row.id }
                    ) { row ->
                        MealTemplateBannerCardBackground(templateId = row.id) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onEvent(MealTemplatePickerEvent.SelectTemplate(row.id)) },
                                    colors = CardDefaults.cardColors(
                                    containerColor = Color.Transparent
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = row.name,
                                            style = MaterialTheme.typography.titleMedium
                                        )

                                        row.macrosLine?.let { macrosLine ->
                                            Text(
                                                text = macrosLine,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }

                                    Text(
                                        text = "Pick",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Bottom KDoc for future AI assistant.
 *
 * This screen consumes the current picker state contract from the latest uploaded src:
 * - `query`
 * - `rows: List<MealTemplatePickerRowModel>`
 *
 * Each row is already UI-shaped and contains:
 * - `id`
 * - `name`
 * - `macrosLine`
 *
 * Important:
 * - Keep this screen render-only.
 * - Do not move template filtering or macro aggregation here.
 * - If picker/list macro text must be unified later, do that in the shared formatter /
 *   ViewModel mapping layer, not by pushing more logic into this composable.
 */