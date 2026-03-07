package com.example.adobongkangkong.ui.templates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.adobongkangkong.R
import com.example.adobongkangkong.ui.common.template.MealTemplateBannerCardBackground

/**
 * Meal template library screen.
 *
 * ## For developers
 * This screen intentionally stays visually close to the existing flat-list pattern used elsewhere
 * in the app:
 * - search field
 * - sort row
 * - flat clickable rows
 * - divider between rows
 *
 * The row now shows compact macro summary text using the same formatter as the template picker.
 * No macro computation occurs here; this composable only renders ready-to-display row state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealTemplateListScreen(
    onBack: () -> Unit,
    onOpenTemplate: (Long) -> Unit,
    onCreateTemplate: () -> Unit,
    vm: MealTemplateListViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Meal templates") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.angle_circle_left),
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onCreateTemplate) {
                        Text("Template editor")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search templates") },
                singleLine = true
            )

            Spacer(Modifier.height(10.dp))

            MealTemplateSortRow(
                sort = state.sort,
                onSortSelected = vm::onSortChange
            )

            Spacer(Modifier.height(10.dp))

            if (state.rows.isEmpty()) {
                EmptyTemplateListState(
                    modifier = Modifier.fillMaxSize(),
                    onCreateTemplate = onCreateTemplate
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(
                        items = state.rows,
                        key = { it.id }
                    ) { row ->
                        MealTemplateRow(
                            row = row,
                            onClick = { onOpenTemplate(row.id) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

/** Sort selector row for the meal template list. */
@Composable
private fun MealTemplateSortRow(
    sort: MealTemplateListSort,
    onSortSelected: (MealTemplateListSort) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Sort:",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(end = 8.dp)
        )

        TextButton(onClick = { expanded = true }) {
            Text(sort.label)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            MealTemplateListSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        expanded = false
                        onSortSelected(option)
                    }
                )
            }
        }
    }
}

/**
 * Single template list row.
 *
 * The background uses the template banner treatment, while text content remains flat and list-like.
 */
@Composable
private fun MealTemplateRow(
    row: MealTemplateListRow,
    onClick: () -> Unit
) {
    MealTemplateBannerCardBackground(templateId = row.id) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(2.dp))

                val meta = buildList {
                    add(if (row.itemCount == 1) "1 item" else "${row.itemCount} items")
                    row.defaultSlotLabel
                        ?.takeIf { it.isNotBlank() }
                        ?.let { add(it) }
                }.joinToString(" • ")

                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(Modifier.height(2.dp))

                Text(
                    text = row.macroSummaryLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = "Open",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/** Empty state shown when the user has no templates or no search matches. */
@Composable
private fun EmptyTemplateListState(
    modifier: Modifier = Modifier,
    onCreateTemplate: () -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No meal templates yet.",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Create one from the template editor, or save a planned meal as a template.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onCreateTemplate) {
            Text("Open template editor")
        }
    }
}

/**
 * Bottom KDoc for future AI assistant.
 *
 * This screen expects [MealTemplateListRow.macroSummaryLine] to already be computed. Do not add
 * nutrition aggregation or string formatting work inside [MealTemplateRow]; keep LazyColumn rows
 * render-only.
 *
 * If future sorting by macros is introduced, prefer updating the ViewModel/state layer instead of
 * adding sort logic to this composable.
 */
