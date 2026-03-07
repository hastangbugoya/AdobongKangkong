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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.adobongkangkong.R
import com.example.adobongkangkong.ui.common.template.MealTemplateBannerCardBackground
import kotlinx.coroutines.flow.StateFlow

/**
 * Renders the planner meal template picker.
 *
 * Renders the template library/browse screen.
 *
 * Relationship to Template Picker:
 * - This screen and MealTemplatePickerScreen intentionally present nearly the same
 *   template-card content:
 *   - banner
 *   - macro summary
 *   - default meal slot
 *   - food preview/details text
 * - The flows differ in purpose (library management vs template selection), but
 *   card-content changes should usually be reviewed in both places.
 *
 * Maintenance rule:
 * - If you change template-card fields, formatting, or shared detail-building logic,
 *   also inspect:
 *   - MealTemplatePickerScreen
 *   - MealTemplatePickerViewModel
 *   - any shared template detail/card helpers
 *
 * Do not assume a change is list-only unless the difference is intentionally
 * screen-specific.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealTemplatePickerScreen(
    state: StateFlow<MealTemplatePickerUiState>,
    onEvent: (MealTemplatePickerEvent) -> Unit,
    dateIso: String,
    slotContextLabel: String? = null
) {
    val s by state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Pick Template")
                        Text(dateIso, style = MaterialTheme.typography.bodySmall)
                        slotContextLabel?.takeIf { it.isNotBlank() }?.let {
                            Text("For $it", style = MaterialTheme.typography.bodySmall)
                        }
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
                        items = s.rows,
                        key = { it.id }
                    ) { row ->
                        MealTemplateBannerCardBackground(templateId = row.id) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onEvent(MealTemplatePickerEvent.SelectTemplate(row.id)) }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = row.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val meta = buildList {
                                        if (row.itemCount > 0) {
                                            add(if (row.itemCount == 1) "1 item" else "${row.itemCount} items")
                                        }
                                        row.defaultSlotLabel
                                            ?.takeIf { it.isNotBlank() }
                                            ?.let { add(it) }
                                    }.joinToString(" • ")
                                    if (meta.isNotBlank()) {
                                        Text(
                                            text = meta,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    row.foodPreviewLine?.takeIf { it.isNotBlank() }?.let { preview ->
                                        Text(
                                            text = preview,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    row.macrosLine?.takeIf { it.isNotBlank() }?.let { macroLine ->
                                        Text(
                                            text = macroLine,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
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

/**
 * Bottom KDoc for future AI assistant.
 *
 * Picker rows are render-only. Banner presence is resolved by [MealTemplateBannerCardBackground],
 * while default-slot labels, preview text, and macro text must already be prepared in picker state.
 */
