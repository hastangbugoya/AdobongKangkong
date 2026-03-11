package com.example.adobongkangkong.ui.daylog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.adobongkangkong.R
import com.example.adobongkangkong.ui.log.QuickAddBottomSheet
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayLogScreen(
    date: LocalDate,
    onBack: () -> Unit,
    onCreateFood: (String) -> Unit,
    onCreateFoodWithBarcode: (String) -> Unit = {},
    onOpenFoodEditor: (Long) -> Unit = {},
    onDelete: ((Long) -> Unit)? = null,
    vm: DayLogViewModel = hiltViewModel()
) {
    val entries by vm.entries.collectAsState()
    val ious by vm.ious.collectAsState()
    val totals by vm.totals.collectAsState()

    val delete = onDelete ?: { id -> vm.deleteEntry(id) }

    var showQuickAdd by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(date) { vm.load(date) }

    if (showQuickAdd) {
        QuickAddBottomSheet(
            onDismiss = { showQuickAdd = false },
            onCreateFood = onCreateFood,
            onCreateFoodWithBarcode = onCreateFoodWithBarcode,
            onOpenFoodEditor = onOpenFoodEditor,
            logDate = date
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(date.toString()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.angle_circle_left),
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showQuickAdd = true }) {
                        Icon(
                            painter = painterResource(R.drawable.add),
                            contentDescription = "Quick add"
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
        ) {
            totals?.let { DayTotalsCard(it) }

            LazyColumn {
                items(
                    items = entries,
                    key = { it.logId }
                ) { row ->
                    DayLogRowCard(
                        row = row,
                        onDelete = { delete(row.logId) }
                    )
                    HorizontalDivider()
                }

                if (ious.isNotEmpty()) {
                    item(key = "iou_header") {
                        Text(
                            text = "IOUs",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }

                    items(
                        items = ious,
                        key = { "iou_${it.iouId}" }
                    ) { row ->
                        DayLogIouRowCard(
                            row = row,
                            onDelete = { vm.deleteIou(row.iouId) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}