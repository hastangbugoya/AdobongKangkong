package com.example.adobongkangkong.ui.meal.editor

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
import androidx.compose.foundation.lazy.itemsIndexed
//import androidx.compose.material.icons.Icons
//import androidx.compose.material.icons.filled.Add
//import androidx.compose.material.icons.filled.ArrowDownward
//import androidx.compose.material.icons.filled.ArrowUpward
//import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.adobongkangkong.R

/**
 * Shared editor screen for both Planned meals and Templates.
 *
 * Navigation note:
 * - This screen does NOT know how to pick a food.
 * - Use [onRequestAddFood] to navigate to your food picker/search UI.
 * - When the user picks a food, call: contract.addFood(foodId)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealEditorScreen(
    contract: MealEditorContract,
    onBack: () -> Unit,
    onRequestAddFood: () -> Unit
) {
    val state = contract.state.collectAsState().value

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        val msg = state.errorMessage?.trim().orEmpty()
        if (msg.isNotBlank()) snackbarHostState.showSnackbar(message = msg)
    }

    val title = when (state.mode) {
        MealEditorMode.PLANNED -> "Edit Planned Meal"
        MealEditorMode.TEMPLATE -> "Edit Meal Template"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painter = painterResource(R.drawable.angle_circle_left), contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.isSaving) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.height(18.dp))
                        }
                    }
                    Button(
                        onClick = { contract.save() },
                        enabled = state.canSave && !state.isSaving
                    ) {
                        Text("Save")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onRequestAddFood,
                content = { Icon(painter = painterResource(R.drawable.add), contentDescription = "Add food") }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            MealEditorHeader(
                state = state,
                onNameChanged = contract::setName
            )

            Divider()

            if (state.items.isEmpty()) {
                EmptyState(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    onAddFood = onRequestAddFood
                )
                return@Column
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                itemsIndexed(
                    items = state.items,
                    key = { _, item -> item.lineId }
                ) { index, item ->
                    MealEditorItemRow(
                        item = item,
                        onServingsChanged = { text -> contract.updateServings(item.lineId, text) },
                        onGramsChanged = { text -> contract.updateGrams(item.lineId, text) },
                        onMillilitersChanged = { text -> contract.updateMilliliters(item.lineId, text) },
                        onRemove = { contract.removeItem(item.lineId) },
                        onMoveUp = if (index > 0) {
                            { contract.moveItem(index, index - 1) }
                        } else null,
                        onMoveDown = if (index < state.items.lastIndex) {
                            { contract.moveItem(index, index + 1) }
                        } else null
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(84.dp)) // space for FAB
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    modifier: Modifier = Modifier,
    onAddFood: () -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No items yet.",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Add a food to start building this meal.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(onClick = onAddFood) {
            Text("Add food")
        }
    }
}
