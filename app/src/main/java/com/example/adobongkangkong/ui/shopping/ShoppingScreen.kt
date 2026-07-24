package com.example.adobongkangkong.ui.shopping

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.adobongkangkong.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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

    val startDateLabel = remember(startDate) {
        startDate.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy"))
    }

    val activeItems = remember(state.simpleItems) {
        state.simpleItems.filter { !it.isChecked }
    }

    val completedItems = remember(state.simpleItems) {
        state.simpleItems.filter { it.isChecked }
    }

    val pinnedItems = remember(state.pinnedItems) {
        state.pinnedItems.sortedWith(
            compareBy<ShoppingPinnedItemUi> { it.isChecked }
                .thenBy { it.name.lowercase() }
                .thenBy { it.id }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shopping") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ms_arrow_back),
                            contentDescription = "Back"
                        )
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
            Text(
                text = "Needs as of $startDateLabel",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = state.daysText,
                onValueChange = vm::onDaysTextChanged,
                label = { Text("Next N days") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            ShoppingUnitModeToggle(
                selectedMode = state.unitMode,
                onModeSelected = vm::onShoppingUnitModeChanged
            )

            Spacer(Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (activeItems.isNotEmpty()) {
                    item {
                        Text(
                            text = "From plan",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                items(
                    items = activeItems,
                    key = { "plan-${it.foodId}" }
                ) { item ->
                    ShoppingRow(
                        item = item,
                        onCheckedChange = { checked ->
                            vm.onSimpleItemCheckedChanged(item.foodId, checked)
                        }
                    )
                }

                item {
                    Spacer(Modifier.height(12.dp))
                    AlwaysOnListHeader(
                        onAdd = vm::openAddPinnedItemDialog
                    )
                }

                if (pinnedItems.isEmpty()) {
                    item {
                        Text(
                            text = "Add staples like soy sauce, coffee, eggs, or paper towels. These stay here until you delete them.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }

                items(
                    items = pinnedItems,
                    key = { "pinned-${it.id}" }
                ) { item ->
                    PinnedShoppingRow(
                        item = item,
                        onCheckedChange = { checked ->
                            vm.onPinnedItemCheckedChanged(item.id, checked)
                        },
                        onEdit = { vm.openEditPinnedItemDialog(item) },
                        onDelete = { vm.deletePinnedItem(item.id) }
                    )
                }

                if (completedItems.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Completed from plan",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    items(
                        items = completedItems,
                        key = { "completed-${it.foodId}" }
                    ) { item ->
                        ShoppingRow(
                            item = item,
                            onCheckedChange = { checked ->
                                vm.onSimpleItemCheckedChanged(item.foodId, checked)
                            }
                        )
                    }
                }
            }
        }
    }

    PinnedGroceryItemDialog(
        editor = state.pinnedEditor,
        onNameChange = vm::onPinnedItemNameChanged,
        onQuantityChange = vm::onPinnedItemQuantityChanged,
        onDismiss = vm::dismissPinnedItemDialog,
        onSave = vm::savePinnedItem
    )
}

@Composable
private fun AlwaysOnListHeader(
    onAdd: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Always on list",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "Manual staples. Checked items stay until deleted.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        TextButton(onClick = onAdd) {
            Text("Add")
        }
    }
}

@Composable
private fun PinnedShoppingRow(
    item: ShoppingPinnedItemUi,
    onCheckedChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Checkbox(
            checked = item.isChecked,
            onCheckedChange = onCheckedChange
        )

        Spacer(Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge
            )

            item.quantityText?.let { quantity ->
                Text(
                    text = quantity,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onEdit) {
                    Text("Edit")
                }
                TextButton(onClick = onDelete) {
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun PinnedGroceryItemDialog(
    editor: ShoppingPinnedItemEditorUi,
    onNameChange: (String) -> Unit,
    onQuantityChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    if (!editor.isOpen) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (editor.isEditing) "Edit always-on item" else "Add always-on item")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = editor.nameText,
                    onValueChange = onNameChange,
                    label = { Text("Item name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = editor.errorMessage != null
                )

                OutlinedTextField(
                    value = editor.quantityText,
                    onValueChange = onQuantityChange,
                    label = { Text("Quantity text optional") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                editor.errorMessage?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ShoppingUnitModeToggle(
    selectedMode: ShoppingUnitMode,
    onModeSelected: (ShoppingUnitMode) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (selectedMode == ShoppingUnitMode.METRIC) {
            Button(
                onClick = { onModeSelected(ShoppingUnitMode.METRIC) },
                modifier = Modifier.weight(1f)
            ) {
                Text("Metric")
            }
        } else {
            OutlinedButton(
                onClick = { onModeSelected(ShoppingUnitMode.METRIC) },
                modifier = Modifier.weight(1f)
            ) {
                Text("Metric")
            }
        }

        if (selectedMode == ShoppingUnitMode.GROCERY) {
            Button(
                onClick = { onModeSelected(ShoppingUnitMode.GROCERY) },
                modifier = Modifier.weight(1f)
            ) {
                Text("Grocery")
            }
        } else {
            OutlinedButton(
                onClick = { onModeSelected(ShoppingUnitMode.GROCERY) },
                modifier = Modifier.weight(1f)
            ) {
                Text("Grocery")
            }
        }
    }
}

@Composable
private fun ShoppingRow(
    item: ShoppingListItemUi,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Checkbox(
            checked = item.isChecked,
            onCheckedChange = onCheckedChange
        )

        Spacer(Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge
            )

            item.amountText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            val secondaryParts = listOfNotNull(
                item.firstNeededDateText,
                item.estimatedCostText
            )

            if (secondaryParts.isNotEmpty()) {
                Text(
                    text = secondaryParts.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}