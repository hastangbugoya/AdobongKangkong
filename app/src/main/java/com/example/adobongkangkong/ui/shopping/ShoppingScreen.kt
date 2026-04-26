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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shopping") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                items(
                    items = activeItems,
                    key = { it.foodId }
                ) { item ->
                    ShoppingRow(
                        item = item,
                        onCheckedChange = { checked ->
                            vm.onSimpleItemCheckedChanged(item.foodId, checked)
                        }
                    )
                }

                if (completedItems.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Completed",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    items(
                        items = completedItems,
                        key = { it.foodId }
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